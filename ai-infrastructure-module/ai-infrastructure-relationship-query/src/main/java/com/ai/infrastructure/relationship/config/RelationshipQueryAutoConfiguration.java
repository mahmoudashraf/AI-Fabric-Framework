package com.ai.infrastructure.relationship.config;

import com.ai.infrastructure.config.AIInfrastructureAutoConfiguration;
import com.ai.infrastructure.core.AICoreService;
import jakarta.persistence.EntityManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * Auto-configuration entry point registered via {@code spring.factories}.
 */
@AutoConfiguration
@AutoConfigureAfter(AIInfrastructureAutoConfiguration.class)
@EnableConfigurationProperties(RelationshipQueryProperties.class)
@ConditionalOnClass({AICoreService.class, EntityManagerFactory.class})
@ConditionalOnExpression("${ai.relationship.enabled:${ai.infrastructure.relationship.enabled:true}}")
@Import(RelationshipQueryConfiguration.class)
public class RelationshipQueryAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RelationshipQueryAutoConfiguration.class);

    public RelationshipQueryAutoConfiguration(RelationshipQueryProperties properties) {
        log.info(
            "Relationship query module enabled (maxTraversalDepth={}, defaultReturnMode={}, vectorSearchEnabled={})",
            properties.getMaxTraversalDepth(),
            properties.getDefaultReturnMode(),
            properties.isEnableVectorSearch()
        );
    }
}
