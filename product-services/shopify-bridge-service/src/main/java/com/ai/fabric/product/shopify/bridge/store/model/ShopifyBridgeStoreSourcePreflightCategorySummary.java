package com.ai.fabric.product.shopify.bridge.store.model;

public record ShopifyBridgeStoreSourcePreflightCategorySummary(
    String category,
    boolean enabled,
    String status,
    int itemCount,
    String message
) {
}
