package com.ai.fabric.platform.backend.shopify.model;

import java.util.List;

public record ShopifyStoreVectorizationSourcePolicyInput(
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
