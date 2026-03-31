package com.ai.fabric.platform.backend.deployment.model;

public record RailwayLogTagsSummary(
    String deploymentId,
    String deploymentInstanceId,
    String environmentId,
    String projectId,
    String serviceId,
    String snapshotId
) {
}
