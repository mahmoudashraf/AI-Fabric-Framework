package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformVerificationSuiteProperties;
import com.ai.fabric.platform.backend.deployment.entity.PlatformVerificationSuiteRunEntity;
import com.ai.fabric.platform.backend.deployment.entity.PlatformVerificationSuiteRunStageEntity;
import com.ai.fabric.platform.backend.deployment.model.PlatformVerificationSuiteDispatchRequest;
import com.ai.fabric.platform.backend.deployment.model.PlatformVerificationSuiteDispatchSummary;
import com.ai.fabric.platform.backend.deployment.model.PlatformVerificationReleaseGateSummary;
import com.ai.fabric.platform.backend.deployment.repository.PlatformVerificationSuiteRunRepository;
import com.ai.fabric.platform.backend.deployment.repository.PlatformVerificationSuiteRunStageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformVerificationSuiteServiceTest {

    @Test
    void dispatchQueuesCanonicalReleaseReadinessSuite() {
        PlatformVerificationSuiteRunRepository runRepository = mock(PlatformVerificationSuiteRunRepository.class);
        PlatformVerificationSuiteRunStageRepository stageRepository = mock(PlatformVerificationSuiteRunStageRepository.class);
        PlatformVerificationSuiteExecutionService executionService = mock(PlatformVerificationSuiteExecutionService.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);

        when(runRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());
        when(runRepository.existsBySuiteKeyAndStatusIn(any(), any())).thenReturn(false);
        when(runRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(stageRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PlatformVerificationSuiteService service = new PlatformVerificationSuiteService(
            new PlatformVerificationSuiteCatalog(),
            runRepository,
            stageRepository,
            executionService,
            new PlatformVerificationSuiteProperties(Duration.ofMinutes(60), Duration.ofMinutes(12), Duration.ofMinutes(20), Duration.ofMinutes(75), Duration.ofHours(12), Duration.ofSeconds(3), 20, 12_000, 80_000, "https://platform-ui.example.test", "weaviate.example.test", "https://bridge.example.test", "shop.example.test", "shopify-bridge-prod", null),
            auditService,
            new ObjectMapper()
        );

        PlatformVerificationSuiteDispatchSummary summary = service.dispatch(
            PlatformVerificationSuiteCatalog.FULL_PLATFORM_RELEASE_READINESS_SUITE_KEY,
            new PlatformVerificationSuiteDispatchRequest(false)
        );

        assertThat(summary.suiteKey()).isEqualTo(PlatformVerificationSuiteCatalog.FULL_PLATFORM_RELEASE_READINESS_SUITE_KEY);
        assertThat(summary.run().status()).isEqualTo("QUEUED");
        assertThat(summary.run().stages()).hasSize(12);
        assertThat(summary.run().stages().getFirst().stageKey()).isEqualTo("shared-inference-health");
        assertThat(summary.run().stages().getLast().targetRef()).isEqualTo("weaviate");

        verify(executionService).execute(summary.run().id(), false);
        verify(runRepository).save(any(PlatformVerificationSuiteRunEntity.class));
        verify(stageRepository).saveAll(any(List.class));
    }

    @Test
    void dispatchRecoversActiveRunWhenSuiteDefinitionChanged() {
        PlatformVerificationSuiteRunRepository runRepository = mock(PlatformVerificationSuiteRunRepository.class);
        PlatformVerificationSuiteRunStageRepository stageRepository = mock(PlatformVerificationSuiteRunStageRepository.class);
        PlatformVerificationSuiteExecutionService executionService = mock(PlatformVerificationSuiteExecutionService.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);

        PlatformVerificationSuiteRunEntity activeRun = new PlatformVerificationSuiteRunEntity();
        activeRun.setId("vsr-old");
        activeRun.setSuiteKey(PlatformVerificationSuiteCatalog.FULL_PLATFORM_RELEASE_READINESS_SUITE_KEY);
        activeRun.setSuiteLabel("Full platform release readiness");
        activeRun.setStatus("RUNNING");
        activeRun.setReleaseBlocking(true);
        activeRun.setCreatedAt(Instant.now().minus(Duration.ofMinutes(5)));
        activeRun.setStartedAt(Instant.now().minus(Duration.ofMinutes(5)));

        PlatformVerificationSuiteRunStageEntity staleStage = new PlatformVerificationSuiteRunStageEntity();
        staleStage.setId("vss-old");
        staleStage.setSuiteRunId(activeRun.getId());
        staleStage.setStageOrder(1);
        staleStage.setStageKey("platform-code-regression");
        staleStage.setStageLabel("Platform code regression");
        staleStage.setStageType("SCRIPT_VERIFICATION");
        staleStage.setTargetRef("platform-code-regression");
        staleStage.setBlocking(true);
        staleStage.setStatus("RUNNING");
        staleStage.setCreatedAt(Instant.now().minus(Duration.ofMinutes(5)));

        when(runRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(activeRun));
        when(runRepository.existsBySuiteKeyAndStatusIn(any(), any())).thenReturn(false);
        when(runRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(stageRepository.findBySuiteRunIdOrderByStageOrderAsc(activeRun.getId())).thenReturn(List.of(staleStage));
        when(stageRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PlatformVerificationSuiteService service = new PlatformVerificationSuiteService(
            new PlatformVerificationSuiteCatalog(),
            runRepository,
            stageRepository,
            executionService,
            new PlatformVerificationSuiteProperties(Duration.ofMinutes(180), Duration.ofMinutes(12), Duration.ofMinutes(20), Duration.ofMinutes(75), Duration.ofHours(12), Duration.ofSeconds(3), 20, 12_000, 80_000, "https://platform-ui.example.test", "weaviate.example.test", "https://bridge.example.test", "shop.example.test", "shopify-bridge-prod", null),
            auditService,
            new ObjectMapper()
        );

        PlatformVerificationSuiteDispatchSummary summary = service.dispatch(
            PlatformVerificationSuiteCatalog.FULL_PLATFORM_RELEASE_READINESS_SUITE_KEY,
            new PlatformVerificationSuiteDispatchRequest(false)
        );

        ArgumentCaptor<PlatformVerificationSuiteRunEntity> runCaptor = ArgumentCaptor.forClass(PlatformVerificationSuiteRunEntity.class);
        verify(runRepository, atLeast(2)).save(runCaptor.capture());
        PlatformVerificationSuiteRunEntity recoveredRun = runCaptor.getAllValues().stream()
            .filter(savedRun -> "vsr-old".equals(savedRun.getId()))
            .reduce((first, second) -> second)
            .orElseThrow();
        assertThat(recoveredRun.getStatus()).isEqualTo("SUPERSEDED");
        assertThat(recoveredRun.getCompletedAt()).isNotNull();
        verify(stageRepository).save(argThat(stage ->
            "vss-old".equals(stage.getId())
                && "SUPERSEDED".equals(stage.getStatus())
                && stage.getCompletedAt() != null
        ));
        assertThat(summary.run().stages()).hasSize(12);
        verify(executionService).execute(summary.run().id(), false);
    }

    @Test
    void releaseGateReportsReadyWhenLatestFullSuitePassIsFresh() {
        PlatformVerificationSuiteRunRepository runRepository = mock(PlatformVerificationSuiteRunRepository.class);
        PlatformVerificationSuiteRunStageRepository stageRepository = mock(PlatformVerificationSuiteRunStageRepository.class);
        PlatformVerificationSuiteExecutionService executionService = mock(PlatformVerificationSuiteExecutionService.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);

        PlatformVerificationSuiteRunEntity run = new PlatformVerificationSuiteRunEntity();
        run.setId("vsr-ready");
        run.setSuiteKey(PlatformVerificationSuiteCatalog.FULL_PLATFORM_RELEASE_READINESS_SUITE_KEY);
        run.setSuiteLabel("Full platform release readiness");
        run.setStatus("PASSED");
        run.setReleaseBlocking(true);
        run.setSummaryMessage("ok");
        run.setRequestedByActorId("admin");
        run.setRequestedByRole("PLATFORM_ADMIN");
        run.setCreatedAt(Instant.now().minus(Duration.ofMinutes(10)));
        run.setStartedAt(Instant.now().minus(Duration.ofMinutes(9)));
        run.setCompletedAt(Instant.now().minus(Duration.ofMinutes(5)));

        when(runRepository.findTopBySuiteKeyOrderByCreatedAtDesc(PlatformVerificationSuiteCatalog.FULL_PLATFORM_RELEASE_READINESS_SUITE_KEY))
            .thenReturn(Optional.of(run));
        when(stageRepository.findBySuiteRunIdOrderByStageOrderAsc(run.getId())).thenReturn(List.of());

        PlatformVerificationSuiteService service = new PlatformVerificationSuiteService(
            new PlatformVerificationSuiteCatalog(),
            runRepository,
            stageRepository,
            executionService,
            new PlatformVerificationSuiteProperties(Duration.ofMinutes(60), Duration.ofMinutes(12), Duration.ofMinutes(20), Duration.ofMinutes(75), Duration.ofHours(12), Duration.ofSeconds(3), 20, 12_000, 80_000, "https://platform-ui.example.test", "weaviate.example.test", "https://bridge.example.test", "shop.example.test", "shopify-bridge-prod", null),
            auditService,
            new ObjectMapper()
        );

        PlatformVerificationReleaseGateSummary summary = service.getReleaseGate();

        assertThat(summary.ready()).isTrue();
        assertThat(summary.status()).isEqualTo("READY");
        assertThat(summary.latestRun()).isNotNull();
        assertThat(summary.latestRun().id()).isEqualTo("vsr-ready");
    }

    @Test
    void releaseGateReportsStaleWhenLatestFullSuitePassExpired() {
        PlatformVerificationSuiteRunRepository runRepository = mock(PlatformVerificationSuiteRunRepository.class);
        PlatformVerificationSuiteRunStageRepository stageRepository = mock(PlatformVerificationSuiteRunStageRepository.class);
        PlatformVerificationSuiteExecutionService executionService = mock(PlatformVerificationSuiteExecutionService.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);

        PlatformVerificationSuiteRunEntity run = new PlatformVerificationSuiteRunEntity();
        run.setId("vsr-stale");
        run.setSuiteKey(PlatformVerificationSuiteCatalog.FULL_PLATFORM_RELEASE_READINESS_SUITE_KEY);
        run.setSuiteLabel("Full platform release readiness");
        run.setStatus("PASSED");
        run.setReleaseBlocking(true);
        run.setSummaryMessage("ok");
        run.setRequestedByActorId("admin");
        run.setRequestedByRole("PLATFORM_ADMIN");
        run.setCreatedAt(Instant.now().minus(Duration.ofHours(13)));
        run.setStartedAt(Instant.now().minus(Duration.ofHours(13)));
        run.setCompletedAt(Instant.now().minus(Duration.ofHours(13)));

        when(runRepository.findTopBySuiteKeyOrderByCreatedAtDesc(PlatformVerificationSuiteCatalog.FULL_PLATFORM_RELEASE_READINESS_SUITE_KEY))
            .thenReturn(Optional.of(run));
        when(stageRepository.findBySuiteRunIdOrderByStageOrderAsc(run.getId())).thenReturn(List.of());

        PlatformVerificationSuiteService service = new PlatformVerificationSuiteService(
            new PlatformVerificationSuiteCatalog(),
            runRepository,
            stageRepository,
            executionService,
            new PlatformVerificationSuiteProperties(Duration.ofMinutes(60), Duration.ofMinutes(12), Duration.ofMinutes(20), Duration.ofMinutes(75), Duration.ofHours(12), Duration.ofSeconds(3), 20, 12_000, 80_000, "https://platform-ui.example.test", "weaviate.example.test", "https://bridge.example.test", "shop.example.test", "shopify-bridge-prod", null),
            auditService,
            new ObjectMapper()
        );

        PlatformVerificationReleaseGateSummary summary = service.getReleaseGate();

        assertThat(summary.ready()).isFalse();
        assertThat(summary.status()).isEqualTo("STALE");
        assertThat(summary.latestRun()).isNotNull();
        assertThat(summary.expiresAt()).isNotNull();
    }
}
