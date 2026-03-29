package com.ai.fabric.platform.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "platform.verification")
public record PlatformVerificationProperties(
    Duration timeout,
    String runtimeHealthPath,
    String connectorHealthPath
) {

    public PlatformVerificationProperties {
        timeout = timeout == null ? Duration.ofSeconds(3) : timeout;
        runtimeHealthPath = normalizePath(runtimeHealthPath, "/actuator/health");
        connectorHealthPath = normalizePath(connectorHealthPath, "/actuator/health");
    }

    private static String normalizePath(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.startsWith("/") ? value : "/" + value;
    }
}
