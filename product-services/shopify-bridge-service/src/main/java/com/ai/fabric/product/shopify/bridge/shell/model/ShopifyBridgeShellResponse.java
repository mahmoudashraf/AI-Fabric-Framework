package com.ai.fabric.product.shopify.bridge.shell.model;

import java.util.List;

public record ShopifyBridgeShellResponse(
    String appName,
    String serviceRef,
    String environmentScope,
    String status,
    boolean merchantSessionAuthConfigured,
    List<String> onboardingPhases,
    List<String> launchCapabilities
) {
}
