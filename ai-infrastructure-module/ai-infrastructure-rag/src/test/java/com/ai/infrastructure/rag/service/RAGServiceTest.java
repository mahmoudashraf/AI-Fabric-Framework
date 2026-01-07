package com.ai.infrastructure.rag.service;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.core.AISearchService;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.dto.RAGRequest;
import com.ai.infrastructure.dto.RAGResponse;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.ai.infrastructure.spi.RAGProvider;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RAGService.
 * 
 * <p>Tests verify that RAGService correctly implements the RAGProvider SPI.</p>
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
    @DisplayName("getProviderName returns expected name")
    void getProviderNameReturnsExpectedName() {
        assertThat(ragService.getProviderName()).isEqualTo("default-rag-service");
    }
    
    @Test
    @DisplayName("isAvailable returns true by default")
    void isAvailableReturnsTrueByDefault() {
        assertThat(ragService.isAvailable()).isTrue();
    }
    
    @Test
    @DisplayName("performRag returns successful response")
    void performRagReturnsSuccessfulResponse() {
        RAGRequest request = RAGRequest.builder()
            .query("test query")
            .entityType("document")
            .limit(10)
            .threshold(0.7)
            .build();
        
        RAGResponse response = ragService.performRag(request);
        
        assertThat(response).isNotNull();
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getDocuments()).isNotNull();
    }
    
    @Test
    @DisplayName("performRAGQuery returns successful response")
    void performRAGQueryReturnsSuccessfulResponse() {
        when(vectorDatabaseService.hybridSearch(any(), anyString(), any())).thenReturn(
            AISearchResponse.builder()
                .results(Collections.emptyList())
                .totalResults(0)
                .build()
        );
        
        RAGRequest request = RAGRequest.builder()
            .query("test query")
            .entityType("document")
            .limit(10)
            .threshold(0.7)
            .build();
        
        RAGResponse response = ragService.performRAGQuery(request);
        
        assertThat(response).isNotNull();
        assertThat(response.getSuccess()).isTrue();
    }
    
    @Test
    @DisplayName("getStatistics returns non-null map")
    void getStatisticsReturnsNonNullMap() {
        when(vectorDatabaseService.getStatistics()).thenReturn(Map.of("count", 100));
        when(vectorDatabase.getStatistics()).thenReturn(Map.of("size", 50));
        
        Map<String, Object> stats = ragService.getStatistics();
        
        assertThat(stats).isNotNull();
        assertThat(stats).containsKey("totalIndexed");
        assertThat(stats).containsKey("vectorDatabase");
    }
    
}
