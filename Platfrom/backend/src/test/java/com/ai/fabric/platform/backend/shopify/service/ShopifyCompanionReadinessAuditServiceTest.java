package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.deployment.entity.PlatformVerificationSuiteRunEntity;
import com.ai.fabric.platform.backend.deployment.entity.PlatformVerificationSuiteRunStageEntity;
import com.ai.fabric.platform.backend.deployment.repository.PlatformVerificationSuiteRunRepository;
import com.ai.fabric.platform.backend.deployment.repository.PlatformVerificationSuiteRunStageRepository;
import com.ai.fabric.platform.backend.deployment.service.PlatformVerificationSuiteCatalog;
import com.ai.fabric.platform.backend.deployment.service.PlatformVerificationSuiteExecutionService;
import com.ai.fabric.platform.backend.deployment.service.PlatformVerificationSuiteService;
import com.ai.fabric.platform.backend.config.PlatformVerificationSuiteProperties;
import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShopifyCompanionReadinessAuditServiceTest {

    @Test
    void exposesCanonicalDefinitionAndLatestRun() {
        PlatformVerificationSuiteRunRepository runRepository = mock(PlatformVerificationSuiteRunRepository.class);
        PlatformVerificationSuiteRunStageRepository stageRepository = mock(PlatformVerificationSuiteRunStageRepository.class);

        PlatformVerificationSuiteRunEntity latest = new PlatformVerificationSuiteRunEntity();
        latest.setId("vsr-readiness");
        latest.setSuiteKey(PlatformVerificationSuiteCatalog.SHOPIFY_FIRST_PRODUCT_READINESS_AUDIT_SUITE_KEY);
        latest.setSuiteLabel("Shopify first-product readiness audit");
        latest.setStatus("PASSED");
        latest.setReleaseBlocking(false);
        latest.setSummaryMessage("ok");
        latest.setRequestedByActorId("admin");
        latest.setRequestedByRole("PLATFORM_ADMIN");
        latest.setCreatedAt(Instant.now());
        latest.setCompletedAt(Instant.now());

        PlatformVerificationSuiteRunStageEntity stage = new PlatformVerificationSuiteRunStageEntity();
        stage.setId("vsrs-readiness");
        stage.setSuiteRunId(latest.getId());
        stage.setStageOrder(1);
        stage.setStageKey(PlatformVerificationSuiteCatalog.SHOPIFY_FIRST_PRODUCT_READINESS_AUDIT_SUITE_KEY);
        stage.setStageLabel("Shopify first-product readiness audit");
        stage.setStageType("SCRIPT");
        stage.setTargetRef("shopify-first-product-readiness-audit");
        stage.setBlocking(false);
        stage.setStatus("PASSED");
        stage.setSummaryMessage("Audit passed.");
        stage.setDetailsJson("{}");
        stage.setLogOutput("""
            QUERY_RESULT {"answerLength":118,"answerPreview":"Travel-friendly product guidance.","checks":{"http_status_ok":true,"answer_non_empty":true},"expectedBehavior":"grounded_product_guidance","failureCategory":null,"forbiddenHits":[],"httpStatus":200,"missingRequiredConcepts":[],"passed":true,"queryId":"product-search-travel","surface":"ai-search","tierProfile":"FREE","unsafeActionExecutionHits":[]}
            Readiness decision: DESIGN_PARTNER_READY
            """);
        stage.setCreatedAt(Instant.now());
        stage.setStartedAt(Instant.now());
        stage.setCompletedAt(Instant.now());

        when(runRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(latest));
        when(stageRepository.findBySuiteRunIdOrderByStageOrderAsc(latest.getId())).thenReturn(List.of(stage));

        PlatformVerificationSuiteService verificationSuiteService = new PlatformVerificationSuiteService(
            new PlatformVerificationSuiteCatalog(),
            runRepository,
            stageRepository,
            mock(PlatformVerificationSuiteExecutionService.class),
            new PlatformVerificationSuiteProperties(Duration.ofMinutes(60), Duration.ofMinutes(12), Duration.ofMinutes(20), Duration.ofMinutes(75), Duration.ofHours(12), Duration.ofSeconds(3), 20, 12_000, 80_000, "https://platform-ui.example.test", "weaviate.example.test", "https://bridge.example.test", "shop.example.test", "shopify-bridge-prod", null, "https://partner-ui.example.test"),
            mock(PlatformAuditService.class),
            new ObjectMapper()
        );

        ShopifyCompanionReadinessAuditService service = new ShopifyCompanionReadinessAuditService(verificationSuiteService, new ObjectMapper());

        assertThat(service.definition().suiteKey()).isEqualTo(PlatformVerificationSuiteCatalog.SHOPIFY_FIRST_PRODUCT_READINESS_AUDIT_SUITE_KEY);
        assertThat(service.definition().checklist()).hasSize(10);
        assertThat(service.definition().queryPack()).hasSize(10);
        assertThat(service.definition().forbiddenInternalTerms()).contains("vectorization", "Railway");
        assertThat(service.latest().latestRun().id()).isEqualTo("vsr-readiness");
        assertThat(service.latest().decision()).isEqualTo("DESIGN_PARTNER_READY");
        assertThat(service.latest().checklistResults()).hasSize(10);
        assertThat(service.latest().answerResults()).singleElement()
            .satisfies(result -> {
                assertThat(result.queryId()).isEqualTo("product-search-travel");
                assertThat(result.httpStatus()).isEqualTo(200);
                assertThat(result.checks()).containsEntry("http_status_ok", true);
            });
        assertThat(service.latest().evidenceArtifacts()).extracting("name").contains("answer-quality-audit.md", "readiness-matrix.md");
    }
}
