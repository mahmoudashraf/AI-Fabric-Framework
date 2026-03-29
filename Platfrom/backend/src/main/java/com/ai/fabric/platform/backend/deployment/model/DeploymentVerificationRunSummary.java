package com.ai.fabric.platform.backend.deployment.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record DeploymentVerificationRunSummary(
    String id,
    String deploymentId,
    String releaseId,
    String deploymentVersionId,
    String verificationType,
    String status,
    String summaryMessage,
    JsonNode checks,
    Instant createdAt,
    Instant completedAt
) {
}
