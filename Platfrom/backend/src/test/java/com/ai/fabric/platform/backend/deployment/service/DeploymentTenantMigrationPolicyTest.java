package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.model.*;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.tenant.entity.*;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerTenantService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeploymentTenantMigrationPolicyTest {
    @Test void tenantCloneInheritsPolicyAndCannotRenameItsEnvironmentToProduction() {
        var deployments = mock(DeploymentRepository.class);
        var drafts = mock(DeploymentDraftRepository.class);
        var access = mock(DeploymentAccessService.class);
        var tenants = mock(PlatformCustomerTenantService.class);
        var vectors = mock(DeploymentTenantScopedVectorService.class);
        var service = new DeploymentTenantMigrationService(deployments, drafts, access,
            mock(DeploymentAssignmentService.class), tenants, vectors, mock(PlatformAuditService.class), new ObjectMapper());
        var source = new DeploymentEntity(); source.setId("source"); source.setName("Source"); source.setEnvironmentName("staging");
        source.setActiveDraftId("draft"); source.restrictToStaging();
        var draft = new DeploymentDraftEntity(); draft.setId("draft"); draft.setProviderConfigJson("{}");
        when(deployments.findById("source")).thenReturn(Optional.of(source));
        when(access.requireDeploymentAdminAccess(source)).thenReturn(source);
        when(drafts.findById("draft")).thenReturn(Optional.of(draft));
        var originalBinding = mock(DeploymentTenantBindingSummary.class);
        when(originalBinding.customerId()).thenReturn("customer"); when(originalBinding.tenantId()).thenReturn("old-tenant");
        when(tenants.summarizeBinding(source)).thenReturn(originalBinding);
        when(tenants.previewBindingForNewDeployment("customer", "next-tenant")).thenReturn(
            new PlatformCustomerTenantService.DeploymentBindingPreview("customer", "Customer", "customer", "next-tenant", "Next", "next", false));
        var customer = new PlatformCustomerEntity(); customer.setId("customer");
        var tenant = new PlatformTenantEntity(); tenant.setId("next-tenant");
        when(tenants.resolveBindingForNewDeployment("Clone", "staging", "customer", "next-tenant"))
            .thenReturn(new PlatformCustomerTenantService.ResolvedDeploymentBinding(customer, tenant, false));
        when(vectors.build(eq(source), any())).thenReturn(mock(DeploymentTenantScopedVectorSummary.class));

        service.createMigrationDeployment("source", new CreateDeploymentTenantMigrationRequest("customer", "next-tenant", "Clone", "staging", "Clone for development"));
        var copy = ArgumentCaptor.forClass(DeploymentEntity.class); verify(deployments).save(copy.capture());
        assertThat(copy.getValue().getEnvironmentPolicy()).isEqualTo(DeploymentEnvironmentPolicy.STAGING_ONLY);
        assertThatThrownBy(() -> service.createMigrationDeployment("source", new CreateDeploymentTenantMigrationRequest(
            "customer", "next-tenant", "Clone", "production", "Try production"))).hasMessageContaining("DEPLOYMENT_STAGING_ONLY");
        verify(deployments, times(1)).save(any());
    }
}
