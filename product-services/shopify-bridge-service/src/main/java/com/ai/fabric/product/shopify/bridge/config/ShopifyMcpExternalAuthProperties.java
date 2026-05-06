package com.ai.fabric.product.shopify.bridge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.List;

@ConfigurationProperties(prefix = "shopify.bridge.mcp-external-auth")
public record ShopifyMcpExternalAuthProperties(
    boolean customerAccountMcpEnabled,
    boolean customerAccountMcpProtectedDataApproved,
    String customerAccountMcpClientId,
    String customerAccountMcpRedirectUri,
    List<String> customerAccountMcpScopes,
    boolean checkoutMcpEnabled,
    boolean checkoutMcpTerminalOperationsEnabled
) {

    public ShopifyMcpExternalAuthProperties {
        customerAccountMcpClientId = normalize(customerAccountMcpClientId);
        customerAccountMcpRedirectUri = normalize(customerAccountMcpRedirectUri);
        customerAccountMcpScopes = customerAccountMcpScopes == null
            ? List.of()
            : customerAccountMcpScopes.stream()
                .map(ShopifyMcpExternalAuthProperties::normalize)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    public boolean customerAccountConfigured() {
        return customerAccountMcpEnabled
            && customerAccountMcpProtectedDataApproved
            && StringUtils.hasText(customerAccountMcpClientId)
            && StringUtils.hasText(customerAccountMcpRedirectUri)
            && !customerAccountMcpScopes.isEmpty();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
