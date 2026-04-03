package com.ai.fabric.platform.backend.deployment.model;

public record BulkDeploymentActionItemSummary(
    String deploymentId,
    String deploymentName,
    String action,
    String status,
    String message
) {
}
