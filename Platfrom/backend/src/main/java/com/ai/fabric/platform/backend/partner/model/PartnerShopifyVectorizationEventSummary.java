package com.ai.fabric.platform.backend.partner.model;

import java.time.Instant;

public record PartnerShopifyVectorizationEventSummary(
    String id,
    String sourceCategory,
    String entityType,
    String operation,
    String status,
    String triggerReason,
    String failureCode,
    Instant occurredAt,
    Instant queuedAt,
    Instant lastAttemptAt,
    Instant completedAt,
    String notes
) {
}
