package com.ai.fabric.platform.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.delivery")
public record PlatformDeliveryProperties(
    String publicBaseUrl
) {

    public PlatformDeliveryProperties {
        publicBaseUrl = normalize(publicBaseUrl);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8088";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
