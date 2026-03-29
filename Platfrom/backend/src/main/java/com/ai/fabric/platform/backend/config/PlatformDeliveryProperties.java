package com.ai.fabric.platform.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "platform.delivery")
public record PlatformDeliveryProperties(
    String publicBaseUrl,
    boolean signedArtifactsEnabled,
    Duration artifactUrlTtl
) {

    public PlatformDeliveryProperties {
        publicBaseUrl = normalize(publicBaseUrl);
        artifactUrlTtl = artifactUrlTtl == null || artifactUrlTtl.isZero() || artifactUrlTtl.isNegative()
            ? Duration.ofDays(3650)
            : artifactUrlTtl;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8088";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
