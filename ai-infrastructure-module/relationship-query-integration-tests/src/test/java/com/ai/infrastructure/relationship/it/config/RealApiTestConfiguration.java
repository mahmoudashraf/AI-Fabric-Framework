package com.ai.infrastructure.relationship.it.config;

import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.relationship.cache.QueryCache;
import com.ai.infrastructure.relationship.config.RelationshipModuleMetadata;
import com.ai.infrastructure.relationship.config.RelationshipQueryProperties;
import com.ai.infrastructure.relationship.dto.RelationshipQueryPlan;
import com.ai.infrastructure.relationship.metrics.QueryMetrics;
import com.ai.infrastructure.relationship.service.DynamicJPAQueryBuilder;
import com.ai.infrastructure.relationship.service.EntityRelationshipMapper;
import com.ai.infrastructure.relationship.service.LLMDrivenJPAQueryService;
import com.ai.infrastructure.relationship.service.JpaRelationshipTraversalService;
import com.ai.infrastructure.relationship.service.MetadataRelationshipTraversalService;
import com.ai.infrastructure.relationship.service.RelationshipQueryPlanner;
import com.ai.infrastructure.relationship.service.RelationshipSchemaProvider;
import com.ai.infrastructure.relationship.service.RelationshipTraversalService;
import com.ai.infrastructure.relationship.service.ReliableRelationshipQueryService;
import com.ai.infrastructure.relationship.validation.RelationshipQueryValidator;
import com.ai.infrastructure.relationship.it.support.RelationshipQueryPlanFixtures;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import jakarta.annotation.Nullable;
import org.mockito.Mockito;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


import static org.mockito.ArgumentMatchers.any;

/**
 * Provides deterministic AI responses for the real API integration tests so we can
 * bootstrap the full Spring context without calling external LLMs.
 */
@TestConfiguration
public class RealApiTestConfiguration {

    @Bean
    @Primary
    public AICoreService fixtureAICoreService(ObjectMapper objectMapper) {
        AICoreService mock = Mockito.mock(AICoreService.class);
        Mockito.when(mock.generateContent(any(AIGenerationRequest.class))).thenAnswer(invocation -> {
            AIGenerationRequest request = invocation.getArgument(0);
            String query = extractUserQuery(request.getPrompt());
            RelationshipQueryPlan plan = RelationshipQueryPlanFixtures.planFor(query);
            String payload = plan != null
                ? objectMapper.writeValueAsString(plan)
                : """
                    {
                      "primaryEntityType": "document",
                      "candidateEntityTypes": ["document"],
                      "relationshipPaths": [],
                      "directFilters": {},
                      "queryStrategy": "RELATIONSHIP",
                      "needsSemanticSearch": false
                    }
                    """;
            return AIGenerationResponse.builder()
                .content(payload)
                .model("real-api-fixture")
                .build();
        });
        return mock;
    }

    @Bean
    @Primary
    public RelationshipSchemaProvider testRelationshipSchemaProvider(EntityManagerFactory entityManagerFactory,
                                                                     RelationshipQueryProperties properties,
                                                                     EntityRelationshipMapper mapper) {
        return new RelationshipSchemaProvider(entityManagerFactory.createEntityManager(), null, properties, mapper);
    }

    @Bean(name = "jpaRelationshipTraversalService")
    @Primary
    @Qualifier("jpaRelationshipTraversalService")
    public RelationshipTraversalService testJpaRelationshipTraversalService(EntityManagerFactory entityManagerFactory) {
        return new JpaRelationshipTraversalService(entityManagerFactory.createEntityManager());
    }

    @Bean(name = "metadataRelationshipTraversalService")
    @Primary
    @Qualifier("metadataRelationshipTraversalService")
    public RelationshipTraversalService testMetadataRelationshipTraversalService(AISearchableEntityRepository repository,
                                                                                 ObjectMapper objectMapper) {
        return new MetadataRelationshipTraversalService(repository, objectMapper);
    }

    @Bean
    @Primary
    public RelationshipQueryPlanner testRelationshipQueryPlanner(AICoreService fixtureAICoreService,
                                                                 RelationshipSchemaProvider schemaProvider,
                                                                 RelationshipQueryProperties properties,
                                                                 RelationshipQueryValidator validator,
                                                                 QueryCache queryCache,
                                                                 QueryMetrics queryMetrics,
                                                                 ObjectMapper objectMapper) {
        return new RelationshipQueryPlanner(
            fixtureAICoreService,
            schemaProvider,
            properties,
            validator,
            queryCache,
            queryMetrics,
            objectMapper
        );
    }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer realApiJacksonCustomizer() {
        return builder -> {
            JavaTimeModule module = new JavaTimeModule();
            module.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            builder.modulesToInstall(module);
        };
    }

    private String extractUserQuery(String prompt) {
        if (prompt == null) {
            return "";
        }
        String marker = "User Query: \"";
        int idx = prompt.lastIndexOf(marker);
        if (idx == -1) {
            return prompt;
        }
        int start = idx + marker.length();
        int end = prompt.indexOf('"', start);
        if (end == -1) {
            end = prompt.length();
        }
        return prompt.substring(start, end);
    }

    @Bean
    @Primary
    public LLMDrivenJPAQueryService testLLMDrivenJPAQueryService(RelationshipQueryPlanner planner,
                                                                 DynamicJPAQueryBuilder queryBuilder,
                                                                 RelationshipQueryValidator validator,
                                                                 RelationshipQueryProperties properties,
                                                                 RelationshipModuleMetadata metadata,
                                                                 @Qualifier("jpaRelationshipTraversalService") RelationshipTraversalService jpaTraversalService,
                                                                 @Qualifier("metadataRelationshipTraversalService") RelationshipTraversalService metadataTraversalService,
                                                                 AISearchableEntityRepository repository,
                                                                 @Nullable VectorDatabaseService vectorDatabaseService,
                                                                 @Nullable AIEmbeddingService embeddingService,
                                                                 QueryCache queryCache,
                                                                 QueryMetrics queryMetrics) {
        return new LLMDrivenJPAQueryService(
            planner,
            queryBuilder,
            validator,
            properties,
            metadata,
            jpaTraversalService,
            metadataTraversalService,
            repository,
            vectorDatabaseService,
            embeddingService,
            queryCache,
            queryMetrics
        );
    }

    @Bean
    @Primary
    public ReliableRelationshipQueryService testReliableRelationshipQueryService(LLMDrivenJPAQueryService llmDrivenJPAQueryService,
                                                                                RelationshipQueryPlanner planner,
                                                                                @Qualifier("metadataRelationshipTraversalService") RelationshipTraversalService metadataTraversalService,
                                                                                @Nullable VectorDatabaseService vectorDatabaseService,
                                                                                @Nullable AIEmbeddingService embeddingService,
                                                                                AISearchableEntityRepository repository,
                                                                                RelationshipQueryValidator validator,
                                                                                RelationshipQueryProperties properties,
                                                                                RelationshipModuleMetadata metadata,
                                                                                QueryCache queryCache,
                                                                                QueryMetrics queryMetrics) {
        return new ReliableRelationshipQueryService(
            llmDrivenJPAQueryService,
            planner,
            metadataTraversalService,
            vectorDatabaseService,
            embeddingService,
            repository,
            validator,
            properties,
            metadata,
            queryCache,
            queryMetrics
        );
    }
}
