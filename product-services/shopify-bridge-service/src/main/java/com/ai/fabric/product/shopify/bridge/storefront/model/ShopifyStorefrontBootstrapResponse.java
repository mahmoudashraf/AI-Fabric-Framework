package com.ai.fabric.product.shopify.bridge.storefront.model;

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
    String preferredIntegrationMode,
    String runtimeAuthMode,
    String bridgeQueryUrl,
    String bridgeSuggestionsUrl,
    String bridgeEventUrl,
    String guidance,
    String message
) {
}
