package com.ai.fabric.platform.backend.tenant;

import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerReviewedBindingBoundary;
import com.ai.fabric.platform.backend.deployment.lifecycle.DeploymentTargetMutationService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerConsumerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformConsumerPublicResolutionTest {
    private final PlatformConsumerRepository consumers = mock(PlatformConsumerRepository.class);
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final DeploymentReleaseRepository releases = mock(DeploymentReleaseRepository.class);
    private final CustomerOfferDeploymentRepository offers = mock(CustomerOfferDeploymentRepository.class);
    private final DeploymentTargetMutationService targetMutations = mock(DeploymentTargetMutationService.class);
    private final PlatformCustomerConsumerService service = new PlatformCustomerConsumerService(consumers,
        null, null, deployments, releases, null, null, null, null, null, new ObjectMapper(), targetMutations, null,
        new CustomerReviewedBindingBoundary(offers));
    private final PlatformConsumerEntity consumer = new PlatformConsumerEntity();

    @BeforeEach void setup() {
        consumer.setConsumerId("customer-web"); consumer.setCustomerId("customer"); consumer.setStatus("ACTIVE");
        consumer.setBoundDeploymentId("deployment"); consumer.setBoundTargetProfileId("customer-staging");
        when(consumers.findByConsumerIdIgnoreCase("customer-web")).thenReturn(Optional.of(consumer));
        var deployment = new DeploymentEntity(); deployment.setId("deployment"); deployment.setCustomerId("customer");
        deployment.setRuntimeBaseUrl("https://must-not-use-deployment-wide-latest.example");
        when(deployments.findById("deployment")).thenReturn(Optional.of(deployment));
        when(offers.existsById("deployment")).thenReturn(true);
    }

    @Test void configurationOnlyReviewedConsumerCannotResolveDeploymentWideRuntime() {
        assertThatThrownBy(() -> service.resolvePublicConsumer("customer-web"))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("explicitly activated verified release");
        verifyNoInteractions(targetMutations);
    }

    @Test void reviewedConsumerRequiresItsExactPersistedTargetEvenForAVerifiedRelease() {
        var release = bindVerifiedRelease(); release.setTargetProfileId("customer-production");
        assertThatThrownBy(() -> service.resolvePublicConsumer("customer-web"))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("verified release and target");
        verifyNoInteractions(targetMutations);
    }

    @Test void reviewedConsumerWithExactVerifiedReleaseUsesCanonicalAssignmentWithoutCapacityPolicy() {
        var release = bindVerifiedRelease();
        assertThat(service.resolvePublicConsumer("customer-web").release()).isSameAs(release);
        verifyNoInteractions(targetMutations);
    }

    @Test void nonOfferDeploymentsRetainTheirExistingDiscoveryContract() {
        when(offers.existsById("deployment")).thenReturn(false);
        assertThat(service.resolvePublicConsumer("customer-web").release()).isNull();
    }

    @Test void verifiedReleaseCannotBypassCrossCustomerDeploymentOwnership() {
        bindVerifiedRelease();
        deployments.findById("deployment").orElseThrow().setCustomerId("foreign");
        assertThatThrownBy(() -> service.resolvePublicConsumer("customer-web"))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("ownership");
    }

    @Test void archivedDeploymentCannotRemainAnActivePublicAssignment() {
        bindVerifiedRelease();
        deployments.findById("deployment").orElseThrow().setArchivedAt(java.time.Instant.now());
        assertThatThrownBy(() -> service.resolvePublicConsumer("customer-web"))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("lifecycle");
    }

    @Test void nonOfferReleaseAlsoMustBelongToItsExplicitAssignedTarget() {
        when(offers.existsById("deployment")).thenReturn(false);
        bindVerifiedRelease().setTargetProfileId("foreign-target");
        assertThatThrownBy(() -> service.resolvePublicConsumer("customer-web"))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("target");
    }

    private DeploymentReleaseEntity bindVerifiedRelease() {
        consumer.setBoundReleaseId("release");
        var release = new DeploymentReleaseEntity(); release.setId("release"); release.setDeploymentId("deployment");
        release.setTargetProfileId("customer-staging"); release.setStatus("APPLIED_VERIFIED"); release.setVerificationStatus("PASSED");
        when(releases.findById("release")).thenReturn(Optional.of(release));
        return release;
    }
}
