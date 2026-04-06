package com.ai.fabric.platform.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "platform.vectorization.runner-provisioning")
public record PlatformVectorizationRunnerProvisioningProperties(
    String serviceRoot,
    String dockerfilePath,
    String serviceNamePrefix,
    Duration pollInterval
) {

    public PlatformVectorizationRunnerProvisioningProperties {
        serviceRoot = normalizeText(serviceRoot, "ai-fabric-product/ai-fabric-vectorization-runner");
        dockerfilePath = normalizeText(
            dockerfilePath,
            "ai-fabric-product/ai-fabric-vectorization-runner/deploy/railway/Dockerfile"
        );
        serviceNamePrefix = normalizeText(serviceNamePrefix, "vectorization-runner");
        pollInterval = pollInterval == null || pollInterval.isZero() || pollInterval.isNegative()
            ? Duration.ofSeconds(15)
            : pollInterval;
    }

    private static String normalizeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
