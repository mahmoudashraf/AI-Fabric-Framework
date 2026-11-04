package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.DeleteDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerConsumerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerEnvironmentDeletionGuardTest {
    @Test void metadataOnlyDeletionIsRefusedBeforeQueueingOrDispatchingEffects() {
        var deployments=mock(DeploymentRepository.class);
        var access=mock(DeploymentAccessService.class);
        var operations=mock(DeploymentDeletionOperationRepository.class);
        var worker=mock(DeploymentDeletionExecutionService.class);
        var quota=mock(CustomerEnvironmentQuotaService.class);
        var d=new DeploymentEntity();d.setId("owned");d.setCustomerId("customer");d.setArchivedAt(Instant.now());
        when(deployments.findByIdForUpdate("owned")).thenReturn(Optional.of(d));
        when(access.requireDeploymentAdminAccess(d)).thenReturn(d);
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"OWNED_INFRASTRUCTURE_REMAINS"))
            .when(quota).requireNoRetainedInfrastructure(d);
        var service=new DeploymentDeletionService(deployments,mock(DeploymentReleaseRepository.class),
            mock(DeploymentManagedVectorResourceRepository.class),access,mock(DeploymentOperationApprovalService.class),
            operations,worker,mock(PlatformAuditService.class),new ObjectMapper(),mock(PlatformCustomerConsumerService.class));
        ReflectionTestUtils.setField(service,"environmentQuota",quota);
        assertThatThrownBy(()->service.queueDeleteDeployment("owned",new DeleteDeploymentRequest(false,null,"Remove old configuration")))
            .hasMessageContaining("OWNED_INFRASTRUCTURE_REMAINS");
        verifyNoInteractions(operations,worker);
        verify(deployments,never()).save(any());
    }
}
