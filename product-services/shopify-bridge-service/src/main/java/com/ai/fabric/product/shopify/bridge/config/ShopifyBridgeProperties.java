package com.ai.fabric.product.shopify.bridge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shopify.bridge")
public record ShopifyBridgeProperties(
    String appName,
    String serviceRef,
    String productFamily,
    String serviceKind,
    String environmentScope,
    String publicBaseUrl,
    String shopifyAdminApiVersion,
    String shopifyApiKey,
    String shopifyApiSecret,
    String platformBaseUrl,
    String platformAdminApiKey,
    String platformAdminApiKeyHeader,
    String webhookSharedSecret,
    String adminApiKey,
    String adminApiKeyHeader
) {

    public ShopifyBridgeProperties {
        appName = normalize(appName, "Shopify Bridge Service");
        serviceRef = normalize(serviceRef, "shopify-bridge-local");
        productFamily = normalize(productFamily, "SHOPIFY");
        serviceKind = normalize(serviceKind, "SHOPIFY_BRIDGE_SERVICE");
        environmentScope = normalize(environmentScope, "local");
        publicBaseUrl = normalize(publicBaseUrl, "");
        shopifyAdminApiVersion = normalize(shopifyAdminApiVersion, "2026-04");
        shopifyApiKey = normalize(shopifyApiKey, "");
        shopifyApiSecret = normalize(shopifyApiSecret, "");
        platformBaseUrl = normalize(platformBaseUrl, "");
        platformAdminApiKey = normalize(platformAdminApiKey, "");
        platformAdminApiKeyHeader = normalize(platformAdminApiKeyHeader, "X-PLATFORM-API-KEY");
        webhookSharedSecret = normalize(webhookSharedSecret, "");
        adminApiKey = normalize(adminApiKey, "");
        adminApiKeyHeader = normalize(adminApiKeyHeader, "X-BRIDGE-API-KEY");
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
