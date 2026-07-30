package com.ai.fabric.platform.backend.partner.model;

import java.util.List;

public record PartnerShopifyWebhookSummary(
    String status,
    String message,
    int expectedCount,
    int readyCount,
    int missingCount,
    int driftedCount,
    String checkedAt,
    List<PartnerShopifyWebhookTopicSummary> topics
) {
}
