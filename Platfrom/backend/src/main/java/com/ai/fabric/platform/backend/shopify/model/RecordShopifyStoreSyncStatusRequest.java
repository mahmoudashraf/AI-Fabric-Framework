package com.ai.fabric.platform.backend.shopify.model;

public record RecordShopifyStoreSyncStatusRequest(
    String status,
    String mode,
    Integer documentCount,
    String message
) {
}
