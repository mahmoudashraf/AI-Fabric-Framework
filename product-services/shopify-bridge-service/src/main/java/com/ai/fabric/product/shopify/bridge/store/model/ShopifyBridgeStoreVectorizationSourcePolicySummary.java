package com.ai.fabric.product.shopify.bridge.store.model;

import java.util.List;

public record ShopifyBridgeStoreVectorizationSourcePolicySummary(
    String sourceCategory,
    boolean enabled,
    boolean manualIndexAllowed,
    boolean manualReindexAllowed,
    boolean autoIndexingEnabled,
    boolean createTriggerEnabled,
    boolean deleteTriggerEnabled,
    String updateTriggerMode,
    List<String> selectedIndexedFields,
    int debounceWindowSeconds,
    int minimumRunIntervalSeconds
) {
}
