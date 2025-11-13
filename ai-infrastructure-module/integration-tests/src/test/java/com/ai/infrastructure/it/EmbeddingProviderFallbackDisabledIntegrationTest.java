package com.ai.infrastructure.it;

import com.ai.infrastructure.cache.AICacheConfig;
import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.embedding.EmbeddingProvider;
import com.ai.infrastructure.exception.AIServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest(classes = TestApplication.class,
    properties = {
        "ai.providers.embedding-provider=openai",
        "ai.providers.enable-fallback=false"
    })
@ActiveProfiles("dev")
@Import(AICacheConfig.class)
class EmbeddingProviderFallbackDisabledIntegrationTest {

    @Autowired
    private AIEmbeddingService embeddingService;

    @Autowired
    private CacheManager cacheManager;

    @MockBean(name = "openAIEmbeddingProvider")
    private EmbeddingProvider primaryEmbeddingProvider;

    @MockBean(name = "onnxFallbackEmbeddingProvider")
    private EmbeddingProvider fallbackEmbeddingProvider;

    private Cache cache;

    @BeforeEach
    void setUp() {
        cache = cacheManager.getCache("embeddings");
        if (cache != null) {
            cache.clear();
        }
        embeddingService.clearMetrics();

        Mockito.reset(primaryEmbeddingProvider, fallbackEmbeddingProvider);
        Mockito.when(primaryEmbeddingProvider.getProviderName()).thenReturn("openai");
        Mockito.when(primaryEmbeddingProvider.isAvailable()).thenReturn(true);

        Mockito.when(fallbackEmbeddingProvider.getProviderName()).thenReturn("onnx");
        Mockito.when(fallbackEmbeddingProvider.isAvailable()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        if (cache != null) {
            cache.clear();
        }
    }

    @Test
    @DisplayName("Fallback pathway is skipped when disabled in configuration")
    void fallbackDisabledSkipsFallbackProvider() {
        Mockito.when(primaryEmbeddingProvider.generateEmbedding(ArgumentMatchers.any()))
            .thenThrow(new AIServiceException("Simulated primary outage"));

        AIEmbeddingRequest request = AIEmbeddingRequest.builder()
            .text("disable fallback scenario")
            .model("text-embedding-3-small")
            .build();

        assertThrows(AIServiceException.class, () -> embeddingService.generateEmbedding(request));

        verify(primaryEmbeddingProvider).generateEmbedding(ArgumentMatchers.any());
        verify(fallbackEmbeddingProvider, never()).generateEmbedding(ArgumentMatchers.any());
    }
}
