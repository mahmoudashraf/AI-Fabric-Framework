package com.ai.fabric.product.shopify.bridge.store.model;

import java.time.Instant;

public record ShopifyBridgeStoreWebhookSummary(
    String topic,
    String eventType,
    String sourceCategory,
    Instant receivedAt,
    boolean invalidateSync,
    String message
) {
}
