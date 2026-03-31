package com.ai.fabric.platform.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "platform.auth")
public record PlatformAuthProperties(
    boolean enabled,
    String headerName,
    boolean apiKeyEnabled,
    boolean sessionEnabled,
    String sessionCookieName,
    Duration sessionTtl,
    boolean sessionCookieSecure,
    String sessionCookieSameSite,
    String operatorApiKey,
    String adminApiKey,
    boolean bootstrapAdminEnabled,
    String bootstrapAdminEmail,
    String bootstrapAdminPassword,
    String bootstrapAdminDisplayName
) {
}
