package com.ai.infrastructure.relationship.config;

import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.relationship.service.DynamicJPAQueryBuilder;
import com.ai.infrastructure.relationship.service.EntityRelationshipMapper;
import com.ai.infrastructure.relationship.service.JpaRelationshipTraversalService;
import com.ai.infrastructure.relationship.service.LLMDrivenJPAQueryService;
import com.ai.infrastructure.relationship.service.MetadataRelationshipTraversalService;
import com.ai.infrastructure.relationship.service.RelationshipQueryPlanner;
import com.ai.infrastructure.relationship.service.RelationshipSchemaProvider;
import com.ai.infrastructure.relationship.service.RelationshipTraversalService;
import com.ai.infrastructure.relationship.validation.RelationshipQueryValidator;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nullable;
import jakarta.persistence.EntityManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Base configuration that exposes shared beans for the relationship query module.
 */
@Configuration(proxyBeanMethods = false)
class RelationshipQueryConfiguration {

    @Bean
    @ConditionalOnMissingBean
    RelationshipModuleMetadata relationshipModuleMetadata(RelationshipQueryProperties properties) {
        return RelationshipModuleMetadata.from(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    EntityRelationshipMapper entityRelationshipMapper() {
        return new EntityRelationshipMapper();
    }

    @Bean
    @ConditionalOnBean(EntityManager.class)
    @ConditionalOnMissingBean
    RelationshipSchemaProvider relationshipSchemaProvider(EntityManager entityManager,
                                                         @Nullable AIEntityConfigurationLoader configurationLoader,
                                                         RelationshipQueryProperties properties,
                                                         EntityRelationshipMapper mapper) {
        return new RelationshipSchemaProvider(entityManager, configurationLoader, properties, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    RelationshipQueryValidator relationshipQueryValidator(EntityRelationshipMapper mapper) {
        return new RelationshipQueryValidator(mapper);
    }

    @Bean
    @ConditionalOnBean({RelationshipSchemaProvider.class, AICoreService.class})
    @ConditionalOnMissingBean
    RelationshipQueryPlanner relationshipQueryPlanner(AICoreService aiCoreService,
                                                      RelationshipSchemaProvider schemaProvider,
                                                      RelationshipQueryProperties properties,
                                                      RelationshipQueryValidator validator,
                                                      ObjectMapper objectMapper) {
        return new RelationshipQueryPlanner(aiCoreService, schemaProvider, properties, validator, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    DynamicJPAQueryBuilder dynamicJPAQueryBuilder(EntityRelationshipMapper mapper) {
        return new DynamicJPAQueryBuilder(mapper);
    }

    @Bean(name = "jpaRelationshipTraversalService")
    @ConditionalOnBean(EntityManager.class)
    @ConditionalOnMissingBean(name = "jpaRelationshipTraversalService")
    RelationshipTraversalService jpaRelationshipTraversalService(EntityManager entityManager) {
        return new JpaRelationshipTraversalService(entityManager);
    }

    @Bean(name = "metadataRelationshipTraversalService")
    @ConditionalOnBean(AISearchableEntityRepository.class)
    @ConditionalOnMissingBean(name = "metadataRelationshipTraversalService")
    RelationshipTraversalService metadataRelationshipTraversalService(AISearchableEntityRepository repository,
                                                                      ObjectMapper objectMapper) {
        return new MetadataRelationshipTraversalService(repository, objectMapper);
    }

    @Bean
    @ConditionalOnBean({
        RelationshipQueryPlanner.class,
        RelationshipTraversalService.class,
        AISearchableEntityRepository.class
    })
    @ConditionalOnMissingBean
    LLMDrivenJPAQueryService relationshipQueryService(RelationshipQueryPlanner planner,
                                                      DynamicJPAQueryBuilder queryBuilder,
                                                      RelationshipQueryValidator validator,
                                                      RelationshipQueryProperties properties,
                                                      RelationshipModuleMetadata metadata,
                                                      RelationshipTraversalService jpaRelationshipTraversalService,
                                                      RelationshipTraversalService metadataRelationshipTraversalService,
                                                      AISearchableEntityRepository repository,
                                                      @Nullable VectorDatabaseService vectorDatabaseService,
                                                      @Nullable AIEmbeddingService embeddingService) {
        return new LLMDrivenJPAQueryService(
            planner,
            queryBuilder,
            validator,
            properties,
            metadata,
            jpaRelationshipTraversalService,
            metadataRelationshipTraversalService,
            repository,
            vectorDatabaseService,
            embeddingService
        );
    }

    @Bean
    @ConditionalOnMissingBean
    RelationshipModuleMarker relationshipModuleMarker() {
        return new RelationshipModuleMarker();
    }

    /**
     * Simple marker bean that allows downstream applications to confirm the module is active.
     */
    static final class RelationshipModuleMarker { }
}
