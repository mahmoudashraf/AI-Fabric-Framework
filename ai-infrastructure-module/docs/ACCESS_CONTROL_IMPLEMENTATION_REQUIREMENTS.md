# Access Control Implementation Requirements

> **How many access control implementations do users need to provide?**  
> Clear guide to minimum, recommended, and optional implementations

---

## 📋 Quick Answer

**Minimum Required:** **1 implementation** (`EntityAccessPolicy`)

**Recommended:** **1-2 implementations** (depending on usage)

**Optional:** **0-3 additional implementations** (for advanced features)

---

## 🎯 Implementation Scenarios

### Scenario 1: Minimum Implementation (Required)

**What You Need:**
- ✅ **1 Implementation**: `EntityAccessPolicy`

**Why:**
- Framework requires `EntityAccessPolicy` bean (throws exception if not provided)
- Used at Layer 1 (Orchestrator) - framework entry point
- Used at Layer 4 (Results) - entity-level filtering

**Code:**

```java
@Component
public class MyEntityAccessPolicy implements EntityAccessPolicy {
    
    @Override
    public boolean canUserAccessEntity(String userId, Map<String, Object> entity) {
        // Your business logic
        String resourceId = (String) entity.get("resourceId");
        
        // Framework-level check
        if ("rag:intent".equals(resourceId)) {
            return canUserUseRAG(userId);
        }
        
        // Entity-level check
        if (resourceId != null && resourceId.startsWith("rag:")) {
            return canUserAccessEntityType(userId, resourceId);
        }
        
        return false;
    }
}
```

**What This Covers:**
- ✅ Layer 1: Orchestrator access control
- ✅ Layer 4: Result-level filtering (if you implement it)
- ⚠️ Layer 2: Action handler validation (if you implement custom actions)
- ❌ Layer 3: Entity type filtering (not covered - uses defaults)

---

### Scenario 2: Using Framework-Provided Relationship Query Handler

**What You Need:**
- ✅ **1 Implementation**: `EntityAccessPolicy` (required)
- ✅ **1 Implementation**: `RelationshipQueryAccessControlPolicy` (recommended)

**Why:**
- `EntityAccessPolicy`: Required for framework-level checks
- `RelationshipQueryAccessControlPolicy`: Recommended for type-safe, action-specific access control

**Code:**

```java
// 1. EntityAccessPolicy (Required)
@Component
public class MyEntityAccessPolicy implements EntityAccessPolicy {
    @Override
    public boolean canUserAccessEntity(String userId, Map<String, Object> entity) {
        // Framework-level and entity-level checks
        return yourBusinessLogic(userId, entity);
    }
}

// 2. RelationshipQueryAccessControlPolicy (Recommended)
@Component
public class MyRelationshipQueryAccessControlPolicy 
        implements RelationshipQueryAccessControlPolicy {
    
    @Override
    public boolean canUserExecuteRelationshipQueries(String userId) {
        return permissionService.hasPermission(userId, "relationship_query:execute");
    }
    
    @Override
    public boolean canUserQueryEntityType(String userId, String entityType) {
        return permissionService.hasPermission(userId, "relationship_query:" + entityType);
    }
    
    @Override
    public List<String> getAllowedEntityTypesForUser(String userId) {
        return permissionService.getEntityTypesWithPermission(userId, "relationship_query:");
    }
}
```

**What This Covers:**
- ✅ Layer 1: Orchestrator access control (`EntityAccessPolicy`)
- ✅ Layer 2: Relationship query action validation (`RelationshipQueryAccessControlPolicy`)
- ✅ Layer 3: Entity type filtering (`RelationshipQueryAccessControlPolicy`)
- ✅ Layer 4: Result-level filtering (can use either policy)

**Total: 2 implementations**

---

### Scenario 3: Custom Action Handlers

**What You Need:**
- ✅ **1 Implementation**: `EntityAccessPolicy` (required)
- ✅ **N Implementations**: `ActionHandler` (one per custom action)

**Why:**
- `EntityAccessPolicy`: Required for framework-level checks
- `ActionHandler`: Required for each custom action you implement

**Code:**

```java
// 1. EntityAccessPolicy (Required)
@Component
public class MyEntityAccessPolicy implements EntityAccessPolicy {
    // ... same as Scenario 1
}

// 2. Custom Action Handler (Required for each custom action)
@Component
public class CancelSubscriptionActionHandler implements ActionHandler {
    
    @Override
    public boolean validateActionAllowed(String userId) {
        // Your business logic
        return permissionService.hasPermission(userId, "subscription:cancel");
    }
    
    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        // Your business logic
    }
    
    // ... other methods
}

// 3. Another Custom Action Handler
@Component
public class RequestRefundActionHandler implements ActionHandler {
    
    @Override
    public boolean validateActionAllowed(String userId) {
        return permissionService.hasPermission(userId, "order:refund");
    }
    
    // ... other methods
}
```

**What This Covers:**
- ✅ Layer 1: Orchestrator access control (`EntityAccessPolicy`)
- ✅ Layer 2: Custom action validation (`ActionHandler.validateActionAllowed()`)
- ⚠️ Layer 3: Entity type filtering (not applicable for custom actions)
- ⚠️ Layer 4: Result-level filtering (implement in `executeAction()` if needed)

**Total: 1 + N implementations** (where N = number of custom actions)

---

### Scenario 4: Complete Implementation (All Features)

**What You Need:**
- ✅ **1 Implementation**: `EntityAccessPolicy` (required)
- ✅ **1 Implementation**: `RelationshipQueryAccessControlPolicy` (recommended)
- ✅ **N Implementations**: `ActionHandler` (for custom actions)

**Code:**

```java
// 1. EntityAccessPolicy (Required)
@Component
public class MyEntityAccessPolicy implements EntityAccessPolicy {
    // Framework-level and entity-level checks
}

// 2. RelationshipQueryAccessControlPolicy (Recommended)
@Component
public class MyRelationshipQueryAccessControlPolicy 
        implements RelationshipQueryAccessControlPolicy {
    // Relationship query-specific access control
}

// 3. Custom Action Handlers (As needed)
@Component
public class MyCustomActionHandler implements ActionHandler {
    // Custom action access control
}
```

**What This Covers:**
- ✅ All 4 layers of access control
- ✅ Framework-provided handlers (relationship queries)
- ✅ Custom action handlers
- ✅ Complete defense in depth

**Total: 2 + N implementations** (where N = number of custom actions)

---

## 📊 Implementation Count Summary

| Scenario | EntityAccessPolicy | RelationshipQueryAccessControlPolicy | ActionHandler | Total |
|----------|-------------------|--------------------------------------|---------------|-------|
| **Minimum** | ✅ 1 (required) | ❌ 0 | ❌ 0 | **1** |
| **Using Framework Handler** | ✅ 1 (required) | ✅ 1 (recommended) | ❌ 0 | **2** |
| **Custom Actions Only** | ✅ 1 (required) | ❌ 0 | ✅ N (one per action) | **1 + N** |
| **Complete** | ✅ 1 (required) | ✅ 1 (recommended) | ✅ N (one per action) | **2 + N** |

---

## 🎯 Detailed Breakdown

### Required Implementation

#### 1. EntityAccessPolicy (Always Required)

**Why Required:**
- Framework throws `IllegalStateException` if not provided
- Used at Layer 1 (Orchestrator) - every query goes through this
- Used at Layer 4 (Results) - entity-level filtering

**What Happens If Not Provided:**

```java
// In AIAccessControlService
private EntityAccessPolicy requirePolicy() {
    if (entityAccessPolicy == null) {
        throw new IllegalStateException("""
            No EntityAccessPolicy bean available. Register a bean implementing \
            com.ai.infrastructure.access.policy.EntityAccessPolicy to evaluate access decisions.""");
    }
    return entityAccessPolicy;
}
```

**Minimum Implementation:**

```java
@Component
public class MyEntityAccessPolicy implements EntityAccessPolicy {
    
    @Override
    public boolean canUserAccessEntity(String userId, Map<String, Object> entity) {
        // Minimum: Allow all authenticated users
        return userId != null && !userId.isBlank();
    }
}
```

**Recommended Implementation:**

```java
@Component
@RequiredArgsConstructor
public class MyEntityAccessPolicy implements EntityAccessPolicy {
    
    private final PermissionService permissionService;
    
    @Override
    public boolean canUserAccessEntity(String userId, Map<String, Object> entity) {
        String resourceId = (String) entity.get("resourceId");
        String operationType = (String) entity.get("operationType");
        
        // Framework-level check
        if ("rag:intent".equals(resourceId)) {
            return permissionService.hasPermission(userId, "rag:query");
        }
        
        // Entity-level check
        if (resourceId != null && resourceId.startsWith("rag:")) {
            String entityType = resourceId.substring(4);
            String permission = "rag:" + entityType + ":" + operationType.toLowerCase();
            return permissionService.hasPermission(userId, permission);
        }
        
        return false;
    }
}
```

---

### Recommended Implementation

#### 2. RelationshipQueryAccessControlPolicy (Recommended for Relationship Queries)

**Why Recommended:**
- Type-safe methods (not Map-based)
- Action-specific (clearer intent)
- Better performance (no Map creation)
- Easier to document and understand

**What Happens If Not Provided:**
- Framework falls back to `EntityAccessPolicy`
- Uses generic Map-based checks
- Works, but less type-safe

**Implementation:**

```java
@Component
@RequiredArgsConstructor
public class MyRelationshipQueryAccessControlPolicy 
        implements RelationshipQueryAccessControlPolicy {
    
    private final PermissionService permissionService;
    
    @Override
    public boolean canUserExecuteRelationshipQueries(String userId) {
        return permissionService.hasPermission(userId, "relationship_query:execute");
    }
    
    @Override
    public boolean canUserQueryEntityType(String userId, String entityType) {
        return permissionService.hasPermission(userId, "relationship_query:" + entityType);
    }
    
    @Override
    public List<String> getAllowedEntityTypesForUser(String userId) {
        return permissionService.getEntityTypesWithPermission(userId, "relationship_query:");
    }
}
```

---

### Optional Implementations

#### 3. ActionHandler (For Custom Actions)

**Why Optional:**
- Only needed if you implement custom actions
- Framework-provided handlers (like relationship query) don't require this
- Each custom action needs its own handler

**When Required:**
- You want to add custom actions (e.g., "cancel_subscription", "request_refund")
- You need action-specific business logic
- You want to integrate with orchestrator

**Implementation:**

```java
@Component
@RequiredArgsConstructor
public class CancelSubscriptionActionHandler implements ActionHandler {
    
    private final PermissionService permissionService;
    
    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name("cancel_subscription")
            .description("Cancel user subscription")
            .build();
    }
    
    @Override
    public boolean validateActionAllowed(String userId) {
        // Your access control logic
        return permissionService.hasPermission(userId, "subscription:cancel");
    }
    
    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        // Your business logic
        return ActionResult.builder()
            .success(true)
            .message("Subscription cancelled")
            .build();
    }
    
    // ... other methods
}
```

---

## 🎯 Decision Tree

```
Do you use the framework?
│
├─→ NO: 0 implementations needed
│
└─→ YES: Do you use RAGOrchestrator?
    │
    ├─→ NO: 0 implementations needed (direct service usage)
    │
    └─→ YES: 1 implementation required (EntityAccessPolicy)
        │
        ├─→ Do you use relationship queries via orchestrator?
        │   │
        │   ├─→ YES: +1 implementation (RelationshipQueryAccessControlPolicy) ✅ Recommended
        │   │   Total: 2 implementations
        │   │
        │   └─→ NO: Total: 1 implementation
        │
        └─→ Do you implement custom actions?
            │
            ├─→ YES: +N implementations (one ActionHandler per action)
            │   Total: 1 + N implementations
            │
            └─→ NO: Total: 1-2 implementations
```

---

## 📋 Implementation Checklist

### Minimum (Required)

- [ ] **EntityAccessPolicy** - 1 implementation
  - [ ] Implement `canUserAccessEntity()` method
  - [ ] Handle framework-level checks (`rag:intent`)
  - [ ] Handle entity-level checks (`rag:entityType`)
  - [ ] Optional: Implement `logAccessDenied()` for audit

### Recommended (For Relationship Queries)

- [ ] **RelationshipQueryAccessControlPolicy** - 1 implementation
  - [ ] Implement `canUserExecuteRelationshipQueries()`
  - [ ] Implement `canUserQueryEntityType()`
  - [ ] Implement `getAllowedEntityTypesForUser()`
  - [ ] Optional: Implement `canUserAccessEntity()` for row-level security

### Optional (For Custom Actions)

- [ ] **ActionHandler** - N implementations (one per custom action)
  - [ ] Implement `getActionMetadata()`
  - [ ] Implement `validateActionAllowed()`
  - [ ] Implement `executeAction()`
  - [ ] Implement `getConfirmationMessage()`
  - [ ] Implement `handleError()`

---

## 💡 Examples by Use Case

### Use Case 1: Simple Application (Minimum)

**Requirements:**
- Use RAG orchestrator
- No relationship queries
- No custom actions

**Implementations Needed:**
- ✅ 1: `EntityAccessPolicy`

**Code:**

```java
@Component
public class SimpleAccessPolicy implements EntityAccessPolicy {
    @Override
    public boolean canUserAccessEntity(String userId, Map<String, Object> entity) {
        return userId != null;  // Allow all authenticated users
    }
}
```

---

### Use Case 2: Enterprise Application (Recommended)

**Requirements:**
- Use RAG orchestrator
- Use relationship queries
- No custom actions

**Implementations Needed:**
- ✅ 1: `EntityAccessPolicy`
- ✅ 1: `RelationshipQueryAccessControlPolicy`

**Code:**

```java
// 1. EntityAccessPolicy
@Component
@RequiredArgsConstructor
public class EnterpriseAccessPolicy implements EntityAccessPolicy {
    private final PermissionService permissionService;
    
    @Override
    public boolean canUserAccessEntity(String userId, Map<String, Object> entity) {
        String resourceId = (String) entity.get("resourceId");
        if ("rag:intent".equals(resourceId)) {
            return permissionService.hasPermission(userId, "rag:query");
        }
        // ... entity-level checks
        return false;
    }
}

// 2. RelationshipQueryAccessControlPolicy
@Component
@RequiredArgsConstructor
public class EnterpriseRelationshipAccessPolicy 
        implements RelationshipQueryAccessControlPolicy {
    private final PermissionService permissionService;
    
    @Override
    public boolean canUserExecuteRelationshipQueries(String userId) {
        return permissionService.hasPermission(userId, "relationship_query:execute");
    }
    
    @Override
    public boolean canUserQueryEntityType(String userId, String entityType) {
        return permissionService.hasPermission(userId, "relationship_query:" + entityType);
    }
    
    @Override
    public List<String> getAllowedEntityTypesForUser(String userId) {
        return permissionService.getEntityTypesWithPermission(userId, "relationship_query:");
    }
}
```

---

### Use Case 3: Custom Actions Application

**Requirements:**
- Use RAG orchestrator
- Implement custom actions (e.g., "cancel_subscription", "request_refund")
- No relationship queries

**Implementations Needed:**
- ✅ 1: `EntityAccessPolicy`
- ✅ 2: `ActionHandler` (one per custom action)

**Code:**

```java
// 1. EntityAccessPolicy
@Component
public class MyEntityAccessPolicy implements EntityAccessPolicy {
    // ... same as Use Case 1
}

// 2. CancelSubscriptionActionHandler
@Component
public class CancelSubscriptionActionHandler implements ActionHandler {
    @Override
    public boolean validateActionAllowed(String userId) {
        return permissionService.hasPermission(userId, "subscription:cancel");
    }
    // ... other methods
}

// 3. RequestRefundActionHandler
@Component
public class RequestRefundActionHandler implements ActionHandler {
    @Override
    public boolean validateActionAllowed(String userId) {
        return permissionService.hasPermission(userId, "order:refund");
    }
    // ... other methods
}
```

---

### Use Case 4: Complete Application (All Features)

**Requirements:**
- Use RAG orchestrator
- Use relationship queries
- Implement custom actions

**Implementations Needed:**
- ✅ 1: `EntityAccessPolicy`
- ✅ 1: `RelationshipQueryAccessControlPolicy`
- ✅ N: `ActionHandler` (one per custom action)

**Total: 2 + N implementations**

---

## 🎯 Summary Table

| Use Case | EntityAccessPolicy | RelationshipQueryAccessControlPolicy | ActionHandler | Total |
|----------|-------------------|--------------------------------------|---------------|-------|
| **Simple App** | ✅ 1 (required) | ❌ 0 | ❌ 0 | **1** |
| **Enterprise App** | ✅ 1 (required) | ✅ 1 (recommended) | ❌ 0 | **2** |
| **Custom Actions** | ✅ 1 (required) | ❌ 0 | ✅ N (one per action) | **1 + N** |
| **Complete App** | ✅ 1 (required) | ✅ 1 (recommended) | ✅ N (one per action) | **2 + N** |

---

## 🔑 Key Points

1. **Minimum Required:** 1 implementation (`EntityAccessPolicy`)
2. **Recommended:** 2 implementations (if using relationship queries)
3. **Optional:** N additional implementations (for custom actions)
4. **Framework-Provided Handlers:** Don't require `ActionHandler` implementation (they're already implemented)
5. **Fallback Strategy:** Framework provides defaults if optional implementations not provided

---

## 📚 Related Documents

- [`ACCESS_CONTROL_MECHANICS.md`](./ACCESS_CONTROL_MECHANICS.md) - Complete access control guide
- [`ACCESS_CONTROL_UNIFICATION_ANALYSIS.md`](./ACCESS_CONTROL_UNIFICATION_ANALYSIS.md) - Unification analysis
- [`RELATIONSHIP_QUERY_ACCESS_CONTROL_SPI.md`](./semantic-relational-implementation/internal-module/RELATIONSHIP_QUERY_ACCESS_CONTROL_SPI.md) - Relationship query access control

---

**Last Updated:** 2025-12-30  
**Status:** Implementation Guide

