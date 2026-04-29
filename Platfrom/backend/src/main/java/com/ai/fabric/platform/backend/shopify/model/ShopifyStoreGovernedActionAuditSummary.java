package com.ai.fabric.platform.backend.shopify.model;

import java.time.Instant;

public record ShopifyStoreGovernedActionAuditSummary(
    String id,
    String actionType,
    String actionPackage,
    String surfaceId,
    String pageType,
    String productHandle,
    String productTitle,
    String variantId,
    Integer requestedQuantity,
    Integer targetQuantity,
    Integer resultingQuantity,
    boolean confirmationRequired,
    boolean confirmationAccepted,
    String shopperSessionRef,
    String status,
    String message,
    Instant createdAt,
    Instant expiresAt,
    Instant completedAt
) {
}
