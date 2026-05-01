package com.ai.fabric.platform.backend.shopify.model;

import java.util.List;

public record ShopifyStoreVectorizationSourcePolicySummary(
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
