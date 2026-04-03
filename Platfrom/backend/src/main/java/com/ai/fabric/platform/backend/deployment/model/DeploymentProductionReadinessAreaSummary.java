package com.ai.fabric.platform.backend.deployment.model;

public record DeploymentProductionReadinessAreaSummary(
    String key,
    String label,
    String status,
    int score,
    String message
) {
}
