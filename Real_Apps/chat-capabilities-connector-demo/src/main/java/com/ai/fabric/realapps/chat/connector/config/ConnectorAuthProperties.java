package com.ai.fabric.realapps.chat.connector.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "connector.auth")
public record ConnectorAuthProperties(
    String apiKey,
    String apiKeyHeader
) {
    public ConnectorAuthProperties {
        apiKeyHeader = apiKeyHeader != null && !apiKeyHeader.isBlank() ? apiKeyHeader.trim() : "X-AIFABRIC-API-KEY";
    }

    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }
}

