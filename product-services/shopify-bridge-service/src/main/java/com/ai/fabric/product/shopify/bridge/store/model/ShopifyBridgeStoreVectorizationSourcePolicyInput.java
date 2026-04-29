package com.ai.fabric.product.shopify.bridge.store.model;

import java.util.List;

public record ShopifyBridgeStoreVectorizationSourcePolicyInput(
    String sourceCategory,
    Boolean autoIndexingEnabled,
    Boolean createTriggerEnabled,
    Boolean deleteTriggerEnabled,
    String updateTriggerMode,
    List<String> selectedIndexedFields,
    Integer debounceWindowSeconds,
    Integer minimumRunIntervalSeconds
) {
}
