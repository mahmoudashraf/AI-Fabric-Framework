package com.ai.fabric.product.shopify.bridge.store.model;

import java.util.Map;

public record ShopifyBridgeStoreSyncDocument(
    String documentId,
    String sourceCategory,
    String entityType,
    String title,
    String content,
    Map<String, Object> metadata
) {
}
