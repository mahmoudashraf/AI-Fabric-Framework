package com.ai.fabric.product.shopify.bridge.governedaction.model;

import java.util.List;

public record ShopifyStorefrontGovernedActionCapability(
    boolean available,
    boolean requiresExplicitConfirmation,
    boolean auditTrailAvailable,
    List<String> actionPackages,
    List<String> allowedActionTypes,
    String grantUrl,
    String completeUrl,
    String message
) {
}
