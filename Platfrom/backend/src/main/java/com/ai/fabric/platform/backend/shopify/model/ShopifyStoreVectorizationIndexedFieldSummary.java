package com.ai.fabric.platform.backend.shopify.model;

public record ShopifyStoreVectorizationIndexedFieldSummary(
    String fieldKey,
    String sourceCategory,
    String entityType,
    String sourceField,
    String label,
    boolean selectableForTriggerPolicy
) {
}
