package com.ai.fabric.vectorization.runner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ai.fabric.vectorization.runner")
public record VectorizationRunnerProperties(
    String platformBaseUrl,
    String registrationToken,
    String runnerInstanceId,
    String deploymentId,
    String productVersion,
    String compatibilityVersion,
    Duration pollInterval,
    Duration requestTimeout
) {

    public VectorizationRunnerProperties {
        registrationToken = registrationToken == null ? "" : registrationToken.trim();
        runnerInstanceId = runnerInstanceId == null || runnerInstanceId.isBlank() ? null : runnerInstanceId.trim();
        deploymentId = deploymentId == null || deploymentId.isBlank() ? null : deploymentId.trim();
        productVersion = productVersion == null || productVersion.isBlank() ? "2026.04.track-b" : productVersion.trim();
        compatibilityVersion = compatibilityVersion == null || compatibilityVersion.isBlank() ? "1" : compatibilityVersion.trim();
        pollInterval = pollInterval == null || pollInterval.isNegative() || pollInterval.isZero()
            ? Duration.ofSeconds(15)
            : pollInterval;
        requestTimeout = requestTimeout == null || requestTimeout.isNegative() || requestTimeout.isZero()
            ? Duration.ofSeconds(60)
            : requestTimeout;
    }
}
