package com.ai.fabric.platform.backend.tenant.model;

import java.time.Instant;

public record PlatformConsumerBindingHistorySummary(
    String consumerId,
    String customerId,
    String fromDeploymentId,
    String fromDeploymentName,
    String toDeploymentId,
    String toDeploymentName,
    String reason,
    String actorId,
    String actorRole,
    Instant createdAt
) {
}
