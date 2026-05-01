package com.ai.fabric.product.shopify.bridge.store.model;

import java.time.Instant;

public record ShopifyBridgeStoreVectorizationEventSummary(
    String id,
    String sourceCategory,
    String entityType,
    String sourceObjectId,
    String shopifyTopic,
    String operation,
    String status,
    String triggerReason,
    String failureCode,
    String coalescedRunId,
    String shopifyWebhookId,
    Instant occurredAt,
    Instant queuedAt,
    Instant lastAttemptAt,
    Instant completedAt,
    String notes
) {
}
