package com.ai.fabric.product.shopify.bridge.webhook.model;

public record ShopifyWebhookSubscriptionTopicStatusSummary(
    String topic,
    String expectedName,
    String status,
    String subscriptionId,
    String subscriptionName,
    String subscriptionUri,
    String message
) {
}
