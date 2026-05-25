package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformVerificationSuiteProperties;
import com.ai.fabric.platform.backend.deployment.entity.PlatformVerificationSuiteRunEntity;
import com.ai.fabric.platform.backend.deployment.entity.PlatformVerificationSuiteRunStageEntity;
import com.ai.fabric.platform.backend.deployment.model.PlatformVerificationSuiteDispatchRequest;
import com.ai.fabric.platform.backend.deployment.model.PlatformVerificationSuiteDispatchSummary;
import com.ai.fabric.platform.backend.deployment.model.PlatformVerificationReleaseGateSummary;
import com.ai.fabric.platform.backend.deployment.model.PlatformVerificationSuiteRunSummary;
import com.ai.fabric.platform.backend.deployment.model.ShopifyCompanionVerificationExpectationOverrides;
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
            new PlatformVerificationSuiteProperties(Duration.ofMinutes(60), Duration.ofMinutes(12), Duration.ofMinutes(20), Duration.ofMinutes(75), Duration.ofHours(12), Duration.ofSeconds(3), 20, 12_000, 80_000, "https://platform-ui.example.test", "weaviate.example.test", "https://bridge.example.test", "shop.example.test", "shopify-bridge-prod", null, "https://partner-ui.example.test"),
            auditService,
            new ObjectMapper()
        );

        PlatformVerificationSuiteDispatchSummary summary = service.dispatch(
            PlatformVerificationSuiteCatalog.FULL_PLATFORM_RELEASE_READINESS_SUITE_KEY,
            new PlatformVerificationSuiteDispatchRequest(false, null, null)
        );

        assertThat(summary.suiteKey()).isEqualTo(PlatformVerificationSuiteCatalog.FULL_PLATFORM_RELEASE_READINESS_SUITE_KEY);
        assertThat(summary.run().status()).isEqualTo("QUEUED");
        assertThat(summary.run().stages()).hasSize(14);
        assertThat(summary.run().stages().getFirst().stageKey()).isEqualTo("shared-inference-health");
        assertThat(summary.run().stages().get(4).targetRef())
            .isEqualTo(PlatformVerificationSuiteScriptContextService.SCRIPT_COOLIFY_PROVIDER_VERIFICATION);
        assertThat(summary.run().stages().get(7).targetRef())
            .isEqualTo(PlatformVerificationSuiteScriptContextService.SCRIPT_SHOPIFY_MCP_GATEWAY_VERIFICATION);
        assertThat(summary.run().stages().get(8).targetRef())
            .isEqualTo(PlatformVerificationSuiteScriptContextService.SCRIPT_SHOPIFY_FIRST_PRODUCT_READINESS_AUDIT);
        assertThat(summary.run().stages().get(9).targetRef())
            .isEqualTo(PlatformVerificationSuiteScriptContextService.SCRIPT_PARTNER_ENABLEMENT_VERIFICATION);
        assertThat(summary.run().stages().get(10).targetRef())
            .isEqualTo(PlatformVerificationSuiteScriptContextService.SCRIPT_THINKER_RESOLVER_READINESS);
        assertThat(summary.run().stages().getLast().targetRef()).isEqualTo("qdrant");
        assertThat(summary.run().stages())
            .extracting(stage -> stage.targetRef())
            .doesNotContain("pinecone", "milvus", "weaviate");

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
            new PlatformVerificationSuiteProperties(Duration.ofMinutes(180), Duration.ofMinutes(12), Duration.ofMinutes(20), Duration.ofMinutes(75), Duration.ofHours(12), Duration.ofSeconds(3), 20, 12_000, 80_000, "https://platform-ui.example.test", "weaviate.example.test", "https://bridge.example.test", "shop.example.test", "shopify-bridge-prod", null, "https://partner-ui.example.test"),
            auditService,
            new ObjectMapper()
        );

        PlatformVerificationSuiteDispatchSummary summary = service.dispatch(
            PlatformVerificationSuiteCatalog.FULL_PLATFORM_RELEASE_READINESS_SUITE_KEY,
            new PlatformVerificationSuiteDispatchRequest(false, null, null)
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
        assertThat(summary.run().stages()).hasSize(14);
        verify(executionService).execute(summary.run().id(), false);
    }

    @Test
    void dispatchStoresShopifyExpectationOverridesOnShopifyStage() {
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
            new PlatformVerificationSuiteProperties(Duration.ofMinutes(60), Duration.ofMinutes(12), Duration.ofMinutes(20), Duration.ofMinutes(75), Duration.ofHours(12), Duration.ofSeconds(3), 20, 12_000, 80_000, "https://platform-ui.example.test", "weaviate.example.test", "https://bridge.example.test", "shop.example.test", "shopify-bridge-prod", null, "https://partner-ui.example.test"),
            auditService,
            new ObjectMapper()
        );

        service.dispatch(
            PlatformVerificationSuiteCatalog.FULL_PLATFORM_RELEASE_READINESS_SUITE_KEY,
            new PlatformVerificationSuiteDispatchRequest(
                false,
                new ShopifyCompanionVerificationExpectationOverrides(
                    false,
                    true,
                    false,
                    "STARTER",
                    "ACTIVE",
                    "PENDING_SCOPE_GRANT",
                    false,
                    false,
                    true,
                    false,
                    "SCOPE_APPROVAL",
                    "comparison,contextual-pill,policy-strip,product-faq,product-insight",
                    "comparison,contextual-pill,policy-strip,product-faq,product-insight"
                ),
                new ShopifyCompanionVerificationExpectationOverrides(
                    true,
                    true,
                    true,
                    "ELITE",
                    "ACTIVE",
                    "READY",
                    true,
                    true,
                    true,
                    false,
                    "RECENT_ORDER_ONLY",
                    "comparison,contextual-pill,order-lookup,policy-strip,product-faq,product-insight",
                    "comparison,contextual-pill,order-lookup,policy-strip,product-faq,product-insight"
                )
            )
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PlatformVerificationSuiteRunStageEntity>> stageCaptor = ArgumentCaptor.forClass(List.class);
        verify(stageRepository).saveAll(stageCaptor.capture());
        PlatformVerificationSuiteRunStageEntity shopifyStage = stageCaptor.getValue().stream()
            .filter(stage -> PlatformVerificationSuiteScriptContextService.SCRIPT_SHOPIFY_COMPANION_VERIFICATION.equals(stage.getTargetRef()))
            .findFirst()
            .orElseThrow();
        assertThat(shopifyStage.getDetailsJson()).contains("EXPECT_STOREFRONT_READY");
        assertThat(shopifyStage.getDetailsJson()).contains("EXPECT_BILLING_TIER");
        assertThat(shopifyStage.getDetailsJson()).contains("STARTER");
        assertThat(shopifyStage.getDetailsJson()).contains("PENDING_SCOPE_GRANT");
        assertThat(shopifyStage.getDetailsJson()).contains("SCOPE_APPROVAL");
        assertThat(shopifyStage.getDetailsJson()).contains("EXPECT_ENABLED_SURFACES");
        assertThat(shopifyStage.getDetailsJson()).contains("comparison,contextual-pill,policy-strip,product-faq,product-insight");

        PlatformVerificationSuiteRunStageEntity readinessAuditStage = stageCaptor.getValue().stream()
            .filter(stage -> PlatformVerificationSuiteScriptContextService.SCRIPT_SHOPIFY_FIRST_PRODUCT_READINESS_AUDIT.equals(stage.getTargetRef()))
            .findFirst()
            .orElseThrow();
        assertThat(readinessAuditStage.getDetailsJson()).contains("EXPECT_ENABLED_SURFACES");
        assertThat(readinessAuditStage.getDetailsJson()).contains("ELITE");
        assertThat(readinessAuditStage.getDetailsJson()).contains("RECENT_ORDER_ONLY");
        assertThat(readinessAuditStage.getDetailsJson()).contains("comparison,contextual-pill,order-lookup,policy-strip,product-faq,product-insight");
    }

    @Test
    void cancelRunMarksActiveRunAndStageCancelled() {
        PlatformVerificationSuiteRunRepository runRepository = mock(PlatformVerificationSuiteRunRepository.class);
        PlatformVerificationSuiteRunStageRepository stageRepository = mock(PlatformVerificationSuiteRunStageRepository.class);
        PlatformVerificationSuiteExecutionService executionService = mock(PlatformVerificationSuiteExecutionService.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);

        PlatformVerificationSuiteRunEntity run = new PlatformVerificationSuiteRunEntity();
        run.setId("vsr-cancel");
        run.setSuiteKey(PlatformVerificationSuiteCatalog.FULL_PLATFORM_RELEASE_READINESS_SUITE_KEY);
        run.setSuiteLabel("Full platform release readiness");
        run.setStatus("RUNNING");
        run.setReleaseBlocking(true);
        run.setSummaryMessage("running");
        run.setRequestedByActorId("admin");
        run.setRequestedByRole("PLATFORM_ADMIN");
        run.setCreatedAt(Instant.now().minus(Duration.ofMinutes(5)));
        run.setStartedAt(Instant.now().minus(Duration.ofMinutes(5)));

        PlatformVerificationSuiteRunStageEntity stage = new PlatformVerificationSuiteRunStageEntity();
        stage.setId("vss-cancel");
        stage.setSuiteRunId(run.getId());
        stage.setStageOrder(1);
        stage.setStageKey("marketplace-install-flow");
        stage.setStageLabel("Marketplace install flow");
        stage.setStageType("SCRIPT_VERIFICATION");
        stage.setTargetRef(PlatformVerificationSuiteScriptContextService.SCRIPT_MARKETPLACE_INSTALL_FLOW);
        stage.setBlocking(true);
        stage.setStatus("RUNNING");
        stage.setSummaryMessage("running");
        stage.setDetailsJson("{}");
        stage.setCreatedAt(Instant.now().minus(Duration.ofMinutes(5)));
        stage.setStartedAt(Instant.now().minus(Duration.ofMinutes(5)));

        when(runRepository.findById(run.getId())).thenReturn(Optional.of(run));
        when(runRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(stageRepository.findBySuiteRunIdOrderByStageOrderAsc(run.getId())).thenReturn(List.of(stage));
        when(stageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PlatformVerificationSuiteService service = new PlatformVerificationSuiteService(
            new PlatformVerificationSuiteCatalog(),
            runRepository,
            stageRepository,
            executionService,
            new PlatformVerificationSuiteProperties(Duration.ofMinutes(60), Duration.ofMinutes(12), Duration.ofMinutes(20), Duration.ofMinutes(75), Duration.ofHours(12), Duration.ofSeconds(3), 20, 12_000, 80_000, "https://platform-ui.example.test", "weaviate.example.test", "https://bridge.example.test", "shop.example.test", "shopify-bridge-prod", null, "https://partner-ui.example.test"),
            auditService,
            new ObjectMapper()
        );

        PlatformVerificationSuiteRunSummary summary = service.cancelRun(run.getId());

        assertThat(summary.status()).isEqualTo("CANCELLED");
        assertThat(run.getStatus()).isEqualTo("CANCELLED");
        assertThat(run.getCompletedAt()).isNotNull();
        assertThat(stage.getStatus()).isEqualTo("CANCELLED");
        assertThat(stage.getCompletedAt()).isNotNull();
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
            new PlatformVerificationSuiteProperties(Duration.ofMinutes(60), Duration.ofMinutes(12), Duration.ofMinutes(20), Duration.ofMinutes(75), Duration.ofHours(12), Duration.ofSeconds(3), 20, 12_000, 80_000, "https://platform-ui.example.test", "weaviate.example.test", "https://bridge.example.test", "shop.example.test", "shopify-bridge-prod", null, "https://partner-ui.example.test"),
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
            new PlatformVerificationSuiteProperties(Duration.ofMinutes(60), Duration.ofMinutes(12), Duration.ofMinutes(20), Duration.ofMinutes(75), Duration.ofHours(12), Duration.ofSeconds(3), 20, 12_000, 80_000, "https://platform-ui.example.test", "weaviate.example.test", "https://bridge.example.test", "shop.example.test", "shopify-bridge-prod", null, "https://partner-ui.example.test"),
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
