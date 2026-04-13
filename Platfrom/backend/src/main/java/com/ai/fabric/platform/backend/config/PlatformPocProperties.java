package com.ai.fabric.platform.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "platform.poc")
public record PlatformPocProperties(
    Duration runtimeTimeout
) {

    public PlatformPocProperties {
        runtimeTimeout = normalizePositiveDuration(runtimeTimeout, Duration.ofSeconds(60));
    }

    private static Duration normalizePositiveDuration(Duration value, Duration fallback) {
        if (value == null || value.isZero() || value.isNegative()) {
            return fallback;
        }
        return value;
    }
}
