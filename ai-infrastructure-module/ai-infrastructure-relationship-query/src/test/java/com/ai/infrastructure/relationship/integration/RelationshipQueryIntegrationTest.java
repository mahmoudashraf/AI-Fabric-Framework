package com.ai.infrastructure.relationship.integration;

import com.ai.infrastructure.dto.RAGResponse;
import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.relationship.config.RelationshipQueryAutoConfiguration;
import com.ai.infrastructure.relationship.cache.QueryCache;
import com.ai.infrastructure.relationship.config.RelationshipModuleMetadata;
import com.ai.infrastructure.relationship.config.RelationshipQueryProperties;
import com.ai.infrastructure.relationship.dto.FilterCondition;
import com.ai.infrastructure.relationship.dto.FilterOperator;
import com.ai.infrastructure.relationship.dto.RelationshipDirection;
import com.ai.infrastructure.relationship.dto.RelationshipPath;
import com.ai.infrastructure.relationship.dto.RelationshipQueryPlan;
import com.ai.infrastructure.relationship.dto.QueryStrategy;
import com.ai.infrastructure.relationship.integration.entity.BrandEntity;
import com.ai.infrastructure.relationship.integration.entity.DocumentEntity;
import com.ai.infrastructure.relationship.integration.entity.ProductEntity;
import com.ai.infrastructure.relationship.integration.entity.UserEntity;
import com.ai.infrastructure.relationship.integration.repository.DocumentRepository;
import com.ai.infrastructure.relationship.integration.repository.BrandRepository;
import com.ai.infrastructure.relationship.integration.repository.ProductRepository;
import com.ai.infrastructure.relationship.integration.repository.UserRepository;
import com.ai.infrastructure.relationship.model.QueryOptions;
import com.ai.infrastructure.relationship.model.ReturnMode;
import com.ai.infrastructure.relationship.metrics.QueryMetrics;
import com.ai.infrastructure.relationship.service.DynamicJPAQueryBuilder;
import com.ai.infrastructure.relationship.service.LLMDrivenJPAQueryService;
import com.ai.infrastructure.relationship.service.RelationshipQueryPlanner;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.ai.infrastructure.provider.onnx.ONNXAutoConfiguration;
import com.ai.infrastructure.provider.onnx.ONNXEmbeddingProvider;
import com.ai.infrastructure.vector.lucene.LuceneVectorAutoConfiguration;
import com.ai.infrastructure.vector.lucene.LuceneVectorDatabaseService;
import com.ai.infrastructure.relationship.validation.RelationshipQueryValidator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.beans.factory.annotation.Qualifier;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(
    classes = RelationshipQueryIntegrationTest.IntegrationTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("integration")
public class RelationshipQueryIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(RelationshipQueryIntegrationTest.class);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        IntegrationTestSupport.registerCommonProperties(registry);
    }

    private LLMDrivenJPAQueryService llmDrivenJPAQueryService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AISearchableEntityRepository searchableEntityRepository;

    @Autowired
    private VectorDatabaseService vectorDatabaseService;

    @Autowired
    private RelationshipQueryPlanner planner;

    @Autowired
    private DynamicJPAQueryBuilder dynamicJPAQueryBuilder;

    @Autowired
    private RelationshipQueryValidator relationshipQueryValidator;

    @Autowired
    private RelationshipQueryProperties relationshipQueryProperties;

    @Autowired
    private RelationshipModuleMetadata relationshipModuleMetadata;

    @Autowired
    private AIEmbeddingService aiEmbeddingService;

    @Autowired
    private QueryCache queryCache;

    @Autowired
    private QueryMetrics queryMetrics;

    @Autowired
    private com.ai.infrastructure.relationship.service.EntityRelationshipMapper entityRelationshipMapper;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private String activeDocumentId;

    @BeforeEach
    void setUpData() {
        searchableEntityRepository.deleteAll();
        documentRepository.deleteAll();
        userRepository.deleteAll();
        if (vectorDatabaseService != null) {
            try {
                vectorDatabaseService.clearVectors();
            } catch (Exception ex) {
                log.warn("Unable to clear vectors from Lucene test index; continuing with fresh context", ex);
            }
        }

        UserEntity author = new UserEntity();
        author.setFullName("Ada Lovelace");
        author.setEmail("ada@example.com");

        DocumentEntity document = new DocumentEntity();
        document.setTitle("LLM Guardrails Playbook");
        document.setStatus("ACTIVE");
        document.setAuthor(author);
        author.getDocuments().add(document);

        userRepository.save(author);
        activeDocumentId = document.getId();

        AISearchableEntity indexedDocument = AISearchableEntity.builder()
            .entityType("document")
            .entityId(activeDocumentId)
            .searchableContent(document.getTitle())
            .metadata("{\"status\":\"ACTIVE\",\"owner\":\"ada@example.com\"}")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
        searchableEntityRepository.save(indexedDocument);

        entityRelationshipMapper.registerEntityType(DocumentEntity.class);
        entityRelationshipMapper.registerEntityType(UserEntity.class);
        entityRelationshipMapper.registerRelationship("document", "user", "author", RelationshipDirection.FORWARD, false);

        com.ai.infrastructure.relationship.service.RelationshipSchemaProvider schemaProvider =
            new com.ai.infrastructure.relationship.service.RelationshipSchemaProvider(
                entityManager,
                null,
                relationshipQueryProperties,
                entityRelationshipMapper
            );
        schemaProvider.refreshSchema();

        com.ai.infrastructure.relationship.service.RelationshipTraversalService jpaTraversalService =
            new com.ai.infrastructure.relationship.service.JpaRelationshipTraversalService(entityManager);

        com.ai.infrastructure.relationship.service.RelationshipTraversalService metadataTraversalService =
            new com.ai.infrastructure.relationship.service.MetadataRelationshipTraversalService(
                searchableEntityRepository,
                objectMapper
            );

        llmDrivenJPAQueryService = new LLMDrivenJPAQueryService(
            planner,
            dynamicJPAQueryBuilder,
            relationshipQueryValidator,
            relationshipQueryProperties,
            relationshipModuleMetadata,
            jpaTraversalService,
            metadataTraversalService,
            searchableEntityRepository,
            vectorDatabaseService,
            aiEmbeddingService,
            queryCache,
            queryMetrics
        );
    }

    @Test
    void shouldExecuteEndToEndThroughJpaTraversal() {
        RelationshipQueryPlan plan = buildPlan();
        when(planner.planQuery(anyString(), anyList())).thenReturn(plan);

        RAGResponse response = llmDrivenJPAQueryService.executeRelationshipQuery(
            "active docs by ada",
            List.of("document"),
            QueryOptions.builder()
                .returnMode(ReturnMode.FULL)
                .limit(5)
                .build()
        );

        assertThat(response.getDocuments()).extracting(RAGResponse.RAGDocument::getId)
            .containsExactly(activeDocumentId);
        assertThat(response.getEntityType()).isEqualTo("document");
        assertThat(response.getDocuments().get(0).getContent()).isEqualTo("LLM Guardrails Playbook");
    }

    private RelationshipQueryPlan buildPlan() {
        FilterCondition statusFilter = FilterCondition.builder()
            .field("status")
            .operator(FilterOperator.EQUALS)
            .value("ACTIVE")
            .build();

        RelationshipPath authorPath = RelationshipPath.builder()
            .fromEntityType("document")
            .relationshipType("author")
            .toEntityType("user")
            .direction(RelationshipDirection.FORWARD)
            .optional(false)
            .build();

        return RelationshipQueryPlan.builder()
            .originalQuery("active docs by ada")
            .primaryEntityType("document")
            .candidateEntityTypes(List.of("document", "user"))
            .relationshipPaths(List.of(authorPath))
            .directFilters(Map.of("document", List.of(statusFilter)))
            .needsSemanticSearch(false)
            .queryStrategy(QueryStrategy.RELATIONSHIP)
            .limit(5)
            .returnMode(ReturnMode.FULL)
            .build();
    }

    @AfterAll
    static void cleanUpLuceneIndex() throws IOException {
        IntegrationTestSupport.cleanUpLuceneIndex();
    }

    @SpringBootApplication(
        scanBasePackages = {
            "com.ai.infrastructure.relationship",
            "com.ai.infrastructure.repository"
        },
        exclude = {
            ONNXAutoConfiguration.class,
            LuceneVectorAutoConfiguration.class
        }
    )
    @EntityScan(basePackageClasses = {
        AISearchableEntity.class,
        DocumentEntity.class,
        UserEntity.class,
        ProductEntity.class,
        BrandEntity.class
    })
    @EnableJpaRepositories(basePackageClasses = {
        AISearchableEntityRepository.class,
        DocumentRepository.class,
        UserRepository.class,
        ProductRepository.class,
        BrandRepository.class
    })
    @EnableConfigurationProperties(AIProviderConfig.class)
    @Import({
        IntegrationTestBeans.class,
        RelationshipQueryAutoConfiguration.class
    })
    public static class IntegrationTestApplication {
    }

    @TestConfiguration
    static class IntegrationTestBeans {

        @Bean
        CacheManager integrationTestCacheManager() {
            return new ConcurrentMapCacheManager();
        }

        @Bean
        ONNXEmbeddingProvider onnxEmbeddingProvider(AIProviderConfig config) {
            return new ONNXEmbeddingProvider(config);
        }

        @Bean
        AIEmbeddingService aiEmbeddingService(AIProviderConfig config,
                                              ONNXEmbeddingProvider onnxEmbeddingProvider,
                                              CacheManager integrationTestCacheManager) {
            return new AIEmbeddingService(config, onnxEmbeddingProvider, integrationTestCacheManager, null);
        }

        @Bean
        @ConditionalOnMissingBean(VectorDatabaseService.class)
        VectorDatabaseService vectorDatabaseService(AIProviderConfig config) {
            return new LuceneVectorDatabaseService(config);
        }

        @Bean
        RelationshipQueryPlanner relationshipQueryPlanner() {
            return Mockito.mock(RelationshipQueryPlanner.class);
        }

    }
}
