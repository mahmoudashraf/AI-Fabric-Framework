package com.ai.fabric.product.shopify.bridge.billing.service;

import com.ai.fabric.product.shopify.bridge.billing.config.ShopifyBridgeBillingProperties;
import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShopifyBridgeBillingServiceTest {

    @Test
    void freeModeIsActiveAndNotBlocked() {
        ShopifyBridgeBillingService service = new ShopifyBridgeBillingService(
            new ShopifyBridgeBillingProperties("FREE", "Companion Free", ""),
            properties("https://bridge.example.com", "shopify-api-key")
        );

        var summary = service.summarize();

        assertThat(summary.mode()).isEqualTo("FREE");
        assertThat(summary.status()).isEqualTo("ACTIVE");
        assertThat(summary.launchBlocked()).isFalse();
        assertThat(summary.merchantApprovalRequired()).isFalse();
    }

    @Test
    void paidModeWithoutPlanHandleIsBlocked() {
        ShopifyBridgeBillingService service = new ShopifyBridgeBillingService(
            new ShopifyBridgeBillingProperties("SHOPIFY_APP_SUBSCRIPTION", "Companion Pro", ""),
            properties("https://bridge.example.com", "shopify-api-key")
        );

        var summary = service.summarize();

        assertThat(summary.mode()).isEqualTo("SHOPIFY_APP_SUBSCRIPTION");
        assertThat(summary.status()).isEqualTo("SETUP_REQUIRED");
        assertThat(summary.launchBlocked()).isTrue();
        assertThat(summary.merchantApprovalRequired()).isTrue();
    }

    @Test
    void paidModeWithPlanHandleIsReadyForApproval() {
        ShopifyBridgeBillingService service = new ShopifyBridgeBillingService(
            new ShopifyBridgeBillingProperties("SHOPIFY_APP_SUBSCRIPTION", "Companion Pro", "companion-pro"),
            properties("https://bridge.example.com", "shopify-api-key")
        );

        var summary = service.summarize();

        assertThat(summary.status()).isEqualTo("READY_FOR_APPROVAL");
        assertThat(summary.launchBlocked()).isFalse();
        assertThat(summary.merchantApprovalRequired()).isTrue();
    }

    private ShopifyBridgeProperties properties(String publicBaseUrl, String shopifyApiKey) {
        return new ShopifyBridgeProperties(
            "Bridge",
            "shopify-bridge-prod",
            "SHOPIFY",
            "SHOPIFY_BRIDGE_SERVICE",
            "prod",
            publicBaseUrl,
            "2026-04",
            shopifyApiKey,
            "shopify-secret",
            "https://platform.example.com",
            "platform-admin-key",
            "X-PLATFORM-API-KEY",
            "webhook-secret",
            "bridge-admin-key",
            "X-BRIDGE-API-KEY"
        );
    }
}
