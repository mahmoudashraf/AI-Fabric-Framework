package com.ai.fabric.product.shopify.bridge.billing.model;

public record ShopifyBridgeBillingSummary(
    String mode,
    String planName,
    String status,
    boolean merchantApprovalRequired,
    boolean launchBlocked,
    String message
) {
}
