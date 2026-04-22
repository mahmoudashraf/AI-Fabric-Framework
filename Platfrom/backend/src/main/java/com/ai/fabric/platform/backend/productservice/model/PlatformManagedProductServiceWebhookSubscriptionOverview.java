package com.ai.fabric.platform.backend.productservice.model;

import java.util.List;

public record PlatformManagedProductServiceWebhookSubscriptionOverview(
    String status,
    String message,
    String webhookUri,
    int expectedCount,
    List<String> expectedTopics
) {
}
