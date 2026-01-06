package com.ai.infrastructure.rag.service;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.core.AISearchService;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.ai.infrastructure.rag.dto.RAGRequest;
import com.ai.infrastructure.rag.dto.RAGResponse;
import com.ai.infrastructure.rag.spi.RAGProvider;
import com.ai.infrastructure.spi.ContentRetriever;
import com.ai.infrastructure.vector.VectorDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RAGService.
 * 
 * <p>Tests verify that RAGService correctly implements both RAGProvider SPI
 * (RAG-specific) and ContentRetriever SPI (generic, used by core pipeline).</p>
 * 
 * <p><strong>Note:</strong> RAGService does NOT perform PII detection. The orchestration
 * pipeline handles PII via {@code PIIDetectionStep} (before) and 
 * {@code ResponseSanitizationStep} (after).</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RAGServiceTest {
    
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
    
    private RAGService ragService;
    
    @BeforeEach
    void setUp() {
        ragService = new RAGService(
            config,
            embeddingService,
            vectorDatabaseService,
            vectorDatabase,
            searchService
        );
        
        // Default mock setup
        when(embeddingService.generateEmbedding(any())).thenReturn(
            AIEmbeddingResponse.builder()
                .embedding(List.of(0.1, 0.2, 0.3))
                .build()
        );
        
        when(searchService.search(any(), any())).thenReturn(
            AISearchResponse.builder()
                .results(Collections.emptyList())
                .totalResults(0)
                .build()
        );
        
        when(config.resolveLlmDefaults()).thenReturn(
            new AIProviderConfig.GenerationDefaults("openai", "gpt-4", 1000, 0.7, 60, 100)
        );
        
        when(config.resolveEmbeddingDefaults()).thenReturn(
            new AIProviderConfig.EmbeddingDefaults("openai", "text-embedding-ada-002")
        );
    }
    
    @Test
    @DisplayName("RAGService implements RAGProvider interface")
    void ragServiceImplementsRAGProvider() {
        assertThat(ragService).isInstanceOf(RAGProvider.class);
    }
    
    @Test
    @DisplayName("RAGService implements ContentRetriever interface")
    void ragServiceImplementsContentRetriever() {
        assertThat(ragService).isInstanceOf(ContentRetriever.class);
    }
    
    @Test
    @DisplayName("getProviderName returns expected name")
    void getProviderNameReturnsExpectedName() {
        assertThat(ragService.getProviderName()).isEqualTo("default-rag-service");
    }
    
    @Test
    @DisplayName("getRetrieverName returns expected name")
    void getRetrieverNameReturnsExpectedName() {
        assertThat(ragService.getRetrieverName()).isEqualTo("default-rag-service");
    }
    
    @Test
    @DisplayName("isAvailable returns true by default")
    void isAvailableReturnsTrueByDefault() {
        assertThat(ragService.isAvailable()).isTrue();
    }
    
    @Test
    @DisplayName("retrieve(RAGRequest) returns successful response")
    void retrieveWithRAGRequestReturnsSuccessfulResponse() {
        RAGRequest request = RAGRequest.builder()
            .query("test query")
            .entityType("document")
            .limit(10)
            .threshold(0.7)
            .build();
        
        RAGResponse response = ragService.retrieve(request);
        
        assertThat(response).isNotNull();
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getDocuments()).isNotNull();
    }
    
    @Test
    @DisplayName("retrieve(String...) returns successful result via ContentRetriever interface")
    void retrieveViaContentRetrieverReturnsSuccessfulResult() {
        ContentRetriever.RetrievalResult result = ragService.retrieve(
            "test query",
            "document",
            10,
            0.7,
            Map.of()
        );
        
        assertThat(result).isNotNull();
        assertThat(result.success()).isTrue();
        assertThat(result.documents()).isNotNull();
    }
    
    @Test
    @DisplayName("getStatistics returns non-null map")
    void getStatisticsReturnsNonNullMap() {
        when(vectorDatabaseService.getStatistics()).thenReturn(Map.of("count", 100));
        when(vectorDatabase.getStatistics()).thenReturn(Map.of("size", 50));
        
        Map<String, Object> stats = ragService.getStatistics();
        
        assertThat(stats).isNotNull();
        assertThat(stats).containsKey("vectorDatabaseService");
        assertThat(stats).containsKey("vectorDatabase");
    }
    
    @Test
    @DisplayName("retrieve handles errors gracefully")
    void retrieveHandlesErrorsGracefully() {
        when(embeddingService.generateEmbedding(any())).thenThrow(new RuntimeException("Test error"));
        
        RAGRequest request = RAGRequest.builder()
            .query("test query")
            .entityType("document")
            .limit(10)
            .build();
        
        RAGResponse response = ragService.retrieve(request);
        
        assertThat(response).isNotNull();
        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("Test error");
    }
    
    @Test
    @DisplayName("retrieve via ContentRetriever handles errors gracefully")
    void retrieveViaContentRetrieverHandlesErrorsGracefully() {
        when(embeddingService.generateEmbedding(any())).thenThrow(new RuntimeException("Test error"));
        
        ContentRetriever.RetrievalResult result = ragService.retrieve(
            "test query",
            "document",
            10,
            0.7,
            Map.of()
        );
        
        assertThat(result).isNotNull();
        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("Test error");
    }
}
