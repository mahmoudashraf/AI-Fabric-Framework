package com.ai.fabric.product.shopify.bridge.store.model;

import java.time.Instant;

public record ShopifyBridgeStoreSyncSummary(
    String status,
    Instant checkedAt,
    String mode,
    int documentCount,
    String message
) {
}
