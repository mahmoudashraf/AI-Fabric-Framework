package com.ai.fabric.platform.backend.deployment.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record DeploymentProviderResourceLifecycleSummary(
    String handleId,
    String deploymentId,
    String releaseId,
    String targetProfileId,
    DeploymentProviderType providerType,
    String resourceKind,
    String fqdn,
    String previousStatus,
    String status,
    String proposedAction,
    String reason,
    JsonNode metadata,
    Instant updatedAt
) {
}
