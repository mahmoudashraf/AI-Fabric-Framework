package com.ai.fabric.product.shopify.bridge.store.model;

import java.time.Instant;
import java.util.List;

public record ShopifyBridgeStoreSourcePreflightSummary(
    String overallStatus,
    Instant checkedAt,
    List<ShopifyBridgeStoreSourcePreflightCategorySummary> categories
) {
}
