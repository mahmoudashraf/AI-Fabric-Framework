package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformVerificationSuiteProperties;
import com.ai.fabric.platform.backend.deployment.entity.PlatformVerificationSuiteRunEntity;
import com.ai.fabric.platform.backend.deployment.entity.PlatformVerificationSuiteRunStageEntity;
import com.ai.fabric.platform.backend.deployment.model.PlatformVerificationSuiteDispatchRequest;
import com.ai.fabric.platform.backend.deployment.model.PlatformVerificationSuiteDispatchSummary;
import com.ai.fabric.platform.backend.deployment.repository.PlatformVerificationSuiteRunRepository;
import com.ai.fabric.platform.backend.deployment.repository.PlatformVerificationSuiteRunStageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
            new PlatformVerificationSuiteProperties(Duration.ofMinutes(60), Duration.ofMinutes(12), Duration.ofMinutes(20), Duration.ofSeconds(3), 20, 12_000, "https://platform-ui.example.test", "weaviate.example.test", "https://bridge.example.test", "shop.example.test", "shopify-bridge-prod", null),
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
}
