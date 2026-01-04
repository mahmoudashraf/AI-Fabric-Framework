package com.ai.infrastructure.relationship.spi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test to demonstrate how to implement RelationshipQueryAccessControlPolicy.
 * 
 * This test shows example implementations for different access control scenarios.
 */
@DisplayName("RelationshipQueryAccessControlPolicy Interface")
class RelationshipQueryAccessControlPolicyTest {

    @Test
    @DisplayName("Example: Role-based access control implementation")
    void exampleRoleBasedAccessControl() {
        RelationshipQueryAccessControlPolicy policy = new RoleBasedAccessControlPolicy();

        // Admin can execute relationship queries
        assertThat(policy.canUserExecuteRelationshipQueries("admin-user")).isTrue();
        
        // Regular user can execute relationship queries
        assertThat(policy.canUserExecuteRelationshipQueries("regular-user")).isTrue();
        
        // Anonymous users cannot
        assertThat(policy.canUserExecuteRelationshipQueries(null)).isFalse();

        // Admin can query all entity types
        assertThat(policy.canUserQueryEntityType("admin-user", "customer")).isTrue();
        assertThat(policy.canUserQueryEntityType("admin-user", "order")).isTrue();
        assertThat(policy.canUserQueryEntityType("admin-user", "product")).isTrue();

        // Regular user can only query specific entity types
        assertThat(policy.canUserQueryEntityType("regular-user", "product")).isTrue();
        assertThat(policy.canUserQueryEntityType("regular-user", "order")).isFalse();
        assertThat(policy.canUserQueryEntityType("regular-user", "customer")).isFalse();

        // Admin gets all entity types
        List<String> adminTypes = policy.getAllowedEntityTypesForUser("admin-user");
        assertThat(adminTypes).containsExactlyInAnyOrder("customer", "order", "product");

        // Regular user gets limited entity types
        List<String> userTypes = policy.getAllowedEntityTypesForUser("regular-user");
        assertThat(userTypes).containsExactly("product");
    }

    @Test
    @DisplayName("Example: Permission-based access control implementation")
    void examplePermissionBasedAccessControl() {
        RelationshipQueryAccessControlPolicy policy = new PermissionBasedAccessControlPolicy();

        // User with permissions can query specific entity types
        assertThat(policy.canUserQueryEntityType("user-with-product-permission", "product")).isTrue();
        assertThat(policy.canUserQueryEntityType("user-with-product-permission", "order")).isFalse();

        assertThat(policy.canUserQueryEntityType("user-with-all-permissions", "product")).isTrue();
        assertThat(policy.canUserQueryEntityType("user-with-all-permissions", "order")).isTrue();
        assertThat(policy.canUserQueryEntityType("user-with-all-permissions", "customer")).isTrue();
    }

    // ==================== Example Implementations ====================

    /**
     * Example implementation: Role-based access control
     */
    private static class RoleBasedAccessControlPolicy implements RelationshipQueryAccessControlPolicy {

        @Override
        public boolean canUserExecuteRelationshipQueries(String userId) {
            // Anonymous users cannot execute relationship queries
            return userId != null && !userId.isBlank();
        }

        @Override
        public boolean canUserQueryEntityType(String userId, String entityType) {
            if (userId == null) {
                return false;
            }

            // Admin users can query all entity types
            if (userId.startsWith("admin-")) {
                return true;
            }

            // Regular users can only query "product" entity type
            if (userId.startsWith("regular-")) {
                return "product".equals(entityType);
            }

            return false;
        }

        @Override
        public List<String> getAllowedEntityTypesForUser(String userId) {
            if (userId == null) {
                return List.of();
            }

            // Admin users get all entity types
            if (userId.startsWith("admin-")) {
                return List.of("customer", "order", "product");
            }

            // Regular users only get "product"
            if (userId.startsWith("regular-")) {
                return List.of("product");
            }

            return List.of();
        }
    }

    /**
     * Example implementation: Permission-based access control
     */
    private static class PermissionBasedAccessControlPolicy implements RelationshipQueryAccessControlPolicy {

        // Simulated permission store
        private final java.util.Map<String, List<String>> userPermissions = java.util.Map.of(
            "user-with-product-permission", List.of("relationship_query:product"),
            "user-with-all-permissions", List.of("relationship_query:product", "relationship_query:order", "relationship_query:customer")
        );

        @Override
        public boolean canUserExecuteRelationshipQueries(String userId) {
            return userId != null && userPermissions.containsKey(userId);
        }

        @Override
        public boolean canUserQueryEntityType(String userId, String entityType) {
            if (userId == null || !userPermissions.containsKey(userId)) {
                return false;
            }

            String permission = "relationship_query:" + entityType;
            return userPermissions.get(userId).contains(permission);
        }

        @Override
        public List<String> getAllowedEntityTypesForUser(String userId) {
            if (userId == null || !userPermissions.containsKey(userId)) {
                return List.of();
            }

            return userPermissions.get(userId).stream()
                .filter(perm -> perm.startsWith("relationship_query:"))
                .map(perm -> perm.substring("relationship_query:".length()))
                .toList();
        }
    }
}

