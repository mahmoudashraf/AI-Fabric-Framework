package com.ai.fabric.product.shopify.bridge.store.model;

public record ShopifyBridgeRecordBillingStateRequest(
    String tierKey,
    String status,
    String subscriptionId,
    String subscriptionName,
    String reason
) {
}
