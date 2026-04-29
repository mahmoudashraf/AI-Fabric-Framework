package com.ai.fabric.platform.backend.shopify.service;

record ShopifyStoreVectorizationFieldDefinition(
    String fieldKey,
    String sourceCategory,
    String entityType,
    String sourceField,
    String label,
    boolean selectable
) {
}
