package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferDeploymentEntity;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceAuthorizationService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation.*;
import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.RESOURCE_SCOPE_MISMATCH;
import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.deny;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CustomerSelfServicePromotionBoundaryTest {
    private final CustomerOfferDeploymentRepository associations = mock(CustomerOfferDeploymentRepository.class);
    private final CustomerSelfServiceAuthorizationService authorization = mock(CustomerSelfServiceAuthorizationService.class);
    private final CustomerSelfServicePromotionBoundary boundary = new CustomerSelfServicePromotionBoundary(associations, authorization);

    @Test void genericDeploymentPromotionUsesActualCustomerTenantTargetAndReleaseAuthority() {
        boundary.require(deployment(), "production-target", "release", null, PROMOTION_APPLY, "promote");
        verify(authorization).authorize(eq("customer"), argThat(request -> request.offerId() == null
            && request.deploymentId().equals("deployment") && request.tenantId().equals("tenant")
            && request.targetProfileId().equals("production-target") && request.releaseId().equals("release")
            && request.operation() == PROMOTION_APPLY && request.operationKey().equals("promote")));
    }

    @Test void originalOfferIsOnlyProvenanceAndDoesNotPinReplacementArtifact() {
        when(associations.findById("deployment")).thenReturn(Optional.of(new CustomerOfferDeploymentEntity(
            "deployment", "customer", "tenant", "old-offer", "old-staging-target", "old-fingerprint", 1,
            "bootstrap", Instant.now())));
        boundary.require(deployment(), "production-target", null, "new-image-artifact", APPLY, "apply");
        verify(authorization).authorize(eq("customer"), argThat(request -> request.offerId().equals("old-offer")
            && request.targetProfileId().equals("production-target") && request.operation() == APPLY));
        verifyNoMoreInteractions(authorization);
    }

    @Test void authorizationDenialCannotBecomeAPromotionBypass() {
        when(authorization.authorize(eq("customer"), any())).thenThrow(deny(RESOURCE_SCOPE_MISMATCH));
        assertThatThrownBy(() -> boundary.require(deployment(), "production-target", "foreign-release",
            "new-image", PROMOTION_APPLY, "promote")).hasMessageContaining(RESOURCE_SCOPE_MISMATCH.name());
    }

    private DeploymentEntity deployment() {
        var entity = new DeploymentEntity(); entity.setId("deployment"); entity.setCustomerId("customer");
        entity.setTenantId("tenant"); return entity;
    }
}
