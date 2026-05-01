package com.ai.fabric.product.shopify.bridge.store.model;

import java.util.List;

public record ShopifyBridgeThinkerHealthSummary(
    String shopDomain,
    String deploymentId,
    boolean enabled,
    String status,
    long recentSessionCount,
    long blockedSessionCount,
    String message,
    List<String> nextActions
) {
}
