package com.ai.fabric.platform.backend.deployment.model;

import java.time.Instant;

public record DeploymentOperationApprovalSummary(
    String id,
    String deploymentId,
    String operationType,
    String targetVersionId,
    String targetVersionLabel,
    String status,
    String requestedByActorId,
    String requestedByDisplayName,
    String requestedReason,
    String approvedByActorId,
    String approvedByDisplayName,
    String resolutionNote,
    Instant createdAt,
    Instant updatedAt,
    Instant approvedAt,
    Instant rejectedAt,
    Instant expiresAt,
    Instant consumedAt
) {
}
