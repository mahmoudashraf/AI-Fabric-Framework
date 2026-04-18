package com.ai.fabric.product.shopify.bridge.storefront.model;

public record ShopifyStorefrontBootstrapResponse(
    boolean available,
    String shopDomain,
    String consumerId,
    String deploymentId,
    String widgetStatus,
    String sourceReadinessStatus,
    String preferredIntegrationMode,
    String runtimeAuthMode,
    String bridgeQueryUrl,
    String bridgeSuggestionsUrl,
    String guidance,
    String message
) {
}
