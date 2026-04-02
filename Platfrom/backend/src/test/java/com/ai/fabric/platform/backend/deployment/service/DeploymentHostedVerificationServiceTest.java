package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.model.DeploymentHostedVerificationContextSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentHostedVerificationDispatchRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentHostedVerificationDispatchSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentHostedVerificationRunRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeploymentHostedVerificationServiceTest {

    @Test
    void dispatchQueuesReadOnlyHostedVerificationRun() {
        DeploymentAccessService accessService = mock(DeploymentAccessService.class);
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentHostedVerificationRunRepository runRepository = mock(DeploymentHostedVerificationRunRepository.class);
        DeploymentHostedVerificationContextService contextService = mock(DeploymentHostedVerificationContextService.class);
        DeploymentHostedVerificationExecutionService executionService = mock(DeploymentHostedVerificationExecutionService.class);
        DeploymentVerificationRolloutService rolloutService = mock(DeploymentVerificationRolloutService.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);

        when(runRepository.existsByDeploymentIdAndStatusIn(any(), any())).thenReturn(false);
        when(runRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(contextService.buildContextForOperator("dep-123", "vector", false)).thenReturn(
            new DeploymentHostedVerificationContextSummary(
                "vector",
                "scripts/verify-vector-deployment.sh",
                "dep-123",
                "rel-123",
                "ver-123",
                false,
                Map.of("RUNTIME_BASE_URL", "https://runtime.example.com")
            )
        );

        DeploymentHostedVerificationService service = new DeploymentHostedVerificationService(
            accessService,
            deploymentRepository,
            runRepository,
            contextService,
            executionService,
            rolloutService,
            auditService,
            new DeploymentHostedVerificationLogParser()
        );

        DeploymentHostedVerificationDispatchSummary summary = service.dispatch(
            "dep-123",
            new DeploymentHostedVerificationDispatchRequest("vector", false)
        );

        assertThat(summary.deploymentId()).isEqualTo("dep-123");
        assertThat(summary.releaseId()).isEqualTo("rel-123");
        assertThat(summary.deploymentVersionId()).isEqualTo("ver-123");
        assertThat(summary.profile()).isEqualTo("vector");
        assertThat(summary.verifyWrite()).isFalse();
        assertThat(summary.run().status()).isEqualTo("QUEUED");
        verify(executionService).execute(summary.run().id());
    }

    @Test
    void dispatchRejectsWriteModeForNonCanonicalDeployment() {
        DeploymentAccessService accessService = mock(DeploymentAccessService.class);
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentHostedVerificationRunRepository runRepository = mock(DeploymentHostedVerificationRunRepository.class);
        DeploymentHostedVerificationContextService contextService = mock(DeploymentHostedVerificationContextService.class);
        DeploymentHostedVerificationExecutionService executionService = mock(DeploymentHostedVerificationExecutionService.class);
        DeploymentVerificationRolloutService rolloutService = mock(DeploymentVerificationRolloutService.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);

        when(rolloutService.isCanonicalRolloutDeployment("dep-unsafe")).thenReturn(false);

        DeploymentHostedVerificationService service = new DeploymentHostedVerificationService(
            accessService,
            deploymentRepository,
            runRepository,
            contextService,
            executionService,
            rolloutService,
            auditService,
            new DeploymentHostedVerificationLogParser()
        );

        assertThatThrownBy(() -> service.dispatch(
            "dep-unsafe",
            new DeploymentHostedVerificationDispatchRequest("vector", true)
        )).hasMessageContaining("Write-enabled hosted verification is restricted");
    }
}
