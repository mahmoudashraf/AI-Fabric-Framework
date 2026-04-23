package com.ai.fabric.product.shopify.bridge.analytics.model;

public record ShopifyBridgeUsageSurfaceSummary(
    String surfaceId,
    String label,
    long count
) {
}
