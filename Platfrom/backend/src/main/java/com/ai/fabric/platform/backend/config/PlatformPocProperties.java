package com.ai.fabric.platform.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.poc")
public record PlatformPocProperties(
    boolean legacyRuntimeCompatibilityFallbackEnabled
) {

    public PlatformPocProperties {
    }
}
