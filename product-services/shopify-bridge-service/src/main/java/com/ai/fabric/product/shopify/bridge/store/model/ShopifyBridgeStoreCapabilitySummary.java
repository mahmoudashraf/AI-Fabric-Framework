package com.ai.fabric.product.shopify.bridge.store.model;

import java.util.List;

public record ShopifyBridgeStoreCapabilitySummary(
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
