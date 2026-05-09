package com.ai.fabric.platform.backend.deployment.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record DeploymentProviderResourceActionSummary(
    String handleId,
    DeploymentProviderType providerType,
    String action,
    String status,
    String message,
    String providerOperationId,
    JsonNode details,
    Instant requestedAt
) {
}
