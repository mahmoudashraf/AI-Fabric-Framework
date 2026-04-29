package com.ai.fabric.product.shopify.bridge.analytics.model;

import java.util.List;

public record ShopifyBridgeUsageRoiSummary(
    String status,
    String message,
    long shopperAssistSignals,
    long decisionSupportSignals,
    long governedActionGrants,
    long governedActionCompletions,
    long governedActionFailures,
    int activeSurfaceCount,
    List<String> strongestSurfaceLabels,
    List<String> recommendations
) {
}
