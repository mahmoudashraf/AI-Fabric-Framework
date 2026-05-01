package com.ai.fabric.product.shopify.bridge.analytics.model;

public record ShopifyBridgeUsageSurfaceJourneySummary(
    String surfaceId,
    String label,
    long shopperQuestions,
    long shopperInteractions,
    long readActions,
    long governedActionGrants,
    long governedActionCompletions,
    long governedActionFailures
) {
}
