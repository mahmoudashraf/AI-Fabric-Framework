# Access Control Mechanics - Complete Framework Guide

> **Comprehensive guide to access control across the AI Infrastructure Framework**  
> This is an open-source library designed to be integrated into your applications.

---

## 📋 Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Access Control Layers](#access-control-layers)
  - [Layer 1: Orchestrator Level (Framework Entry Point)](#layer-1-orchestrator-level-framework-entry-point)
  - [Layer 2: Action Handler Level (Action-Specific)](#layer-2-action-handler-level-action-specific)
  - [Layer 3: Entity Type Level (Relationship Queries)](#layer-3-entity-type-level-relationship-queries)
  - [Layer 4: Result Level (Entity-Level Filtering)](#layer-4-result-level-entity-level-filtering)
- [SPI Interfaces](#spi-interfaces)
  - [EntityAccessPolicy](#entityaccesspolicy)
  - [ActionHandler](#actionhandler)
- [Implementation Patterns](#implementation-patterns)
  - [Pattern 1: Role-Based Access Control](#pattern-1-role-based-access-control)
  - [Pattern 2: Permission-Based Access Control](#pattern-2-permission-based-access-control)
  - [Pattern 3: Tenant-Based Access Control](#pattern-3-tenant-based-access-control)
  - [Pattern 4: Data Classification-Based Access](#pattern-4-data-classification-based-access)
  - [Pattern 5: Hybrid Access Control (Recommended)](#pattern-5-hybrid-access-control-recommended)
- [Configuration](#configuration)
- [Integration Guide](#integration-guide)
- [Best Practices](#best-practices)
- [Security Model](#security-model)
- [Examples](#examples)
- [Troubleshooting](#troubleshooting)

---

## Overview

The AI Infrastructure Framework provides **multi-layered access control** designed for enterprise applications. Access control is enforced at multiple levels to ensure defense-in-depth security.

### Key Principles

1. **Fail-Closed Security**: If access control check fails or throws exception, access is **denied**
2. **Defense in Depth**: Multiple layers of access control checks
3. **SPI Pattern**: Framework provides hooks, you implement business logic
4. **Optional but Recommended**: Framework works without access control, but production apps should implement it
5. **Open Source Friendly**: Designed to be integrated into any Spring Boot application

### Access Control Flow

```
User Query
  ↓
Layer 1: Orchestrator Level (Framework Entry Point)
  ├─→ Security Analysis (AISecurityService)
  ├─→ Access Control Check (AIAccessControlService + EntityAccessPolicy)
  └─→ PII Detection & Compliance
  ↓
Layer 2: Action Handler Level (Action-Specific)
  ├─→ validateActionAllowed() - Can user execute this action?
  └─→ Action-specific filtering (e.g., entity type filtering)
  ↓
Layer 3: Entity Type Level (Relationship Queries)
  ├─→ filterAllowedEntityTypes() - Which entity types can user query?
  └─→ Token optimization (only send allowed schemas to LLM)
  ↓
Layer 4: Result Level (Entity-Level Filtering)
  ├─→ Filter individual results via EntityAccessPolicy
  └─→ Final defense-in-depth check
  ↓
Results Returned to User
```

---

## Architecture

### Components

```
┌─────────────────────────────────────────────────────────────┐
│  Your Application                                           │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐ │
│  │  EntityAccessPolicy (Your Implementation)            │ │
│  │  - canUserAccessEntity()                              │ │
│  │  - logAccessDenied()                                 │ │
│  └──────────────────────────────────────────────────────┘ │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐ │
│  │  ActionHandler (Your Implementation)                │ │
│  │  - validateActionAllowed()                          │ │
│  │  - filterAllowedEntityTypes()                        │ │
│  └──────────────────────────────────────────────────────┘ │
└───────────────────────┬───────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│  AI Infrastructure Framework (Library)                     │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐ │
│  │  RAGOrchestrator                                      │ │
│  │  - Entry point for all queries                       │ │
│  │  - Calls AIAccessControlService                      │ │
│  └──────────────────────────────────────────────────────┘ │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐ │
│  │  AIAccessControlService                              │ │
│  │  - Framework service                                  │ │
│  │  - Delegates to EntityAccessPolicy                   │ │
│  │  - Fail-closed security                              │ │
│  └──────────────────────────────────────────────────────┘ │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐ │
│  │  ActionHandlerRegistry                                │ │
│  │  - Discovers ActionHandler implementations            │ │
│  │  - Calls validateActionAllowed()                      │ │
│  └──────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### SPI Pattern

The framework uses **Service Provider Interface (SPI)** pattern:

- **Framework defines interfaces** (e.g., `EntityAccessPolicy`, `ActionHandler`)
- **You implement interfaces** in your application
- **Framework discovers and uses** your implementations automatically
- **No circular dependencies** - framework doesn't depend on your code

---

## Access Control Layers

### Layer 1: Orchestrator Level (Framework Entry Point)

**Location**: `RAGOrchestrator.orchestrate()`

**Purpose**: Framework-level access control check before any processing begins.

**When**: Every query that goes through the orchestrator.

**How It Works**:

```java
// In RAGOrchestrator.orchestrate()
AIAccessControlResponse accessResponse = accessControlService.checkAccess(
    AIAccessControlRequest.builder()
        .requestId(requestId)
        .userId(userId)
        .resourceId("rag:intent")  // Framework resource identifier
        .operationType("READ")       // Operation type
        .context(query)            // User query
        .metadata(Map.of("entryPoint", "RAG_ORCHESTRATOR"))
        .timestamp(requestTimestamp)
        .build()
);

if (!Boolean.TRUE.equals(accessResponse.getAccessGranted())) {
    return OrchestrationResult.error("Access denied by policy.");
}
```

**What Gets Checked**:
- Can the user use the RAG orchestrator at all?
- General framework-level permissions
- User authentication status

**Your Implementation**:

```java
@Component
public class MyEntityAccessPolicy implements EntityAccessPolicy {
    
    @Override
    public boolean canUserAccessEntity(String userId, Map<String, Object> entity) {
        String resourceId = (String) entity.get("resourceId");
        String operationType = (String) entity.get("operationType");
        
        // Check if user can use RAG orchestrator
        if ("rag:intent".equals(resourceId) && "READ".equals(operationType)) {
            // Your business logic
            return userService.isUserActive(userId) && 
                   permissionService.hasPermission(userId, "rag:query");
        }
        
        // Handle other resources...
        return false;
    }
}
```

**Benefits**:
- ✅ Early rejection (saves processing time)
- ✅ Framework-level security
- ✅ Consistent across all queries

---

### Layer 2: Action Handler Level (Action-Specific)

**Location**: `ActionHandler.validateActionAllowed()` and `ActionHandler.executeAction()`

**Purpose**: Action-specific access control before executing business logic.

**When**: After intent extraction, before action execution.

**How It Works**:

```java
// In RAGOrchestrator.handleAction()
ActionHandler handler = actionHandlerRegistry.findHandler(actionName);

// Layer 2a: Validate action is allowed
if (!handler.validateActionAllowed(userId)) {
    return OrchestrationResult.builder()
        .type(OrchestrationResultType.ACTION_DENIED)
        .success(false)
        .message("Action not permitted for this user.")
        .build();
}

// Layer 2b: Execute action (may include additional filtering)
ActionResult result = handler.executeAction(params, userId);
```

**Your Implementation**:

```java
@Component
public class RelationshipQueryActionHandler implements ActionHandler {
    
    @Override
    public boolean validateActionAllowed(String userId) {
        // Check if user can execute relationship queries
        return permissionService.hasPermission(userId, "relationship_query:execute");
    }
    
    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        // Additional filtering inside executeAction()
        List<String> entityTypes = extractEntityTypes(params);
        List<String> allowedEntityTypes = filterAllowedEntityTypes(userId, entityTypes);
        
        if (allowedEntityTypes.isEmpty()) {
            return ActionResult.builder()
                .success(false)
                .message("Access denied: No entity types accessible")
                .build();
        }
        
        // Execute query with filtered entity types...
    }
    
    private List<String> filterAllowedEntityTypes(String userId, List<String> requestedTypes) {
        // Your business logic to filter entity types
        return requestedTypes.stream()
            .filter(type -> canUserQueryEntityType(userId, type))
            .toList();
    }
}
```

**Benefits**:
- ✅ Action-specific security
- ✅ Can filter parameters before execution
- ✅ Saves processing (e.g., LLM calls) for denied actions

---

### Layer 3: Entity Type Level (Relationship Queries)

**Location**: `ActionHandler.filterAllowedEntityTypes()` (specifically in relationship query handlers)

**Purpose**: Filter which entity types a user can query before query planning.

**When**: For relationship queries, before LLM query planning.

**How It Works**:

```java
// In RelationshipQueryActionHandler.executeAction()
List<String> requestedEntityTypes = extractEntityTypes(params);
List<String> allowedEntityTypes = filterAllowedEntityTypes(userId, requestedEntityTypes);

// Only send allowed entity types to query planner
// This optimizes token usage (LLM only sees schemas for allowed entities)
RAGResponse response = queryService.execute(query, allowedEntityTypes, options);
```

**Your Implementation**:

```java
private boolean canUserQueryEntityType(String userId, String entityType) {
    // Pattern 1: Role-based
    User user = userService.getUser(userId);
    if (user.getRole().equals("ADMIN")) {
        return true;  // Admins can query all
    }
    return user.getAllowedEntityTypes().contains(entityType);
    
    // Pattern 2: Permission-based
    // return permissionService.hasPermission(userId, "relationship_query:" + entityType);
    
    // Pattern 3: Tenant-based
    // String tenant = tenantService.getTenantId(userId);
    // return tenantMappingService.isEntityTypeAccessible(tenant, entityType);
}
```

**Benefits**:
- ✅ Token efficiency (LLM only sees allowed schemas)
- ✅ Early filtering (before expensive operations)
- ✅ Prevents unauthorized schema exposure

---

### Layer 4: Result Level (Entity-Level Filtering)

**Location**: After query execution, before returning results.

**Purpose**: Final defense-in-depth check on individual results.

**When**: After query execution, for each result entity.

**How It Works**:

```java
// In ActionHandler.executeAction() after query execution
RAGResponse response = queryService.execute(query, allowedEntityTypes, options);

// Filter results based on entity-level access
List<RAGDocument> filteredDocuments = response.getDocuments().stream()
    .filter(doc -> canUserAccessEntity(userId, doc))
    .toList();

private boolean canUserAccessEntity(String userId, RAGDocument document) {
    Map<String, Object> entityContext = Map.of(
        "resourceId", "rag:" + document.getMetadata().get("entityType"),
        "operationType", "READ",
        "entityId", document.getId(),
        "entityType", document.getMetadata().get("entityType")
    );
    
    return entityAccessPolicy.canUserAccessEntity(userId, entityContext);
}
```

**Benefits**:
- ✅ Defense in depth (final check)
- ✅ Row-level security (multi-tenant, data ownership)
- ✅ Handles edge cases (e.g., entities created after query planning)

---

## SPI Interfaces

### EntityAccessPolicy

**Location**: `com.ai.infrastructure.access.policy.EntityAccessPolicy`

**Purpose**: Framework hook for entity-level access control decisions.

**Interface**:

```java
@FunctionalInterface
public interface EntityAccessPolicy {
    
    /**
     * Determine whether a user can access the supplied entity.
     *
     * @param userId identifier of the user making the request
     * @param entity immutable view of the entity metadata for the access decision
     * @return {@code true} when access should be granted, {@code false} otherwise
     */
    boolean canUserAccessEntity(String userId, Map<String, Object> entity);
    
    /**
     * Optional callback invoked when access is denied.
     * Override to emit custom audit records or telemetry.
     */
    default void logAccessDenied(String userId, Map<String, Object> entity, String reason) {
        // Default: no-op (you can override)
    }
}
```

**Entity Context Map**:

The `entity` parameter contains:

```java
{
    "resourceId": "rag:intent",           // Framework resource identifier
    "operationType": "READ",             // Operation: READ, WRITE, DELETE, etc.
    "timestamp": LocalDateTime,            // When the check is happening
    "context": "user query text",         // Optional: query context
    "purpose": "intent_extraction",       // Optional: purpose of access
    "metadata": Map<String, Object>,      // Optional: additional metadata
    "userAttributes": Map<String, Object> // Optional: user attributes
}
```

**Implementation Example**:

```java
@Component
@RequiredArgsConstructor
public class MyEntityAccessPolicy implements EntityAccessPolicy {
    
    private final UserService userService;
    private final PermissionService permissionService;
    private final TenantService tenantService;
    
    @Override
    public boolean canUserAccessEntity(String userId, Map<String, Object> entity) {
        String resourceId = (String) entity.get("resourceId");
        String operationType = (String) entity.get("operationType");
        
        // Framework-level checks
        if ("rag:intent".equals(resourceId)) {
            return canUserUseRAG(userId);
        }
        
        // Entity-level checks
        if (resourceId != null && resourceId.startsWith("rag:")) {
            String entityType = resourceId.substring(4); // Remove "rag:" prefix
            String entityId = (String) entity.get("entityId");
            
            return canUserAccessEntityType(userId, entityType, entityId);
        }
        
        // Default: deny
        return false;
    }
    
    private boolean canUserUseRAG(String userId) {
        User user = userService.getUser(userId);
        return user != null && user.isActive() && 
               permissionService.hasPermission(userId, "rag:query");
    }
    
    private boolean canUserAccessEntityType(String userId, String entityType, String entityId) {
        // Multi-tenant check
        String userTenant = tenantService.getTenantId(userId);
        String entityTenant = getEntityTenant(entityType, entityId);
        if (!userTenant.equals(entityTenant)) {
            return false;  // Tenant isolation
        }
        
        // Permission check
        return permissionService.hasPermission(userId, "rag:" + entityType + ":read");
    }
    
    @Override
    public void logAccessDenied(String userId, Map<String, Object> entity, String reason) {
        // Custom audit logging
        auditService.logAccessDenial(userId, entity, reason);
    }
}
```

**Fail-Closed Security**:

If your `EntityAccessPolicy` throws an exception, the framework:
- ✅ **Denies access** (fail-closed)
- ✅ Logs a warning
- ✅ Returns `accessGranted = false` in response

```java
// In AIAccessControlService
private Decision evaluateAccess(EntityAccessPolicy policy, String userId, Map<String, Object> entityContext) {
    try {
        boolean granted = policy.canUserAccessEntity(userId, Collections.unmodifiableMap(entityContext));
        return new Decision(granted, false, null);
    } catch (Exception ex) {
        log.warn("EntityAccessPolicy threw an exception for user {}: {}", userId, ex.getMessage());
        return new Decision(false, true, ex.getMessage());  // ← DENY on exception
    }
}
```

---

### ActionHandler

**Location**: `com.ai.infrastructure.intent.action.ActionHandler`

**Purpose**: Handle specific actions detected by the LLM.

**Interface**:

```java
public interface ActionHandler {
    
    /**
     * @return metadata describing the action handled by this component.
     */
    AIActionMetaData getActionMetadata();
    
    /**
     * Validate whether the current user may perform the action.
     *
     * @param userId identifier for the current user
     * @return {@code true} when the action is allowed, otherwise {@code false}
     */
    boolean validateActionAllowed(String userId);
    
    /**
     * Resolve the confirmation message presented to the user.
     */
    String getConfirmationMessage(Map<String, Object> params);
    
    /**
     * Execute the business logic associated with the action.
     */
    ActionResult executeAction(Map<String, Object> params, String userId);
    
    /**
     * Fallback invoked when executeAction() raises an exception.
     */
    ActionResult handleError(Exception e, String userId);
}
```

**Implementation Example**:

```java
@Component
@RequiredArgsConstructor
public class RelationshipQueryActionHandler implements ActionHandler {
    
    private final ReliableRelationshipQueryService queryService;
    private final PermissionService permissionService;
    private final EntityAccessPolicy entityAccessPolicy;
    
    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name("relationship_query")
            .description("Execute natural language queries against relational data")
            .category("data_query")
            .build();
    }
    
    @Override
    public boolean validateActionAllowed(String userId) {
        // Layer 2: Action-level check
        return permissionService.hasPermission(userId, "relationship_query:execute");
    }
    
    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        // Extract parameters
        String query = extractQuery(params);
        List<String> entityTypes = extractEntityTypes(params);
        
        // Layer 3: Entity type filtering
        List<String> allowedEntityTypes = filterAllowedEntityTypes(userId, entityTypes);
        if (allowedEntityTypes.isEmpty()) {
            return ActionResult.builder()
                .success(false)
                .message("Access denied: No entity types accessible")
                .build();
        }
        
        // Execute query
        RAGResponse response = queryService.execute(query, allowedEntityTypes, options);
        
        // Layer 4: Result-level filtering
        List<RAGDocument> filtered = response.getDocuments().stream()
            .filter(doc -> canUserAccessEntity(userId, doc))
            .toList();
        
        return ActionResult.builder()
            .success(true)
            .data(buildResultData(filtered))
            .build();
    }
    
    private List<String> filterAllowedEntityTypes(String userId, List<String> requestedTypes) {
        return requestedTypes.stream()
            .filter(type -> canUserQueryEntityType(userId, type))
            .toList();
    }
    
    private boolean canUserQueryEntityType(String userId, String entityType) {
        return permissionService.hasPermission(userId, "relationship_query:" + entityType);
    }
    
    private boolean canUserAccessEntity(String userId, RAGDocument doc) {
        Map<String, Object> entityContext = Map.of(
            "resourceId", "rag:" + doc.getMetadata().get("entityType"),
            "operationType", "READ",
            "entityId", doc.getId()
        );
        return entityAccessPolicy.canUserAccessEntity(userId, entityContext);
    }
}
```

**Auto-Discovery**:

The framework automatically discovers `ActionHandler` implementations via Spring's dependency injection:

```java
// In ActionHandlerRegistry
public ActionHandlerRegistry(List<ActionHandler> allHandlers) {
    this.handlers = allHandlers.stream()
        .filter(handler -> handler.getActionMetadata() != null)
        .filter(handler -> handler.getActionMetadata().getName() != null)
        .collect(Collectors.toMap(
            handler -> handler.getActionMetadata().getName(),
            Function.identity()
        ));
}
```

---

## Implementation Patterns

### Pattern 1: Role-Based Access Control

**Use Case**: Different user roles have different access levels.

**Example**:

```java
@Component
@RequiredArgsConstructor
public class RoleBasedAccessPolicy implements EntityAccessPolicy {
    
    private final UserService userService;
    
    @Override
    public boolean canUserAccessEntity(String userId, Map<String, Object> entity) {
        User user = userService.getUser(userId);
        if (user == null) {
            return false;
        }
        
        String resourceId = (String) entity.get("resourceId");
        String operationType = (String) entity.get("operationType");
        
        // Admins can access everything
        if (user.getRole().equals("ADMIN")) {
            return true;
        }
        
        // Analysts can read but not write
        if (user.getRole().equals("ANALYST")) {
            return "READ".equals(operationType);
        }
        
        // Regular users have limited access
        if (user.getRole().equals("USER")) {
            return canRegularUserAccess(resourceId, operationType);
        }
        
        return false;
    }
    
    private boolean canRegularUserAccess(String resourceId, String operationType) {
        // Regular users can only read their own data
        return "READ".equals(operationType) && 
               (resourceId.startsWith("rag:user:") || resourceId.equals("rag:intent"));
    }
}
```

---

### Pattern 2: Permission-Based Access Control

**Use Case**: Fine-grained permissions for specific resources and operations.

**Example**:

```java
@Component
@RequiredArgsConstructor
public class PermissionBasedAccessPolicy implements EntityAccessPolicy {
    
    private final PermissionService permissionService;
    
    @Override
    public boolean canUserAccessEntity(String userId, Map<String, Object> entity) {
        String resourceId = (String) entity.get("resourceId");
        String operationType = (String) entity.get("operationType");
        
        // Build permission string: "rag:customer:read"
        String permission = buildPermission(resourceId, operationType);
        
        return permissionService.hasPermission(userId, permission);
    }
    
    private String buildPermission(String resourceId, String operationType) {
        if (resourceId == null) {
            return null;
        }
        
        // "rag:customer" -> "rag:customer:read"
        if (resourceId.startsWith("rag:")) {
            String entityType = resourceId.substring(4);
            return "rag:" + entityType + ":" + operationType.toLowerCase();
        }
        
        return resourceId + ":" + operationType.toLowerCase();
    }
}
```

**Permission Structure**:

```
User Permissions:
- user-123: ["rag:customer:read", "rag:order:read", "rag:product:read"]
- user-456: ["rag:customer:read", "rag:customer:write", "rag:order:read"]
- admin-789: ["rag:*:*"]  // Wildcard = all permissions
```

---

### Pattern 3: Tenant-Based Access Control

**Use Case**: Multi-tenant applications where users can only access their tenant's data.

**Example**:

```java
@Component
@RequiredArgsConstructor
public class TenantBasedAccessPolicy implements EntityAccessPolicy {
    
    private final TenantService tenantService;
    private final EntityTenantMappingService tenantMapping;
    
    @Override
    public boolean canUserAccessEntity(String userId, Map<String, Object> entity) {
        String userTenant = tenantService.getTenantId(userId);
        if (userTenant == null) {
            return false;  // User has no tenant
        }
        
        String resourceId = (String) entity.get("resourceId");
        String entityId = (String) entity.get("entityId");
        
        // Framework-level resources (no tenant isolation)
        if ("rag:intent".equals(resourceId)) {
            return true;  // All tenants can use RAG orchestrator
        }
        
        // Entity-level resources (tenant isolation)
        if (resourceId != null && resourceId.startsWith("rag:")) {
            String entityType = resourceId.substring(4);
            String entityTenant = tenantMapping.getTenantId(entityType, entityId);
            
            return userTenant.equals(entityTenant);  // Must match tenant
        }
        
        return false;
    }
}
```

---

### Pattern 4: Data Classification-Based Access

**Use Case**: Different data classifications require different permission levels.

**Example**:

```java
@Component
@RequiredArgsConstructor
public class ClassificationBasedAccessPolicy implements EntityAccessPolicy {
    
    private final EntityClassificationService classificationService;
    private final PermissionService permissionService;
    
    @Override
    public boolean canUserAccessEntity(String userId, Map<String, Object> entity) {
        String resourceId = (String) entity.get("resourceId");
        
        if (resourceId == null || !resourceId.startsWith("rag:")) {
            return false;
        }
        
        String entityType = resourceId.substring(4);
        EntityClassification classification = classificationService.getClassification(entityType);
        
        switch (classification) {
            case PUBLIC:
                // Everyone can access public entities
                return true;
                
            case INTERNAL:
                // Authenticated users can access internal entities
                return userId != null;
                
            case SENSITIVE:
                // Requires special permission
                return permissionService.hasPermission(userId, "rag:sensitive:read");
                
            case RESTRICTED:
                // Requires admin role
                return permissionService.hasRole(userId, "ADMIN");
                
            default:
                return false;
        }
    }
}
```

---

### Pattern 5: Hybrid Access Control (Recommended)

**Use Case**: Combine multiple access control strategies for comprehensive security.

**Example**:

```java
@Component
@RequiredArgsConstructor
public class HybridAccessPolicy implements EntityAccessPolicy {
    
    private final UserService userService;
    private final PermissionService permissionService;
    private final TenantService tenantService;
    private final EntityClassificationService classificationService;
    
    @Override
    public boolean canUserAccessEntity(String userId, Map<String, Object> entity) {
        // Step 1: Get user
        User user = userService.getUser(userId);
        if (user == null || !user.isActive()) {
            return false;
        }
        
        // Step 2: Role-based check (admins bypass other checks)
        if (user.getRole().equals("ADMIN")) {
            return true;  // Admins can access everything
        }
        
        String resourceId = (String) entity.get("resourceId");
        String operationType = (String) entity.get("operationType");
        String entityId = (String) entity.get("entityId");
        
        // Step 3: Framework-level resources
        if ("rag:intent".equals(resourceId)) {
            return permissionService.hasPermission(userId, "rag:query");
        }
        
        // Step 4: Entity-level resources
        if (resourceId != null && resourceId.startsWith("rag:")) {
            String entityType = resourceId.substring(4);
            
            // Step 4a: Data classification check
            EntityClassification classification = classificationService.getClassification(entityType);
            if (classification == EntityClassification.RESTRICTED) {
                return false;  // Restricted entities require admin
            }
            
            // Step 4b: Tenant isolation check
            String userTenant = tenantService.getTenantId(userId);
            String entityTenant = getEntityTenant(entityType, entityId);
            if (!userTenant.equals(entityTenant)) {
                return false;  // Tenant mismatch
            }
            
            // Step 4c: Permission check
            String permission = "rag:" + entityType + ":" + operationType.toLowerCase();
            if (permissionService.hasPermission(userId, permission)) {
                return true;
            }
            
            // Step 4d: Role-based fallback
            if (user.getRole().equals("ANALYST") && "READ".equals(operationType)) {
                return true;  // Analysts can read all entities in their tenant
            }
        }
        
        // Default: deny
        return false;
    }
}
```

---

## Configuration

### Enable Access Control

```yaml
ai:
  infrastructure:
    access:
      # Access control is enabled by default when EntityAccessPolicy bean is present
      # No explicit configuration needed
```

### Disable Access Control (Not Recommended)

If you don't provide an `EntityAccessPolicy` bean, the framework will throw an exception:

```
IllegalStateException: No EntityAccessPolicy bean available. 
Register a bean implementing EntityAccessPolicy to evaluate access decisions.
```

**To disable (not recommended for production)**:

```java
@Configuration
public class AccessControlConfig {
    
    @Bean
    @Primary
    public EntityAccessPolicy permissiveAccessPolicy() {
        return (userId, entity) -> true;  // Allow all (NOT RECOMMENDED)
    }
}
```

### Relationship Query Access Control

```yaml
ai:
  infrastructure:
    relationship:
      enabled: true
      enable-orchestrator-integration: true
      
      # Access control settings
      enable-entity-type-filtering: true  # Filter entity types in handler
      require-explicit-entity-types: false  # If true, empty entityTypes = deny
```

---

## Integration Guide

### Step 1: Add Dependency

```xml
<dependency>
    <groupId>com.ai.fabric</groupId>
    <artifactId>ai-infrastructure-core</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Step 2: Implement EntityAccessPolicy

```java
@Component
@RequiredArgsConstructor
public class MyEntityAccessPolicy implements EntityAccessPolicy {
    
    private final YourUserService userService;
    private final YourPermissionService permissionService;
    
    @Override
    public boolean canUserAccessEntity(String userId, Map<String, Object> entity) {
        // Your business logic here
        return permissionService.hasPermission(userId, "rag:query");
    }
    
    @Override
    public void logAccessDenied(String userId, Map<String, Object> entity, String reason) {
        // Optional: Custom audit logging
        log.warn("Access denied: userId={}, resourceId={}, reason={}", 
            userId, entity.get("resourceId"), reason);
    }
}
```

### Step 3: Implement ActionHandler (Optional)

```java
@Component
@RequiredArgsConstructor
public class MyActionHandler implements ActionHandler {
    
    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name("my_action")
            .description("My custom action")
            .build();
    }
    
    @Override
    public boolean validateActionAllowed(String userId) {
        // Your business logic
        return true;
    }
    
    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        // Your business logic
        return ActionResult.builder()
            .success(true)
            .message("Action executed")
            .build();
    }
    
    // ... other methods
}
```

### Step 4: Use RAGOrchestrator

```java
@Service
@RequiredArgsConstructor
public class MyQueryService {
    
    private final RAGOrchestrator orchestrator;
    
    public OrchestrationResult query(String userQuery, String userId) {
        // Access control is automatically enforced
        return orchestrator.orchestrate(userQuery, userId);
    }
}
```

---

## Best Practices

### 1. Fail-Closed Security

✅ **DO**: Return `false` when unsure
```java
@Override
public boolean canUserAccessEntity(String userId, Map<String, Object> entity) {
    try {
        // Your logic
        return checkAccess(userId, entity);
    } catch (Exception e) {
        log.error("Access check failed", e);
        return false;  // ← Fail closed
    }
}
```

❌ **DON'T**: Return `true` when unsure
```java
// BAD: Fail open
catch (Exception e) {
    return true;  // ← Security risk!
}
```

### 2. Log Access Denials

✅ **DO**: Log all access denials for audit
```java
@Override
public void logAccessDenied(String userId, Map<String, Object> entity, String reason) {
    auditService.logAccessDenial(userId, entity, reason);
}
```

### 3. Cache Permissions (Carefully)

✅ **DO**: Cache permissions with TTL
```java
@Cacheable(value = "permissions", key = "#userId + ':' + #permission")
public boolean hasPermission(String userId, String permission) {
    // Check permission
}
```

⚠️ **WARNING**: Invalidate cache when permissions change!

### 4. Validate Early

✅ **DO**: Filter entity types before expensive operations
```java
// Filter before LLM call (saves tokens)
List<String> allowedTypes = filterAllowedEntityTypes(userId, requestedTypes);
queryService.execute(query, allowedTypes, options);
```

### 5. Defense in Depth

✅ **DO**: Use multiple layers
- Layer 1: Orchestrator level
- Layer 2: Action handler level
- Layer 3: Entity type level
- Layer 4: Result level

### 6. Clear Error Messages

✅ **DO**: Tell users why access was denied
```java
return ActionResult.builder()
    .success(false)
    .message("Access denied: You don't have permission to query 'customer' entities")
    .build();
```

---

## Security Model

### Fail-Closed Principle

The framework implements **fail-closed security**:

- ✅ If `EntityAccessPolicy` throws exception → **DENY**
- ✅ If `EntityAccessPolicy` returns `false` → **DENY**
- ✅ If `EntityAccessPolicy` is not provided → **Exception** (forces implementation)
- ✅ If `validateActionAllowed()` returns `false` → **DENY**

### Immutable Context

The entity context passed to `EntityAccessPolicy` is **immutable**:

```java
// Framework ensures immutability
policy.canUserAccessEntity(userId, Collections.unmodifiableMap(entityContext));
```

### No Default Permissions

The framework does **not** grant any default permissions. You must explicitly implement access control.

---

## Examples

### Example 1: Simple Permission Check

```java
@Component
public class SimpleAccessPolicy implements EntityAccessPolicy {
    
    @Override
    public boolean canUserAccessEntity(String userId, Map<String, Object> entity) {
        // Simple: All authenticated users can use RAG
        return userId != null && !userId.isBlank();
    }
}
```

### Example 2: Multi-Tenant SaaS Application

```java
@Component
@RequiredArgsConstructor
public class SaaSAccessPolicy implements EntityAccessPolicy {
    
    private final TenantService tenantService;
    private final EntityRepository entityRepository;
    
    @Override
    public boolean canUserAccessEntity(String userId, Map<String, Object> entity) {
        String userTenant = tenantService.getTenantId(userId);
        String resourceId = (String) entity.get("resourceId");
        String entityId = (String) entity.get("entityId");
        
        // Framework access
        if ("rag:intent".equals(resourceId)) {
            return true;  // All tenants can use orchestrator
        }
        
        // Entity access (tenant isolation)
        if (resourceId != null && resourceId.startsWith("rag:")) {
            String entityType = resourceId.substring(4);
            Object entityObj = entityRepository.findById(entityType, entityId);
            
            if (entityObj == null) {
                return false;
            }
            
            String entityTenant = extractTenant(entityObj);
            return userTenant.equals(entityTenant);
        }
        
        return false;
    }
}
```

### Example 3: Enterprise with Roles and Permissions

```java
@Component
@RequiredArgsConstructor
public class EnterpriseAccessPolicy implements EntityAccessPolicy {
    
    private final UserService userService;
    private final PermissionService permissionService;
    private final RoleService roleService;
    
    @Override
    public boolean canUserAccessEntity(String userId, Map<String, Object> entity) {
        User user = userService.getUser(userId);
        if (user == null) {
            return false;
        }
        
        String resourceId = (String) entity.get("resourceId");
        String operationType = (String) entity.get("operationType");
        
        // Admin role bypass
        if (roleService.hasRole(userId, "ADMIN")) {
            return true;
        }
        
        // Permission check
        String permission = buildPermission(resourceId, operationType);
        if (permissionService.hasPermission(userId, permission)) {
            return true;
        }
        
        // Role-based fallback
        if (roleService.hasRole(userId, "ANALYST") && "READ".equals(operationType)) {
            return true;  // Analysts can read
        }
        
        return false;
    }
}
```

---

## Troubleshooting

### Issue: "No EntityAccessPolicy bean available"

**Problem**: Framework can't find your `EntityAccessPolicy` implementation.

**Solution**:
1. Ensure your class is annotated with `@Component` or `@Service`
2. Ensure it's in a package scanned by Spring (`@ComponentScan`)
3. Ensure it implements `EntityAccessPolicy` interface

```java
@Component  // ← Must be a Spring component
public class MyAccessPolicy implements EntityAccessPolicy {
    // ...
}
```

### Issue: Access always denied

**Problem**: Your `EntityAccessPolicy` always returns `false`.

**Solution**:
1. Check your business logic
2. Add logging to debug
3. Verify user permissions in your system

```java
@Override
public boolean canUserAccessEntity(String userId, Map<String, Object> entity) {
    log.debug("Checking access: userId={}, resourceId={}", 
        userId, entity.get("resourceId"));
    
    boolean granted = yourLogic(userId, entity);
    
    log.debug("Access decision: {}", granted);
    return granted;
}
```

### Issue: Performance problems

**Problem**: Access control checks are slow.

**Solution**:
1. Cache permissions (with TTL)
2. Use database indexes for permission lookups
3. Consider async permission checks for non-critical paths

```java
@Cacheable(value = "permissions", key = "#userId + ':' + #permission")
public boolean hasPermission(String userId, String permission) {
    // Expensive lookup
}
```

---

## Summary

The AI Infrastructure Framework provides **comprehensive, multi-layered access control**:

1. **Layer 1**: Orchestrator level (framework entry point)
2. **Layer 2**: Action handler level (action-specific)
3. **Layer 3**: Entity type level (relationship queries)
4. **Layer 4**: Result level (entity-level filtering)

**Key Features**:
- ✅ Fail-closed security
- ✅ SPI pattern (you implement, framework uses)
- ✅ Defense in depth
- ✅ Flexible implementation patterns
- ✅ Open-source friendly

**Next Steps**:
1. Implement `EntityAccessPolicy` for your application
2. Implement `ActionHandler` for custom actions (optional)
3. Configure access control settings
4. Test with your user/permission system

---

**Last Updated**: 2025-12-30  
**Framework Version**: 1.0.0  
**License**: Open Source

