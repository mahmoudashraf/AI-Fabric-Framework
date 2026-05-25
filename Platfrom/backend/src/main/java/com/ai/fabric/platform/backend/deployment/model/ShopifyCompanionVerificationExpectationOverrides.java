package com.ai.fabric.platform.backend.deployment.model;

import java.util.LinkedHashMap;
import java.util.Map;

public record ShopifyCompanionVerificationExpectationOverrides(
    Boolean storefrontReady,
    Boolean storefrontShopperTrafficReady,
    Boolean goLiveEligible,
    String billingTier,
    String billingStatus,
    String orderLookupStatus,
    Boolean orderLookupSupported,
    Boolean orderLookupScopeGranted,
    Boolean orderLookupAppScopesWebhookReady,
    Boolean historicalOrderLookupSupported,
    String supportLifecycleStage,
    String enabledSurfaces,
    String configuredEnabledSurfaces
) {

    public Map<String, String> toEnvironmentOverrides() {
        Map<String, String> overrides = new LinkedHashMap<>();
        putBoolean(overrides, "EXPECT_STOREFRONT_READY", storefrontReady);
        putBoolean(overrides, "EXPECT_STOREFRONT_SHOPPER_TRAFFIC_READY", storefrontShopperTrafficReady);
        putBoolean(overrides, "EXPECT_GO_LIVE_ELIGIBLE", goLiveEligible);
        putText(overrides, "EXPECT_BILLING_TIER", billingTier);
        putText(overrides, "EXPECT_BILLING_STATUS", billingStatus);
        putText(overrides, "EXPECT_ORDER_LOOKUP_STATUS", orderLookupStatus);
        putBoolean(overrides, "EXPECT_ORDER_LOOKUP_SUPPORTED", orderLookupSupported);
        putBoolean(overrides, "EXPECT_ORDER_LOOKUP_SCOPE_GRANTED", orderLookupScopeGranted);
        putBoolean(overrides, "EXPECT_ORDER_LOOKUP_APP_SCOPES_WEBHOOK_READY", orderLookupAppScopesWebhookReady);
        putBoolean(overrides, "EXPECT_HISTORICAL_ORDER_LOOKUP_SUPPORTED", historicalOrderLookupSupported);
        putText(overrides, "EXPECT_SUPPORT_LIFECYCLE_STAGE", supportLifecycleStage);
        putText(overrides, "EXPECT_ENABLED_SURFACES", enabledSurfaces);
        putText(overrides, "EXPECT_CONFIGURED_ENABLED_SURFACES", configuredEnabledSurfaces);
        return Map.copyOf(overrides);
    }

    public boolean isEmpty() {
        return toEnvironmentOverrides().isEmpty();
    }

    private static void putBoolean(Map<String, String> overrides, String key, Boolean value) {
        if (value != null) {
            overrides.put(key, value.toString());
        }
    }

    private static void putText(Map<String, String> overrides, String key, String value) {
        if (value != null && !value.isBlank()) {
            overrides.put(key, value.trim());
        }
    }
}
