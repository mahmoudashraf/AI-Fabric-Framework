package com.ai.fabric.platform.backend.deployment.model;

public record DeploymentPocMigrationCheckSummary(
    String key,
    String label,
    String status,
    String message
) {
}
