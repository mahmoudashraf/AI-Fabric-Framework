package com.ai.fabric.platform.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "platform.inference.provisioning")
public record PlatformInferenceProvisioningProperties(
    String sharedProjectNamePrefix,
    String sharedEmbeddingServiceRoot,
    String sharedEmbeddingDockerfilePath,
    String sharedEmbeddingServiceNamePrefix,
    String sharedEmbeddingHealthPath,
    String sharedOllamaServiceRoot,
    String sharedOllamaDockerfilePath,
    String sharedOllamaServiceNamePrefix,
    String sharedOllamaHealthPath,
    String dedicatedEmbeddingServiceRoot,
    String dedicatedEmbeddingDockerfilePath,
    String dedicatedEmbeddingServiceNamePrefix,
    String dedicatedEmbeddingHealthPath,
    Duration pollInterval,
    Duration requestTimeout
) {

    public PlatformInferenceProvisioningProperties {
        sharedProjectNamePrefix = normalizeText(sharedProjectNamePrefix, "loom-inference");
        sharedEmbeddingServiceRoot = normalizeText(sharedEmbeddingServiceRoot, "ai-fabric-product/ai-fabric-embedding-worker");
        sharedEmbeddingDockerfilePath = normalizeText(
            sharedEmbeddingDockerfilePath,
            "ai-fabric-product/ai-fabric-embedding-worker/deploy/railway/Dockerfile"
        );
        sharedEmbeddingServiceNamePrefix = normalizeText(sharedEmbeddingServiceNamePrefix, "shared-embeddings");
        sharedEmbeddingHealthPath = normalizeText(sharedEmbeddingHealthPath, "/actuator/health");
        sharedOllamaServiceRoot = normalizeText(sharedOllamaServiceRoot, "ai-fabric-product/ai-fabric-shared-ollama-service");
        sharedOllamaDockerfilePath = normalizeText(
            sharedOllamaDockerfilePath,
            "ai-fabric-product/ai-fabric-shared-ollama-service/deploy/railway/Dockerfile"
        );
        sharedOllamaServiceNamePrefix = normalizeText(sharedOllamaServiceNamePrefix, "shared-ollama");
        sharedOllamaHealthPath = normalizeText(sharedOllamaHealthPath, "/api/tags");
        dedicatedEmbeddingServiceRoot = normalizeText(dedicatedEmbeddingServiceRoot, "ai-fabric-product/ai-fabric-embedding-worker");
        dedicatedEmbeddingDockerfilePath = normalizeText(
            dedicatedEmbeddingDockerfilePath,
            "ai-fabric-product/ai-fabric-embedding-worker/deploy/railway/Dockerfile"
        );
        dedicatedEmbeddingServiceNamePrefix = normalizeText(dedicatedEmbeddingServiceNamePrefix, "embedding-worker");
        dedicatedEmbeddingHealthPath = normalizeText(dedicatedEmbeddingHealthPath, "/actuator/health");
        pollInterval = pollInterval == null || pollInterval.isZero() || pollInterval.isNegative()
            ? Duration.ofSeconds(15)
            : pollInterval;
        requestTimeout = requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()
            ? Duration.ofMinutes(5)
            : requestTimeout;
    }

    private static String normalizeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
