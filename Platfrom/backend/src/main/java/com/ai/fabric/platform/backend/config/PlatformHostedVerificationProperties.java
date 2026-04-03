package com.ai.fabric.platform.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "platform.verification.runner")
public record PlatformHostedVerificationProperties(
    String scriptRoot,
    Duration timeout,
    int maxOutputCharacters
) {

    public PlatformHostedVerificationProperties {
        scriptRoot = normalize(scriptRoot, "scripts");
        timeout = timeout == null || timeout.isZero() || timeout.isNegative()
            ? Duration.ofMinutes(10)
            : timeout;
        maxOutputCharacters = maxOutputCharacters <= 0 ? 200_000 : maxOutputCharacters;
    }

    private static String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
