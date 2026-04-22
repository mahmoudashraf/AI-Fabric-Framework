package com.ai.fabric.product.shopify.bridge.store.model;

public record ShopifyBridgeUpdateWidgetSettingsRequest(
    String launcherLabel,
    String welcomeMessage
) {
}
