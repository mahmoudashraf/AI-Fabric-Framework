package com.ai.fabric.platform.backend.deployment.model;

import java.util.List;

public record DeploymentProductionReadinessScorecardSummary(
    String deploymentId,
    String deploymentName,
    String environment,
    String overallStatus,
    int overallScore,
    String latestReleaseStatus,
    String latestVerificationStatus,
    DeploymentProductionReadinessOwnerSummary ownership,
    List<DeploymentProductionReadinessAreaSummary> areas,
    String summaryMessage
) {
}
