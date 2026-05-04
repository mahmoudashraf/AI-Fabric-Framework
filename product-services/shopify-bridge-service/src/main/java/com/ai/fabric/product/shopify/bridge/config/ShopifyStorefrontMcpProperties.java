package com.ai.fabric.product.shopify.bridge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "shopify.bridge.mcp")
public record ShopifyStorefrontMcpProperties(
    String protocolVersion,
    String ucpAgentProfile,
    String storefrontPassword,
    Duration storefrontPasswordCookieTtl
) {

    public ShopifyStorefrontMcpProperties {
        protocolVersion = normalize(protocolVersion, "2025-11-25");
        ucpAgentProfile = normalize(
            ucpAgentProfile,
            "https://shopify.dev/ucp/agent-profiles/examples/2026-04-08/valid-with-capabilities.json"
        );
        storefrontPassword = normalize(storefrontPassword, "");
        storefrontPasswordCookieTtl = storefrontPasswordCookieTtl == null
            || storefrontPasswordCookieTtl.isZero()
            || storefrontPasswordCookieTtl.isNegative()
            ? Duration.ofMinutes(20)
            : storefrontPasswordCookieTtl;
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
