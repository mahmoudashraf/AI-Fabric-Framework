package com.ai.fabric.platform.backend.deployment.model;

import java.time.Instant;

public record DeploymentVersionSummary(
    String id,
    String deploymentId,
    String sourceDraftId,
    String versionLabel,
    String status,
    String configHash,
    boolean reindexRequired,
    Instant publishedAt
) {
}

