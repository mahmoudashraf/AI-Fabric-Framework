package com.ai.fabric.product.shopify.bridge.billing.model;

import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeSupportSubscriptionSummary;

import java.util.List;

public record ShopifyBridgeStoreBillingState(
    String tierKey,
    String status,
    List<ShopifyBridgeSupportSubscriptionSummary> activeSubscriptions
) {
}
