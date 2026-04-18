package com.ai.fabric.product.shopify.bridge.store.model;

import java.time.Instant;

public record ShopifyBridgeStoreWidgetSummary(
    String status,
    Instant checkedAt,
    String channel,
    String message
) {
}
