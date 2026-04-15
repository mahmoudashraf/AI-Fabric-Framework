package com.ai.fabric.platform.backend.marketplace.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record MarketplacePluginVersionSummary(
    String id,
    String pluginId,
    String version,
    String releaseChannel,
    String status,
    JsonNode manifest,
    MarketplacePluginPricingSummary pricing,
    MarketplacePluginCompatibilitySummary compatibility,
    java.util.List<MarketplacePluginInstallFieldSummary> installForm,
    MarketplacePluginPermissionsSummary permissions,
    MarketplacePluginContributionSummary contributions,
    java.util.List<String> recommendedPluginIds,
    Instant publishedAt
) {
}
