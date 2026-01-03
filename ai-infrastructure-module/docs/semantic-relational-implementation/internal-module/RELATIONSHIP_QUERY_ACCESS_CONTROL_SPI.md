# Relationship Query Access Control SPI

> **How framework users control access to the framework-provided RelationshipQueryActionHandler**

## 🎯 The Problem

**Question:** If the framework provides `RelationshipQueryActionHandler`, how can framework users control access to this action?

**Challenge:**
- Framework provides the handler implementation
- Users need to control:
  - Who can execute relationship queries (`validateActionAllowed()`)
  - Which entity types users can query (`filterAllowedEntityTypes()`)
  - Row-level security on results (`canUserAccessEntity()`)

**Solution:** Use SPI (Service Provider Interface) pattern with optional hooks.

---

## 🏗️ Architecture Solution

### Option 1: RelationshipQueryAccessControlPolicy SPI (Recommended)

**Create a new SPI interface that users implement:**

```java
// Location: ai-infrastructure-relationship-query
package com.ai.infrastructure.relationship.access;

import java.util.List;

/**
 * SPI for relationship query access control.
 * 
 * Framework users implement this interface to control:
 * - Who can execute relationship queries
 * - Which entity types users can query
 * - Row-level security on results
 * 
 * If not provided, framework uses permissive defaults (for development).
 * In production, users MUST implement this interface.
 */
public interface RelationshipQueryAccessControlPolicy {
    
    /**
     * Check if user can execute relationship queries.
     * 
     * Called by RelationshipQueryActionHandler.validateActionAllowed()
     * 
     * @param userId User identifier
     * @return true if user can execute relationship queries, false otherwise
     */
    boolean canUserExecuteRelationshipQueries(String userId);
    
    /**
     * Check if user can query a specific entity type.
     * 
     * Called by RelationshipQueryActionHandler.filterAllowedEntityTypes()
     * 
     * @param userId User identifier
     * @param entityType Entity type to check (e.g., "customer", "order")
     * @return true if user can query this entity type, false otherwise
     */
    boolean canUserQueryEntityType(String userId, String entityType);
    
    /**
     * Get all entity types a user is allowed to query.
     * 
     * Used when no entity types are specified in the query.
     * 
     * @param userId User identifier
     * @return List of entity types the user can query (empty list = none allowed)
     */
    List<String> getAllowedEntityTypesForUser(String userId);
    
    /**
     * Optional: Check if user can access a specific entity result.
     * 
     * Called for each result entity (row-level security).
     * If not implemented, uses EntityAccessPolicy instead.
     * 
     * @param userId User identifier
     * @param entityType Entity type (e.g., "customer")
     * @param entityId Entity ID
     * @return true if user can access this entity, false otherwise
     */
    default boolean canUserAccessEntity(String userId, String entityType, String entityId) {
        // Default: delegate to EntityAccessPolicy if available
        return true;  // Override for custom logic
    }
}
```

### Framework Handler Implementation

**The framework's handler uses this SPI:**

```java
// Location: ai-infrastructure-relationship-query
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "ai.infrastructure.relationship.enable-orchestrator-integration",
    havingValue = "true",
    matchIfMissing = true
)
public class RelationshipQueryActionHandler implements ActionHandler {
    
    private final ReliableRelationshipQueryService queryService;
    
    // SPI hook - optional, uses ObjectProvider for optional injection
    private final ObjectProvider<RelationshipQueryAccessControlPolicy> accessControlPolicyProvider;
    
    @Override
    public boolean validateActionAllowed(String userId) {
        // Use SPI if provided, otherwise permissive default (with warning)
        RelationshipQueryAccessControlPolicy policy = accessControlPolicyProvider.getIfAvailable();
        
        if (policy != null) {
            return policy.canUserExecuteRelationshipQueries(userId);
        }
        
        // Default: allow if authenticated (with warning in logs)
        log.warn("No RelationshipQueryAccessControlPolicy provided - using permissive default. " +
                 "Implement RelationshipQueryAccessControlPolicy for production security.");
        return userId != null && !userId.isBlank();
    }
    
    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        String query = extractQuery(params);
        List<String> entityTypes = extractEntityTypes(params);
        
        // Filter entity types using SPI
        List<String> allowedEntityTypes = filterAllowedEntityTypes(userId, entityTypes);
        
        if (allowedEntityTypes.isEmpty()) {
            return ActionResult.builder()
                .success(false)
                .message("Access denied: You don't have permission to query the requested entity types")
                .data(Map.of("error", "ACCESS_DENIED", "requestedEntityTypes", entityTypes))
                .build();
        }
        
        // Execute query
        RAGResponse response = queryService.execute(query, allowedEntityTypes, options);
        
        // Filter results using SPI (row-level security)
        List<RAGDocument> filtered = filterResults(userId, response.getDocuments());
        
        return ActionResult.builder()
            .success(true)
            .data(buildResultData(filtered))
            .build();
    }
    
    private List<String> filterAllowedEntityTypes(String userId, List<String> requestedTypes) {
        RelationshipQueryAccessControlPolicy policy = accessControlPolicyProvider.getIfAvailable();
        
        if (policy != null) {
            // Use SPI implementation
            if (requestedTypes == null || requestedTypes.isEmpty()) {
                return policy.getAllowedEntityTypesForUser(userId);
            }
            
            return requestedTypes.stream()
                .filter(type -> policy.canUserQueryEntityType(userId, type))
                .toList();
        }
        
        // Default: allow all (with warning)
        log.warn("No RelationshipQueryAccessControlPolicy provided - allowing all entity types. " +
                 "Implement RelationshipQueryAccessControlPolicy for production security.");
        return requestedTypes != null ? requestedTypes : Collections.emptyList();
    }
    
    private List<RAGDocument> filterResults(String userId, List<RAGDocument> documents) {
        RelationshipQueryAccessControlPolicy policy = accessControlPolicyProvider.getIfAvailable();
        
        if (policy != null) {
            return documents.stream()
                .filter(doc -> {
                    String entityType = doc.getMetadata().get("entityType");
                    String entityId = doc.getId();
                    return policy.canUserAccessEntity(userId, entityType, entityId);
                })
                .toList();
        }
        
        // Default: return all (with warning)
        log.warn("No RelationshipQueryAccessControlPolicy provided - returning all results. " +
                 "Implement RelationshipQueryAccessControlPolicy for production security.");
        return documents;
    }
}
```

### User Implementation

**Framework users implement the SPI:**

```java
// Location: User's application code
@Component
@RequiredArgsConstructor
public class MyRelationshipQueryAccessControlPolicy 
        implements RelationshipQueryAccessControlPolicy {
    
    private final UserService userService;
    private final PermissionService permissionService;
    private final TenantService tenantService;
    
    @Override
    public boolean canUserExecuteRelationshipQueries(String userId) {
        // Check if user can execute relationship queries
        User user = userService.getUser(userId);
        if (user == null || !user.isActive()) {
            return false;
        }
        
        // Permission-based check
        return permissionService.hasPermission(userId, "relationship_query:execute");
    }
    
    @Override
    public boolean canUserQueryEntityType(String userId, String entityType) {
        User user = userService.getUser(userId);
        
        // Admins can query all
        if (user.getRole().equals("ADMIN")) {
            return true;
        }
        
        // Permission-based check
        String permission = "relationship_query:" + entityType;
        if (permissionService.hasPermission(userId, permission)) {
            return true;
        }
        
        // Role-based fallback
        if (user.getRole().equals("ANALYST") && 
            Arrays.asList("customer", "order", "product").contains(entityType)) {
            return true;  // Analysts can query these types
        }
        
        return false;
    }
    
    @Override
    public List<String> getAllowedEntityTypesForUser(String userId) {
        User user = userService.getUser(userId);
        
        if (user.getRole().equals("ADMIN")) {
            // Return all entity types (you'd get this from schema provider)
            return List.of("customer", "order", "product", "payment", "invoice");
        }
        
        // Return entity types user has permission for
        return permissionService.getEntityTypesWithPermission(
            userId, 
            "relationship_query:"
        );
    }
    
    @Override
    public boolean canUserAccessEntity(String userId, String entityType, String entityId) {
        // Multi-tenant check
        String userTenant = tenantService.getTenantId(userId);
        String entityTenant = getEntityTenant(entityType, entityId);
        
        if (!userTenant.equals(entityTenant)) {
            return false;  // Tenant mismatch
        }
        
        // Additional checks (ownership, etc.)
        return true;
    }
}
```

---

## 🔄 Integration with EntityAccessPolicy

**The framework also integrates with the existing `EntityAccessPolicy` SPI:**

### Layer 1: Orchestrator Level (EntityAccessPolicy)

```java
// In RAGOrchestrator - already uses EntityAccessPolicy
AIAccessControlResponse accessResponse = accessControlService.checkAccess(
    AIAccessControlRequest.builder()
        .resourceId("rag:relationship_query")  // Framework checks this
        .operationType("EXECUTE")
        .build()
);
```

**Your EntityAccessPolicy implementation:**

```java
@Component
public class MyEntityAccessPolicy implements EntityAccessPolicy {
    
    @Override
    public boolean canUserAccessEntity(String userId, Map<String, Object> entity) {
        String resourceId = (String) entity.get("resourceId");
        
        // Framework-level check: Can user use relationship query feature?
        if ("rag:relationship_query".equals(resourceId)) {
            return permissionService.hasPermission(userId, "relationship_query:execute");
        }
        
        // Entity-level checks (for Layer 4)
        if (resourceId != null && resourceId.startsWith("rag:")) {
            String entityType = resourceId.substring(4);
            String entityId = (String) entity.get("entityId");
            
            // Multi-tenant check
            String userTenant = tenantService.getTenantId(userId);
            String entityTenant = getEntityTenant(entityType, entityId);
            return userTenant.equals(entityTenant);
        }
        
        return false;
    }
}
```

### Access Control Layers Summary

```
┌─────────────────────────────────────────────────────────────────────┐
│  LAYER 1: ORCHESTRATOR LEVEL                                        │
│  ═══════════════════════════════════════════════════════════════════│
│  EntityAccessPolicy.canUserAccessEntity()                            │
│    └─→ Checks: "rag:relationship_query" resource                     │
│    └─→ Purpose: Can user use relationship query feature?            │
│    └─→ User implements: EntityAccessPolicy                           │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│  LAYER 2: ACTION HANDLER LEVEL                                      │
│  ═══════════════════════════════════════════════════════════════════│
│  RelationshipQueryAccessControlPolicy.canUserExecuteRelationshipQueries()│
│    └─→ Checks: Can user execute relationship queries?              │
│    └─→ Purpose: Action-level permission check                       │
│    └─→ User implements: RelationshipQueryAccessControlPolicy         │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│  LAYER 3: ENTITY TYPE LEVEL                                         │
│  ═══════════════════════════════════════════════════════════════════│
│  RelationshipQueryAccessControlPolicy.canUserQueryEntityType()       │
│    └─→ Checks: Which entity types can user query?                   │
│    └─→ Purpose: Filter entity types before query planning           │
│    └─→ User implements: RelationshipQueryAccessControlPolicy         │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│  LAYER 4: RESULT LEVEL                                              │
│  ═══════════════════════════════════════════════════════════════════│
│  RelationshipQueryAccessControlPolicy.canUserAccessEntity()          │
│  OR                                                                  │
│  EntityAccessPolicy.canUserAccessEntity()                            │
│    └─→ Checks: Can user access this specific entity?                │
│    └─→ Purpose: Row-level security (multi-tenant, ownership)         │
│    └─→ User implements: Either SPI (preference)                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 📋 Implementation Checklist

### Framework Side

- [ ] Create `RelationshipQueryAccessControlPolicy` interface
- [ ] Update `RelationshipQueryActionHandler` to use `ObjectProvider<RelationshipQueryAccessControlPolicy>`
- [ ] Implement default behavior (permissive with warnings) when SPI not provided
- [ ] Add configuration property to require SPI in production
- [ ] Document SPI interface
- [ ] Add unit tests for handler with/without SPI

### User Side

- [ ] Implement `RelationshipQueryAccessControlPolicy` interface
- [ ] Implement `canUserExecuteRelationshipQueries()` method
- [ ] Implement `canUserQueryEntityType()` method
- [ ] Implement `getAllowedEntityTypesForUser()` method
- [ ] Optionally implement `canUserAccessEntity()` for row-level security
- [ ] Test access control with different user roles/permissions

---

## 🔧 Configuration

### Require Access Control Policy in Production

```yaml
ai:
  infrastructure:
    relationship:
      enable-orchestrator-integration: true
      
      # Access control settings
      require-access-control-policy: true  # Fail if SPI not provided
      enable-entity-type-filtering: true
```

**Framework implementation:**

```java
@PostConstruct
public void validateAccessControl() {
    if (properties.isRequireAccessControlPolicy()) {
        RelationshipQueryAccessControlPolicy policy = 
            accessControlPolicyProvider.getIfAvailable();
        
        if (policy == null) {
            throw new IllegalStateException(
                "RelationshipQueryAccessControlPolicy is required but not provided. " +
                "Implement RelationshipQueryAccessControlPolicy interface or set " +
                "ai.infrastructure.relationship.require-access-control-policy=false"
            );
        }
    }
}
```

---

## 🎯 Alternative: Use EntityAccessPolicy Only

**If you prefer not to create a new SPI, you can use only `EntityAccessPolicy`:**

### Framework Handler (Alternative)

```java
@Component
@RequiredArgsConstructor
public class RelationshipQueryActionHandler implements ActionHandler {
    
    private final ReliableRelationshipQueryService queryService;
    private final ObjectProvider<EntityAccessPolicy> entityAccessPolicyProvider;
    
    @Override
    public boolean validateActionAllowed(String userId) {
        EntityAccessPolicy policy = entityAccessPolicyProvider.getIfAvailable();
        
        if (policy != null) {
            Map<String, Object> entityContext = Map.of(
                "resourceId", "rag:relationship_query",
                "operationType", "EXECUTE"
            );
            return policy.canUserAccessEntity(userId, entityContext);
        }
        
        // Default: allow if authenticated
        return userId != null && !userId.isBlank();
    }
    
    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        // Extract entity types
        List<String> entityTypes = extractEntityTypes(params);
        
        // Filter using EntityAccessPolicy
        EntityAccessPolicy policy = entityAccessPolicyProvider.getIfAvailable();
        List<String> allowedEntityTypes = entityTypes.stream()
            .filter(type -> {
                if (policy == null) return true;  // Default: allow
                
                Map<String, Object> context = Map.of(
                    "resourceId", "rag:relationship_query:" + type,
                    "operationType", "QUERY"
                );
                return policy.canUserAccessEntity(userId, context);
            })
            .toList();
        
        // Execute query...
    }
}
```

**Pros:**
- ✅ No new SPI interface needed
- ✅ Reuses existing `EntityAccessPolicy`
- ✅ Consistent with framework access control

**Cons:**
- ❌ Less specific (generic interface)
- ❌ Harder to document relationship query-specific access control
- ❌ EntityAccessPolicy might be used for other purposes

---

## 🏆 Recommended Approach

**Use Option 1: RelationshipQueryAccessControlPolicy SPI**

**Reasons:**
1. **Separation of Concerns**: Relationship query access control is specific
2. **Better Documentation**: Clear interface for relationship query access
3. **Type Safety**: Specific methods for relationship query use cases
4. **Flexibility**: Can still delegate to EntityAccessPolicy if needed
5. **Future Extensibility**: Easy to add relationship query-specific features

**Integration:**
- Layer 1 (Orchestrator): Uses `EntityAccessPolicy` (framework-level)
- Layer 2-4 (Action Handler): Uses `RelationshipQueryAccessControlPolicy` (action-specific)

---

## 📚 Example: Complete Implementation

### User's Access Control Policy

```java
@Component
@RequiredArgsConstructor
public class MyRelationshipQueryAccessControlPolicy 
        implements RelationshipQueryAccessControlPolicy {
    
    private final UserService userService;
    private final PermissionService permissionService;
    private final TenantService tenantService;
    private final EntityAccessPolicy entityAccessPolicy;  // For Layer 4
    
    @Override
    public boolean canUserExecuteRelationshipQueries(String userId) {
        User user = userService.getUser(userId);
        return user != null && 
               user.isActive() && 
               permissionService.hasPermission(userId, "relationship_query:execute");
    }
    
    @Override
    public boolean canUserQueryEntityType(String userId, String entityType) {
        User user = userService.getUser(userId);
        
        // Admins can query all
        if (user.getRole().equals("ADMIN")) {
            return true;
        }
        
        // Permission-based
        return permissionService.hasPermission(userId, "relationship_query:" + entityType);
    }
    
    @Override
    public List<String> getAllowedEntityTypesForUser(String userId) {
        User user = userService.getUser(userId);
        
        if (user.getRole().equals("ADMIN")) {
            return getAllEntityTypes();  // All types
        }
        
        return permissionService.getEntityTypesWithPermission(
            userId, 
            "relationship_query:"
        );
    }
    
    @Override
    public boolean canUserAccessEntity(String userId, String entityType, String entityId) {
        // Option 1: Use EntityAccessPolicy (delegation)
        Map<String, Object> context = Map.of(
            "resourceId", "rag:" + entityType,
            "operationType", "READ",
            "entityId", entityId
        );
        return entityAccessPolicy.canUserAccessEntity(userId, context);
        
        // Option 2: Custom logic
        // String userTenant = tenantService.getTenantId(userId);
        // String entityTenant = getEntityTenant(entityType, entityId);
        // return userTenant.equals(entityTenant);
    }
}
```

---

## 🎯 Summary

**Problem:** Framework provides handler, users need to control access.

**Solution:** 
1. Create `RelationshipQueryAccessControlPolicy` SPI interface
2. Framework handler uses `ObjectProvider<RelationshipQueryAccessControlPolicy>`
3. Users implement the SPI in their application
4. Framework provides permissive defaults (with warnings) if SPI not provided
5. Configuration option to require SPI in production

**Result:** Framework users have full control over relationship query access control while using the framework-provided handler.

---

**Last Updated:** 2025-12-30  
**Status:** Design Proposal  
**Related:** `RELATIONSHIP_QUERY_ORCHESTRATOR_INTEGRATION.md`, `ACCESS_CONTROL_MECHANICS.md`

