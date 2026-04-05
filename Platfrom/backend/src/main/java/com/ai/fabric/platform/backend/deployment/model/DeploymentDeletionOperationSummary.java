package com.ai.fabric.platform.backend.deployment.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record DeploymentDeletionOperationSummary(
    String id,
    String deploymentId,
    String deploymentName,
    String environmentName,
    String customerId,
    String tenantId,
    String status,
    String statusMessage,
    boolean hardDelete,
    String approvalId,
    String requestReason,
    String requestedByActorId,
    String requestedByRole,
    JsonNode requestDetails,
    JsonNode resultDetails,
    String errorMessage,
    Instant createdAt,
    Instant startedAt,
    Instant completedAt,
    Instant updatedAt
) {
}
