package com.ai.infrastructure.relationship.config;

import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.ai.infrastructure.relationship.*;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for Relationship Query functionality
 * 
 * This configuration is automatically loaded when:
 * - ai-infrastructure-relationship-query is on classpath
 * - ai.infrastructure.relationship.enabled=true (default: true)
 * - AICoreService is available
 */
@Slf4j
@Configuration
@ConditionalOnClass({RelationshipQueryPlanner.class, AICoreService.class})
@ConditionalOnProperty(
    prefix = "ai.infrastructure.relationship",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
@AutoConfigureAfter(name = "com.ai.infrastructure.config.AIInfrastructureAutoConfiguration")
@EnableConfigurationProperties(RelationshipQueryProperties.class)
public class RelationshipQueryAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public RelationshipSchemaProvider relationshipSchemaProvider(
            com.ai.infrastructure.config.AIEntityConfigurationLoader configurationLoader) {
        log.info("Configuring RelationshipSchemaProvider");
        return new RelationshipSchemaProvider(configurationLoader);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public EntityRelationshipMapper entityRelationshipMapper() {
        log.info("Configuring EntityRelationshipMapper");
        return new EntityRelationshipMapper();
    }
    
    @Bean
    @ConditionalOnMissingBean
    public RelationshipQueryBuilder relationshipQueryBuilder() {
        log.info("Configuring RelationshipQueryBuilder");
        return new RelationshipQueryBuilder();
    }
    
    @Bean
    @ConditionalOnMissingBean
    public RelationshipQueryPlanner relationshipQueryPlanner(
            AICoreService aiCoreService,
            EntityRelationshipMapper entityMapper,
            RelationshipSchemaProvider schemaProvider) {
        log.info("Configuring RelationshipQueryPlanner");
        return new RelationshipQueryPlanner(aiCoreService, entityMapper, schemaProvider);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public DynamicJPAQueryBuilder dynamicJPAQueryBuilder(
            EntityManager entityManager,
            EntityRelationshipMapper entityMapper) {
        log.info("Configuring DynamicJPAQueryBuilder");
        return new DynamicJPAQueryBuilder(entityManager, entityMapper);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public RelationshipTraversalService relationshipTraversalService(
            AISearchableEntityRepository searchableEntityRepository,
            EntityManager entityManager,
            RelationshipQueryBuilder queryBuilder) {
        log.info("Configuring RelationshipTraversalService");
        return new RelationshipTraversalService(
            searchableEntityRepository, entityManager, queryBuilder
        );
    }
    
    @Bean
    @ConditionalOnMissingBean
    public JPARelationshipTraversalService jpaRelationshipTraversalService(
            AISearchableEntityRepository searchableEntityRepository,
            EntityManager entityManager,
            RelationshipQueryBuilder queryBuilder) {
        log.info("Configuring JPARelationshipTraversalService");
        return new JPARelationshipTraversalService(
            searchableEntityRepository, entityManager, queryBuilder
        );
    }
    
    @Bean
    @ConditionalOnMissingBean
    public LLMDrivenJPAQueryService llmDrivenJPAQueryService(
            RelationshipQueryPlanner queryPlanner,
            DynamicJPAQueryBuilder queryBuilder,
            EntityManager entityManager,
            AISearchableEntityRepository searchableEntityRepository,
            VectorDatabaseService vectorDatabaseService,
            AIEmbeddingService embeddingService) {
        log.info("Configuring LLMDrivenJPAQueryService");
        return new LLMDrivenJPAQueryService(
            queryPlanner, queryBuilder, entityManager,
            searchableEntityRepository, vectorDatabaseService, embeddingService
        );
    }
}
