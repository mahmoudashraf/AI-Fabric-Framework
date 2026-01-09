package com.ai.infrastructure.relationship.it.config;

import com.ai.infrastructure.relationship.spi.RelationshipQueryAccessControlPolicy;
import com.ai.infrastructure.relationship.service.RelationshipSchemaProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.List;

/**
 * Test implementation of RelationshipQueryAccessControlPolicy for integration tests.
 * 
 * This implementation allows all access by default but can be extended for specific test scenarios.
 */
@Slf4j
@TestConfiguration
public class TestRelationshipQueryAccessControlPolicy {

    @Bean
    public RelationshipQueryAccessControlPolicy relationshipQueryAccessControlPolicy(RelationshipSchemaProvider schemaProvider) {
        return new AllowAllAccessControlPolicy(schemaProvider);
    }

    /**
     * Simple implementation that allows all access.
     * Tests can override this bean to test access control scenarios.
     */
    private static class AllowAllAccessControlPolicy implements RelationshipQueryAccessControlPolicy {
        private final RelationshipSchemaProvider schemaProvider;

        private AllowAllAccessControlPolicy(RelationshipSchemaProvider schemaProvider) {
            this.schemaProvider = schemaProvider;
        }

        @Override
        public boolean canUserExecuteRelationshipQueries(String userId) {
            log.debug("Test policy: allowing relationship queries for user: {}", userId);
            return true;
        }

        @Override
        public boolean canUserQueryEntityType(String userId, String entityType) {
            log.debug("Test policy: allowing user {} to query entity type {}", userId, entityType);
            return true;
        }

        @Override
        public List<String> getAllowedEntityTypesForUser(String userId) {
            // IMPORTANT: Keep contract semantics consistent:
            // - empty list means "no entity types allowed"
            // - allow-all should be expressed as an explicit allow-list (derived from schema)
            List<String> types = new ArrayList<>(schemaProvider.getSchema().entities().keySet());
            types.sort(String::compareToIgnoreCase);
            log.debug("Test policy: allowing all schema entity types {} for user {}", types, userId);
            return types;
        }
    }
}

