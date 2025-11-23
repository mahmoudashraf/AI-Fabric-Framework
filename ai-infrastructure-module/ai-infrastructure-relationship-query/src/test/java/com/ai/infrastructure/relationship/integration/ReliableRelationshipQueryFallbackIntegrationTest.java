package com.ai.infrastructure.relationship.integration;

import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.dto.RAGResponse;
import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.relationship.cache.QueryCache;
import com.ai.infrastructure.relationship.config.RelationshipModuleMetadata;
import com.ai.infrastructure.relationship.config.RelationshipQueryProperties;
import com.ai.infrastructure.relationship.dto.FilterCondition;
import com.ai.infrastructure.relationship.dto.FilterOperator;
import com.ai.infrastructure.relationship.dto.RelationshipDirection;
import com.ai.infrastructure.relationship.dto.RelationshipPath;
import com.ai.infrastructure.relationship.dto.RelationshipQueryPlan;
import com.ai.infrastructure.relationship.integration.entity.DocumentEntity;
import com.ai.infrastructure.relationship.integration.entity.UserEntity;
import com.ai.infrastructure.relationship.integration.repository.DocumentRepository;
import com.ai.infrastructure.relationship.integration.repository.UserRepository;
import com.ai.infrastructure.relationship.metrics.QueryMetrics;
import com.ai.infrastructure.relationship.model.QueryOptions;
import com.ai.infrastructure.relationship.model.ReturnMode;
import com.ai.infrastructure.relationship.service.EntityRelationshipMapper;
import com.ai.infrastructure.relationship.service.LLMDrivenJPAQueryService;
import com.ai.infrastructure.relationship.service.MetadataRelationshipTraversalService;
import com.ai.infrastructure.relationship.service.RelationshipQueryPlanner;
import com.ai.infrastructure.relationship.service.ReliableRelationshipQueryService;
import com.ai.infrastructure.relationship.validation.RelationshipQueryValidator;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(
    classes = RelationshipQueryIntegrationTest.IntegrationTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("integration")
class ReliableRelationshipQueryFallbackIntegrationTest {

    private static final List<String> DOCUMENT_ENTITY_TYPES = List.of("document");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        IntegrationTestSupport.registerCommonProperties(registry);
    }

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AISearchableEntityRepository searchableEntityRepository;

    @Autowired
    private RelationshipQueryProperties relationshipQueryProperties;

    @Autowired
    private RelationshipModuleMetadata relationshipModuleMetadata;

    @Autowired
    private RelationshipQueryValidator relationshipQueryValidator;

    @Autowired
    private EntityRelationshipMapper entityRelationshipMapper;

    @Autowired
    private RelationshipQueryPlanner planner;

    @Autowired
    private ObjectMapper objectMapper;

    private QueryCache queryCache;
    private QueryMetrics queryMetrics;
    private LLMDrivenJPAQueryService primaryService;
    private MetadataRelationshipTraversalService metadataTraversalService;
    private VectorDatabaseService vectorDatabaseService;
    private AIEmbeddingService embeddingService;
    private ReliableRelationshipQueryService reliableService;
    private String activeDocumentId;

    @BeforeEach
    void setUp() {
        Mockito.reset(planner);
        searchableEntityRepository.deleteAll();
        documentRepository.deleteAll();
        userRepository.deleteAll();

        seedEntities();

        metadataTraversalService = new MetadataRelationshipTraversalService(searchableEntityRepository, objectMapper);
        primaryService = Mockito.mock(LLMDrivenJPAQueryService.class);
        vectorDatabaseService = Mockito.mock(VectorDatabaseService.class);
        embeddingService = Mockito.mock(AIEmbeddingService.class);
        queryCache = new QueryCache(relationshipQueryProperties);
        queryMetrics = new QueryMetrics(relationshipQueryProperties);

        reliableService = new ReliableRelationshipQueryService(
            primaryService,
            planner,
            metadataTraversalService,
            vectorDatabaseService,
            embeddingService,
            searchableEntityRepository,
            relationshipQueryValidator,
            relationshipQueryProperties,
            relationshipModuleMetadata,
            queryCache,
            queryMetrics
        );
    }

    @Test
    void shouldFallbackToMetadataWhenPrimaryFails() {
        RelationshipQueryPlan plan = planWithStatusFilter("active");
        when(planner.planQuery(eq("resilient docs"), eq(DOCUMENT_ENTITY_TYPES))).thenReturn(plan);
        when(primaryService.executeRelationshipQuery(eq("resilient docs"), eq(DOCUMENT_ENTITY_TYPES), any(QueryOptions.class)))
            .thenThrow(new IllegalStateException("LLM offline"));

        long metadataBefore = queryMetrics.snapshot().getFallbackMetadataCount();

        RAGResponse response = reliableService.execute("resilient docs", DOCUMENT_ENTITY_TYPES, fullReturnOptions());

        assertThat(response.getMetadata().get("executionStage")).isEqualTo("FALLBACK_METADATA");
        assertThat(response.getDocuments()).extracting(RAGResponse.RAGDocument::getId)
            .containsExactly(activeDocumentId);
        assertThat(queryMetrics.snapshot().getFallbackMetadataCount()).isEqualTo(metadataBefore + 1);
    }

    @Test
    void shouldFallbackToVectorWhenMetadataReturnsNoResults() {
        RelationshipQueryPlan plan = planWithStatusFilter("archived");
        when(planner.planQuery(eq("vector docs"), eq(DOCUMENT_ENTITY_TYPES))).thenReturn(plan);
        when(primaryService.executeRelationshipQuery(eq("vector docs"), eq(DOCUMENT_ENTITY_TYPES), any(QueryOptions.class)))
            .thenReturn(emptyPrimaryResponse("vector docs"));

        AIEmbeddingResponse embeddingResponse = AIEmbeddingResponse.builder()
            .embedding(List.of(0.1, 0.2, 0.3))
            .build();
        when(embeddingService.generateEmbedding(any(AIEmbeddingRequest.class))).thenReturn(embeddingResponse);

        AISearchResponse vectorResponse = AISearchResponse.builder()
            .results(List.of(Map.of("entityId", activeDocumentId, "score", 0.92d, "source", "vector-fallback")))
            .totalResults(1)
            .build();
        when(vectorDatabaseService.searchByEntityType(eq(embeddingResponse.getEmbedding()), eq("document"), eq(5), anyDouble()))
            .thenReturn(vectorResponse);

        long vectorBefore = queryMetrics.snapshot().getFallbackVectorCount();

        RAGResponse response = reliableService.execute("vector docs", DOCUMENT_ENTITY_TYPES, fullReturnOptions());

        assertThat(response.getMetadata().get("executionStage")).isEqualTo("FALLBACK_VECTOR");
        assertThat(response.getDocuments()).extracting(RAGResponse.RAGDocument::getId)
            .containsExactly(activeDocumentId);
        assertThat(queryMetrics.snapshot().getFallbackVectorCount()).isEqualTo(vectorBefore + 1);
        verify(vectorDatabaseService).searchByEntityType(eq(embeddingResponse.getEmbedding()), eq("document"), eq(5), anyDouble());
    }

    @Test
    void shouldFallbackToSimpleRepositoryWhenVectorReturnsNoResults() {
        RelationshipQueryPlan plan = planWithStatusFilter("archived");
        when(planner.planQuery(eq("simple docs"), eq(DOCUMENT_ENTITY_TYPES))).thenReturn(plan);
        when(primaryService.executeRelationshipQuery(eq("simple docs"), eq(DOCUMENT_ENTITY_TYPES), any(QueryOptions.class)))
            .thenReturn(emptyPrimaryResponse("simple docs"));

        AIEmbeddingResponse embeddingResponse = AIEmbeddingResponse.builder()
            .embedding(List.of(0.4, 0.5, 0.6))
            .build();
        when(embeddingService.generateEmbedding(any(AIEmbeddingRequest.class))).thenReturn(embeddingResponse);

        AISearchResponse emptyVectorResponse = AISearchResponse.builder()
            .results(List.of())
            .totalResults(0)
            .build();
        when(vectorDatabaseService.searchByEntityType(eq(embeddingResponse.getEmbedding()), eq("document"), eq(5), anyDouble()))
            .thenReturn(emptyVectorResponse);

        long vectorBefore = queryMetrics.snapshot().getFallbackVectorCount();
        long simpleBefore = queryMetrics.snapshot().getFallbackSimpleCount();

        RAGResponse response = reliableService.execute("simple docs", DOCUMENT_ENTITY_TYPES, fullReturnOptions());

        assertThat(response.getMetadata().get("executionStage")).isEqualTo("FALLBACK_SIMPLE");
        assertThat(response.getDocuments()).isNotEmpty();
        assertThat(response.getDocuments().get(0).getMetadata()).containsEntry("source", "simple-fallback");
        assertThat(queryMetrics.snapshot().getFallbackVectorCount()).isEqualTo(vectorBefore + 1);
        assertThat(queryMetrics.snapshot().getFallbackSimpleCount()).isEqualTo(simpleBefore + 1);
        verify(vectorDatabaseService).searchByEntityType(eq(embeddingResponse.getEmbedding()), eq("document"), eq(5), anyDouble());
    }

    private void seedEntities() {
        UserEntity author = new UserEntity();
        author.setFullName("Grace Hopper");
        author.setEmail("grace@example.com");

        DocumentEntity document = new DocumentEntity();
        document.setTitle("Resilient Query Playbook");
        document.setStatus("active");
        document.setAuthor(author);
        author.getDocuments().add(document);

        userRepository.save(author);
        activeDocumentId = document.getId();

        AISearchableEntity searchableEntity = AISearchableEntity.builder()
            .entityType("document")
            .entityId(activeDocumentId)
            .searchableContent(document.getTitle())
            .metadata("{\"status\":\"active\",\"owner\":\"grace@example.com\"}")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
        searchableEntityRepository.save(searchableEntity);

        entityRelationshipMapper.registerEntityType(DocumentEntity.class);
        entityRelationshipMapper.registerEntityType(UserEntity.class);
        entityRelationshipMapper.registerRelationship("document", "user", "author", RelationshipDirection.FORWARD, false);
    }

    private RelationshipQueryPlan planWithStatusFilter(String statusValue) {
        FilterCondition statusFilter = FilterCondition.builder()
            .field("status")
            .operator(FilterOperator.EQUALS)
            .value(statusValue)
            .build();

        RelationshipPath authorPath = RelationshipPath.builder()
            .fromEntityType("document")
            .toEntityType("user")
            .relationshipType("author")
            .direction(RelationshipDirection.FORWARD)
            .optional(false)
            .build();

        return RelationshipQueryPlan.builder()
            .originalQuery("resilient docs")
            .semanticQuery("resilient docs " + statusValue)
            .primaryEntityType("document")
            .candidateEntityTypes(List.of("document", "user"))
            .relationshipPaths(List.of(authorPath))
            .directFilters(Map.of("document", List.of(statusFilter)))
            .needsSemanticSearch(true)
            .returnMode(ReturnMode.FULL)
            .limit(5)
            .build();
    }

    private QueryOptions fullReturnOptions() {
        return QueryOptions.builder()
            .returnMode(ReturnMode.FULL)
            .limit(5)
            .build();
    }

    private RAGResponse emptyPrimaryResponse(String query) {
        return RAGResponse.builder()
            .originalQuery(query)
            .entityType("document")
            .documents(List.of())
            .build();
    }

    @AfterAll
    static void cleanUpLuceneIndex() throws IOException {
        IntegrationTestSupport.cleanUpLuceneIndex();
    }
}
