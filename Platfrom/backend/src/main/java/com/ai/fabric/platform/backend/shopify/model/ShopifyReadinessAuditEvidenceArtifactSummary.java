package com.ai.fabric.platform.backend.shopify.model;

public record ShopifyReadinessAuditEvidenceArtifactSummary(
    String name,
    String category,
    String path,
    String description,
    String status
) {
}
