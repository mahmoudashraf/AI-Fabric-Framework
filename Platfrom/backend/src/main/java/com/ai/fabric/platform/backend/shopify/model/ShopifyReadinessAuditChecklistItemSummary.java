package com.ai.fabric.platform.backend.shopify.model;

public record ShopifyReadinessAuditChecklistItemSummary(
    String key,
    String label,
    String category,
    boolean blocking,
    String passCriteria
) {
}
