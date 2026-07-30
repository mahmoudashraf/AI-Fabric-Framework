package com.ai.fabric.platform.backend.partner.model;

public record PartnerShopifyWebhookTopicSummary(
    String topic,
    String expectedName,
    String status,
    String message
) {
}
