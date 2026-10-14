package com.ai.fabric.platform.backend.customerselfservice;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerEntitlementCommandEntity;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferEntitlementEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerDeploymentBootstrapContracts.Request;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerDeploymentBootstrapContracts.Result;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerEntitlementCommandRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.*;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.marketplace.entity.DeploymentMarketplacePluginInstallEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.ai.fabric.platform.backend.marketplace.model.CreateMarketplaceTemplateBootstrapRequest;
import com.ai.fabric.platform.backend.marketplace.repository.DeploymentMarketplacePluginInstallRepository;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginVersionRepository;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceTemplateBootstrapService;
import com.ai.fabric.platform.backend.security.PlatformRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.*;
import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.deny;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerDeploymentBootstrapServiceTest {
    private static final String ACTOR = "owner@test.invalid";
    private final CustomerOfferEntitlementService entitlements = mock(CustomerOfferEntitlementService.class);
    private final CustomerSelfServiceAuthorizationService authorization = mock(CustomerSelfServiceAuthorizationService.class);
    private final CustomerEntitlementCommandRepository commands = mock(CustomerEntitlementCommandRepository.class);
    private final MarketplacePluginVersionRepository versions = mock(MarketplacePluginVersionRepository.class);
    private final DeploymentMarketplacePluginInstallRepository installs = mock(DeploymentMarketplacePluginInstallRepository.class);
    private final MarketplaceTemplateBootstrapService templates = mock(MarketplaceTemplateBootstrapService.class);
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final CustomerOfferCodec codec = CustomerOfferTestFixtures.codec();
    private final PlatformAuditService audit = mock(PlatformAuditService.class);
    private final Request request = new Request("staging-target", "Reviewed workspace", 2, "bootstrap-key");
    private CustomerDeploymentBootstrapService service;
    private CustomerOfferEntitlementEntity grant;
    private MarketplacePluginVersionEntity version;
    private DeploymentEntity deployment;
    private Authorization approved;

    @BeforeEach void setup() {
        CustomerOfferTestFixtures.authenticate(ACTOR, PlatformRole.CUSTOMER_ADMIN, "SESSION");
        service = new CustomerDeploymentBootstrapService(entitlements, authorization, commands, versions, installs,
            templates, deployments, codec, audit);
        grant = new CustomerOfferEntitlementEntity();
        grant.setId("grant"); grant.setRevision(2);
        when(entitlements.requireCustomerActor()).thenReturn(new CustomerOfferEntitlementService.CustomerActor("customer", "owner"));
        when(entitlements.requireEntitlement("customer", "offer", "owner", true)).thenReturn(grant);
        var definition = CustomerOfferTestFixtures.definition();
        approved = new Authorization(new AdmissionContext("customer", null, null, null, "staging-target",
            TargetIntent.CUSTOMER_STAGING, "offer", codec.hash(definition), 2, "placement-proof",
            Operation.BOOTSTRAP_CONFIGURATION, request.operationKey(), definition.limits()), definition);
        when(authorization.authorize(eq("customer"), any())).thenReturn(approved);
        version = new MarketplacePluginVersionEntity();
        version.setId("template-v1"); version.setPluginId("template-plugin");
        version.setVersion("1.0.0"); version.setStatus("PUBLISHED");
        when(versions.findById(version.getId())).thenReturn(Optional.of(version));
        deployment = new DeploymentEntity(); deployment.setId("deployment"); deployment.setCustomerId("customer");
        deployment.setTenantId("tenant"); deployment.setStatus("DRAFT"); deployment.setEnvironmentName("staging");
        when(deployments.findById(deployment.getId())).thenReturn(Optional.of(deployment));
        when(templates.bootstrapConfiguration(eq("template-plugin"), any())).thenReturn(new DeploymentSummary(
            deployment.getId(), request.name(), "staging", "reviewed-template", "CONVERSATIONAL", null, null,
            "DRAFT", null, null, false, false, false, Instant.now()));
        when(installs.findByDeploymentIdOrderByUpdatedAtDesc(deployment.getId())).thenReturn(List.of(templateInstall()));
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void delegatesOnlyTheReviewedVersionToConfigurationCreationAndRecordsThePublicResult() {
        Result result = service.bootstrap("offer", request);
        assertThat(result).isEqualTo(new Result("deployment", "tenant", "offer", approved.context().offerFingerprint(),
            "staging-target", 2, "CONFIGURATION_ONLY"));
        var input = ArgumentCaptor.forClass(AuthorizationRequest.class);
        verify(authorization).authorize(eq("customer"), input.capture());
        assertThat(input.getValue()).isEqualTo(new AuthorizationRequest("offer", "staging-target", null, null, null,
            Operation.BOOTSTRAP_CONFIGURATION, "bootstrap-key", Set.of(Setting.DEPLOYMENT_NAME), null, null, null));
        var settings = ArgumentCaptor.forClass(SettingsPatch.class);
        verify(authorization).validateSettings(eq(approved), settings.capture());
        assertThat(settings.getValue().values()).containsOnlyKeys(Setting.DEPLOYMENT_NAME);
        assertThat(settings.getValue().values().get(Setting.DEPLOYMENT_NAME).asText()).isEqualTo(request.name());
        verify(templates).bootstrapConfiguration("template-plugin", new CreateMarketplaceTemplateBootstrapRequest(
            "1.0.0", request.name(), "staging", "reviewed-template", null, null, null));
        verify(templates, never()).bootstrap(anyString(), any());
        verify(authorization).requirePinnedComposition(approved, "template-v1", Set.of(), Set.of("runtime-artifact"));
        verify(authorization).registerBootstrap(approved, "deployment");
        var saved = ArgumentCaptor.forClass(CustomerEntitlementCommandEntity.class);
        verify(commands).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEntitlementId()).isEqualTo("grant");
        assertThat(saved.getValue().getOperationKey()).isEqualTo(request.operationKey());
        assertThat(saved.getValue().getCommandType()).isEqualTo("BOOTSTRAP_CONFIG");
        assertThat(saved.getValue().getActorId()).isEqualTo(ACTOR);
        assertThat(saved.getValue().getRequestFingerprint()).isEqualTo(fingerprint(request));
        assertThat(codec.read(saved.getValue().getResultJson(), Result.class)).isEqualTo(result);
        assertThat(grant.getRevision()).isEqualTo(2);
        verify(audit).record(eq("CUSTOMER_OFFER_CONFIGURATION_BOOTSTRAPPED"), eq("DEPLOYMENT"), eq("deployment"), anyMap());
    }

    @Test void exactReplayUsesTheStoredResultBeforeCheckingTheNowStaleRevision() {
        Result stored = new Result("original-deployment", "original-tenant", "offer", "original-fingerprint",
            "staging-target", 2, "CONFIGURATION_ONLY");
        grant.setRevision(3);
        when(commands.findByEntitlementIdAndOperationKey("grant", request.operationKey())).thenReturn(Optional.of(
            command("BOOTSTRAP_CONFIG", ACTOR, fingerprint(request), codec.write(stored))));
        assertThat(service.bootstrap("offer", request)).isEqualTo(stored);
        verifyNoInteractions(authorization, versions, installs, templates, deployments, audit);
        verify(commands, never()).saveAndFlush(any());
        verify(entitlements).requireEntitlement("customer", "offer", "owner", true);
    }

    @ParameterizedTest @MethodSource("changedPayloads")
    void sameKeyWithAChangedPayloadConflicts(Request changed) {
        when(commands.findByEntitlementIdAndOperationKey("grant", request.operationKey())).thenReturn(Optional.of(
            command("BOOTSTRAP_CONFIG", ACTOR, fingerprint(request), "{}")));
        assertCode(OPERATION_KEY_CONFLICT, () -> service.bootstrap("offer", changed));
        verifyNoInteractions(authorization, versions, templates, deployments, installs, audit);
        verify(commands, never()).saveAndFlush(any());
    }

    @Test void anotherCommandTypeCannotReplayEvenWithAMatchingFingerprintAndActor() {
        when(commands.findByEntitlementIdAndOperationKey("grant", request.operationKey())).thenReturn(Optional.of(
            command("ACCEPT", ACTOR, fingerprint(request), "{}")));
        assertCode(OPERATION_KEY_CONFLICT, () -> service.bootstrap("offer", request));
        verifyNoInteractions(authorization, templates, audit);
    }

    @Test void anotherRecordedActorCannotReplayEvenWithAMatchingFingerprint() {
        when(commands.findByEntitlementIdAndOperationKey("grant", request.operationKey())).thenReturn(Optional.of(
            command("BOOTSTRAP_CONFIG", "other@test.invalid", fingerprint(request), "{}")));
        assertCode(OPERATION_KEY_CONFLICT, () -> service.bootstrap("offer", request));
        verifyNoInteractions(authorization, templates, audit);
    }

    @Test void staleRevisionCannotCreateANewConfiguration() {
        grant.setRevision(3);
        assertCode(REVISION_CONFLICT, () -> service.bootstrap("offer", request));
        verifyNoInteractions(authorization, templates, audit);
        verify(commands, never()).saveAndFlush(any());
    }

    @ParameterizedTest @MethodSource("entitlementDenials")
    void entitlementDenialHappensBeforeReplayLookup(CustomerSelfServiceException.Code code) {
        when(entitlements.requireEntitlement("customer", "offer", "owner", true)).thenThrow(deny(code));
        assertCode(code, () -> service.bootstrap("offer", request));
        verifyNoInteractions(commands, authorization, templates, audit);
    }

    @Test void missingOrUnpublishedOrForeignTemplateVersionCannotReachTheFactory() {
        when(versions.findById("template-v1")).thenReturn(Optional.empty());
        assertCode(REVIEWED_COMPOSITION_CHANGED, () -> service.bootstrap("offer", request));
        when(versions.findById("template-v1")).thenReturn(Optional.of(version));
        version.setStatus("DRAFT");
        assertCode(REVIEWED_COMPOSITION_CHANGED, () -> service.bootstrap("offer", request));
        version.setStatus("PUBLISHED"); version.setPluginId("another-plugin");
        assertCode(REVIEWED_COMPOSITION_CHANGED, () -> service.bootstrap("offer", request));
        verifyNoInteractions(templates, audit);
        verify(commands, never()).saveAndFlush(any());
    }

    @Test void actualDeploymentMustBeAnOwnedInactiveStagingDraft() {
        when(deployments.findById("deployment")).thenReturn(Optional.empty());
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.bootstrap("offer", request));
        when(deployments.findById("deployment")).thenReturn(Optional.of(deployment));
        deployment.setCustomerId("foreign"); assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.bootstrap("offer", request));
        deployment.setCustomerId("customer"); deployment.setStatus("ACTIVE");
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.bootstrap("offer", request));
        deployment.setStatus("DRAFT"); deployment.setEnvironmentName("production");
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.bootstrap("offer", request));
        deployment.setEnvironmentName("staging"); deployment.setActiveVersionId("live-version");
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.bootstrap("offer", request));
        verify(authorization, never()).registerBootstrap(any(), anyString());
        verify(commands, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @Test void absentDuplicateOrNonBootstrappedTemplateInstallCannotBeRecordedAsSuccess() {
        var install = templateInstall();
        for (var records : List.of(List.<DeploymentMarketplacePluginInstallEntity>of(), List.of(install, install))) {
            when(installs.findByDeploymentIdOrderByUpdatedAtDesc("deployment")).thenReturn(records);
            assertCode(REVIEWED_COMPOSITION_CHANGED, () -> service.bootstrap("offer", request));
        }
        install.setStatus("INSTALLED");
        when(installs.findByDeploymentIdOrderByUpdatedAtDesc("deployment")).thenReturn(List.of(install));
        assertCode(REVIEWED_COMPOSITION_CHANGED, () -> service.bootstrap("offer", request));
        verify(authorization, never()).registerBootstrap(any(), anyString());
        verify(commands, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @Test void compositionCheckReceivesActualInstalledVersionsAndFailurePreventsRecording() {
        var template = templateInstall(); template.setPluginVersionId("wrong-template-version");
        var extra = new DeploymentMarketplacePluginInstallEntity();
        extra.setPluginId("unreviewed-plugin"); extra.setPluginVersionId("unreviewed-version");
        when(installs.findByDeploymentIdOrderByUpdatedAtDesc("deployment")).thenReturn(List.of(template, extra));
        doThrow(deny(REVIEWED_COMPOSITION_CHANGED)).when(authorization).requirePinnedComposition(approved,
            "wrong-template-version", Set.of("unreviewed-version"), Set.of("runtime-artifact"));
        assertCode(REVIEWED_COMPOSITION_CHANGED, () -> service.bootstrap("offer", request));
        verify(authorization, never()).registerBootstrap(any(), anyString());
        verify(commands, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @ParameterizedTest @MethodSource("invalidRequests")
    void malformedRequestCannotResolveAnActorOrGrant(Request invalid) {
        assertCode(INVALID_CONTRACT, () -> service.bootstrap("offer", invalid));
        verifyNoInteractions(entitlements, commands, authorization, templates, audit);
    }

    @Test void contractsRoundTripWithoutAcceptingRawConfigurationFields() {
        assertThat(codec.read(codec.write(request), Request.class)).isEqualTo(request);
        var raw = codec.tree(codec.write(request));
        ((com.fasterxml.jackson.databind.node.ObjectNode) raw).put("sourceRepository", "https://unreviewed.invalid/repo");
        assertCode(INVALID_CONTRACT, () -> codec.read(codec.write(raw), Request.class));
    }

    private DeploymentMarketplacePluginInstallEntity templateInstall() {
        var install = new DeploymentMarketplacePluginInstallEntity();
        install.setPluginId("template-plugin"); install.setPluginVersionId("template-v1"); install.setStatus("BOOTSTRAPPED");
        return install;
    }

    private String fingerprint(Request input) {
        return codec.hash(Map.of("command", "BOOTSTRAP_CONFIG", "offerId", "offer", "request", input,
            "customerId", "customer", "ownerId", "owner", "actor", ACTOR));
    }

    private CustomerEntitlementCommandEntity command(String type, String actor, String hash, String result) {
        return new CustomerEntitlementCommandEntity("command", "grant", request.operationKey(), hash, type, actor, result, Instant.now());
    }

    private void assertCode(CustomerSelfServiceException.Code code, Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(CustomerSelfServiceException.class,
            ex -> assertThat(ex.code()).isEqualTo(code));
    }

    static Stream<Request> changedPayloads() {
        return Stream.of(new Request("staging-target", "Changed name", 2, "bootstrap-key"),
            new Request("production-target", "Reviewed workspace", 2, "bootstrap-key"),
            new Request("staging-target", "Reviewed workspace", 3, "bootstrap-key"));
    }

    static Stream<CustomerSelfServiceException.Code> entitlementDenials() {
        return Stream.of(ENTITLEMENT_REVOKED, ENTITLEMENT_EXPIRED, INVITE_OWNER_MISMATCH);
    }

    static Stream<Request> invalidRequests() {
        return Stream.of(null, new Request(null, "Name", 2, "key"), new Request(" ", "Name", 2, "key"),
            new Request("x".repeat(65), "Name", 2, "key"), new Request("target", null, 2, "key"),
            new Request("target", " ", 2, "key"), new Request("target", " Name", 2, "key"),
            new Request("target", "Name ", 2, "key"), new Request("target", "x".repeat(256), 2, "key"),
            new Request("target", "Name", -1, "key"));
    }
}
