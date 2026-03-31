package com.ai.fabric.platform.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "platform.public-api")
public record PlatformPublicApiProperties(
    boolean enabled,
    String clientIdHeaderName,
    String apiKeyHeaderName,
    Map<String, String> clients
) {
}
