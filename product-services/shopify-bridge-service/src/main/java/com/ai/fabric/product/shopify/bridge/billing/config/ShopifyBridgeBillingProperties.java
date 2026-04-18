package com.ai.fabric.product.shopify.bridge.billing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shopify.bridge.billing")
public record ShopifyBridgeBillingProperties(
    String mode,
    String planName,
    String appSubscriptionPlanHandle
) {

    public ShopifyBridgeBillingProperties {
        mode = normalize(mode, "FREE");
        planName = normalize(planName, "Companion Free");
        appSubscriptionPlanHandle = normalize(appSubscriptionPlanHandle, "");
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
