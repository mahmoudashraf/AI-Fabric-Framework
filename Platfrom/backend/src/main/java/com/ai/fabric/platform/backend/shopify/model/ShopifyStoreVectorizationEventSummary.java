package com.ai.fabric.platform.backend.shopify.model;

import java.time.Instant;

public record ShopifyStoreVectorizationEventSummary(
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
