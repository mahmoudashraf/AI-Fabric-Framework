package com.ai.fabric.platform.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.auth")
public record PlatformAuthProperties(
    boolean enabled,
    String headerName,
    String operatorApiKey,
    String adminApiKey
) {
}
