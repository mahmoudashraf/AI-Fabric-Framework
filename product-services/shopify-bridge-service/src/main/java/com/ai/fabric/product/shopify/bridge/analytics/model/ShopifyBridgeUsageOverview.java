package com.ai.fabric.product.shopify.bridge.analytics.model;

import java.time.Instant;
import java.util.List;

public record ShopifyBridgeUsageOverview(
    Instant generatedAt,
    Instant lastActivityAt,
    int activeShopsToday,
    int activeShopsLast7Days,
    long totalToday,
    long totalLast7Days,
    List<ShopifyBridgeUsageEventCountSummary> todayBreakdown,
    List<ShopifyBridgeUsageEventCountSummary> last7DayBreakdown
) {
}
