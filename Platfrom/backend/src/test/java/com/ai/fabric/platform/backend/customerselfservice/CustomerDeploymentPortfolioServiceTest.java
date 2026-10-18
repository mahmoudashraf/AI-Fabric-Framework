package com.ai.fabric.platform.backend.customerselfservice;

import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferDeploymentEntity;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerDeploymentPortfolioService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.RESOURCE_SCOPE_MISMATCH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CustomerDeploymentPortfolioServiceTest {
    private final CustomerOfferEntitlementService access = mock(CustomerOfferEntitlementService.class);
    private final CustomerOfferDeploymentRepository associations = mock(CustomerOfferDeploymentRepository.class);
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final CustomerDeploymentPortfolioService service = new CustomerDeploymentPortfolioService(access, associations, deployments);

    @Test void listsOnlyServerOwnedAssociationsForTheCurrentCustomer() {
        when(access.requireCustomerActor()).thenReturn(new CustomerOfferEntitlementService.CustomerActor("customer", "owner"));
        var createdAt = Instant.parse("2026-10-16T00:00:00Z");
        when(associations.findByCustomerIdOrderByCreatedAtDesc("customer")).thenReturn(List.of(
            new CustomerOfferDeploymentEntity("deployment", "customer", "tenant", "offer", "staging-target", "fingerprint", 3,
                "private-bootstrap-key", createdAt)));
        var deployment = deployment("customer", "tenant");
        when(deployments.findById("deployment")).thenReturn(Optional.of(deployment));

        var result = service.deployments();

        assertThat(result).singleElement().satisfies(summary -> {
            assertThat(summary.deploymentId()).isEqualTo("deployment");
            assertThat(summary.offerId()).isEqualTo("offer");
            assertThat(summary.bootstrapTargetProfileId()).isEqualTo("staging-target");
            assertThat(summary.authorizationEntitlementRevision()).isEqualTo(3);
            assertThat(summary.name()).isEqualTo("Northfield");
        });
        assertThat(result.toString()).doesNotContain("private-bootstrap-key");
        verify(associations).findByCustomerIdOrderByCreatedAtDesc("customer");
    }

    @Test void failsClosedWhenAssociationAndDeploymentOwnershipDiverge() {
        when(access.requireCustomerActor()).thenReturn(new CustomerOfferEntitlementService.CustomerActor("customer", "owner"));
        when(associations.findByCustomerIdOrderByCreatedAtDesc("customer")).thenReturn(List.of(
            new CustomerOfferDeploymentEntity("deployment", "customer", "tenant", "offer", "staging-target", "fingerprint", 3,
                "key", Instant.now())));
        when(deployments.findById("deployment")).thenReturn(Optional.of(deployment("other-customer", "tenant")));

        assertThatThrownBy(service::deployments).isInstanceOfSatisfying(
            com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.class,
            error -> assertThat(error.code()).isEqualTo(RESOURCE_SCOPE_MISMATCH));
    }

    private DeploymentEntity deployment(String customerId, String tenantId) {
        var entity = new DeploymentEntity();
        entity.setId("deployment");
        entity.setName("Northfield");
        entity.setEnvironmentName("staging");
        entity.setStatus("DRAFT");
        entity.setCustomerId(customerId);
        entity.setTenantId(tenantId);
        return entity;
    }
}
