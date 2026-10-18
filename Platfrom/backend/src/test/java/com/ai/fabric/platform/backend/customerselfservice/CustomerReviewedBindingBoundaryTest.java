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

    @Test void genericBindingCannotEnterOrLeaveAReviewedOfferDeployment() {
        when(deployments.existsById("reviewed")).thenReturn(true);

        assertThatThrownBy(() -> boundary.requireGenericMutationAllowed(null, "reviewed"))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Reviewed customer deployments");
        assertThatThrownBy(() -> boundary.requireGenericMutationAllowed("reviewed", null))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Reviewed customer deployments");
    }

    @Test void genericDeploymentsKeepTheirExistingBindingPath() {
        assertThatCode(() -> boundary.requireGenericMutationAllowed("plain-a", "plain-b"))
            .doesNotThrowAnyException();
    }
}
