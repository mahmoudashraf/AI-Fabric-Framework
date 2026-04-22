package com.ai.fabric.platform.backend.shopify.model;

import java.util.Map;

public record ShopifyStoreSyncDocument(
    String documentId,
    String sourceCategory,
    String entityType,
    String title,
    String content,
    Map<String, Object> metadata
) {
}
