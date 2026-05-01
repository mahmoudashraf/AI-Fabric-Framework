package com.ai.fabric.platform.backend.deployment.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record DeploymentProviderResourceStatusSummary(
    String handleId,
    DeploymentProviderType providerType,
    String providerResourceUuid,
    String status,
    String observedStatus,
    String fqdn,
    JsonNode details,
    Instant observedAt
) {
}
