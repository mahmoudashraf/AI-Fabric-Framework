package com.ai.fabric.product.shopify.bridge.store.model;

public record ShopifyBridgeRecordWidgetStatusRequest(
    String status,
    String channel,
    String message
) {
}
