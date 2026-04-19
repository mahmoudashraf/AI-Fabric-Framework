package com.ai.fabric.platform.backend.shopify.model;

import java.time.Instant;
import java.util.List;

public record ShopifyStoreVectorizationRunSummary(
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
