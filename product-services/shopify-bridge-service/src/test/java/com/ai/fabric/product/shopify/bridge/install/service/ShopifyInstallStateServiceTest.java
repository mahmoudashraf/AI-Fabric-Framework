package com.ai.fabric.product.shopify.bridge.install.service;

import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShopifyInstallStateServiceTest {

    @Test
    void issuedStateRoundTripsForMatchingShop() {
        ShopifyInstallStateService service = new ShopifyInstallStateService(properties());

        String state = service.issueState("alpha.myshopify.com");

        assertThatNoException().isThrownBy(() -> service.verifyState(state, "alpha.myshopify.com"));
    }

    @Test
    void verifyRejectsDifferentShopDomain() {
        ShopifyInstallStateService service = new ShopifyInstallStateService(properties());

        String state = service.issueState("alpha.myshopify.com");

        assertThatThrownBy(() -> service.verifyState(state, "beta.myshopify.com"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .hasMessageContaining("does not match");
    }

    private ShopifyBridgeProperties properties() {
        return new ShopifyBridgeProperties(
            "Bridge",
            "shopify-bridge-prod",
            "SHOPIFY",
            "SHOPIFY_BRIDGE_SERVICE",
            "prod",
            "https://bridge.example.com",
            "2026-04",
            "shopify-api-key",
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
