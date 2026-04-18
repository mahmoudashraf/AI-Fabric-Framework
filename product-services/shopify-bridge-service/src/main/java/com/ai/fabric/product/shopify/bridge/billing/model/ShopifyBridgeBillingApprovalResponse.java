package com.ai.fabric.product.shopify.bridge.billing.model;

public record ShopifyBridgeBillingApprovalResponse(
    String status,
    String confirmationUrl,
    String message
) {
}
