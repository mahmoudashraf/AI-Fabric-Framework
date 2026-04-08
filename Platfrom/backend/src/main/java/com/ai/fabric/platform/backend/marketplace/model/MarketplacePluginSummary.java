package com.ai.fabric.platform.backend.marketplace.model;

import java.time.Instant;

public record MarketplacePluginSummary(
    String pluginId,
    String slug,
    String displayName,
    String pluginType,
    String publisherSlug,
    String publisherDisplayName,
    String shortDescription,
    String status,
    MarketplacePluginVersionSummary latestVersion,
    Instant updatedAt
) {
}
