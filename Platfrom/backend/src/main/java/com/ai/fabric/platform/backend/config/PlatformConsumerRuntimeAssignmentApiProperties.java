package com.ai.fabric.platform.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.public-api.consumer-runtime-assignment")
public record PlatformConsumerRuntimeAssignmentApiProperties(
    boolean enabled,
    String consumerId,
    String apiKeyHeaderName,
    String apiKey,
    String apiKeySecretName
) {
}
