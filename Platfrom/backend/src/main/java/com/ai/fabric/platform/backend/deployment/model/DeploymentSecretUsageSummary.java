package com.ai.fabric.platform.backend.deployment.model;

import java.util.List;

public record DeploymentSecretUsageSummary(
    String deploymentId,
    List<DeploymentSecretUsageItemSummary> secrets,
    List<DeploymentSecretLiteralRiskSummary> literalRisks,
    int missingRequiredCount,
    int literalRiskCount,
    String summaryMessage
) {
}
