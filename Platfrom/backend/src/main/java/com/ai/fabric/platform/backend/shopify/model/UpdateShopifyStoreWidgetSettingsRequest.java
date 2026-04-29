package com.ai.fabric.platform.backend.shopify.model;

import java.util.List;
import java.util.Map;

public record UpdateShopifyStoreWidgetSettingsRequest(
    String launcherLabel,
    String welcomeMessage,
    String shellModeProfile,
    List<String> enabledSurfaces,
    String defaultConversationMode,
    List<String> allowedConversationModes,
    Map<String, String> pageModeMappings
) {
}
