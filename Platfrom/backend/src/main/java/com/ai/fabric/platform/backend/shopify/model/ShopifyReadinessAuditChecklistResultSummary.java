package com.ai.fabric.platform.backend.shopify.model;

public record ShopifyReadinessAuditChecklistResultSummary(
    String key,
    String label,
    String category,
    boolean blocking,
    String passCriteria,
    String status,
    String evidence,
    String notes
) {
}
