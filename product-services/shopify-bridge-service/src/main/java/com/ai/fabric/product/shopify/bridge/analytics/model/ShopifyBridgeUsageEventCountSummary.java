package com.ai.fabric.product.shopify.bridge.analytics.model;

public record ShopifyBridgeUsageEventCountSummary(
    String eventType,
    long count
) {
}
