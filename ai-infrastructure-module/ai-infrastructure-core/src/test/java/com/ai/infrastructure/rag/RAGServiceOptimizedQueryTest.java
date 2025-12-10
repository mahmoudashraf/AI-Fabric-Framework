package com.ai.infrastructure.rag;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.core.AISearchService;
import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.dto.PIIDetectionResult;
import com.ai.infrastructure.dto.PIIMode;
import com.ai.infrastructure.dto.RAGRequest;
import com.ai.infrastructure.dto.RAGResponse;
import com.ai.infrastructure.privacy.pii.PIIDetectionService;
import com.ai.infrastructure.vector.VectorDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RAGServiceOptimizedQueryTest {

    @Mock
    private AIProviderConfig config;

    @Mock
    private AIEmbeddingService embeddingService;

    @Mock
    private VectorDatabaseService vectorDatabaseService;

    @Mock
    private VectorDatabase vectorDatabase;

    @Mock
    private AISearchService searchService;

    @Mock
    private PIIDetectionService piiDetectionService;

    @Mock
    private AIEmbeddingResponse embeddingResponse;

    private RAGService ragService;

    @BeforeEach
    void setUp() {
        ragService = new RAGService(config, embeddingService, vectorDatabaseService, vectorDatabase, searchService, piiDetectionService);

        when(config.resolveLlmDefaults()).thenReturn(new AIProviderConfig.GenerationDefaults("openai", "gpt-test", 1024, 0.0, 60, 1));

        when(embeddingService.generateEmbedding(any(AIEmbeddingRequest.class))).thenReturn(embeddingResponse);
        when(embeddingResponse.getEmbedding()).thenReturn(List.of(0.1, 0.2));

        AISearchResponse searchResponse = AISearchResponse.builder()
            .results(List.of(Map.of(
                "id", "doc-1",
                "content", "example",
                "title", "title",
                "type", "doc",
                "score", 0.9,
                "similarity", 0.9,
                "metadata", Map.of()
            )))
            .totalResults(1)
            .processingTimeMs(5L)
            .build();

        when(searchService.search(any(), any())).thenReturn(searchResponse);

        when(piiDetectionService.detectAndProcess(any(String.class))).thenAnswer(invocation ->
            PIIDetectionResult.builder()
                .originalQuery(invocation.getArgument(0))
                .processedQuery(invocation.getArgument(0))
                .modeApplied(PIIMode.PASS_THROUGH)
                .piiDetected(false)
                .build()
        );
    }

    @Test
    void usesOptimizedQueryForEmbeddingsWhenProvided() {
        ArgumentCaptor<AIEmbeddingRequest> embeddingCaptor = ArgumentCaptor.forClass(AIEmbeddingRequest.class);

        RAGResponse response = ragService.performRag(RAGRequest.builder()
            .query("user query about products")
            .metadata(Map.of("optimizedQuery", "Product entities with price_usd < 50"))
            .entityType("product")
            .limit(3)
            .threshold(0.5)
            .build());

        assertThat(response).isNotNull();
        assertThat(response.getMetadata()).containsEntry("optimizedQueryProvided", true);

        org.mockito.Mockito.verify(embeddingService).generateEmbedding(embeddingCaptor.capture());
        assertThat(embeddingCaptor.getValue().getText()).isEqualTo("Product entities with price_usd < 50");
    }
}
