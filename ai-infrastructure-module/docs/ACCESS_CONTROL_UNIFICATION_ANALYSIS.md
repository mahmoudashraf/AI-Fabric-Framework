# Access Control Unification Analysis

> **Should we unify access control across the framework?**  
> Comprehensive analysis of current state, options, and recommendation

---

## 📋 Current State Analysis

### Existing Access Control Mechanisms

```
┌─────────────────────────────────────────────────────────────────────┐
│  CURRENT ACCESS CONTROL INTERFACES                                   │
│  ═══════════════════════════════════════════════════════════════════│
│                                                                      │
│  1. EntityAccessPolicy (ai-infrastructure-core)                     │
│     ├─→ Used at: Layer 1 (Orchestrator), Layer 4 (Results)          │
│     ├─→ Method: canUserAccessEntity(userId, Map<String, Object>)    │
│     ├─→ Purpose: Framework-level and entity-level access control   │
│     └─→ Status: ✅ Implemented                                      │
│                                                                      │
│  2. ActionHandler.validateActionAllowed() (ai-infrastructure-core) │
│     ├─→ Used at: Layer 2 (Action Handler)                           │
│     ├─→ Method: validateActionAllowed(String userId)                │
│     ├─→ Purpose: Action-specific access control                     │
│     └─→ Status: ✅ Implemented                                      │
│                                                                      │
│  3. RelationshipQueryAccessControlPolicy (proposed)                  │
│     ├─→ Used at: Layer 2-4 (Relationship Queries)                   │
│     ├─→ Methods: Multiple (canUserExecute, canUserQueryEntityType)  │
│     ├─→ Purpose: Relationship query-specific access control         │
│     └─→ Status: 🆕 Proposed (not yet implemented)                    │
│                                                                      │
│  4. Custom ActionHandler implementations (user code)                │
│     ├─→ Used at: Layer 2 (Action Handler)                           │
│     ├─→ Methods: validateActionAllowed() + custom logic            │
│     ├─→ Purpose: Action-specific access control                     │
│     └─→ Status: ✅ User implements                                  │
└─────────────────────────────────────────────────────────────────────┘
```

### Current Usage Patterns

#### Pattern 1: EntityAccessPolicy (Generic)

```java
// Layer 1: Orchestrator
EntityAccessPolicy.canUserAccessEntity(userId, {
    "resourceId": "rag:intent",
    "operationType": "READ"
})

// Layer 4: Results
EntityAccessPolicy.canUserAccessEntity(userId, {
    "resourceId": "rag:customer",
    "operationType": "READ",
    "entityId": "customer-123"
})
```

**Pros:**
- ✅ Single interface for framework-level and entity-level checks
- ✅ Consistent pattern
- ✅ Flexible (Map-based context)

**Cons:**
- ❌ Generic (not action-specific)
- ❌ Requires building context Map
- ❌ Less type-safe

#### Pattern 2: ActionHandler.validateActionAllowed() (Action-Specific)

```java
// Layer 2: Action Handler
ActionHandler.validateActionAllowed(userId)
```

**Pros:**
- ✅ Action-specific
- ✅ Simple interface
- ✅ Clear intent

**Cons:**
- ❌ Only checks if action is allowed, not entity types
- ❌ Framework-provided handlers can't be customized
- ❌ No parameter context

#### Pattern 3: RelationshipQueryAccessControlPolicy (Proposed)

```java
// Layer 2-4: Relationship Queries
RelationshipQueryAccessControlPolicy.canUserExecuteRelationshipQueries(userId)
RelationshipQueryAccessControlPolicy.canUserQueryEntityType(userId, entityType)
RelationshipQueryAccessControlPolicy.canUserAccessEntity(userId, entityType, entityId)
```

**Pros:**
- ✅ Relationship query-specific
- ✅ Type-safe methods
- ✅ Clear separation of concerns

**Cons:**
- ❌ New interface (adds complexity)
- ❌ Only for relationship queries
- ❌ Potential duplication with EntityAccessPolicy

---

## 🎯 Options Analysis

### Option 1: Keep Separate Interfaces (Current + Proposed)

**Architecture:**

```
EntityAccessPolicy (generic, framework-level)
  ├─→ Layer 1: Orchestrator
  └─→ Layer 4: Results

ActionHandler.validateActionAllowed() (action-level)
  └─→ Layer 2: Action Handler

RelationshipQueryAccessControlPolicy (relationship-specific)
  ├─→ Layer 2: Relationship Query Action
  ├─→ Layer 3: Entity Type Filtering
  └─→ Layer 4: Result Filtering (optional)
```

**Pros:**
- ✅ **Separation of Concerns**: Each interface has clear purpose
- ✅ **Type Safety**: Specific methods for specific use cases
- ✅ **Flexibility**: Can have action-specific access control
- ✅ **No Breaking Changes**: Existing code continues to work
- ✅ **Clear Documentation**: Each interface is self-documenting

**Cons:**
- ❌ **Multiple Interfaces**: Users need to learn multiple patterns
- ❌ **Potential Duplication**: Some logic might be duplicated
- ❌ **Inconsistency**: Different patterns for different layers
- ❌ **Complexity**: More interfaces to maintain

**Use Cases:**
- ✅ Different actions need different access control logic
- ✅ Relationship queries have specific requirements (entity type filtering)
- ✅ Framework-level checks are different from action-level checks

---

### Option 2: Unify Under EntityAccessPolicy

**Architecture:**

```
EntityAccessPolicy (unified, all layers)
  ├─→ Layer 1: Orchestrator
  ├─→ Layer 2: Action Handler (via resourceId: "action:relationship_query")
  ├─→ Layer 3: Entity Type (via resourceId: "rag:relationship_query:customer")
  └─→ Layer 4: Results (via resourceId: "rag:customer", entityId: "123")
```

**Implementation:**

```java
// Framework handler uses EntityAccessPolicy for everything
@Component
public class RelationshipQueryActionHandler implements ActionHandler {
    
    private final ObjectProvider<EntityAccessPolicy> entityAccessPolicyProvider;
    
    @Override
    public boolean validateActionAllowed(String userId) {
        EntityAccessPolicy policy = entityAccessPolicyProvider.getIfAvailable();
        if (policy == null) return userId != null;
        
        Map<String, Object> context = Map.of(
            "resourceId", "action:relationship_query",
            "operationType", "EXECUTE"
        );
        return policy.canUserAccessEntity(userId, context);
    }
    
    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        EntityAccessPolicy policy = entityAccessPolicyProvider.getIfAvailable();
        
        // Filter entity types
        List<String> entityTypes = extractEntityTypes(params);
        List<String> allowedTypes = entityTypes.stream()
            .filter(type -> {
                if (policy == null) return true;
                Map<String, Object> context = Map.of(
                    "resourceId", "rag:relationship_query:" + type,
                    "operationType", "QUERY"
                );
                return policy.canUserAccessEntity(userId, context);
            })
            .toList();
        
        // Execute query...
        
        // Filter results
        List<RAGDocument> filtered = results.stream()
            .filter(doc -> {
                if (policy == null) return true;
                Map<String, Object> context = Map.of(
                    "resourceId", "rag:" + doc.getMetadata().get("entityType"),
                    "operationType", "READ",
                    "entityId", doc.getId()
                );
                return policy.canUserAccessEntity(userId, context);
            })
            .toList();
        
        return buildResult(filtered);
    }
}
```

**User Implementation:**

```java
@Component
public class MyEntityAccessPolicy implements EntityAccessPolicy {
    
    @Override
    public boolean canUserAccessEntity(String userId, Map<String, Object> entity) {
        String resourceId = (String) entity.get("resourceId");
        String operationType = (String) entity.get("operationType");
        
        // Framework-level (Layer 1)
        if ("rag:intent".equals(resourceId)) {
            return canUserUseRAG(userId);
        }
        
        // Action-level (Layer 2)
        if (resourceId != null && resourceId.startsWith("action:")) {
            String actionName = resourceId.substring(7);
            return canUserExecuteAction(userId, actionName);
        }
        
        // Entity type-level (Layer 3)
        if (resourceId != null && resourceId.startsWith("rag:relationship_query:")) {
            String entityType = resourceId.substring("rag:relationship_query:".length());
            return canUserQueryEntityType(userId, entityType);
        }
        
        // Entity-level (Layer 4)
        if (resourceId != null && resourceId.startsWith("rag:")) {
            String entityType = resourceId.substring(4);
            String entityId = (String) entity.get("entityId");
            return canUserAccessEntity(userId, entityType, entityId);
        }
        
        return false;
    }
}
```

**Pros:**
- ✅ **Single Interface**: One interface to learn and implement
- ✅ **Consistency**: Same pattern across all layers
- ✅ **No New Interfaces**: Reuses existing EntityAccessPolicy
- ✅ **Unified Logic**: All access control in one place
- ✅ **Easier to Understand**: One pattern, not multiple

**Cons:**
- ❌ **Generic Interface**: Less type-safe, requires Map building
- ❌ **String-Based Resource IDs**: Error-prone, no compile-time checking
- ❌ **Complex Context Building**: Need to build Map for each check
- ❌ **Less Action-Specific**: Harder to have action-specific logic
- ❌ **Potential Performance**: Map creation overhead

**Use Cases:**
- ✅ Simple applications with straightforward access control
- ✅ All access control logic is similar
- ✅ Prefer single interface over multiple

---

### Option 3: Hybrid Approach (Recommended)

**Architecture:**

```
EntityAccessPolicy (framework-level, generic)
  ├─→ Layer 1: Orchestrator (framework entry point)
  └─→ Layer 4: Results (entity-level, generic)

ActionHandler.validateActionAllowed() (action-level, required)
  └─→ Layer 2: Action Handler (action-specific)

Optional: Action-Specific Access Control SPI (for framework-provided handlers)
  ├─→ RelationshipQueryAccessControlPolicy (relationship queries)
  └─→ Future: OtherActionAccessControlPolicy (other actions)
```

**Key Principle:**
- **EntityAccessPolicy**: Generic, framework-level, entity-level
- **ActionHandler.validateActionAllowed()**: Action-level, required for all actions
- **Action-Specific SPI**: Optional, for framework-provided handlers that need customization

**Implementation:**

```java
// Framework handler: Use EntityAccessPolicy OR action-specific SPI
@Component
public class RelationshipQueryActionHandler implements ActionHandler {
    
    private final ObjectProvider<EntityAccessPolicy> entityAccessPolicyProvider;
    private final ObjectProvider<RelationshipQueryAccessControlPolicy> relationshipPolicyProvider;
    
    @Override
    public boolean validateActionAllowed(String userId) {
        // Option 1: Use action-specific SPI (preferred)
        RelationshipQueryAccessControlPolicy relationshipPolicy = 
            relationshipPolicyProvider.getIfAvailable();
        if (relationshipPolicy != null) {
            return relationshipPolicy.canUserExecuteRelationshipQueries(userId);
        }
        
        // Option 2: Fallback to EntityAccessPolicy
        EntityAccessPolicy policy = entityAccessPolicyProvider.getIfAvailable();
        if (policy != null) {
            Map<String, Object> context = Map.of(
                "resourceId", "action:relationship_query",
                "operationType", "EXECUTE"
            );
            return policy.canUserAccessEntity(userId, context);
        }
        
        // Option 3: Default (with warning)
        log.warn("No access control policy provided - using permissive default");
        return userId != null && !userId.isBlank();
    }
    
    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        // Prefer action-specific SPI, fallback to EntityAccessPolicy
        RelationshipQueryAccessControlPolicy relationshipPolicy = 
            relationshipPolicyProvider.getIfAvailable();
        EntityAccessPolicy entityPolicy = entityAccessPolicyProvider.getIfAvailable();
        
        // Filter entity types
        List<String> allowedTypes = filterEntityTypes(
            userId, 
            extractEntityTypes(params),
            relationshipPolicy,
            entityPolicy
        );
        
        // Execute and filter results...
    }
    
    private List<String> filterEntityTypes(
            String userId,
            List<String> requestedTypes,
            RelationshipQueryAccessControlPolicy relationshipPolicy,
            EntityAccessPolicy entityPolicy) {
        
        // Prefer action-specific SPI
        if (relationshipPolicy != null) {
            return requestedTypes.stream()
                .filter(type -> relationshipPolicy.canUserQueryEntityType(userId, type))
                .toList();
        }
        
        // Fallback to EntityAccessPolicy
        if (entityPolicy != null) {
            return requestedTypes.stream()
                .filter(type -> {
                    Map<String, Object> context = Map.of(
                        "resourceId", "rag:relationship_query:" + type,
                        "operationType", "QUERY"
                    );
                    return entityPolicy.canUserAccessEntity(userId, context);
                })
                .toList();
        }
        
        // Default: allow all (with warning)
        return requestedTypes;
    }
}
```

**Pros:**
- ✅ **Best of Both Worlds**: Specific when needed, generic as fallback
- ✅ **Backward Compatible**: Works with existing EntityAccessPolicy
- ✅ **Flexible**: Can use action-specific SPI or generic policy
- ✅ **Type Safety**: Action-specific SPI provides type-safe methods
- ✅ **No Breaking Changes**: Existing code continues to work
- ✅ **Future Extensible**: Easy to add action-specific SPIs for other actions

**Cons:**
- ❌ **Slightly More Complex**: Need to handle fallback logic
- ❌ **Multiple Options**: Users need to choose which to implement

**Use Cases:**
- ✅ **Recommended for most applications**
- ✅ Want type-safe, action-specific access control
- ✅ But also want fallback to generic policy
- ✅ Framework provides handlers that need customization

---

## 🏆 Recommendation: Hybrid Approach (Option 3)

### Why Hybrid?

1. **Separation of Concerns**
   - `EntityAccessPolicy`: Generic, framework-level, entity-level
   - `ActionHandler.validateActionAllowed()`: Action-level, required
   - Action-specific SPI: Optional, for framework-provided handlers

2. **Flexibility**
   - Users can implement action-specific SPI for type safety
   - Or use generic `EntityAccessPolicy` for simplicity
   - Framework provides sensible defaults

3. **Backward Compatibility**
   - Existing `EntityAccessPolicy` implementations continue to work
   - No breaking changes
   - Gradual migration path

4. **Type Safety**
   - Action-specific SPI provides type-safe methods
   - Generic policy provides flexibility
   - Users choose based on their needs

5. **Future Extensibility**
   - Easy to add action-specific SPIs for other actions
   - Pattern is established and documented
   - Framework can provide more handlers with SPIs

### Implementation Strategy

#### Phase 1: Current State (Keep As-Is)
- ✅ `EntityAccessPolicy` for Layer 1 and Layer 4
- ✅ `ActionHandler.validateActionAllowed()` for Layer 2
- ✅ User implements both as needed

#### Phase 2: Add Action-Specific SPI (New)
- 🆕 `RelationshipQueryAccessControlPolicy` for relationship queries
- 🆕 Framework handler uses SPI with fallback to `EntityAccessPolicy`
- 🆕 Document pattern for future action-specific SPIs

#### Phase 3: Future Actions (As Needed)
- 🔮 Other action-specific SPIs if needed
- 🔮 Pattern established, easy to add

### Migration Path

**For Existing Users:**
- ✅ No changes required
- ✅ Existing `EntityAccessPolicy` continues to work
- ✅ Can optionally implement action-specific SPI for better type safety

**For New Users:**
- ✅ Can start with `EntityAccessPolicy` (simpler)
- ✅ Can add action-specific SPI later (more type-safe)
- ✅ Framework provides defaults if neither is provided

---

## 📊 Comparison Matrix

| Aspect | Separate Interfaces | Unified (EntityAccessPolicy) | Hybrid (Recommended) |
|--------|---------------------|------------------------------|----------------------|
| **Number of Interfaces** | 3+ | 1 | 2 (with optional SPI) |
| **Type Safety** | ✅ High (specific methods) | ❌ Low (Map-based) | ✅ High (SPI) or Low (fallback) |
| **Flexibility** | ✅ High | ⚠️ Medium | ✅ High |
| **Ease of Use** | ⚠️ Medium (learn multiple) | ✅ High (one interface) | ✅ High (choose your level) |
| **Action-Specific** | ✅ Yes | ❌ No (generic) | ✅ Yes (via SPI) |
| **Backward Compatible** | ✅ Yes | ⚠️ Requires changes | ✅ Yes |
| **Performance** | ✅ Good | ⚠️ Map creation overhead | ✅ Good |
| **Documentation** | ⚠️ Multiple docs | ✅ Single doc | ✅ Clear docs |
| **Future Extensibility** | ✅ Easy | ⚠️ Harder | ✅ Easy |

---

## 🎯 Final Recommendation

### Use Hybrid Approach (Option 3)

**Architecture:**

```
┌─────────────────────────────────────────────────────────────────────┐
│  UNIFIED ACCESS CONTROL ARCHITECTURE (Hybrid)                       │
│  ═══════════════════════════════════════════════════════════════════│
│                                                                      │
│  EntityAccessPolicy (Generic, Framework-Level)                     │
│    ├─→ Layer 1: Orchestrator (required)                            │
│    └─→ Layer 4: Results (optional, can use action-specific SPI)     │
│                                                                      │
│  ActionHandler.validateActionAllowed() (Action-Level, Required)    │
│    └─→ Layer 2: Action Handler (all actions)                        │
│                                                                      │
│  Action-Specific Access Control SPI (Optional, Type-Safe)           │
│    ├─→ RelationshipQueryAccessControlPolicy (relationship queries) │
│    └─→ Future: OtherActionAccessControlPolicy (as needed)           │
│                                                                      │
│  Fallback Strategy:                                                 │
│    Action-Specific SPI → EntityAccessPolicy → Default (with warning)│
└─────────────────────────────────────────────────────────────────────┘
```

**Benefits:**
1. ✅ **Single Generic Interface**: `EntityAccessPolicy` for framework-level and entity-level
2. ✅ **Action-Specific SPI**: Optional, type-safe interfaces for framework-provided handlers
3. ✅ **Backward Compatible**: Existing code continues to work
4. ✅ **Flexible**: Users choose based on their needs
5. ✅ **Future-Proof**: Easy to add more action-specific SPIs

**Implementation:**
- Keep `EntityAccessPolicy` as the primary interface
- Add action-specific SPIs as optional enhancements
- Framework handlers prefer action-specific SPI, fallback to `EntityAccessPolicy`
- Document the pattern for future actions

---

## 📝 Implementation Plan

### Step 1: Keep EntityAccessPolicy (No Changes)

```java
// Keep as-is: Generic, framework-level, entity-level
public interface EntityAccessPolicy {
    boolean canUserAccessEntity(String userId, Map<String, Object> entity);
    default void logAccessDenied(String userId, Map<String, Object> entity, String reason) {}
}
```

### Step 2: Add Action-Specific SPI (New)

```java
// New: Action-specific, type-safe
public interface RelationshipQueryAccessControlPolicy {
    boolean canUserExecuteRelationshipQueries(String userId);
    boolean canUserQueryEntityType(String userId, String entityType);
    List<String> getAllowedEntityTypesForUser(String userId);
    default boolean canUserAccessEntity(String userId, String entityType, String entityId) {
        return true;  // Override for custom logic
    }
}
```

### Step 3: Framework Handler Uses Both (Hybrid)

```java
// Framework handler: Prefer SPI, fallback to EntityAccessPolicy
@Component
public class RelationshipQueryActionHandler implements ActionHandler {
    
    private final ObjectProvider<RelationshipQueryAccessControlPolicy> relationshipPolicyProvider;
    private final ObjectProvider<EntityAccessPolicy> entityAccessPolicyProvider;
    
    @Override
    public boolean validateActionAllowed(String userId) {
        // Prefer action-specific SPI
        RelationshipQueryAccessControlPolicy relationshipPolicy = 
            relationshipPolicyProvider.getIfAvailable();
        if (relationshipPolicy != null) {
            return relationshipPolicy.canUserExecuteRelationshipQueries(userId);
        }
        
        // Fallback to EntityAccessPolicy
        EntityAccessPolicy entityPolicy = entityAccessPolicyProvider.getIfAvailable();
        if (entityPolicy != null) {
            Map<String, Object> context = Map.of(
                "resourceId", "action:relationship_query",
                "operationType", "EXECUTE"
            );
            return entityPolicy.canUserAccessEntity(userId, context);
        }
        
        // Default (with warning)
        log.warn("No access control policy provided");
        return userId != null && !userId.isBlank();
    }
}
```

### Step 4: Document Pattern

- Document that `EntityAccessPolicy` is the primary interface
- Document that action-specific SPIs are optional enhancements
- Document fallback strategy
- Provide examples for both approaches

---

## 🎯 Summary

**Question:** Do we need to unify access control across the framework?

**Answer:** **Partially unified with hybrid approach**

**Recommendation:**
1. ✅ **Keep `EntityAccessPolicy`** as the primary, generic interface
2. ✅ **Add action-specific SPIs** as optional, type-safe enhancements
3. ✅ **Framework handlers use both** with fallback strategy
4. ✅ **Document the pattern** for consistency

**Result:**
- ✅ Single primary interface (`EntityAccessPolicy`) for consistency
- ✅ Optional action-specific SPIs for type safety and clarity
- ✅ Backward compatible (existing code works)
- ✅ Flexible (users choose their level of specificity)
- ✅ Future-proof (easy to add more action-specific SPIs)

**This gives us the best of both worlds:**
- **Unified** at the framework level (`EntityAccessPolicy`)
- **Specific** at the action level (action-specific SPIs)
- **Flexible** for users (choose your approach)

---

**Last Updated:** 2025-12-30  
**Status:** Recommendation  
**Related:** `ACCESS_CONTROL_MECHANICS.md`, `RELATIONSHIP_QUERY_ACCESS_CONTROL_SPI.md`

