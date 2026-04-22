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
    boolean appSubscriptionTest,
    boolean starterEnabled,
    String starterPlanName,
    String starterPlanHandle,
    String starterAmount,
    String starterCurrencyCode,
    String starterInterval,
    Integer starterTrialDays,
    boolean starterTest,
    boolean eliteEnabled,
    String elitePlanName,
    String elitePlanHandle,
    String eliteAmount,
    String eliteCurrencyCode,
    String eliteInterval,
    Integer eliteTrialDays,
    boolean eliteTest
) {

    public ShopifyBridgeBillingProperties {
        mode = normalize(mode, "FREE");
        planName = normalize(planName, "Companion Free");
        appSubscriptionPlanHandle = normalize(appSubscriptionPlanHandle, "");
        appSubscriptionAmount = normalize(appSubscriptionAmount, "");
        appSubscriptionCurrencyCode = normalize(appSubscriptionCurrencyCode, "USD");
        appSubscriptionInterval = normalize(appSubscriptionInterval, "EVERY_30_DAYS");
        appSubscriptionTrialDays = appSubscriptionTrialDays == null || appSubscriptionTrialDays < 0 ? 0 : appSubscriptionTrialDays;
        starterEnabled = starterEnabled || hasAnyValue(starterPlanName, starterPlanHandle, starterAmount, appSubscriptionAmount);
        starterPlanName = normalize(starterPlanName, normalize(planName, "Loom Companion Starter"));
        starterPlanHandle = normalize(starterPlanHandle, normalize(appSubscriptionPlanHandle, "loom-companion-starter"));
        starterAmount = normalize(starterAmount, appSubscriptionAmount);
        starterCurrencyCode = normalize(starterCurrencyCode, appSubscriptionCurrencyCode);
        starterInterval = normalize(starterInterval, appSubscriptionInterval);
        starterTrialDays = starterTrialDays == null || starterTrialDays < 0 ? appSubscriptionTrialDays : starterTrialDays;
        starterTest = starterTest || appSubscriptionTest;
        eliteEnabled = eliteEnabled;
        elitePlanName = normalize(elitePlanName, "Loom Companion Elite");
        elitePlanHandle = normalize(elitePlanHandle, "loom-companion-elite");
        eliteAmount = normalize(eliteAmount, "179.00");
        eliteCurrencyCode = normalize(eliteCurrencyCode, "USD");
        eliteInterval = normalize(eliteInterval, "EVERY_30_DAYS");
        eliteTrialDays = eliteTrialDays == null || eliteTrialDays < 0 ? 0 : eliteTrialDays;
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static boolean hasAnyValue(String... values) {
        if (values == null) {
            return false;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return true;
            }
        }
        return false;
    }
}
