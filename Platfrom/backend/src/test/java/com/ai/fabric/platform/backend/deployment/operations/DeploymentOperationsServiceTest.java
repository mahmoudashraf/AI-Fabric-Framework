package com.ai.fabric.platform.backend.deployment.operations;

import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferDeploymentEntity;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Definition;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.ReviewedTopology;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferCodec;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.ai.fabric.platform.backend.deployment.budget.ProviderUsageModeResolver;
import com.ai.fabric.platform.backend.deployment.entity.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static com.ai.fabric.platform.backend.deployment.operations.DeploymentOperationsContracts.*;
import static com.ai.fabric.platform.backend.deployment.operations.DeploymentOperationsProperties.Mode;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeploymentOperationsServiceTest {
    private final DeploymentOperationsProperties properties = new DeploymentOperationsProperties();
    private final DeploymentOperationsClient client = mock(DeploymentOperationsClient.class);
    private final CoreReleaseAuthorizationStore store = mock(CoreReleaseAuthorizationStore.class);
    private final ProviderUsageModeResolver modes = mock(ProviderUsageModeResolver.class);
    private final CustomerOfferDeploymentRepository offers = mock(CustomerOfferDeploymentRepository.class);
    private final CustomerOfferEntitlementService entitlements = mock(CustomerOfferEntitlementService.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final DeploymentOperationsService service = new DeploymentOperationsService(properties, client, store, modes,
        offers, entitlements, mapper, new CustomerOfferCodec(mapper));
    private final DeploymentEntity deployment = new DeploymentEntity();
    private final DeploymentVersionEntity version = new DeploymentVersionEntity();
    private final DeploymentReleaseEntity release = new DeploymentReleaseEntity();

    @BeforeEach void setup() {
        deployment.setId("deployment"); deployment.setCustomerId("customer"); deployment.setTenantId("tenant");
        version.setId("version"); version.setDeploymentId("deployment"); version.setConfigHash("a".repeat(64));
        version.setStatus("PUBLISHED"); version.setProviderConfigJson("{}");
        release.setId("release"); release.setDeploymentId("deployment"); release.setTargetProfileId("staging");
        release.setDeploymentVersionId("version"); release.setSourceArtifactId("artifact");
        when(modes.resolve(deployment, "staging")).thenReturn("TEAM_AUTHORIZED");
        var offer = new CustomerOfferEntity("offer", "{}", "hash", "review", "admin", Instant.now());
        var definition = mock(Definition.class);
        when(definition.topology()).thenReturn(new ReviewedTopology(1, 2000, 4096, 6144, 1, 2, 1, 1));
        when(definition.aiStateStorageTopologies()).thenReturn(Map.of());
        when(entitlements.requireOffer("offer")).thenReturn(offer);
        when(entitlements.definition(offer)).thenReturn(definition);
        when(offers.findById("deployment")).thenReturn(Optional.of(new CustomerOfferDeploymentEntity(
            "deployment", "customer", "tenant", "offer", "staging", "hash", 1, "bootstrap", Instant.now())));
    }

    @Test void disabledDoesNotCallPolicyAndPersistsRealCoreAuthorityNotReservation() {
        var result = service.authorize(deployment, version, release, "command");
        assertThat(result.status()).isEqualTo("BYPASSED");
        assertThat(result.reason()).isEqualTo("DISABLED_MANUAL_TEAM");
        verifyNoInteractions(client, offers, entitlements);
        verify(store).save(deployment, version, release, "command", "TEAM_AUTHORIZED", result);
    }

    @Test void unavailableEnforcementPermitsTheCoreAuthorizedCommandOnce() {
        properties.setMode(Mode.ENFORCE);
        when(client.assess(any())).thenThrow(new DeploymentOperationsClient.OperationsUnavailableException());
        var result = service.authorize(deployment, version, release, "command");
        assertThat(result.reason()).isEqualTo("OPERATIONS_UNAVAILABLE");
        assertThat(result.status()).isEqualTo("BYPASSED");
        verify(client, times(1)).assess(any());
        verify(store).save(deployment, version, release, "command", "TEAM_AUTHORIZED", result);
    }

    @Test void explicitHealthyDenialCannotBecomeAnOutageBypass() {
        properties.setMode(Mode.ENFORCE);
        when(client.assess(any())).thenAnswer(call -> response(call.getArgument(0), Decision.DENY, "CAPACITY_EXCEEDED"));
        assertThatThrownBy(() -> service.authorize(deployment, version, release, "command"))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("DEPLOYMENT_OPERATIONS_DENIED");
        verify(client, times(1)).assess(any()); verifyNoInteractions(store);
    }

    @Test void observeRecordsADenialWithoutBlockingTheCommand() {
        properties.setMode(Mode.OBSERVE);
        when(client.assess(any())).thenAnswer(call -> response(call.getArgument(0), Decision.DENY, "CAPACITY_EXCEEDED"));
        var result = service.authorize(deployment, version, release, "command");
        assertThat(result.status()).isEqualTo("OBSERVED");
        assertThat(result.reason()).isEqualTo("CAPACITY_EXCEEDED");
        verify(store).save(deployment, version, release, "command", "TEAM_AUTHORIZED", result);
    }

    @Test void exactAllowCarriesPolicyIdentityAndReviewedDemand() {
        properties.setMode(Mode.ENFORCE);
        when(client.assess(any())).thenAnswer(call -> {
            AssessmentRequest request = call.getArgument(0);
            assertThat(request.resourceDemand()).isEqualTo(new ResourceDemand(2000,4096,6144,1,2,1,1,1));
            assertThat(request.customerId()).isEqualTo("customer"); assertThat(request.tenantId()).isEqualTo("tenant");
            return response(request, Decision.ALLOW, "CAPACITY_AVAILABLE");
        });
        var result = service.authorize(deployment, version, release, "command");
        assertThat(result.status()).isEqualTo("ALLOWED"); assertThat(result.assessmentId()).isEqualTo("assessment");
    }

    @Test void mismatchedOrExpiredResponsesAreUnavailableNotTrustedDenials() {
        properties.setMode(Mode.ENFORCE);
        when(client.assess(any())).thenReturn(new AssessmentResponse("1", "different-release", "b".repeat(64),
            "assessment", Decision.DENY, "CAPACITY_EXCEEDED", "policy", Instant.now(), Instant.now().minusSeconds(1)));
        assertThat(service.authorize(deployment, version, release, "command").reason()).isEqualTo("OPERATIONS_UNAVAILABLE");
    }

    @Test void financialOrOwnershipFailuresAreNotOptionalServiceOutages() {
        properties.setMode(Mode.ENFORCE);
        when(modes.resolve(any(), any())).thenThrow(new IllegalStateException("CREDENTIAL_OWNER_MISMATCH"));
        assertThatThrownBy(() -> service.authorize(deployment, version, release, "command"))
            .hasMessage("CREDENTIAL_OWNER_MISMATCH");
        verifyNoInteractions(client, store);
    }

    private AssessmentResponse response(AssessmentRequest request, Decision decision, String reason) {
        return new AssessmentResponse("1", request.commandId(), request.requestFingerprint(), "assessment", decision,
            reason, "policy1", Instant.now(), Instant.now().plusSeconds(60));
    }
}
