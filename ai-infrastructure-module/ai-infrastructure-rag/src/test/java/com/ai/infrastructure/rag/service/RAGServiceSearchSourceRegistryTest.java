package com.ai.infrastructure.rag.service;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.core.AISearchService;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.dto.RAGRequest;
import com.ai.infrastructure.dto.RAGResponse;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.ai.infrastructure.rag.source.SearchSource;
import com.ai.infrastructure.rag.source.SearchSourceRegistry;
import com.ai.infrastructure.vector.VectorDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RAGServiceSearchSourceRegistryTest {

    @Mock
    private AIEmbeddingService embeddingService;
    @Mock
    private VectorDatabaseService vectorDatabaseService;
    @Mock
    private VectorDatabase vectorDatabase;
    @Mock
    private AISearchService searchService;
    @Mock
    private SearchSourceRegistry searchSourceRegistry;
    @Mock
    private SearchSource privateSource;
    @Mock
    private SearchSource sharedSource;

    private RAGService ragService;

    @BeforeEach
    void setUp() {
        AIProviderConfig config = new AIProviderConfig();
        AIEmbeddingResponse embeddingResponse = AIEmbeddingResponse.builder()
            .embedding(List.of(0.1, 0.2, 0.3))
            .build();
        when(embeddingService.executeEmbedding(any())).thenReturn(
            new AIEmbeddingService.EmbeddingExecution(
                embeddingResponse,
                false,
                "openai",
                "text-embedding-3-small",
                9L,
                9L
            )
        );

        ragService = new RAGService(
            config,
            embeddingService,
            vectorDatabaseService,
            vectorDatabase,
            searchService,
            searchSourceRegistry
        );
    }

    @Test
    void performRagMergesResolvedSearchSourcesAndAddsAttribution() {
        when(privateSource.isEligible(any())).thenReturn(true);
        when(sharedSource.isEligible(any())).thenReturn(true);
        when(searchSourceRegistry.resolveSearchSources(any())).thenReturn(List.of(privateSource, sharedSource));
        when(privateSource.search(any(), any(), any())).thenReturn(
            AISearchResponse.builder()
                .results(List.of(Map.of(
                    "id", "product-1",
                    "content", "Alienware m18 R2 is available.",
                    "score", 0.74,
                    "similarity", 0.74,
                    "metadata", Map.of(
                        "knowledgeSourceId", "deployment-private-vector",
                        "knowledgeSourceType", "deployment-private-vector",
                        "knowledgeSourceAdapterType", "deployment-private-vector",
                        "knowledgeSourceAttributionLabel", "Deployment knowledge"
                    )
                )))
                .totalResults(1)
                .maxScore(0.74)
                .build()
        );
        when(sharedSource.search(any(), any(), any())).thenReturn(
            AISearchResponse.builder()
                .results(List.of(Map.of(
                    "id", "product-2",
                    "content", "Shared catalog lists Alienware m18 R2 deals.",
                    "score", 0.92,
                    "similarity", 0.92,
                    "metadata", Map.of(
                        "knowledgeSourceId", "shared-catalog",
                        "knowledgeSourceType", "shared-vector",
                        "knowledgeSourceAdapterType", "shared-index",
                        "knowledgeSourceAttributionLabel", "Shared catalog"
                    )
                )))
                .totalResults(1)
                .maxScore(0.92)
                .build()
        );

        RAGResponse response = ragService.performRag(RAGRequest.builder()
            .query("tell me about Alienware m18 R2")
            .entityType("product")
            .limit(5)
            .threshold(0.1)
            .build());

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getDocuments()).hasSize(2);
        assertThat(response.getDocuments().get(0).getSource()).isEqualTo("Shared catalog");
        assertThat(response.getDocuments().get(0).getMetadata())
            .containsEntry("knowledgeSourceId", "shared-catalog")
            .containsEntry("knowledgeSourceAdapterType", "shared-index");
        assertThat(response.getMetadata())
            .containsEntry("searchSourceCount", 2)
            .containsEntry("searchSourceIds", List.of("shared-catalog", "deployment-private-vector"))
            .containsEntry("searchSourceAdapterTypes", List.of("shared-index", "deployment-private-vector"));
    }
}
