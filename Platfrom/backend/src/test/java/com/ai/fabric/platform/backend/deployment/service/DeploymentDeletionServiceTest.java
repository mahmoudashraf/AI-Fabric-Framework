package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentDeletionOperationEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentDeletionOperationRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentManagedVectorResourceRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeploymentDeletionServiceTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void retryFailedOperationQueuesFreshOperationAndClearsDeploymentFailureState() throws Exception {
        authenticateAdmin();

        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentManagedVectorResourceRepository managedVectorResourceRepository = mock(DeploymentManagedVectorResourceRepository.class);
        DeploymentAccessService deploymentAccessService = mock(DeploymentAccessService.class);
        DeploymentOperationApprovalService approvalService = mock(DeploymentOperationApprovalService.class);
        DeploymentDeletionOperationRepository deletionOperationRepository = mock(DeploymentDeletionOperationRepository.class);
        DeploymentDeletionExecutionService executionService = mock(DeploymentDeletionExecutionService.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);

        DeploymentDeletionService service = new DeploymentDeletionService(
            deploymentRepository,
            releaseRepository,
            managedVectorResourceRepository,
            deploymentAccessService,
            approvalService,
            deletionOperationRepository,
            executionService,
            auditService,
            new ObjectMapper()
        );

        DeploymentDeletionOperationEntity failedOperation = failedOperation();
        DeploymentEntity deployment = deployment();

        when(deletionOperationRepository.findById("del-failed")).thenReturn(Optional.of(failedOperation));
        when(deploymentRepository.findByIdForUpdate("dep-123")).thenReturn(Optional.of(deployment));
        when(deploymentAccessService.requireDeploymentAdminAccess(deployment)).thenReturn(deployment);
        when(releaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc("dep-123")).thenReturn(Optional.empty());
        when(deletionOperationRepository.save(any(DeploymentDeletionOperationEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(deploymentRepository.save(any(DeploymentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(managedVectorResourceRepository.findByDeploymentIdOrderByUpdatedAtDesc("dep-123")).thenReturn(List.of());

        var summary = service.retryFailedOperation("del-failed");

        assertThat(summary.status()).isEqualTo("QUEUED");
        assertThat(summary.deploymentId()).isEqualTo("dep-123");
        assertThat(summary.requestedByActorId()).isEqualTo("admin@example.com");
        assertThat(summary.requestDetails().path("retryOfOperationId").asText()).isEqualTo("del-failed");
        assertThat(summary.resultDetails().path("retryOfOperationId").asText()).isEqualTo("del-failed");

        ArgumentCaptor<DeploymentDeletionOperationEntity> operationCaptor =
            ArgumentCaptor.forClass(DeploymentDeletionOperationEntity.class);
        verify(deletionOperationRepository).save(operationCaptor.capture());
        DeploymentDeletionOperationEntity queuedOperation = operationCaptor.getValue();
        assertThat(queuedOperation.getId()).startsWith("del-");
        assertThat(queuedOperation.getId()).isNotEqualTo("del-failed");
        assertThat(queuedOperation.getStatus()).isEqualTo("QUEUED");
        assertThat(queuedOperation.getRequestedByRole()).isEqualTo("PLATFORM_ADMIN");

        ArgumentCaptor<DeploymentEntity> deploymentCaptor = ArgumentCaptor.forClass(DeploymentEntity.class);
        verify(deploymentRepository).save(deploymentCaptor.capture());
        DeploymentEntity updatedDeployment = deploymentCaptor.getValue();
        assertThat(updatedDeployment.getDeletionStatus()).isEqualTo("QUEUED");
        assertThat(updatedDeployment.getDeletionFailureMessage()).isNull();
        assertThat(updatedDeployment.getDeletionOperationId()).isEqualTo(queuedOperation.getId());

        verify(executionService).executeDeletion(queuedOperation.getId());
    }

    @Test
    void retryFailedOperationRejectsNonFailedOperation() {
        authenticateAdmin();

        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentManagedVectorResourceRepository managedVectorResourceRepository = mock(DeploymentManagedVectorResourceRepository.class);
        DeploymentAccessService deploymentAccessService = mock(DeploymentAccessService.class);
        DeploymentOperationApprovalService approvalService = mock(DeploymentOperationApprovalService.class);
        DeploymentDeletionOperationRepository deletionOperationRepository = mock(DeploymentDeletionOperationRepository.class);
        DeploymentDeletionExecutionService executionService = mock(DeploymentDeletionExecutionService.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);

        DeploymentDeletionService service = new DeploymentDeletionService(
            deploymentRepository,
            releaseRepository,
            managedVectorResourceRepository,
            deploymentAccessService,
            approvalService,
            deletionOperationRepository,
            executionService,
            auditService,
            new ObjectMapper()
        );

        DeploymentDeletionOperationEntity succeededOperation = failedOperation();
        succeededOperation.setStatus("SUCCEEDED");
        when(deletionOperationRepository.findById("del-done")).thenReturn(Optional.of(succeededOperation));

        assertThatThrownBy(() -> service.retryFailedOperation("del-done"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Only failed deletion operations can be retried");

        verify(executionService, never()).executeDeletion(any());
    }

    private void authenticateAdmin() {
        PlatformPrincipal principal = new PlatformPrincipal(
            "admin@example.com",
            PlatformRole.PLATFORM_ADMIN,
            "Admin",
            "SESSION"
        );
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            principal,
            null,
            List.of(new SimpleGrantedAuthority(principal.role().authority()))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private DeploymentDeletionOperationEntity failedOperation() {
        DeploymentDeletionOperationEntity entity = new DeploymentDeletionOperationEntity();
        entity.setId("del-failed");
        entity.setDeploymentId("dep-123");
        entity.setDeploymentName("Deletion Retry Smoke");
        entity.setEnvironmentName("dev");
        entity.setCustomerId("cus-123");
        entity.setTenantId("ten-123");
        entity.setStatus("FAILED");
        entity.setHardDelete(true);
        entity.setRequestReason("retry hard delete");
        entity.setRequestedByActorId("prior-admin@example.com");
        entity.setRequestedByRole("PLATFORM_ADMIN");
        entity.setRequestDetailsJson("{\"deploymentId\":\"dep-123\"}");
        entity.setResultDetailsJson(Map.of().toString());
        entity.setErrorMessage("Timed out");
        entity.setCreatedAt(Instant.parse("2026-04-06T10:00:00Z"));
        entity.setStartedAt(Instant.parse("2026-04-06T10:00:05Z"));
        entity.setCompletedAt(Instant.parse("2026-04-06T10:01:00Z"));
        entity.setUpdatedAt(Instant.parse("2026-04-06T10:01:00Z"));
        return entity;
    }

    private DeploymentEntity deployment() {
        DeploymentEntity entity = new DeploymentEntity();
        entity.setId("dep-123");
        entity.setName("Deletion Retry Smoke");
        entity.setEnvironmentName("dev");
        entity.setTemplateId("custom-start-from-scratch");
        entity.setStatus("ARCHIVED");
        entity.setCustomerId("cus-123");
        entity.setTenantId("ten-123");
        entity.setArchivedAt(Instant.parse("2026-04-06T09:50:00Z"));
        entity.setDeletionStatus("FAILED");
        entity.setDeletionOperationId("del-failed");
        entity.setDeletionFailureMessage("Timed out");
        entity.setCreatedAt(Instant.parse("2026-04-06T09:00:00Z"));
        entity.setUpdatedAt(Instant.parse("2026-04-06T10:01:00Z"));
        return entity;
    }
}
