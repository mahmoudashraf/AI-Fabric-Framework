package com.ai.fabric.product.shopify.bridge.storefront.model;

import java.util.List;

public record ShopifyStorefrontPreviewResponse(
    boolean ready,
    String shopDomain,
    String storefrontBaseUrl,
    String bridgeBaseUrl,
    String widgetStatus,
    String onboardingStatus,
    String consumerId,
    String deploymentId,
    String extensionHandle,
    String launcherLabelDefault,
    List<String> activationSteps,
    List<String> blockingReasons,
    String message
) {
}
