package com.ai.fabric.product.shopify.bridge.billing.service;

import com.ai.fabric.product.shopify.bridge.billing.config.ShopifyBridgeBillingProperties;
import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeBillingSummary;
import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class ShopifyBridgeBillingService {

    private final ShopifyBridgeBillingProperties billingProperties;
    private final ShopifyBridgeProperties bridgeProperties;

    public ShopifyBridgeBillingService(ShopifyBridgeBillingProperties billingProperties,
                                       ShopifyBridgeProperties bridgeProperties) {
        this.billingProperties = billingProperties;
        this.bridgeProperties = bridgeProperties;
    }

    public ShopifyBridgeBillingSummary summarize() {
        String mode = normalizeMode(billingProperties.mode());
        if ("FREE".equals(mode)) {
            return new ShopifyBridgeBillingSummary(
                "FREE",
                billingProperties.planName(),
                "ACTIVE",
                false,
                false,
                "The Shopify Companion app is currently running in free mode. No merchant billing approval is required."
            );
        }

        boolean configured = hasText(billingProperties.appSubscriptionPlanHandle())
            && hasText(bridgeProperties.publicBaseUrl())
            && hasText(bridgeProperties.shopifyApiKey());
        return new ShopifyBridgeBillingSummary(
            "SHOPIFY_APP_SUBSCRIPTION",
            billingProperties.planName(),
            configured ? "READY_FOR_APPROVAL" : "SETUP_REQUIRED",
            true,
            !configured,
            configured
                ? "Paid launch posture is configured. Merchant approval is required when billing activation is enabled."
                : "Paid launch posture is selected, but Shopify app subscription configuration is incomplete."
        );
    }

    private String normalizeMode(String mode) {
        return hasText(mode) ? mode.trim().toUpperCase(Locale.ROOT) : "FREE";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
