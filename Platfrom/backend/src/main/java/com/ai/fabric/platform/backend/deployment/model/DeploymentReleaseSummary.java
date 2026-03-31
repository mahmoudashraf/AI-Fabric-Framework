package com.ai.fabric.platform.backend.deployment.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record DeploymentReleaseSummary(
    String id,
    String deploymentId,
    String deploymentVersionId,
    String status,
    String verificationStatus,
    String provisioningStatus,
    String provisioningTarget,
    String currentStepKey,
    String currentStepDescription,
    String errorMessage,
    String verificationRunId,
    JsonNode provisioningDetails,
    Instant createdAt,
    Instant appliedAt,
    Instant updatedAt
) {
}
