package com.ai.fabric.platform.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "platform.verification.suites")
public record PlatformVerificationSuiteProperties(
    Duration timeout,
    Duration hostedStageTimeout,
    Duration scriptStageTimeout,
    Duration pollInterval,
    int maxRecentRuns,
    int maxStageLogCharacters,
    String platformUiBaseUrl,
    String weaviateHost,
    String shopifyBridgeBaseUrl,
    String shopifyShopDomain,
    String shopifyProductServiceRef,
    String shopifyEmbeddedHost
) {

    private static final String DEFAULT_PLATFORM_UI_BASE_URL = "https://platform-ui-production-00e3.up.railway.app";
    private static final String DEFAULT_WEAVIATE_HOST = "weaviate-external-verify-dev.up.railway.app";
    private static final String DEFAULT_SHOPIFY_BRIDGE_BASE_URL = "https://shopify-bridge-shopify-bridge-pr-production.up.railway.app";
    private static final String DEFAULT_SHOPIFY_SHOP_DOMAIN = "shopping-companion-test.myshopify.com";
    private static final String DEFAULT_SHOPIFY_PRODUCT_SERVICE_REF = "shopify-bridge-prod";

    public PlatformVerificationSuiteProperties {
        timeout = timeout == null || timeout.isZero() || timeout.isNegative()
            ? Duration.ofMinutes(60)
            : timeout;
        hostedStageTimeout = hostedStageTimeout == null || hostedStageTimeout.isZero() || hostedStageTimeout.isNegative()
            ? Duration.ofMinutes(12)
            : hostedStageTimeout;
        scriptStageTimeout = scriptStageTimeout == null || scriptStageTimeout.isZero() || scriptStageTimeout.isNegative()
            ? Duration.ofMinutes(20)
            : scriptStageTimeout;
        pollInterval = pollInterval == null || pollInterval.isZero() || pollInterval.isNegative()
            ? Duration.ofSeconds(3)
            : pollInterval;
        maxRecentRuns = maxRecentRuns <= 0 ? 20 : maxRecentRuns;
        maxStageLogCharacters = maxStageLogCharacters <= 0 ? 12_000 : maxStageLogCharacters;
        platformUiBaseUrl = defaultValue(platformUiBaseUrl, DEFAULT_PLATFORM_UI_BASE_URL);
        weaviateHost = defaultValue(weaviateHost, DEFAULT_WEAVIATE_HOST);
        shopifyBridgeBaseUrl = defaultValue(shopifyBridgeBaseUrl, DEFAULT_SHOPIFY_BRIDGE_BASE_URL);
        shopifyShopDomain = defaultValue(shopifyShopDomain, DEFAULT_SHOPIFY_SHOP_DOMAIN);
        shopifyProductServiceRef = defaultValue(shopifyProductServiceRef, DEFAULT_SHOPIFY_PRODUCT_SERVICE_REF);
        shopifyEmbeddedHost = normalize(shopifyEmbeddedHost);
    }

    private static String defaultValue(String value, String defaultValue) {
        String normalized = normalize(value);
        return normalized == null ? defaultValue : normalized;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }
}
