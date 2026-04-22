package com.ai.fabric.product.shopify.bridge.store.model;

import java.time.Instant;
import java.util.List;

public record ShopifyBridgeStoreVectorizationRunSummary(
    String id,
    String reason,
    String status,
    String requestedStatus,
    List<String> entityScope,
    Instant createdAt,
    Instant startedAt,
    Instant completedAt,
    Instant updatedAt
) {
}
