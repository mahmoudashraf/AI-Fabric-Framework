package com.ai.fabric.platform.backend.customerselfservice;

import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerReviewedBindingBoundary;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomerReviewedBindingBoundaryTest {
    private final CustomerOfferDeploymentRepository deployments = mock(CustomerOfferDeploymentRepository.class);
    private final CustomerReviewedBindingBoundary boundary = new CustomerReviewedBindingBoundary(deployments);

    @Test void templateOriginStillRequiresAnExplicitVerifiedRelease() {
        when(deployments.existsById("reviewed")).thenReturn(true);
        assertThatThrownBy(() -> boundary.requireExplicitRuntimeAssignment("reviewed", "production", null))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("verified release and target");
    }

    @Test void updatedVerifiedReleaseIsNotRestrictedToTheOriginalOfferArtifact() {
        when(deployments.existsById("reviewed")).thenReturn(true);
        var release = new com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity();
        release.setDeploymentId("reviewed"); release.setTargetProfileId("production");
        release.setSourceArtifactId("new-runtime-image");
        release.setStatus("APPLIED_VERIFIED"); release.setVerificationStatus("PASSED");
        assertThatCode(() -> boundary.requireExplicitRuntimeAssignment("reviewed", "production", release))
            .doesNotThrowAnyException();
    }

    @Test void aForeignReleaseIsNeverAssignable() {
        when(deployments.existsById("reviewed")).thenReturn(true);
        var release = new com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity();
        release.setDeploymentId("another-deployment"); release.setTargetProfileId("production");
        release.setStatus("APPLIED_VERIFIED"); release.setVerificationStatus("PASSED");
        assertThatThrownBy(() -> boundary.requireExplicitRuntimeAssignment("reviewed", "production", release))
            .isInstanceOf(ResponseStatusException.class);
    }
}
