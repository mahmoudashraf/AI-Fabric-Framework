package com.ai.fabric.product.shopify.bridge.store.model;

public record ShopifyBridgeRecordWebhookEventRequest(
    String topic,
    String eventType,
    String sourceCategory,
    String operation,
    String sourceObjectId,
    String sourceRecordVersion,
    String shopifyWebhookId,
    String payloadChecksum,
    Integer deliveryAttempt,
    String message,
    Boolean invalidateSync
) {
}
