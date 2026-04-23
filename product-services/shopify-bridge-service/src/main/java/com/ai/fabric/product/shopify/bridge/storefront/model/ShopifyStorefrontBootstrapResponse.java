package com.ai.fabric.product.shopify.bridge.storefront.model;

import com.ai.fabric.product.shopify.bridge.governedaction.model.ShopifyStorefrontGovernedActionCapability;

import java.util.List;

public record ShopifyStorefrontBootstrapResponse(
    boolean available,
    String shopDomain,
    String consumerId,
    String deploymentId,
    String widgetStatus,
    String sourceReadinessStatus,
    String billingTier,
    String billingStatus,
    Integer catalogProductCap,
    boolean poweredByBadgeRequired,
    boolean chatFallbackEnabled,
    String launcherLabel,
    String welcomeMessage,
    String shellModeProfile,
    List<String> enabledSurfaces,
    List<String> groundingSignals,
    List<String> supportedReviewProviders,
    String preferredIntegrationMode,
    String runtimeAuthMode,
    String bridgeQueryUrl,
    String bridgeSuggestionsUrl,
    String bridgeEventUrl,
    ShopifyStorefrontGovernedActionCapability actionCapability,
    String guidance,
    String message
) {
}
