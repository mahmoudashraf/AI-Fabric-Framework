package com.ai.fabric.platform.backend.shopify.model;

public record RecordShopifyStoreWebhookEventRequest(
    String topic,
    String eventType,
    String sourceCategory,
    String message,
    Boolean invalidateSync
) {
}
