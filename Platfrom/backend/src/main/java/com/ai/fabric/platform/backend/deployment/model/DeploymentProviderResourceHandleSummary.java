package com.ai.fabric.platform.backend.deployment.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record DeploymentProviderResourceHandleSummary(
    String id,
    String deploymentId,
    String releaseId,
    String targetProfileId,
    DeploymentProviderType providerType,
    String resourceKind,
    String providerResourceUuid,
    String providerProjectUuid,
    String providerEnvironmentUuid,
    String providerServerUuid,
    String fqdn,
    String status,
    String lastObservedStatus,
    Instant lastObservedAt,
    JsonNode metadata,
    Instant createdAt,
    Instant updatedAt
) {
}
