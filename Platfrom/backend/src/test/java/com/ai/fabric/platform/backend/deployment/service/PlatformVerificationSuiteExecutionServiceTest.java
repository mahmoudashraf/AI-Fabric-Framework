package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformVerificationSuiteProperties;
import com.ai.fabric.platform.backend.deployment.entity.PlatformVerificationSuiteRunEntity;
import com.ai.fabric.platform.backend.deployment.entity.PlatformVerificationSuiteRunStageEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentHostedVerificationRunRepository;
import com.ai.fabric.platform.backend.deployment.repository.PlatformVerificationSuiteRunRepository;
import com.ai.fabric.platform.backend.deployment.repository.PlatformVerificationSuiteRunStageRepository;
import com.ai.fabric.platform.backend.marketplace.model.PlatformManagedInferenceHealthSummary;
import com.ai.fabric.platform.backend.marketplace.model.PlatformManagedInferenceProbeSummary;
import com.ai.fabric.platform.backend.marketplace.service.PlatformManagedInferenceAdminService;
import com.ai.fabric.platform.backend.vectorization.service.VectorizationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformVerificationSuiteExecutionServiceTest {

    @Test
    void executeInlineFailsFastWhenSharedInferenceIsUnhealthy() {
        PlatformVerificationSuiteRunRepository runRepository = mock(PlatformVerificationSuiteRunRepository.class);
        PlatformVerificationSuiteRunStageRepository stageRepository = mock(PlatformVerificationSuiteRunStageRepository.class);
        PlatformManagedInferenceAdminService inferenceAdminService = mock(PlatformManagedInferenceAdminService.class);
        DeploymentVerificationRolloutService rolloutService = mock(DeploymentVerificationRolloutService.class);
        DeploymentService deploymentService = mock(DeploymentService.class);
        DeploymentHostedVerificationService hostedVerificationService = mock(DeploymentHostedVerificationService.class);
        DeploymentHostedVerificationRunRepository hostedRunRepository = mock(DeploymentHostedVerificationRunRepository.class);
        PlatformVerificationSuiteScriptContextService scriptContextService = mock(PlatformVerificationSuiteScriptContextService.class);
        PlatformVerificationScriptRunnerService scriptRunnerService = mock(PlatformVerificationScriptRunnerService.class);
        VectorizationService vectorizationService = mock(VectorizationService.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);

        PlatformVerificationSuiteRunEntity run = new PlatformVerificationSuiteRunEntity();
        run.setId("vsr-123");
        run.setSuiteKey(PlatformVerificationSuiteCatalog.CANONICAL_RELEASE_READINESS_SUITE_KEY);
        run.setSuiteLabel("Canonical release readiness");
        run.setStatus("QUEUED");
        run.setReleaseBlocking(true);
        run.setSummaryMessage("queued");
        run.setRequestedByActorId("actor-1");
        run.setRequestedByRole("PLATFORM_ADMIN");
        run.setCreatedAt(Instant.now());

        PlatformVerificationSuiteRunStageEntity stage1 = new PlatformVerificationSuiteRunStageEntity();
        stage1.setId("vss-1");
        stage1.setSuiteRunId(run.getId());
        stage1.setStageOrder(1);
        stage1.setStageKey("shared-inference-health");
        stage1.setStageLabel("Shared inference service health");
        stage1.setStageType("INFERENCE_SERVICE_HEALTH");
        stage1.setTargetRef(PlatformVerificationSuiteCatalog.SHARED_INFERENCE_SERVICE_REF);
        stage1.setBlocking(true);
        stage1.setStatus("QUEUED");
        stage1.setSummaryMessage("queued");
        stage1.setDetailsJson("{}");
        stage1.setCreatedAt(Instant.now());

        PlatformVerificationSuiteRunStageEntity stage2 = new PlatformVerificationSuiteRunStageEntity();
        stage2.setId("vss-2");
        stage2.setSuiteRunId(run.getId());
        stage2.setStageOrder(2);
        stage2.setStageKey("canonical-rollout-inventory");
        stage2.setStageLabel("Canonical rollout inventory");
        stage2.setStageType("CANONICAL_ROLLOUTS");
        stage2.setTargetRef("canonical-verification-fleet");
        stage2.setBlocking(true);
        stage2.setStatus("QUEUED");
        stage2.setSummaryMessage("queued");
        stage2.setDetailsJson("{}");
        stage2.setCreatedAt(Instant.now());

        when(runRepository.findById(run.getId())).thenReturn(Optional.of(run));
        when(runRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(stageRepository.findBySuiteRunIdOrderByStageOrderAsc(run.getId())).thenReturn(List.of(stage1, stage2));
        when(stageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(inferenceAdminService.getHealth(PlatformVerificationSuiteCatalog.SHARED_INFERENCE_SERVICE_REF)).thenReturn(
            new PlatformManagedInferenceHealthSummary(
                PlatformVerificationSuiteCatalog.SHARED_INFERENCE_SERVICE_REF,
                "FAILED",
                true,
                true,
                "RAILWAY_RESOURCE_DRIFT",
                "Project not found",
                null,
                null,
                null,
                null,
                "FAILED",
                "Project not found",
                new PlatformManagedInferenceProbeSummary("health", "Health", "FAILED", "https://example.com/health", "GET", "Project not found", Instant.now().toString()),
                new PlatformManagedInferenceProbeSummary("inference", "Inference", "FAILED", "https://example.com/v1/chat/completions", "POST", "Project not found", Instant.now().toString())
            )
        );

        PlatformVerificationSuiteExecutionService service = new PlatformVerificationSuiteExecutionService(
            runRepository,
            stageRepository,
            new PlatformVerificationSuiteCatalog(),
            new PlatformVerificationSuiteProperties(Duration.ofMinutes(60), Duration.ofMinutes(12), Duration.ofMinutes(20), Duration.ofMinutes(75), Duration.ofHours(12), Duration.ofMillis(10), 20, 12_000, 80_000, "https://platform-ui.example.test", "weaviate.example.test", "https://bridge.example.test", "shop.example.test", "shopify-bridge-prod", null),
            inferenceAdminService,
            rolloutService,
            deploymentService,
            hostedVerificationService,
            hostedRunRepository,
            scriptContextService,
            scriptRunnerService,
            vectorizationService,
            auditService,
            new ObjectMapper()
        );

        service.executeInline(run.getId(), false);

        assertThat(run.getStatus()).isEqualTo("FAILED");
        assertThat(stage1.getStatus()).isEqualTo("FAILED");
        assertThat(stage1.getSummaryMessage()).contains("Project not found");
        assertThat(stage2.getStatus()).isEqualTo("BLOCKED");

        verify(runRepository, atLeastOnce()).save(run);
        verify(stageRepository, atLeastOnce()).save(stage1);
        verify(stageRepository, atLeastOnce()).save(stage2);
    }
}
