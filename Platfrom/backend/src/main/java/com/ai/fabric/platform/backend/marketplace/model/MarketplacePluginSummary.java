package com.ai.fabric.platform.backend.marketplace.model;

import java.time.Instant;
import java.util.List;

public record MarketplacePluginSummary(
    String id,
    String slug,
    String displayName,
    String pluginType,
    String publisherSlug,
    String publisherDisplayName,
    String shortDescription,
    String status,
    String latestVersion,
    List<String> categories,
    MarketplacePluginContributionSummary contributions,
    Instant updatedAt
) {
}
