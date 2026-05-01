package com.ai.fabric.platform.backend.shopify.model;

import java.time.Instant;

public record ShopifyStoreBillingStateSummary(
    String shopDomain,
    String tierKey,
    String status,
    String subscriptionId,
    String subscriptionName,
    Instant recordedAt,
    String reason
) {
}
