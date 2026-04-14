package com.ai.fabric.platform.backend.marketplace.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record DeploymentMarketplaceInstallSummary(
    String id,
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
    MarketplacePluginContributionSummary contributions,
    String readinessStatus,
    java.util.List<String> warnings,
    String liveState,
    Instant createdAt,
    Instant updatedAt
) {
}
