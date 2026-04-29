package com.ai.fabric.product.shopify.bridge.storefront.model;

public record ShopifyStorefrontPlacementSummary(
    String surfaceId,
    String label,
    String placementType,
    String blockHandle,
    String template,
    String target,
    String themeEditorUrl,
    String requiredTierKey,
    String guidance
) {
}
