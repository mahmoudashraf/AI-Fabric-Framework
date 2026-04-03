package com.ai.fabric.platform.backend.deployment.model;

import java.time.Instant;

public record DeploymentWorkspaceDraftSummary(
    String id,
    int revisionNumber,
    String status,
    Instant updatedAt
) {
}
