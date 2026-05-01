package com.ai.fabric.platform.backend.shopify.model;

import com.ai.fabric.platform.backend.deployment.model.PlatformVerificationSuiteRunSummary;
import com.ai.fabric.platform.backend.deployment.model.PlatformVerificationSuiteStageRunSummary;

import java.time.Instant;
import java.util.List;

public record ShopifyReadinessAuditStateSummary(
    ShopifyReadinessAuditDefinitionSummary definition,
    PlatformVerificationSuiteRunSummary latestRun,
    PlatformVerificationSuiteStageRunSummary latestStage,
    String decision,
    String freshnessStatus,
    Instant evidenceCompletedAt,
    List<String> blockers,
    String nextHandoff,
    List<ShopifyReadinessAuditChecklistResultSummary> checklistResults,
    List<ShopifyReadinessAuditAnswerResultSummary> answerResults,
    List<ShopifyReadinessAuditEvidenceArtifactSummary> evidenceArtifacts
) {
}
