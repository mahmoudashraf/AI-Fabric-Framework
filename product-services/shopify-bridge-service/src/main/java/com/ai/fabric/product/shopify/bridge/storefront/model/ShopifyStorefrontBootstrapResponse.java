package com.ai.fabric.product.shopify.bridge.storefront.model;

import com.ai.fabric.product.shopify.bridge.governedaction.model.ShopifyStorefrontGovernedActionCapability;

import java.util.List;
import java.util.Map;

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
    boolean debugEnabled,
    boolean assistantDockEnabled,
    boolean askAssistantLauncherEnabled,
    String defaultConversationMode,
    String effectiveConversationMode,
    List<String> allowedConversationModes,
    Map<String, String> pageModeMappings,
    List<String> enabledSurfaces,
    List<String> groundingSignals,
    List<String> supportedReviewProviders,
    String preferredIntegrationMode,
    String runtimeAuthMode,
    String bridgeQueryUrl,
    String bridgeSuggestionsUrl,
    String bridgeOrderLookupUrl,
    String bridgeEventUrl,
    String customerAccountAuthStartUrl,
    String customerAccountAuthSessionUrl,
    boolean orderLookupEnabled,
    boolean olderOrdersRequireBroaderScope,
    String orderLookupMessage,
    ShopifyStorefrontGovernedActionCapability actionCapability,
    String guidance,
    String message
) {
}
