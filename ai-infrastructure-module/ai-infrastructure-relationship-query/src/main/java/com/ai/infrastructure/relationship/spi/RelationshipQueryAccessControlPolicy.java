package com.ai.infrastructure.relationship.spi;

import java.util.List;

/**
 * SPI (Service Provider Interface) for controlling access to relationship queries.
 * 
 * Framework users implement this interface to provide their own access control logic.
 * The framework's {@link com.ai.infrastructure.relationship.action.RelationshipQueryActionHandler}
 * automatically discovers and uses implementations via Spring's dependency injection.
 * 
 * <p>Example implementation:</p>
 * <pre>{@code
 * @Component
 * @RequiredArgsConstructor
 * public class MyRelationshipQueryAccessControlPolicy 
 *         implements RelationshipQueryAccessControlPolicy {
 *     
 *     private final PermissionService permissionService;
 *     
 *     @Override
 *     public boolean canUserExecuteRelationshipQueries(String userId) {
 *         return permissionService.hasPermission(userId, "relationship_query:execute");
 *     }
 *     
 *     @Override
 *     public boolean canUserQueryEntityType(String userId, String entityType) {
 *         return permissionService.hasPermission(userId, "relationship_query:" + entityType);
 *     }
 *     
 *     @Override
 *     public List<String> getAllowedEntityTypesForUser(String userId) {
 *         return permissionService.getEntityTypesWithPermission(userId, "relationship_query:");
 *     }
 * }
 * }</pre>
 * 
 * <p><strong>REQUIREMENT:</strong> This interface MUST be implemented when orchestrator
 * integration is enabled ({@code enable-orchestrator-integration=true}). The application will
 * fail to start if no implementation is provided.</p>
 * 
 * <p><strong>Note:</strong> When orchestrator integration is disabled ({@code enable-orchestrator-integration=false}),
 * this policy is NOT required. In standalone mode, users handle access control manually when
 * using {@link com.ai.infrastructure.relationship.service.ReliableRelationshipQueryService} directly.</p>
 */
public interface RelationshipQueryAccessControlPolicy {

    /**
     * Check if the user can execute relationship queries at all.
     * 
     * This is a general permission check - if this returns false, the user
     * cannot execute any relationship queries regardless of entity type.
     * 
     * @param userId User identifier (may be null for anonymous users)
     * @return true if user can execute relationship queries, false otherwise
     */
    boolean canUserExecuteRelationshipQueries(String userId);

    /**
     * Check if the user can query a specific entity type.
     * 
     * This method is called for each entity type in the query to determine
     * which entity types the user is allowed to access.
     * 
     * @param userId User identifier (may be null for anonymous users)
     * @param entityType Entity type to check (e.g., "customer", "order", "product")
     * @return true if user can query this entity type, false otherwise
     */
    boolean canUserQueryEntityType(String userId, String entityType);

    /**
     * Get all entity types the user is allowed to query.
     * 
     * This is used when no entity types are specified in the query,
     * to determine which entity types should be available for auto-detection.
     * 
     * @param userId User identifier (may be null for anonymous users)
     * @return List of entity types the user can query, or empty list if none
     */
    List<String> getAllowedEntityTypesForUser(String userId);
}

