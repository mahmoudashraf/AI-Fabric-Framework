package com.ai.fabric.platform.backend.shopify.model;

import java.util.List;
import java.util.Map;

public record ShopifyReadinessAuditQuerySummary(
    String queryId,
    String tierProfile,
    String surface,
    String query,
    Map<String, Object> storefrontContext,
    String expectedBehavior,
    List<String> requiredConcepts,
    List<String> forbiddenClaims,
    boolean expectedDenial,
    boolean groundingRequired
) {
}
