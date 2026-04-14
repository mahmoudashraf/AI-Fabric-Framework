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
    MarketplacePluginContributionSummary contributions,
    Instant publishedAt
) {
}
