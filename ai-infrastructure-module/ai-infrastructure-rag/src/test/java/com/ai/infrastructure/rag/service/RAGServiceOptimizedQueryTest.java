package com.ai.infrastructure.rag.service;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.core.AISearchService;
import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.ai.infrastructure.rag.dto.RAGRequest;
import com.ai.infrastructure.rag.dto.RAGResponse;
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
import static org.mockito.Mockito.verify;

/**
 * Tests for RAGService retrieval with query handling.
 * 
 * <p><strong>Note:</strong> RAGService does NOT perform PII detection or LLM generation.
 * The orchestration pipeline handles PII via {@code PIIDetectionStep} and generation
 * is done in orchestrator steps.</p>
 */
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
    private AIEmbeddingResponse embeddingResponse;

    private RAGService ragService;

    @BeforeEach
    void setUp() {
        ragService = new RAGService(config, embeddingService, vectorDatabaseService, vectorDatabase, searchService);

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
    }

    @Test
    void retrieveUsesQueryForEmbeddings() {
        ArgumentCaptor<AIEmbeddingRequest> embeddingCaptor = ArgumentCaptor.forClass(AIEmbeddingRequest.class);

        RAGResponse response = ragService.retrieve(RAGRequest.builder()
            .query("user query about products")
            .entityType("product")
            .limit(3)
            .threshold(0.5)
            .build());

        assertThat(response).isNotNull();
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getDocuments()).hasSize(1);

        verify(embeddingService).generateEmbedding(embeddingCaptor.capture());
        assertThat(embeddingCaptor.getValue().getText()).isEqualTo("user query about products");
    }
    
    @Test
    void retrieveReturnsDocumentsWithRelevanceScores() {
        RAGResponse response = ragService.retrieve(RAGRequest.builder()
            .query("search query")
            .entityType("product")
            .limit(5)
            .threshold(0.6)
            .build());

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getDocuments()).hasSize(1);
        assertThat(response.getDocuments().get(0).getScore()).isEqualTo(0.9);
        assertThat(response.getMaxScore()).isEqualTo(0.9);
    }
}
