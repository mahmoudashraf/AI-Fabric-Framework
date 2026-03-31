package com.ai.fabric.platform.backend.deployment.model;

import java.time.Instant;

public record DeploymentAssignmentSummary(
    String id,
    String deploymentId,
    String userId,
    String userEmail,
    String userDisplayName,
    String platformRole,
    String assignmentRole,
    Instant createdAt,
    Instant updatedAt
) {
}
