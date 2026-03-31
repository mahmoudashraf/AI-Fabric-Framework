package com.ai.fabric.platform.backend.deployment.model;

public record DeploymentRailwayLiveFieldDriftSummary(
    String key,
    String label,
    String expectedValue,
    String actualValue,
    String driftState,
    String summaryMessage
) {
}
