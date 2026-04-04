package com.ai.fabric.platform.backend.vectorization.model;

import java.util.List;

public record VectorizationOverviewSummary(
    String deploymentId,
    String customerId,
    String tenantId,
    String activeVersionId,
    String activeVersionLabel,
    String activeConfigHash,
    VectorizationSourceConnectionSummary sourceConnection,
    VectorizationPlanSummary plan,
    VectorizationRunnerSummary runner,
    List<VectorizationRunSummary> recentRuns
) {
}
