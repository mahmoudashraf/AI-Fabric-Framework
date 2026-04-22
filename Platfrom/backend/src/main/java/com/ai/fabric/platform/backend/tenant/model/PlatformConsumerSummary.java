package com.ai.fabric.platform.backend.tenant.model;

import java.time.Instant;

public record PlatformConsumerSummary(
    String consumerId,
    String customerId,
    String displayName,
    String description,
    String status,
    String boundDeploymentId,
    String boundDeploymentName,
    String boundDeploymentEnvironment,
    String boundDeploymentStatus,
    Instant lastBoundAt,
    Instant createdAt,
    Instant updatedAt
) {
}
