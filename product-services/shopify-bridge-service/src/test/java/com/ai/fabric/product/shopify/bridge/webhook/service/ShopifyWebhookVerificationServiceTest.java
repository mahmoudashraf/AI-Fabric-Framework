package com.ai.fabric.product.shopify.bridge.webhook.service;

import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShopifyWebhookVerificationServiceTest {

    @Test
    void verifyMatchesComputedHmac() {
        ShopifyWebhookVerificationService service = new ShopifyWebhookVerificationService(properties("super-secret"));
        String payload = "{\"myshopify_domain\":\"alpha.myshopify.com\"}";

        String hmac = service.compute(payload);

        assertThat(service.verify(payload, hmac)).isTrue();
        assertThat(service.verify(payload, "bad-hmac")).isFalse();
    }

    private ShopifyBridgeProperties properties(String webhookSecret) {
        return new ShopifyBridgeProperties(
            "Bridge",
            "shopify-bridge-test",
            "SHOPIFY",
            "SHOPIFY_BRIDGE_SERVICE",
            "test",
            "https://bridge.example.com",
            "2026-04",
            "shopify-api-key",
            "shopify-api-secret",
            "https://platform.example.com",
            "platform-admin-key",
            "X-PLATFORM-API-KEY",
            webhookSecret,
            "bridge-admin-key",
            "X-BRIDGE-API-KEY",
        "runtime-api-key",
        "runtime-signing-key",
        "platform-consumer-bridge",
        300
        );
    }
}
