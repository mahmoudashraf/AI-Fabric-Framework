package com.ai.fabric.product.shopify.bridge.webhook.model;

import java.time.Instant;
import java.util.List;

public record ShopifyWebhookSubscriptionStatusSummary(
    String shopDomain,
    String status,
    String message,
    String webhookUri,
    int expectedCount,
    int readyCount,
    int missingCount,
    int driftedCount,
    Instant checkedAt,
    List<ShopifyWebhookSubscriptionTopicStatusSummary> topics
) {
}
