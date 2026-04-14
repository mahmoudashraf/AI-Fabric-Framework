package com.ai.fabric.platform.backend.marketplace.model;

import java.util.List;

public record MarketplacePluginContributionSummary(
    String templateCuratedModuleId,
    List<String> actionIds,
    List<String> knowledgeSourceIds,
    List<String> shellModuleIds,
    List<String> shellCardIds
) {
}
