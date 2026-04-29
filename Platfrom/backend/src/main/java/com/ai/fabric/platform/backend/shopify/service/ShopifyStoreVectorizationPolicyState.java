package com.ai.fabric.platform.backend.shopify.service;

import java.util.List;

record ShopifyStoreVectorizationPolicyState(
    boolean autoIndexingEnabled,
    boolean createTriggerEnabled,
    boolean deleteTriggerEnabled,
    String updateTriggerMode,
    List<String> selectedIndexedFields,
    int debounceWindowSeconds,
    int minimumRunIntervalSeconds
) {
}
