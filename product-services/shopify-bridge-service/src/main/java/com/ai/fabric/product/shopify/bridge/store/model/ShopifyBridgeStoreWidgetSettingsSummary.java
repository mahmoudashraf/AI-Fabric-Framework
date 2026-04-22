package com.ai.fabric.product.shopify.bridge.store.model;

import java.util.List;

public record ShopifyBridgeStoreWidgetSettingsSummary(
    String launcherLabel,
    String welcomeMessage,
    String shellModeProfile,
    List<String> enabledSurfaces
) {
}
