package com.ai.fabric.platform.backend.shopify.model;

import java.util.List;

public record ShopifyReadinessAuditDefinitionSummary(
    String suiteKey,
    String productRef,
    String displayName,
    String targetStore,
    String tierProfile,
    String artifactRoot,
    List<String> decisionOptions,
    List<String> rubric,
    List<String> forbiddenInternalTerms,
    List<ShopifyReadinessAuditChecklistItemSummary> checklist,
    List<ShopifyReadinessAuditQuerySummary> queryPack
) {
}
