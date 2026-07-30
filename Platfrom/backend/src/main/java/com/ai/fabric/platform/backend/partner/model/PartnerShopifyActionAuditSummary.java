package com.ai.fabric.platform.backend.partner.model;

import java.time.Instant;

public record PartnerShopifyActionAuditSummary(
    String id,
    String actionType,
    String actionPackage,
    String surfaceId,
    String pageType,
    String productHandle,
    String productTitle,
    String variantId,
    Integer requestedQuantity,
    Integer resultingQuantity,
    boolean confirmationRequired,
    boolean confirmationAccepted,
    String status,
    String message,
    Instant createdAt,
    Instant completedAt
) {
}
