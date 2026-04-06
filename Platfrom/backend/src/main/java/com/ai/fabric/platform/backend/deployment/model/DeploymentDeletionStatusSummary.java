package com.ai.fabric.platform.backend.deployment.model;

import java.time.Instant;

public record DeploymentDeletionStatusSummary(
    String operationId,
    String status,
    String message,
    Instant requestedAt,
    String failureMessage
) {
}
