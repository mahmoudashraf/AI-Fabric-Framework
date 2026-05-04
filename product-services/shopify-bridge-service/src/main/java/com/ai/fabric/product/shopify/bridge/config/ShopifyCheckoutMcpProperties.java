package com.ai.fabric.product.shopify.bridge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "shopify.bridge.checkout-mcp")
public record ShopifyCheckoutMcpProperties(
    boolean enabled,
    String clientId,
    String clientSecret,
    URI tokenUrl,
    Duration tokenRefreshSkew,
    boolean terminalOperationsEnabled
) {

    public ShopifyCheckoutMcpProperties {
        clientId = normalize(clientId, "");
        clientSecret = normalize(clientSecret, "");
        tokenUrl = tokenUrl == null ? URI.create("https://api.shopify.com/auth/access_token") : tokenUrl;
        tokenRefreshSkew = tokenRefreshSkew == null || tokenRefreshSkew.isNegative() || tokenRefreshSkew.isZero()
            ? Duration.ofMinutes(5)
            : tokenRefreshSkew;
    }

    public boolean configured() {
        return enabled && !clientId.isBlank() && !clientSecret.isBlank() && tokenUrl != null;
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
