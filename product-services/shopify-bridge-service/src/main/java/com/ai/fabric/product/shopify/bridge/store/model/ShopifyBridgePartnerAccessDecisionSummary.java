package com.ai.fabric.product.shopify.bridge.store.model;

import java.time.Instant;

public record ShopifyBridgePartnerAccessDecisionSummary(
    String requestId,
    String assignmentId,
    String shopDomain,
    String status,
    Instant decidedAt
) {
}
