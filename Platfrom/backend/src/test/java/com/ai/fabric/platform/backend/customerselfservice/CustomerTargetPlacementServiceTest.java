package com.ai.fabric.platform.backend.customerselfservice;

import com.ai.fabric.platform.backend.customerselfservice.config.CustomerSelfServiceProperties;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerTargetPlacementService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerReviewedCompositionService;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerPlacementEvidenceEntity;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerPlacementEvidenceRepository;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.model.*;
import com.ai.fabric.platform.backend.deployment.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerTargetPlacementServiceTest {
    private final CoolifyTargetProfileResolver resolver = mock(CoolifyTargetProfileResolver.class);
    private final CoolifyApiClient client = mock(CoolifyApiClient.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final CustomerReviewedCompositionService composition = mock(CustomerReviewedCompositionService.class);
    private final CustomerPlacementEvidenceRepository evidenceRepository = mock(CustomerPlacementEvidenceRepository.class);
    private CustomerTargetPlacementService service;
    private DeploymentTargetProfileEntity profile;
    private CoolifyTargetProfileConfig config;
    private CoolifyConnection connection;
    private ObjectNode details;
    private ObjectNode server;

    @BeforeEach
    void setup() {
        service = new CustomerTargetPlacementService(new CustomerSelfServiceProperties(Map.of(
            TargetIntent.CUSTOMER_STAGING, new CustomerSelfServiceProperties.TrustedServer("stage-server", "46.224.145.148"),
            TargetIntent.CUSTOMER_PRODUCTION, new CustomerSelfServiceProperties.TrustedServer("prod-server", "46.225.162.106")), 60),
            resolver, client, composition, CustomerOfferTestFixtures.codec(), evidenceRepository);
        profile = CustomerOfferTestFixtures.profile("staging-target", "stage-server");
        config = mock(CoolifyTargetProfileConfig.class);
        when(config.serverUuid()).thenReturn("stage-server");
        when(config.destinationUuid()).thenReturn("destination");
        when(composition.profileFingerprint(profile)).thenReturn("profile-hash");
        when(evidenceRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        connection = new CoolifyConnection("https://provider.test", "PRIVATE_TOKEN", config);
        when(resolver.requireConnection(profile)).thenReturn(connection);
        details = mapper.createObjectNode().put("serverUuid", "stage-server").put("destinationServerUuid", "stage-server")
            .put("serverReachable", true).put("serverUsable", true).put("destinationUuid", "destination");
        preflight("PASSED", Instant.now().minusSeconds(1));
        server = mapper.createObjectNode().put("uuid", "stage-server").put("ip", "46.224.145.148");
        server.putObject("settings").put("is_reachable", true).put("is_usable", true);
        when(client.getServer(connection, "stage-server")).thenReturn(server);
    }

    @Test
    void acceptsOnlyCurrentVerifiedPhysicalPlacement() {
        profile.setEnvironmentName("production");
        var evidence = service.probeAndRecord(profile, TargetIntent.CUSTOMER_STAGING);
        assertThat(evidence.getProfileFingerprint()).isEqualTo("profile-hash");
        assertThat(evidence.getDestinationUuid()).isEqualTo("destination");
        verify(client).getServer(connection, "stage-server");
    }

    @Test
    void mislabeledProductionServerNeverBecomesCustomerStaging() {
        profile.setId("dtp-coolify-prod-staging");
        profile.setEnvironmentName("staging");
        when(config.serverUuid()).thenReturn("prod-server");
        assertCode(PLACEMENT_DENIED);
        verifyNoInteractions(client);
    }

    @Test
    void actualProductionIpIsDeniedEvenWithCorrectConfiguredUuid() {
        server.put("ip", "46.225.162.106");
        assertCode(PLACEMENT_DENIED);
    }

    @Test
    void actualUuidCannotBeGuessedFromTheProfile() {
        server.put("uuid", "other-server");
        assertCode(PLACEMENT_DENIED);
    }

    @Test
    void destinationMismatchDenies() {
        details.put("destinationServerUuid", "prod-server");
        assertCode(PREFLIGHT_REQUIRED);
    }

    @Test
    void staleWarningAndMissingPreflightDeny() {
        preflight("PASSED", Instant.now().minusSeconds(120));
        assertCode(PREFLIGHT_REQUIRED);
        preflight("WARNING", Instant.now().minusSeconds(1));
        assertCode(PREFLIGHT_REQUIRED);
        when(resolver.preflight(profile)).thenReturn(null);
        assertCode(PREFLIGHT_REQUIRED);
    }

    @Test
    void unavailableCredentialsDoNotLeakProviderErrorsOrFallback() {
        when(resolver.requireConnection(profile)).thenThrow(new IllegalStateException("PRIVATE_CREDENTIAL_REF"));
        assertCode(PREFLIGHT_REQUIRED);
    }

    @Test
    void missingTrustedConfigurationAndSharedServerTrustDeny() {
        service = new CustomerTargetPlacementService(new CustomerSelfServiceProperties(null, 0), resolver, client, composition,
            CustomerOfferTestFixtures.codec(), evidenceRepository);
        assertCode(TRUSTED_PLACEMENT_REQUIRED);
        var trusted = new CustomerSelfServiceProperties.TrustedServer("prod-server", "46.225.162.106");
        service = new CustomerTargetPlacementService(new CustomerSelfServiceProperties(Map.of(
            TargetIntent.CUSTOMER_STAGING, trusted, TargetIntent.CUSTOMER_PRODUCTION, trusted), 60), resolver, client, composition,
            CustomerOfferTestFixtures.codec(), evidenceRepository);
        assertCode(TRUSTED_PLACEMENT_REQUIRED);
    }

    @Test void cachedAdmissionValidationHasNoProviderCallsAndMissingEvidenceDenies() {
        var proof = proof("profile-hash", "stage-server", "46.224.145.148", "destination", Instant.now().minusSeconds(1), Instant.now().plusSeconds(59));
        cached(proof);
        assertThat(service.requireFreshEvidence(profile, TargetIntent.CUSTOMER_STAGING)).isEqualTo(proof);
        verifyNoInteractions(resolver, client);
        cached(null);
        assertCachedCode(PLACEMENT_EVIDENCE_REQUIRED);
        verifyNoInteractions(resolver, client);
    }

    @Test void staleExpiredAndFutureEvidenceDenyWithoutRefreshingInsideAdmission() {
        cached(proof("profile-hash", "stage-server", "46.224.145.148", "destination", Instant.now().minusSeconds(120), Instant.now().plusSeconds(10)));
        assertCachedCode(PLACEMENT_EVIDENCE_STALE);
        cached(proof("profile-hash", "stage-server", "46.224.145.148", "destination", Instant.now().minusSeconds(1), Instant.now().minusSeconds(1)));
        assertCachedCode(PLACEMENT_EVIDENCE_STALE);
        cached(proof("profile-hash", "stage-server", "46.224.145.148", "destination", Instant.now().plusSeconds(1), Instant.now().plusSeconds(60)));
        assertCachedCode(PLACEMENT_EVIDENCE_STALE);
        verifyNoInteractions(resolver, client);
    }

    @Test void profileFingerprintServerAddressUuidAndDestinationMismatchDeny() {
        cached(proof("old-profile-hash", "stage-server", "46.224.145.148", "destination", Instant.now().minusSeconds(1), Instant.now().plusSeconds(59)));
        assertCachedCode(PLACEMENT_DENIED);
        cached(proof("profile-hash", "prod-server", "46.224.145.148", "destination", Instant.now().minusSeconds(1), Instant.now().plusSeconds(59)));
        assertCachedCode(PLACEMENT_DENIED);
        cached(proof("profile-hash", "stage-server", "46.225.162.106", "destination", Instant.now().minusSeconds(1), Instant.now().plusSeconds(59)));
        assertCachedCode(PLACEMENT_DENIED);
        cached(proof("profile-hash", "stage-server", "46.224.145.148", "other-destination", Instant.now().minusSeconds(1), Instant.now().plusSeconds(59)));
        assertCachedCode(PLACEMENT_DENIED);
        verifyNoInteractions(resolver, client);
    }

    private void preflight(String status, Instant checkedAt) {
        when(resolver.preflight(profile)).thenReturn(new DeploymentProviderPreflightSummary(profile.getId(), DeploymentProviderType.COOLIFY,
            status, "not exposed", "https://provider.test", "4", List.of("destination_server_matches"), details, checkedAt));
    }
    private void assertCode(CustomerSelfServiceException.Code code) {
        assertThatThrownBy(() -> service.probeAndRecord(profile, TargetIntent.CUSTOMER_STAGING))
            .isInstanceOfSatisfying(CustomerSelfServiceException.class, ex -> assertThat(ex.code()).isEqualTo(code));
    }
    private void assertCachedCode(CustomerSelfServiceException.Code code) {
        assertThatThrownBy(() -> service.requireFreshEvidence(profile, TargetIntent.CUSTOMER_STAGING))
            .isInstanceOfSatisfying(CustomerSelfServiceException.class, ex -> assertThat(ex.code()).isEqualTo(code));
    }
    private void cached(CustomerPlacementEvidenceEntity evidence) {
        when(evidenceRepository.findFirstByTargetProfileIdAndIntentAndProfileFingerprintOrderByCheckedAtDesc(
            "staging-target", TargetIntent.CUSTOMER_STAGING, "profile-hash")).thenReturn(Optional.ofNullable(evidence));
    }
    private CustomerPlacementEvidenceEntity proof(String hash, String uuid, String address, String destination, Instant checkedAt, Instant expiresAt) {
        return new CustomerPlacementEvidenceEntity("evidence", "staging-target", TargetIntent.CUSTOMER_STAGING, hash, uuid, address, destination, checkedAt, expiresAt);
    }
}
