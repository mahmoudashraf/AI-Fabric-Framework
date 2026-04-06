package com.ai.fabric.platform.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "platform.vectorization")
public record PlatformVectorizationProperties(
    Duration registrationTokenTtl,
    Duration runnerSessionTtl,
    Duration runLeaseTtl,
    int maxRecentRuns,
    String requiredProductVersion,
    String requiredCompatibilityVersion
) {

    public PlatformVectorizationProperties {
        registrationTokenTtl = normalizeDuration(registrationTokenTtl, Duration.ofDays(7));
        runnerSessionTtl = normalizeDuration(runnerSessionTtl, Duration.ofHours(6));
        runLeaseTtl = normalizeDuration(runLeaseTtl, Duration.ofMinutes(15));
        maxRecentRuns = maxRecentRuns <= 0 ? 20 : maxRecentRuns;
        requiredProductVersion = normalizeText(requiredProductVersion, "2026.04.track-b");
        requiredCompatibilityVersion = normalizeText(requiredCompatibilityVersion, "1");
    }

    private static Duration normalizeDuration(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }

    private static String normalizeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
