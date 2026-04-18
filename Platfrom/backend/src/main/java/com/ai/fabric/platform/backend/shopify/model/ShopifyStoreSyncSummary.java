package com.ai.fabric.platform.backend.shopify.model;

import java.time.Instant;

public record ShopifyStoreSyncSummary(
    String status,
    Instant checkedAt,
    String mode,
    int documentCount,
    String message
) {
}
