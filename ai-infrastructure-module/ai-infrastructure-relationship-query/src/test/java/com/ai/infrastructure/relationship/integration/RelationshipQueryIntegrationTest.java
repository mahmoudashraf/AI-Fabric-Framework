package com.ai.infrastructure.relationship.integration;

import com.ai.infrastructure.dto.RAGResponse;
import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.relationship.config.RelationshipQueryAutoConfiguration;
import com.ai.infrastructure.relationship.dto.FilterCondition;
import com.ai.infrastructure.relationship.dto.FilterOperator;
import com.ai.infrastructure.relationship.dto.RelationshipDirection;
import com.ai.infrastructure.relationship.dto.RelationshipPath;
import com.ai.infrastructure.relationship.dto.RelationshipQueryPlan;
import com.ai.infrastructure.relationship.dto.QueryStrategy;
import com.ai.infrastructure.relationship.integration.entity.DocumentEntity;
import com.ai.infrastructure.relationship.integration.entity.UserEntity;
import com.ai.infrastructure.relationship.integration.repository.DocumentRepository;
import com.ai.infrastructure.relationship.integration.repository.UserRepository;
import com.ai.infrastructure.relationship.model.QueryOptions;
import com.ai.infrastructure.relationship.model.ReturnMode;
import com.ai.infrastructure.relationship.service.ReliableRelationshipQueryService;
import com.ai.infrastructure.relationship.service.RelationshipSchemaProvider;
import com.ai.infrastructure.relationship.service.RelationshipQueryPlanner;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
    classes = RelationshipQueryIntegrationTest.IntegrationTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("integration")
class RelationshipQueryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("relationship_query")
        .withUsername("tester")
        .withPassword("tester");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("OPENAI_API_KEY", () ->
            Optional.ofNullable(System.getenv("OPENAI_API_KEY"))
                .orElse("sk-test-integration"));
    }

    @Autowired
    private ReliableRelationshipQueryService reliableRelationshipQueryService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AISearchableEntityRepository searchableEntityRepository;

    @Autowired
    private RelationshipSchemaProvider schemaProvider;

    @MockBean
    private RelationshipQueryPlanner planner;

    private String activeDocumentId;

    @BeforeEach
    void setUpData() {
        searchableEntityRepository.deleteAll();
        documentRepository.deleteAll();
        userRepository.deleteAll();

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

        schemaProvider.refreshSchema();
    }

    @Test
    void shouldExecuteEndToEndThroughJpaTraversal() {
        RelationshipQueryPlan plan = buildPlan();
        when(planner.planQuery(anyString(), anyList())).thenReturn(plan);

        RAGResponse response = reliableRelationshipQueryService.execute(
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

    @SpringBootApplication(scanBasePackages = {
        "com.ai.infrastructure.relationship",
        "com.ai.infrastructure.repository"
    })
    @EntityScan(basePackageClasses = {
        AISearchableEntity.class,
        DocumentEntity.class,
        UserEntity.class
    })
    @EnableJpaRepositories(basePackageClasses = {
        AISearchableEntityRepository.class,
        DocumentRepository.class,
        UserRepository.class
    })
    @Import(RelationshipQueryAutoConfiguration.class)
    static class IntegrationTestApplication {
    }
}
