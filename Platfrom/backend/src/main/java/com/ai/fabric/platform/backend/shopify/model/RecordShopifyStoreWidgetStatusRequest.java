package com.ai.fabric.platform.backend.shopify.model;

public record RecordShopifyStoreWidgetStatusRequest(
    String status,
    String channel,
    String message
) {
}
