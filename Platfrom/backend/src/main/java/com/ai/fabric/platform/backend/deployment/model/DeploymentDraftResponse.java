package com.ai.fabric.platform.backend.deployment.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record DeploymentDraftResponse(
    String id,
    String deploymentId,
    int revisionNumber,
    String status,
    JsonNode actionsConfig,
    JsonNode entityConfig,
    JsonNode routingConfig,
    JsonNode providerConfig,
    JsonNode securityConfig,
    JsonNode promptConfig,
    Instant createdAt,
    Instant updatedAt
) {
}
