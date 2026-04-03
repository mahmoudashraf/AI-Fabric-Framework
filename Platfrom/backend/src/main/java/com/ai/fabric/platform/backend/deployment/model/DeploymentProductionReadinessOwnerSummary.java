package com.ai.fabric.platform.backend.deployment.model;

public record DeploymentProductionReadinessOwnerSummary(
    String status,
    int totalAssigned,
    int adminCount,
    int operatorCount,
    int editorCount,
    int viewerCount,
    String message
) {
}
