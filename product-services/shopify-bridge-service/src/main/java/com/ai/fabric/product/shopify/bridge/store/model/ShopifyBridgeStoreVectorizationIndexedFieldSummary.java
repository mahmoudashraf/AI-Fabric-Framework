package com.ai.fabric.product.shopify.bridge.store.model;

public record ShopifyBridgeStoreVectorizationIndexedFieldSummary(
    String fieldKey,
    String sourceCategory,
    String entityType,
    String sourceField,
    String label,
    boolean selectableForTriggerPolicy
) {
}
