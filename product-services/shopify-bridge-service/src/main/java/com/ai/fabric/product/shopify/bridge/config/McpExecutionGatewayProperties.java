package com.ai.fabric.product.shopify.bridge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "shopify.bridge.mcp-gateway")
public record McpExecutionGatewayProperties(
    String baseUrl,
    String apiKey,
    String apiKeyHeader,
    String executePath,
    Duration connectTimeout,
    Duration readTimeout
) {

    public McpExecutionGatewayProperties {
        baseUrl = normalize(baseUrl, "");
        apiKey = normalize(apiKey, "");
        apiKeyHeader = normalize(apiKeyHeader, "X-MCP-GATEWAY-API-KEY");
        executePath = normalize(executePath, "/api/internal/mcp/actions/execute");
        connectTimeout = connectTimeout == null || connectTimeout.isZero() || connectTimeout.isNegative()
            ? Duration.ofSeconds(5)
            : connectTimeout;
        readTimeout = readTimeout == null || readTimeout.isZero() || readTimeout.isNegative()
            ? Duration.ofSeconds(30)
            : readTimeout;
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
