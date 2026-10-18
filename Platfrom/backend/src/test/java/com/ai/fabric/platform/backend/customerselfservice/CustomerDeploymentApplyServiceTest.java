package com.ai.fabric.platform.backend.customerselfservice;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.entity.*;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerDeploymentApplyContracts.Request;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException;
import com.ai.fabric.platform.backend.customerselfservice.repository.*;
import com.ai.fabric.platform.backend.customerselfservice.service.*;
import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.model.DeploymentReleaseSummary;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.security.PlatformRole;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerDeploymentApplyServiceTest {
    private final CustomerOfferEntitlementService entitlements = mock(CustomerOfferEntitlementService.class);
    private final CustomerOfferDeploymentRepository associations = mock(CustomerOfferDeploymentRepository.class);
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final DeploymentVersionRepository versions = mock(DeploymentVersionRepository.class);
    private final CustomerSelfServiceAuthorizationService authorization = mock(CustomerSelfServiceAuthorizationService.class);
    private final CustomerSelfServiceOperationBoundary boundary = mock(CustomerSelfServiceOperationBoundary.class);
    private final CustomerReviewedCompositionService composition = mock(CustomerReviewedCompositionService.class);
    private final CustomerEntitlementCommandRepository commands = mock(CustomerEntitlementCommandRepository.class);
    private final DeploymentService deploymentService = mock(DeploymentService.class);
    private final CustomerOfferCodec codec = CustomerOfferTestFixtures.codec();
    private final PlatformAuditService audit = mock(PlatformAuditService.class);
    private final Request request = new Request("version", 2, "apply-key");
    private CustomerDeploymentApplyService service;
    private CustomerOfferEntitlementEntity grant;
    private Authorization approved;
    private DeploymentReleaseSummary release;

    @BeforeEach void setup() {
        CustomerOfferTestFixtures.authenticate("owner@test.invalid", PlatformRole.CUSTOMER_ADMIN, "SESSION");
        service = new CustomerDeploymentApplyService(entitlements, associations, deployments, versions, authorization,
            boundary, composition, commands, deploymentService, codec, audit);
        when(entitlements.requireCustomerActor()).thenReturn(new CustomerOfferEntitlementService.CustomerActor("customer", "owner"));
        grant = new CustomerOfferEntitlementEntity(); grant.setId("grant"); grant.setRevision(2);
        when(entitlements.requireEntitlement("customer", "offer", "owner", true)).thenReturn(grant);
        var deployment = new DeploymentEntity(); deployment.setId("deployment"); deployment.setCustomerId("customer");
        deployment.setTenantId("tenant");
        when(deployments.findByIdForUpdate("deployment")).thenReturn(Optional.of(deployment));
        when(associations.findById("deployment")).thenReturn(Optional.of(new CustomerOfferDeploymentEntity(
            "deployment", "customer", "tenant", "offer", "staging-target", "fingerprint", 2, "bootstrap", Instant.now())));
        var definition = CustomerOfferTestFixtures.definition();
        var offer = new CustomerOfferEntity("offer", codec.write(definition), "fingerprint", "review", "reviewer", Instant.now());
        when(entitlements.requireOffer("offer")).thenReturn(offer);
        when(entitlements.definition(offer)).thenReturn(definition);
        when(composition.runtimeArtifactId(definition)).thenReturn("runtime-artifact");
        var version = new DeploymentVersionEntity(); version.setId("version"); version.setDeploymentId("deployment");
        version.setStatus("PUBLISHED");
        when(versions.findById("version")).thenReturn(Optional.of(version));
        approved = new Authorization(new AdmissionContext("customer", "tenant", "deployment", null,
            "staging-target", TargetIntent.CUSTOMER_STAGING, "offer", "fingerprint", 2, "placement",
            Operation.APPLY, "apply-key", definition.limits(), definition.topology()), definition);
        when(authorization.authorize(eq("customer"), any())).thenReturn(approved);
        release = new DeploymentReleaseSummary("release", "deployment", "version", "APPLY_REQUESTED", "PENDING",
            "QUEUED", "coolify", "staging-target", null, "runtime-artifact", null, "queue_release",
            "Queued", null, null, null, Instant.now(), Instant.now(), Instant.now());
        when(deploymentService.applyVersionForTrustedCaller("deployment", "version", "staging-target", "runtime-artifact"))
            .thenReturn(release);
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void derivesTargetAndArtifactFromTheReviewedServerAssociation() {
        var replayed = service.apply("offer", "deployment", request);
        assertThat(replayed.id()).isEqualTo(release.id());
        assertThat(replayed.deploymentId()).isEqualTo(release.deploymentId());
        assertThat(replayed.targetProfileId()).isEqualTo(release.targetProfileId());
        assertThat(replayed.sourceArtifactId()).isEqualTo(release.sourceArtifactId());
        assertThat(replayed.status()).isEqualTo(release.status());
        var captured = ArgumentCaptor.forClass(AuthorizationRequest.class);
        verify(authorization).authorize(eq("customer"), captured.capture());
        assertThat(captured.getValue()).isEqualTo(new AuthorizationRequest("offer", "staging-target", "deployment",
            "tenant", null, Operation.APPLY, "apply-key", Set.of(), null, null, null));
        verify(boundary).requireArtifact(approved, "runtime-artifact");
        verify(deploymentService).applyVersionForTrustedCaller("deployment", "version", "staging-target", "runtime-artifact");
        verify(commands).saveAndFlush(any(CustomerEntitlementCommandEntity.class));
        verify(audit).record(eq("CUSTOMER_OFFER_STAGING_VERSION_APPLIED"), eq("DEPLOYMENT_RELEASE"), eq("release"), anyMap());
    }

    @Test void exactReplayDoesNotApplyTwice() {
        String fingerprint = codec.hash(Map.of("command", "APPLY_STAGING_VERSION", "offerId", "offer",
            "deploymentId", "deployment", "request", request, "customerId", "customer", "actor", "owner@test.invalid"));
        when(commands.findByEntitlementIdAndOperationKey("grant", "apply-key")).thenReturn(Optional.of(
            new CustomerEntitlementCommandEntity("command", "grant", "apply-key", fingerprint,
                "APPLY_STAGING_VERSION", "owner@test.invalid", codec.write(release), Instant.now())));
        var replayed = service.apply("offer", "deployment", request);
        assertThat(replayed.id()).isEqualTo(release.id());
        assertThat(replayed.deploymentId()).isEqualTo(release.deploymentId());
        assertThat(replayed.targetProfileId()).isEqualTo(release.targetProfileId());
        assertThat(replayed.sourceArtifactId()).isEqualTo(release.sourceArtifactId());
        assertThat(replayed.status()).isEqualTo(release.status());
        verifyNoInteractions(authorization, boundary, composition, deploymentService, audit);
    }

    @Test void staleRevisionAndAssociationDriftFailClosed() {
        grant.setRevision(3);
        assertCode(REVISION_CONFLICT, () -> service.apply("offer", "deployment", request));
        grant.setRevision(2);
        when(associations.findById("deployment")).thenReturn(Optional.of(new CustomerOfferDeploymentEntity(
            "deployment", "other", "tenant", "offer", "staging-target", "fingerprint", 2, "bootstrap", Instant.now())));
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.apply("offer", "deployment", request));
        verify(deploymentService, never()).applyVersionForTrustedCaller(anyString(), anyString(), anyString(), anyString());
    }

    private void assertCode(CustomerSelfServiceException.Code code, org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(CustomerSelfServiceException.class)
            .satisfies(error -> assertThat(((CustomerSelfServiceException) error).code()).isEqualTo(code));
    }
}
