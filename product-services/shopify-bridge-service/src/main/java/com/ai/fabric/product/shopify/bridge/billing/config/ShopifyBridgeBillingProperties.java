package com.ai.fabric.product.shopify.bridge.billing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shopify.bridge.billing")
public record ShopifyBridgeBillingProperties(
    String mode,
    String planName,
    String appSubscriptionPlanHandle,
    String appSubscriptionAmount,
    String appSubscriptionCurrencyCode,
    String appSubscriptionInterval,
    Integer appSubscriptionTrialDays,
    boolean appSubscriptionTest
) {

    public ShopifyBridgeBillingProperties {
        mode = normalize(mode, "FREE");
        planName = normalize(planName, "Companion Free");
        appSubscriptionPlanHandle = normalize(appSubscriptionPlanHandle, "");
        appSubscriptionAmount = normalize(appSubscriptionAmount, "");
        appSubscriptionCurrencyCode = normalize(appSubscriptionCurrencyCode, "USD");
        appSubscriptionInterval = normalize(appSubscriptionInterval, "EVERY_30_DAYS");
        appSubscriptionTrialDays = appSubscriptionTrialDays == null || appSubscriptionTrialDays < 0 ? 0 : appSubscriptionTrialDays;
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
