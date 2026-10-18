package com.ai.fabric.embedding.worker.budget;

import ai.fabric.config.AIProviderConfig;
import ai.fabric.embedding.EmbeddingProvider;
import ai.fabric.provider.onnx.ONNXEmbeddingProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** A separate paid worker is not covered by the runtime's deployment-local ledger. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "loomai.provider-budget.enabled", havingValue = "true")
public class BoundedEmbeddingWorkerConfiguration {
    @Bean
    SmartInitializingSingleton boundedEmbeddingWorkerLocalOnly(AIProviderConfig providers,
        @Qualifier("onnxEmbeddingProvider") EmbeddingProvider localProvider, Environment environment) {
        return () -> validate(providers, localProvider,
            environment.getProperty("ai.service.features.enable-generation", Boolean.class, false));
    }

    static void validate(AIProviderConfig providers, EmbeddingProvider provider, boolean generationEnabled) {
        if (generationEnabled || !"onnx".equals(providers.getEmbeddingProvider())
            || Boolean.TRUE.equals(providers.getEnableFallback())
            || (providers.getEmbeddingFallbackProviders() != null && !providers.getEmbeddingFallbackProviders().isEmpty())
            || provider == null || provider.getClass() != ONNXEmbeddingProvider.class || !provider.isAvailable()) {
            throw new IllegalStateException("BOUNDED_EMBEDDING_WORKER_REQUIRES_LOCAL_ONNX_WITHOUT_FALLBACK");
        }
    }
}
