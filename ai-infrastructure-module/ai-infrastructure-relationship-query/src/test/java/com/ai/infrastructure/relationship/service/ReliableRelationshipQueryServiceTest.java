package com.ai.infrastructure.relationship.service;

import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.dto.RAGResponse;
import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.relationship.cache.QueryCache;
import com.ai.infrastructure.relationship.config.RelationshipModuleMetadata;
import com.ai.infrastructure.relationship.config.RelationshipQueryProperties;
import com.ai.infrastructure.relationship.dto.RelationshipQueryPlan;
import com.ai.infrastructure.relationship.exception.FallbackExhaustedException;
import com.ai.infrastructure.relationship.metrics.QueryMetrics;
import com.ai.infrastructure.relationship.model.QueryOptions;
import com.ai.infrastructure.relationship.validation.RelationshipQueryValidator;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReliableRelationshipQueryServiceTest {

    @Mock
    private LLMDrivenJPAQueryService llmService;
    @Mock
    private RelationshipQueryPlanner planner;
    @Mock
    private RelationshipTraversalService metadataTraversalService;
    @Mock
    private VectorDatabaseService vectorDatabaseService;
    @Mock
    private AIEmbeddingService embeddingService;
    @Mock
    private AISearchableEntityRepository entityRepository;
    @Mock
    private RelationshipQueryValidator validator;
    @Mock
    private QueryCache queryCache;
    @Mock
    private QueryMetrics queryMetrics;

    private RelationshipQueryProperties properties;
    private RelationshipModuleMetadata metadata;

    private ReliableRelationshipQueryService service;

    @BeforeEach
    void setUp() {
        properties = new RelationshipQueryProperties();
        metadata = RelationshipModuleMetadata.from(properties);
        when(queryCache.isEnabled()).thenReturn(false);
        when(queryMetrics.isEnabled()).thenReturn(true);
        service = new ReliableRelationshipQueryService(
            llmService,
            planner,
            metadataTraversalService,
            vectorDatabaseService,
            embeddingService,
            entityRepository,
            validator,
            properties,
            metadata,
            queryCache,
            queryMetrics
        );
    }

    @Test
    void shouldReturnPrimaryResponseWhenAvailable() {
        RAGResponse primary = RAGResponse.builder()
            .documents(List.of(RAGResponse.RAGDocument.builder().id("1").build()))
            .build();
        when(llmService.executeRelationshipQuery(anyString(), anyList(), any())).thenReturn(primary);

        RAGResponse result = service.execute("who created docs", List.of("document"), QueryOptions.defaults());

        assertThat(result.getDocuments()).hasSize(1);
        verifyNoInteractions(planner);
    }

    @Test
    void shouldFallbackToMetadataWhenPrimaryEmpty() {
        RelationshipQueryPlan plan = RelationshipQueryPlan.builder()
            .originalQuery("q")
            .primaryEntityType("document")
            .build();
        when(llmService.executeRelationshipQuery(anyString(), anyList(), any()))
            .thenReturn(RAGResponse.builder().documents(List.of()).build());
        when(planner.planQuery(anyString(), anyList())).thenReturn(plan);
        when(metadataTraversalService.traverse(any(), any()))
            .thenReturn(List.of("123"));
        AISearchableEntity entity = AISearchableEntity.builder()
            .entityId("123")
            .entityType("document")
            .searchableContent("doc-content")
            .build();
        when(entityRepository.findByEntityTypeAndEntityId("document", "123")).thenReturn(Optional.of(entity));

        RAGResponse response = service.execute("fallback metadata", List.of("document"), QueryOptions.defaults());

        assertThat(response.getDocuments()).hasSize(1);
        assertThat(response.getMetadata()).containsEntry("executionStage", "FALLBACK_METADATA");
        org.mockito.Mockito.verify(queryMetrics).recordFallbackStage("FALLBACK_METADATA", true, 1);
    }

    @Test
    void shouldFallbackToVectorWhenMetadataEmpty() {
        RelationshipQueryPlan plan = RelationshipQueryPlan.builder()
            .originalQuery("q")
            .semanticQuery("q")
            .primaryEntityType("document")
            .build();
        when(llmService.executeRelationshipQuery(anyString(), anyList(), any()))
            .thenReturn(RAGResponse.builder().documents(List.of()).build());
        when(planner.planQuery(anyString(), anyList())).thenReturn(plan);
        when(metadataTraversalService.traverse(any(), any())).thenReturn(List.of());
        when(embeddingService.generateEmbedding(any()))
            .thenReturn(AIEmbeddingResponse.builder().embedding(List.of(0.1d)).build());
        AISearchResponse aisResponse = AISearchResponse.builder()
            .results(List.of(Map.of("entityId", "v1", "score", 0.92d)))
            .build();
        when(vectorDatabaseService.searchByEntityType(any(), anyString(), anyInt(), anyDouble()))
            .thenReturn(aisResponse);

        RAGResponse response = service.execute("vector fallback", List.of("document"), QueryOptions.defaults());

        assertThat(response.getDocuments()).hasSize(1);
        assertThat(response.getMetadata()).containsEntry("executionStage", "FALLBACK_VECTOR");
        org.mockito.Mockito.verify(queryMetrics).recordFallbackStage("FALLBACK_VECTOR", true, 1);
    }

    @Test
    void shouldFallbackToSimpleRepositoryWhenOthersEmpty() {
        properties.setFallbackToVectorSearch(false);
        RelationshipQueryPlan plan = RelationshipQueryPlan.builder()
            .originalQuery("q")
            .primaryEntityType("document")
            .build();
        when(llmService.executeRelationshipQuery(anyString(), anyList(), any()))
            .thenReturn(RAGResponse.builder().documents(List.of()).build());
        when(planner.planQuery(anyString(), anyList())).thenReturn(plan);
        when(metadataTraversalService.traverse(any(), any())).thenReturn(List.of());
        AISearchableEntity entity = AISearchableEntity.builder()
            .entityId("simple-1")
            .entityType("document")
            .searchableContent("Fallback doc")
            .build();
        when(entityRepository.findByEntityType("document")).thenReturn(List.of(entity));

        RAGResponse response = service.execute("simple fallback", List.of("document"), QueryOptions.defaults());

        assertThat(response.getDocuments()).hasSize(1);
        assertThat(response.getMetadata()).containsEntry("executionStage", "FALLBACK_SIMPLE");
        org.mockito.Mockito.verify(queryMetrics).recordFallbackStage("FALLBACK_SIMPLE", true, 1);
    }

    @Test
    void shouldSurfaceErrorContextWhenAllFallbacksFail() {
        properties.setFallbackToMetadata(false);
        properties.setFallbackToVectorSearch(false);
        RelationshipQueryPlan plan = RelationshipQueryPlan.builder()
            .originalQuery("q")
            .primaryEntityType("document")
            .candidateEntityTypes(List.of("document"))
            .build();
        when(llmService.executeRelationshipQuery(anyString(), anyList(), any()))
            .thenReturn(RAGResponse.builder().documents(List.of()).build());
        when(planner.planQuery(anyString(), anyList())).thenReturn(plan);
        when(entityRepository.findByEntityType("document")).thenReturn(List.of());

        assertThatThrownBy(() -> service.execute("exhausted", List.of("document"), QueryOptions.defaults()))
            .isInstanceOf(FallbackExhaustedException.class)
            .extracting(ex -> ((FallbackExhaustedException) ex).getContext().orElseThrow().getExecutionStage())
            .isEqualTo("FALLBACK_CHAIN_EXHAUSTED");
    }
}
