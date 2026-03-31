package com.ai.fabric.platform.backend.security.model;

import java.time.Instant;

public record PlatformUserDeploymentAccessSummary(
    String assignmentId,
    String deploymentId,
    String deploymentName,
    String deploymentEnvironment,
    String deploymentStatus,
    String assignmentRole,
    Instant createdAt,
    Instant updatedAt
) {
}
