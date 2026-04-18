package com.ai.fabric.platform.backend.shopify.model;

public record UpdateShopifyStoreWidgetSettingsRequest(
    String launcherLabel,
    String welcomeMessage
) {
}
