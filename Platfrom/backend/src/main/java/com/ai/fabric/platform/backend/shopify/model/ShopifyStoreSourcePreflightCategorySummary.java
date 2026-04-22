package com.ai.fabric.platform.backend.shopify.model;

public record ShopifyStoreSourcePreflightCategorySummary(
    String category,
    boolean enabled,
    String status,
    int itemCount,
    String message
) {
}
