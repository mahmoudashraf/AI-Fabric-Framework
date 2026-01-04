package com.ai.infrastructure.relationship.it.config;

import com.ai.infrastructure.relationship.spi.RelationshipQueryAccessControlPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Test implementation of RelationshipQueryAccessControlPolicy for integration tests.
 * 
 * This implementation allows all access by default but can be extended for specific test scenarios.
 */
@Slf4j
@TestConfiguration
public class TestRelationshipQueryAccessControlPolicy {

    @Bean
    public RelationshipQueryAccessControlPolicy relationshipQueryAccessControlPolicy() {
        return new AllowAllAccessControlPolicy();
    }

    /**
     * Simple implementation that allows all access.
     * Tests can override this bean to test access control scenarios.
     */
    private static class AllowAllAccessControlPolicy implements RelationshipQueryAccessControlPolicy {

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
            log.debug("Test policy: returning empty list (all entity types allowed) for user: {}", userId);
            // Return empty list to allow auto-detection of all entity types
            return List.of();
        }
    }
}

