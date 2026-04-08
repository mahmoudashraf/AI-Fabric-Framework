package com.ai.fabric.platform.backend.marketplace.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record DeploymentMarketplacePluginInstallSummary(
    String installId,
    String deploymentId,
    String pluginId,
    String pluginSlug,
    String pluginDisplayName,
    String pluginType,
    String pluginVersionId,
    String pluginVersion,
    String status,
    JsonNode config,
    JsonNode secretRefs,
    Instant createdAt,
    Instant updatedAt
) {
}
