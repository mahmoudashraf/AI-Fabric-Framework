package com.ai.fabric.platform.backend.deployment.model;

public record RailwayWorkspaceCleanupOwnerSummary(
    String deploymentId,
    String deploymentName,
    String environment,
    boolean archived
) {
}
