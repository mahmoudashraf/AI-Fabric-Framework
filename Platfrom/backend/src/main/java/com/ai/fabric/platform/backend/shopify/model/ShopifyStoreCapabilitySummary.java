package com.ai.fabric.platform.backend.shopify.model;

import java.util.List;

public record ShopifyStoreCapabilitySummary(
    int actionCount,
    int knowledgeSourceCount,
    int shellModuleCount,
    int marketplaceDatasetCount,
    List<String> actionNames,
    List<String> knowledgeSourceIds,
    List<String> shellModuleIds,
    List<String> marketplaceDatasetIds
) {
}
