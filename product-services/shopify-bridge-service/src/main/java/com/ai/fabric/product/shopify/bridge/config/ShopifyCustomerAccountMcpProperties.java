package com.ai.fabric.product.shopify.bridge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shopify.bridge.customer-account-mcp")
public record ShopifyCustomerAccountMcpProperties(
    boolean enabled,
    boolean protectedCustomerDataApproved,
    String clientId,
    String redirectUri,
    String scopes
) {

    public ShopifyCustomerAccountMcpProperties {
        clientId = normalize(clientId, "");
        redirectUri = normalize(redirectUri, "");
        scopes = normalize(scopes, "customer-account-mcp-api:full");
    }

    public boolean configured() {
        return enabled
            && protectedCustomerDataApproved
            && !clientId.isBlank()
            && !redirectUri.isBlank()
            && !scopes.isBlank();
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
