package com.ai.fabric.platform.backend.deployment.model;

import java.time.Instant;

public record DeploymentReleaseSummary(
    String id,
    String deploymentId,
    String deploymentVersionId,
    String status,
    String verificationStatus,
    Instant createdAt,
    Instant appliedAt
) {
}

