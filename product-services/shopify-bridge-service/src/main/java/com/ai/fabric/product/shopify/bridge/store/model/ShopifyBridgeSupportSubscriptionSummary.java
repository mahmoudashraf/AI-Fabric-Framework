package com.ai.fabric.product.shopify.bridge.store.model;

public record ShopifyBridgeSupportSubscriptionSummary(
    String subscriptionId,
    String name,
    String status,
    String tierKey,
    boolean active
) {
}
