package com.ai.fabric.platform.backend.shopify.model;

import java.time.Instant;

public record ShopifyStoreWebhookSummary(
    String topic,
    String eventType,
    String sourceCategory,
    Instant receivedAt,
    boolean invalidateSync,
    String message
) {
}
