package com.ai.fabric.product.shopify.bridge.governedaction.model;

public record ShopifyStorefrontGovernedActionGrantRequest(
    String actionType,
    String surfaceId,
    String pageType,
    String productId,
    String productHandle,
    String productTitle,
    String variantId,
    Integer requestedQuantity,
    Integer targetQuantity,
    String cartLineKey,
    boolean confirmationAccepted
) {
}
