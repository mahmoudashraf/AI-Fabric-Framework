package com.ai.fabric.product.shopify.bridge.governedaction.model;

import java.time.Instant;

public record ShopifyStorefrontGovernedActionGrantResponse(
    String auditId,
    String token,
    String actionType,
    String actionPackage,
    String operationKind,
    String variantId,
    Integer requestedQuantity,
    Integer targetQuantity,
    String cartLineKey,
    boolean confirmationRequired,
    Instant expiresAt,
    String message
) {
}
