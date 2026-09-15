package com.ai.fabric.platform.backend.model;

import java.time.Instant;

public record PlatformCoreServiceDeploymentSummary(
    String serviceRef,
    String deploymentUuid,
    String applicationName,
    String applicationUuid,
    String status,
    String commit,
    String commitMessage,
    String createdAt,
    String updatedAt,
    String finishedAt,
    String targetProfileId,
    Instant observedAt
) {
}
