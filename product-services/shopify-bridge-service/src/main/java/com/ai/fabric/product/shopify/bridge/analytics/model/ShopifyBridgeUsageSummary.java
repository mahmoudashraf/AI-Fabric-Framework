package com.ai.fabric.product.shopify.bridge.analytics.model;

import java.time.Instant;
import java.util.List;

public record ShopifyBridgeUsageSummary(
    String shopDomain,
    Instant generatedAt,
    Instant lastActivityAt,
    long totalToday,
    long totalLast7Days,
    List<ShopifyBridgeUsageEventCountSummary> todayBreakdown,
    List<ShopifyBridgeUsageEventCountSummary> last7DayBreakdown,
    List<ShopifyBridgeUsageSurfaceSummary> todaySurfaceUsage,
    List<ShopifyBridgeUsageSurfaceSummary> last7DaySurfaceUsage,
    List<ShopifyBridgeUsageTopQuerySummary> topQuestionsLast7Days
) {
}
