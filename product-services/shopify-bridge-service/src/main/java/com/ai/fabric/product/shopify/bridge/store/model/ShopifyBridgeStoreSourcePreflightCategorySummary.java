package com.ai.fabric.product.shopify.bridge.store.model;

import java.util.List;

public record ShopifyBridgeStoreSourcePreflightCategorySummary(
    String category,
    boolean enabled,
    String status,
    int itemCount,
    String message,
    List<String> signals
) {
}
