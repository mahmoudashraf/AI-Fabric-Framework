package com.ai.fabric.product.shopify.bridge.store.model;

public record ShopifyBridgeRecordWebhookEventRequest(
    String topic,
    String eventType,
    String sourceCategory,
    String message,
    Boolean invalidateSync
) {
}
