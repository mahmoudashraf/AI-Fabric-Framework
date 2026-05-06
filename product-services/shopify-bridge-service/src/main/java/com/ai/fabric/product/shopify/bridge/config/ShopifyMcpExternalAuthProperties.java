package com.ai.fabric.product.shopify.bridge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "shopify.bridge.mcp-external-auth")
public record ShopifyMcpExternalAuthProperties(
    boolean customerAccountMcpEnabled,
    boolean customerAccountMcpProtectedDataApproved,
    String customerAccountMcpClientId,
    String customerAccountMcpClientSecret,
    String customerAccountMcpRedirectUri,
    List<String> customerAccountMcpScopes,
    Duration customerAccountMcpStateTtl,
    Duration customerAccountMcpSessionTtl,
    Duration customerAccountMcpConnectTimeout,
    Duration customerAccountMcpReadTimeout,
    boolean checkoutMcpEnabled,
    boolean checkoutMcpTerminalOperationsEnabled
) {

    public ShopifyMcpExternalAuthProperties {
        customerAccountMcpClientId = normalize(customerAccountMcpClientId);
        customerAccountMcpClientSecret = normalize(customerAccountMcpClientSecret);
        customerAccountMcpRedirectUri = normalize(customerAccountMcpRedirectUri);
        customerAccountMcpScopes = customerAccountMcpScopes == null
            ? List.of()
            : customerAccountMcpScopes.stream()
                .map(ShopifyMcpExternalAuthProperties::normalize)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        customerAccountMcpStateTtl = normalizeDuration(customerAccountMcpStateTtl, Duration.ofMinutes(10));
        customerAccountMcpSessionTtl = normalizeDuration(customerAccountMcpSessionTtl, Duration.ofDays(30));
        customerAccountMcpConnectTimeout = normalizeDuration(customerAccountMcpConnectTimeout, Duration.ofSeconds(5));
        customerAccountMcpReadTimeout = normalizeDuration(customerAccountMcpReadTimeout, Duration.ofSeconds(30));
    }

    public boolean customerAccountConfigured() {
        return customerAccountMcpEnabled
            && customerAccountMcpProtectedDataApproved
            && StringUtils.hasText(customerAccountMcpClientId)
            && StringUtils.hasText(customerAccountMcpClientSecret)
            && StringUtils.hasText(customerAccountMcpRedirectUri)
            && !customerAccountMcpScopes.isEmpty();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static Duration normalizeDuration(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }
}
