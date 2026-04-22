package com.ai.fabric.product.shopify.bridge.store.model;

public record ShopifyBridgeRecordSyncStatusRequest(
    String status,
    String mode,
    Integer documentCount,
    String message
) {
}
