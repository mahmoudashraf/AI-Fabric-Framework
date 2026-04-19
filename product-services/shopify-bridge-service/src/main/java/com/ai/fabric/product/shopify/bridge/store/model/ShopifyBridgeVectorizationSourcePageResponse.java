package com.ai.fabric.product.shopify.bridge.store.model;

import java.util.List;

public record ShopifyBridgeVectorizationSourcePageResponse(
    String shopDomain,
    String entityType,
    int totalCount,
    boolean hasMore,
    String nextCursor,
    List<ShopifyBridgeVectorizationSourceRecord> items
) {
}
