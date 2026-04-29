package com.ai.fabric.platform.backend.shopify.model;

import java.util.List;

public record ShopifyStoreSourcePreflightCategorySummary(
    String category,
    boolean enabled,
    String status,
    int itemCount,
    String message,
    List<String> signals
) {
}
