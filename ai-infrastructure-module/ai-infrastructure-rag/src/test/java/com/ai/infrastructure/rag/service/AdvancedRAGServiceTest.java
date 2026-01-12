package com.ai.infrastructure.rag.service;

import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.dto.AdvancedRAGRequest;
import com.ai.infrastructure.dto.AdvancedRAGResponse;
import com.ai.infrastructure.dto.RAGRequest;
import com.ai.infrastructure.dto.RAGResponse;
import com.ai.infrastructure.core.AISearchService;
import com.ai.infrastructure.spi.RAGProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdvancedRAGServiceTest {

    @Mock
    private AISearchService aiSearchService;

    @Mock
    private AIEmbeddingService aiEmbeddingService;

    @Mock
    private AICoreService aiCoreService;

    @Mock
    private RAGProvider ragProvider;

    @Test
    void performAdvancedRAGDoesNotFailWhenDocumentSimilarityIsNull() {
        when(aiCoreService.generateText(any())).thenReturn("ok");

        when(ragProvider.performRag(any(RAGRequest.class))).thenReturn(
            RAGResponse.builder()
                .success(true)
                .documents(List.of(
                    RAGResponse.RAGDocument.builder()
                        .id("doc-1")
                        .content("test content")
                        .type("FAQ")
                        .score(0.9)
                        .similarity(null)
                        .build()
                ))
                .build()
        );

        AdvancedRAGService service = new AdvancedRAGService(
            aiSearchService,
            aiEmbeddingService,
            aiCoreService,
            ragProvider
        );

        AdvancedRAGResponse response = service.performAdvancedRAG(
            AdvancedRAGRequest.builder()
                .query("test query")
                .expansionLevel(1)
                .rerankingStrategy("hybrid")
                .contextOptimizationLevel("low")
                .maxDocuments(5)
                .maxResults(5)
                .enableHybridSearch(true)
                .enableContextualSearch(false)
                .build()
        );

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getDocuments()).hasSize(1);
        assertThat(response.getConfidenceScore()).isNotNull();
    }
}

