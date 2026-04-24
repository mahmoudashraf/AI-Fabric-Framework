package com.ai.fabric.product.shopify.bridge.store.model;

import java.util.List;
import java.util.Map;

public record ShopifyBridgeStoreWidgetSettingsSummary(
    String launcherLabel,
    String welcomeMessage,
    String shellModeProfile,
    List<String> enabledSurfaces,
    String defaultConversationMode,
    List<String> allowedConversationModes,
    Map<String, String> pageModeMappings
) {
}
