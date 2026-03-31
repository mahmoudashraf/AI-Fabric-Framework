package com.ai.fabric.platform.backend.deployment.model;

public record DeploymentRailwayLiveEnvVarDriftSummary(
    String key,
    boolean sensitive,
    String driftState,
    String expectedValue,
    String actualValue,
    String summaryMessage
) {
}
