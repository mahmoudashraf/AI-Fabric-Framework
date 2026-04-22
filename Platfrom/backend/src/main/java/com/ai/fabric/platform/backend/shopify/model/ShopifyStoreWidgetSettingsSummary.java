package com.ai.fabric.platform.backend.shopify.model;

import java.util.List;

public record ShopifyStoreWidgetSettingsSummary(
    String launcherLabel,
    String welcomeMessage,
    String shellModeProfile,
    List<String> enabledSurfaces
) {
}
