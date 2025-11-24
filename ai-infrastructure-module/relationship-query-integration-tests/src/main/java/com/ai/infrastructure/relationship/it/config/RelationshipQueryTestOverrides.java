package com.ai.infrastructure.relationship.it.config;

import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.relationship.cache.QueryCache;
import com.ai.infrastructure.relationship.config.RelationshipModuleMetadata;
import com.ai.infrastructure.relationship.config.RelationshipQueryProperties;
import com.ai.infrastructure.relationship.metrics.QueryMetrics;
import com.ai.infrastructure.relationship.service.LLMDrivenJPAQueryService;
import com.ai.infrastructure.relationship.service.RelationshipQueryPlanner;
import com.ai.infrastructure.relationship.service.RelationshipTraversalService;
import com.ai.infrastructure.relationship.service.ReliableRelationshipQueryService;
import com.ai.infrastructure.relationship.validation.RelationshipQueryValidator;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.ai.infrastructure.rag.VectorDatabaseService;
import jakarta.annotation.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Provides a deterministic ReliableRelationshipQueryService bean for the real API tests.
 */
@Configuration
public class RelationshipQueryTestOverrides {

    @Bean
    @Primary
    public ReliableRelationshipQueryService testReliableRelationshipQueryService(
        LLMDrivenJPAQueryService llmDrivenJPAQueryService,
        RelationshipQueryPlanner planner,
        @Qualifier("metadataRelationshipTraversalService") RelationshipTraversalService metadataTraversalService,
        @Nullable VectorDatabaseService vectorDatabaseService,
        @Nullable AIEmbeddingService embeddingService,
        AISearchableEntityRepository repository,
        RelationshipQueryValidator validator,
        RelationshipQueryProperties properties,
        RelationshipModuleMetadata metadata,
        QueryCache queryCache,
        QueryMetrics queryMetrics
    ) {
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
