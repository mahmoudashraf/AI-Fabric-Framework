package com.ai.fabric.platform.backend.deployment.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;

public record DeploymentProviderPreflightSummary(
    String targetProfileId,
    DeploymentProviderType providerType,
    String status,
    String message,
    String baseUrl,
    String version,
    List<String> checks,
    JsonNode details,
    Instant checkedAt
) {
}
