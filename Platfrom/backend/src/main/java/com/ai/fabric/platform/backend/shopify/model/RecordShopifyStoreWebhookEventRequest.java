package com.ai.fabric.platform.backend.shopify.model;

public record RecordShopifyStoreWebhookEventRequest(
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
