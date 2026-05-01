package com.ai.fabric.platform.backend.deployment.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record DeploymentTargetProfileSummary(
    String id,
    String name,
    DeploymentProviderType providerType,
    String environmentName,
    String region,
    boolean active,
    boolean defaultForRuntime,
    boolean defaultForRestartableServices,
    boolean platformServicesAllowed,
    String sourceStrategy,
    String credentialRefId,
    JsonNode providerConfig,
    JsonNode networkPolicy,
    JsonNode resourceDefaults,
    Instant createdAt,
    Instant updatedAt
) {
}
