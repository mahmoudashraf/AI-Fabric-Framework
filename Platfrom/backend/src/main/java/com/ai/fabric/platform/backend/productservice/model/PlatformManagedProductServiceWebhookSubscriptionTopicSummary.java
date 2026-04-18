package com.ai.fabric.platform.backend.productservice.model;

public record PlatformManagedProductServiceWebhookSubscriptionTopicSummary(
    String topic,
    String expectedName,
    String status,
    String subscriptionId,
    String subscriptionName,
    String subscriptionUri,
    String message
) {
}
