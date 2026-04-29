package com.ai.fabric.product.shopify.bridge.analytics.model;

import java.time.Instant;

public record ShopifyBridgeUsageTopQuerySummary(
    String surfaceId,
    String label,
    String queryText,
    long count,
    Instant lastAskedAt
) {
}
