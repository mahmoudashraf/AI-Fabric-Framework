package com.ai.fabric.product.shopify.bridge.store.model;

public record ShopifyBridgePartnerAccessDecisionRequest(
    String approverName,
    String approverEmail,
    String approvedScope,
    String decisionReason
) {
}
