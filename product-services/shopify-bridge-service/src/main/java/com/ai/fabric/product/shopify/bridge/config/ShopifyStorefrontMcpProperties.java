package com.ai.fabric.product.shopify.bridge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shopify.bridge.mcp")
public record ShopifyStorefrontMcpProperties(
    String protocolVersion,
    String ucpAgentProfile
) {

    public ShopifyStorefrontMcpProperties {
        protocolVersion = normalize(protocolVersion, "2025-11-25");
        ucpAgentProfile = normalize(
            ucpAgentProfile,
            "https://shopify.dev/ucp/agent-profiles/examples/2026-04-08/valid-with-capabilities.json"
        );
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
