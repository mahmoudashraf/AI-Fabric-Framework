package com.ai.fabric.platform.backend.productservice.model;

import java.util.List;

public record PlatformManagedProductServiceWebhookSubscriptionSummary(
    String shopDomain,
    String status,
    String message,
    String webhookUri,
    int expectedCount,
    int readyCount,
    int missingCount,
    int driftedCount,
    String checkedAt,
    List<PlatformManagedProductServiceWebhookSubscriptionTopicSummary> topics
) {
}
