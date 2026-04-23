package com.ai.fabric.product.shopify.bridge.governedaction.model;

public record ShopifyStorefrontGovernedActionCompletionRequest(
    String auditId,
    String token,
    String status,
    String message,
    String cartLineKey,
    Integer resultingQuantity
) {
}
