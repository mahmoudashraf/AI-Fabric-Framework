package com.ai.fabric.platform.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform")
public record PlatformProperties(
    String name,
    String stage,
    String currentPhase
) {
}

