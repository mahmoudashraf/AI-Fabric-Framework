package com.ai.fabric.product.shopify.bridge.store.model;

import java.time.Instant;

public record ShopifyBridgeRecordedBillingStateSummary(
    String shopDomain,
    String tierKey,
    String status,
    String subscriptionId,
    String subscriptionName,
    Instant recordedAt,
    String reason
) {
}
