package com.ai.fabric.platform.backend.marketplace.model;

import java.util.List;

public record MarketplacePluginDetailSummary(
    MarketplacePluginSummary plugin,
    List<MarketplacePluginVersionSummary> versions
) {
}
