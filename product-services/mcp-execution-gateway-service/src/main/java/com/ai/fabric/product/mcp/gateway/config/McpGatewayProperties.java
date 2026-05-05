package com.ai.fabric.product.mcp.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "mcp.gateway")
public record McpGatewayProperties(
    String serviceRef,
    String environmentScope,
    String internalApiKey,
    String internalApiKeyHeader,
    String protocolVersion,
    List<String> apiKeyHeaderAllowlist,
    List<String> profileRefAllowlist,
    boolean environmentSecretResolutionEnabled,
    String environmentSecretRefPrefix,
    Duration connectTimeout,
    Duration readTimeout
) {

    public McpGatewayProperties {
        serviceRef = normalize(serviceRef, "mcp-execution-gateway-local");
        environmentScope = normalize(environmentScope, "local");
        internalApiKey = trim(internalApiKey);
        internalApiKeyHeader = normalize(internalApiKeyHeader, "X-MCP-GATEWAY-API-KEY");
        protocolVersion = normalize(protocolVersion, "2025-11-25");
        apiKeyHeaderAllowlist = normalizeList(apiKeyHeaderAllowlist, List.of(
            "X-API-KEY",
            "X-MCP-API-KEY",
            "X-LOOM-MCP-KEY"
        ));
        profileRefAllowlist = normalizeList(profileRefAllowlist, List.of(
            "MCP_PROFILE_SHOPIFY_UCP_AGENT",
            "SHOPIFY_BRIDGE_MCP_UCP_AGENT_PROFILE"
        ));
        environmentSecretRefPrefix = normalize(environmentSecretRefPrefix, "MCP_SECRET_");
        connectTimeout = validDuration(connectTimeout, Duration.ofSeconds(5));
        readTimeout = validDuration(readTimeout, Duration.ofSeconds(30));
    }

    public boolean internalApiKeyConfigured() {
        return StringUtils.hasText(internalApiKey);
    }

    private static String normalize(String value, String fallback) {
        String normalized = trim(value);
        return StringUtils.hasText(normalized) ? normalized : fallback;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static Duration validDuration(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }

    private static List<String> normalizeList(List<String> values, List<String> fallback) {
        if (values == null || values.isEmpty()) {
            return fallback;
        }
        List<String> normalized = values.stream()
            .map(McpGatewayProperties::trim)
            .filter(StringUtils::hasText)
            .map(value -> value.trim().toUpperCase(java.util.Locale.ROOT))
            .distinct()
            .toList();
        return normalized.isEmpty() ? fallback : normalized;
    }
}
