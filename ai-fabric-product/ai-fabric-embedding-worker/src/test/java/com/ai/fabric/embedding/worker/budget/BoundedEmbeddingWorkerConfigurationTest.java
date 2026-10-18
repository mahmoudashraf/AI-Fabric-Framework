package com.ai.fabric.embedding.worker.budget;

import ai.fabric.config.AIProviderConfig;
import ai.fabric.embedding.EmbeddingProvider;
import ai.fabric.provider.onnx.ONNXEmbeddingProvider;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BoundedEmbeddingWorkerConfigurationTest {
    @Test void permitsOnlyAvailableLocalOnnx() {
        assertThatCode(() -> BoundedEmbeddingWorkerConfiguration.validate(config(), local(), false))
            .doesNotThrowAnyException();
    }

    @Test void rejectsRemoteProviderEvenWhenLocalProviderAlsoExists() {
        var config = config(); config.setEmbeddingProvider("openai");
        rejects(config, local(), false);
    }

    @Test void rejectsFallbackAndFallbackLists() {
        var config = config(); config.setEnableFallback(true); rejects(config, local(), false);
        config = config(); config.setEmbeddingFallbackProviders(List.of("openai")); rejects(config, local(), false);
    }

    @Test void rejectsGenerationOrUnavailableOrSubstitutedProvider() {
        rejects(config(), local(), true);
        rejects(config(), mock(ONNXEmbeddingProvider.class), false);
        rejects(config(), mock(EmbeddingProvider.class), false);
        rejects(config(), null, false);
    }

    private static AIProviderConfig config() {
        var config = new AIProviderConfig(); config.setEmbeddingProvider("onnx");
        config.setEnableFallback(false); config.setEmbeddingFallbackProviders(List.of()); return config;
    }

    private static EmbeddingProvider local() {
        var provider = mock(ONNXEmbeddingProvider.class); when(provider.isAvailable()).thenReturn(true); return provider;
    }

    private static void rejects(AIProviderConfig config, EmbeddingProvider provider, boolean generation) {
        assertThatThrownBy(() -> BoundedEmbeddingWorkerConfiguration.validate(config, provider, generation))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("BOUNDED_EMBEDDING_WORKER_REQUIRES_LOCAL_ONNX_WITHOUT_FALLBACK");
    }
}
