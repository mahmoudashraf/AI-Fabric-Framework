package com.ai.infrastructure.it;

import com.ai.infrastructure.cache.AICacheConfig;
import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import com.ai.infrastructure.embedding.EmbeddingProvider;
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

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest(classes = TestApplication.class,
    properties = {
        "ai.providers.embedding-provider=rest",
        "ai.providers.rest.enabled=true",
        "ai.providers.rest.base-url=http://localhost:8900/mock",
        "ai.providers.enable-fallback=true"
    })
@ActiveProfiles("dev")
@Import(AICacheConfig.class)
class EmbeddingProviderSelectionIntegrationTest {

    @Autowired
    private AIEmbeddingService embeddingService;

    @Autowired
    private CacheManager cacheManager;

    @MockBean(name = "restEmbeddingProvider")
    private EmbeddingProvider restEmbeddingProvider;

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

        Mockito.reset(restEmbeddingProvider, fallbackEmbeddingProvider);
        Mockito.when(restEmbeddingProvider.getProviderName()).thenReturn("rest");
        Mockito.when(restEmbeddingProvider.isAvailable()).thenReturn(true);

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
    @DisplayName("Embedding service honours explicit provider selection")
    void embeddingServiceUsesRequestedProvider() {
        AIEmbeddingResponse expectedResponse = AIEmbeddingResponse.builder()
            .embedding(List.of(0.12, 0.34, 0.56))
            .model("rest-batch")
            .dimensions(3)
            .processingTimeMs(15L)
            .requestId(UUID.randomUUID().toString())
            .build();

        Mockito.when(restEmbeddingProvider.generateEmbedding(ArgumentMatchers.any()))
            .thenReturn(expectedResponse);

        AIEmbeddingRequest request = AIEmbeddingRequest.builder()
            .text("provider selection smoke test")
            .model("rest-embed-simulated")
            .build();

        AIEmbeddingResponse response = embeddingService.generateEmbedding(request);

        assertSame(expectedResponse, response, "Embedding service should use the explicitly requested provider");
        verify(restEmbeddingProvider).generateEmbedding(ArgumentMatchers.any());
        verify(fallbackEmbeddingProvider, never()).generateEmbedding(ArgumentMatchers.any());
    }
}
