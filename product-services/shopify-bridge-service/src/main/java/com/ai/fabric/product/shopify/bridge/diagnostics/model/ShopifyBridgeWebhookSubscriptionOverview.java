package com.ai.fabric.product.shopify.bridge.diagnostics.model;

import java.util.List;

public record ShopifyBridgeWebhookSubscriptionOverview(
    String status,
    String message,
    String webhookUri,
    int expectedCount,
    List<String> expectedTopics
) {
}
