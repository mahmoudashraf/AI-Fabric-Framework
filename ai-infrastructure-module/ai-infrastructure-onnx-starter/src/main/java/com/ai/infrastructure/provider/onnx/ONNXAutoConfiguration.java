package com.ai.infrastructure.provider.onnx;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.embedding.EmbeddingProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Auto-configuration for ONNX embedding provider support.
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(ONNXEmbeddingProvider.class)
public class ONNXAutoConfiguration {

    @Bean
    @Primary
    @ConditionalOnProperty(name = "ai.providers.embedding-provider", havingValue = "onnx", matchIfMissing = true)
    public EmbeddingProvider onnxEmbeddingProvider(AIProviderConfig config) {
        log.info("Creating ONNX Embedding Provider (primary/default)");
        ONNXEmbeddingProvider provider = new ONNXEmbeddingProvider(config);
        if (!provider.isAvailable()) {
            log.warn("WARNING: ONNX Embedding Provider is not available. Model file may be missing.");
            log.warn("Please ensure the ONNX model file exists at: {}", config.getOnnx().getModelPath());
        }
        return provider;
    }

    @Bean(name = "onnxFallbackEmbeddingProvider")
    @ConditionalOnProperty(name = "ai.providers.enable-fallback", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(name = "onnxEmbeddingProvider")
    public EmbeddingProvider onnxFallbackEmbeddingProvider(AIProviderConfig config) {
        log.info("Creating ONNX fallback Embedding Provider");
        ONNXEmbeddingProvider provider = new ONNXEmbeddingProvider(config);
        if (!provider.isAvailable()) {
            log.warn("WARNING: ONNX fallback provider is not available. Model file may be missing.");
            log.warn("Please ensure the ONNX model file exists at: {}", config.getOnnx().getModelPath());
        }
        return provider;
    }
}
