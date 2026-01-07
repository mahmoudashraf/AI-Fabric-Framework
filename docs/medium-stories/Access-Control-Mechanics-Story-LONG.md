# 🛡️ Access Control Mechanics: Defense in Depth Across Four Layers

> **How multi-layered access control ensures security at every level—from framework entry point to individual entity results**  
> *Part of the AI Fabric Framework series — open-source library for enterprise applications*

🚧 **Status:** Production-ready | Open-source | Multi-layer security | Fail-closed architecture

---

## The Security Challenge: One Layer Is Never Enough

**Enterprise application. Multiple teams. Complex permissions:**

- **Sales Team**: Can query customers, orders, products
- **Support Team**: Can query customers, tickets, but not financial data
- **Finance Team**: Can query orders, payments, invoices
- **Admins**: Can query everything
- **Multi-tenant**: Each tenant isolated from others

**Traditional approach (single security check):**

```java
@PostMapping("/api/query")
public OrchestrationResult query(@RequestBody String query, 
                                  @AuthenticationPrincipal User user) {
    // Single check at entry point
    if (!user.hasPermission("rag:query")) {
        return OrchestrationResult.error("Access denied");
    }
    
    // Execute query...
    // ❌ But what if query accesses sensitive entities?
    // ❌ What if user queries other tenant's data?
    // ❌ What if results contain restricted information?
    
    return orchestrator.orchestrate(query, context);
}
```

**Problems:**
- ❌ Single point of failure
- ❌ No defense in depth
- ❌ Easy to bypass with complex queries
- ❌ No entity-level filtering
- ❌ No audit trail
- ❌ Framework can't enforce your rules

**Every security breach starts with "we checked at the entry point..."**

---

## Our Solution: Four-Layer Defense in Depth

**Framework provides infrastructure. You define rules. Framework enforces them at every layer.**

```
┌─────────────────────────────────────────────────────────────────────┐
│  LAYER 1: ORCHESTRATOR LEVEL (Framework Entry Point)               │
│  ═══════════════════════════════════════════════════════════════════│
│  RAGOrchestrator.orchestrate()                                      │
│    │                                                                 │
│    ├─→ Security Analysis (AISecurityService)                        │
│    ├─→ Access Control Check (AIAccessControlService)                │
│    │   └─→ EntityAccessPolicy.canUserAccessEntity()                │
│    ├─→ PII Detection                                                │
│    └─→ Compliance Check                                             │
│                                                                      │
│  Purpose: Framework-level security before any processing            │
│  When: Every query that goes through orchestrator                  │
│  Checks: Can user use RAG orchestrator? General permissions?       │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼ (if allowed)
┌─────────────────────────────────────────────────────────────────────┐
│  LAYER 2: ACTION HANDLER LEVEL (Action-Specific)                    │
│  ═══════════════════════════════════════════════════════════════════│
│  ActionHandler.validateActionAllowed()                              │
│    │                                                                 │
│    ├─→ Can user execute this action?                                 │
│    └─→ Action-specific permission checks                            │
│                                                                      │
│  ActionHandler.executeAction()                                      │
│    │                                                                 │
│    └─→ Additional filtering (e.g., entity type filtering)           │
│                                                                      │
│  Purpose: Action-specific security before business logic            │
│  When: After intent extraction, before action execution            │
│  Checks: Can user execute relationship_query? Can user query       │
│          specific entity types?                                      │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼ (if allowed)
┌─────────────────────────────────────────────────────────────────────┐
│  LAYER 3: ENTITY TYPE LEVEL (Relationship Queries)                  │
│  ═══════════════════════════════════════════════════════════════════│
│  RelationshipQueryActionHandler.filterAllowedEntityTypes()          │
│    │                                                                 │
│    ├─→ Extract requested entity types: ["customer", "order"]        │
│    ├─→ Filter based on user permissions                             │
│    │   └─→ canUserQueryEntityType(userId, "customer") → true       │
│    │   └─→ canUserQueryEntityType(userId, "order") → true          │
│    │   └─→ canUserQueryEntityType(userId, "payment") → false       │
│    │                                                                 │
│    └─→ Pass only allowed types to query planner                     │
│        (Optimizes token usage - LLM only sees allowed schemas)      │
│                                                                      │
│  Purpose: Filter entity types before query planning                 │
│  When: For relationship queries, before LLM query planning         │
│  Checks: Which entity types can user query?                         │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼ (if allowed)
┌─────────────────────────────────────────────────────────────────────┐
│  LAYER 4: RESULT LEVEL (Entity-Level Filtering)                     │
│  ═══════════════════════════════════════════════════════════════════│
│  After query execution:                                             │
│    │                                                                 │
│    ├─→ For each result entity:                                      │
│    │   └─→ EntityAccessPolicy.canUserAccessEntity(userId, entity)  │
│    │                                                                 │
│    └─→ Return only accessible entities                               │
│                                                                      │
│  Purpose: Final defense-in-depth check on individual results        │
│  When: After query execution, for each result entity                │
│  Checks: Can user access this specific entity? (row-level security) │
└─────────────────────────────────────────────────────────────────────┘
```

**Result: Comprehensive security at every level**

---

## 🎬 Act I: Layer 1 - Orchestrator Level (Framework Entry Point)

### The First Line of Defense

**Every query goes through the orchestrator. This is where framework-level security happens.**

```
┌─────────────────────────────────────────────────────────────────────┐
│  USER QUERY ARRIVES                                                │
│  ═══════════════════════════════════════════════════════════════════│
│  Query: "Find premium customers who ordered in December"           │
│  User: "sales-user-123"                                            │
└────────────────────────────┬───────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│  RAGOrchestrator.orchestrate(query, userId)                        │
│  ═══════════════════════════════════════════════════════════════════│
│                                                                      │
│  STEP 1: Security Analysis                                          │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ AISecurityService.analyzeRequest()                          │   │
│  │   ├─→ Check for malicious patterns                          │   │
│  │   ├─→ Rate limiting                                         │   │
│  │   └─→ Should block? → NO                                    │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  STEP 2: Access Control Check (LAYER 1)                             │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ AIAccessControlService.checkAccess()                         │   │
│  │   │                                                           │   │
│  │   ├─→ Build entity context:                                  │   │
│  │   │   {                                                       │   │
│  │   │     "resourceId": "rag:intent",                          │   │
│  │   │     "operationType": "READ",                              │   │
│  │   │     "context": "Find premium customers...",               │   │
│  │   │     "metadata": {"entryPoint": "RAG_ORCHESTRATOR"}       │   │
│  │   │   }                                                       │   │
│  │   │                                                           │   │
│  │   └─→ EntityAccessPolicy.canUserAccessEntity(                │   │
│  │         "sales-user-123",                                     │   │
│  │         entityContext                                         │   │
│  │       )                                                       │   │
│  │       │                                                       │   │
│  │       ├─→ Check: Can user use RAG orchestrator?              │   │
│  │       ├─→ Check: Is user active?                             │   │
│  │       ├─→ Check: Does user have "rag:query" permission?      │   │
│  │       └─→ Result: TRUE ✅                                    │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  STEP 3: PII Detection                                              │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ PIIDetectionService.analyze(query)                            │   │
│  │   └─→ No PII detected ✅                                      │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  STEP 4: Compliance Check                                            │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ ComplianceService.checkCompliance()                           │   │
│  │   └─→ Compliant ✅                                            │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  ✅ ALL CHECKS PASSED - Continue to intent extraction              │
└─────────────────────────────────────────────────────────────────────┘
```

### Your Implementation: EntityAccessPolicy

**The framework provides the infrastructure. You implement the business logic.**

```java
@Component
@RequiredArgsConstructor
public class MyEntityAccessPolicy implements EntityAccessPolicy {
    
    private final UserService userService;
    private final PermissionService permissionService;
    
    @Override
    public boolean canUserAccessEntity(String userId, Map<String, Object> entity) {
        String resourceId = (String) entity.get("resourceId");
        String operationType = (String) entity.get("operationType");
        
        // Framework-level check: Can user use RAG orchestrator?
        if ("rag:intent".equals(resourceId) && "READ".equals(operationType)) {
            // Step 1: Check if user exists and is active
            User user = userService.getUser(userId);
            if (user == null || !user.isActive()) {
                return false;  // User doesn't exist or inactive
            }
            
            // Step 2: Check permission
            return permissionService.hasPermission(userId, "rag:query");
        }
        
        // Handle other resources (entity-level checks happen in Layer 4)
        return false;
    }
    
    @Override
    public void logAccessDenied(String userId, Map<String, Object> entity, String reason) {
        // Custom audit logging
        auditService.logAccessDenial(userId, entity, reason);
    }
}
```

**What happens if your policy throws an exception?**

```
┌─────────────────────────────────────────────────────────────────────┐
│  FAIL-CLOSED SECURITY MODEL                                        │
│  ═══════════════════════════════════════════════════════════════════│
│                                                                      │
│  EntityAccessPolicy.canUserAccessEntity() throws exception          │
│    │                                                                 │
│    ├─→ AIAccessControlService catches exception                    │
│    ├─→ Logs warning: "EntityAccessPolicy threw an exception"      │
│    ├─→ Returns: Decision(granted=false, hookFailed=true)           │
│    └─→ Access: DENIED ❌                                            │
│                                                                      │
│  Principle: If security check fails, deny access                    │
│  Result: System is secure even if policy implementation has bugs    │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 🎬 Act II: Layer 2 - Action Handler Level (Action-Specific)

### Action-Specific Security

**After intent extraction, the framework checks if the user can execute the specific action.**

```
┌─────────────────────────────────────────────────────────────────────┐
│  INTENT EXTRACTION COMPLETE                                         │
│  ═══════════════════════════════════════════════════════════════════│
│  Intent: {                                                          │
│    "type": "ACTION",                                                │
│    "action": "relationship_query",                                  │
│    "actionParams": {                                                │
│      "query": "Find premium customers who ordered in December",     │
│      "entityTypes": ["customer", "order"]                          │
│    }                                                                │
│  }                                                                  │
└────────────────────────────┬───────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│  ACTION HANDLER DISCOVERY                                            │
│  ═══════════════════════════════════════════════════════════════════│
│  ActionHandlerRegistry.findHandler("relationship_query")            │
│    └─→ Found: RelationshipQueryActionHandler                       │
└────────────────────────────┬───────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│  LAYER 2a: VALIDATE ACTION ALLOWED                                  │
│  ═══════════════════════════════════════════════════════════════════│
│  handler.validateActionAllowed("sales-user-123")                   │
│    │                                                                 │
│    ├─→ Check: Does user have "relationship_query:execute"?        │
│    │   └─→ permissionService.hasPermission(                         │
│    │         "sales-user-123",                                      │
│    │         "relationship_query:execute"                           │
│    │       )                                                         │
│    │   └─→ Result: TRUE ✅                                          │
│    │                                                                 │
│    └─→ Action allowed - continue                                    │
└────────────────────────────┬───────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│  LAYER 2b: EXECUTE ACTION (with additional filtering)                │
│  ═══════════════════════════════════════════════════════════════════│
│  handler.executeAction(params, userId)                              │
│    │                                                                 │
│    ├─→ Extract entity types: ["customer", "order"]                 │
│    │                                                                 │
│    ├─→ LAYER 3: Filter entity types (see Act III)                   │
│    │                                                                 │
│    ├─→ Execute query with filtered entity types                     │
│    │                                                                 │
│    └─→ LAYER 4: Filter results (see Act IV)                        │
└─────────────────────────────────────────────────────────────────────┘
```

### Your Implementation: ActionHandler

```java
@Component
@RequiredArgsConstructor
public class RelationshipQueryActionHandler implements ActionHandler {
    
    private final PermissionService permissionService;
    private final ReliableRelationshipQueryService queryService;
    
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
        String query = extractQuery(params);
        List<String> entityTypes = extractEntityTypes(params);
        
        // Layer 3: Entity type filtering (see Act III)
        List<String> allowedEntityTypes = filterAllowedEntityTypes(userId, entityTypes);
        
        if (allowedEntityTypes.isEmpty()) {
            return ActionResult.builder()
                .success(false)
                .message("Access denied: No entity types accessible")
                .build();
        }
        
        // Execute query
        RAGResponse response = queryService.execute(query, allowedEntityTypes, options);
        
        // Layer 4: Result filtering (see Act IV)
        List<RAGDocument> filtered = filterResults(userId, response.getDocuments());
        
        return ActionResult.builder()
            .success(true)
            .data(buildResultData(filtered))
            .build();
    }
}
```

---

## 🎬 Act III: Layer 3 - Entity Type Level (Relationship Queries)

### Filter Entity Types Before Query Planning

**This layer optimizes token usage by only sending allowed entity schemas to the LLM.**

```
┌─────────────────────────────────────────────────────────────────────┐
│  ENTITY TYPE FILTERING (LAYER 3)                                    │
│  ═══════════════════════════════════════════════════════════════════│
│                                                                      │
│  Requested Entity Types: ["customer", "order", "payment"]           │
│  User: "sales-user-123"                                             │
│                                                                      │
│  STEP 1: Check Each Entity Type                                     │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ for (entityType : ["customer", "order", "payment"]) {        │   │
│  │   if (canUserQueryEntityType(userId, entityType)) {          │   │
│  │     allowedTypes.add(entityType);                            │   │
│  │   }                                                           │   │
│  │ }                                                             │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  STEP 2: Permission Checks                                           │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ canUserQueryEntityType("sales-user-123", "customer")         │   │
│  │   └─→ permissionService.hasPermission(                        │   │
│  │         "sales-user-123",                                     │   │
│  │         "relationship_query:customer"                         │   │
│  │       )                                                       │   │
│  │   └─→ Result: TRUE ✅                                         │   │
│  │                                                                 │   │
│  │ canUserQueryEntityType("sales-user-123", "order")            │   │
│  │   └─→ permissionService.hasPermission(                         │   │
│  │         "sales-user-123",                                     │   │
│  │         "relationship_query:order"                            │   │
│  │       )                                                       │   │
│  │   └─→ Result: TRUE ✅                                         │   │
│  │                                                                 │   │
│  │ canUserQueryEntityType("sales-user-123", "payment")         │   │
│  │   └─→ permissionService.hasPermission(                         │   │
│  │         "sales-user-123",                                     │   │
│  │         "relationship_query:payment"                          │   │
│  │       )                                                       │   │
│  │   └─→ Result: FALSE ❌ (Sales team can't access payments)     │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  STEP 3: Filtered Entity Types                                       │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ Allowed: ["customer", "order"]                               │   │
│  │ Denied: ["payment"]                                          │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  STEP 4: Token Optimization                                          │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ LLM Query Planning                                           │   │
│  │   ├─→ Schema sent to LLM:                                    │   │
│  │   │   - customer schema ✅                                    │   │
│  │   │   - order schema ✅                                       │   │
│  │   │   - payment schema ❌ (NOT sent - user can't access)      │   │
│  │   │                                                           │   │
│  │   └─→ Token savings: ~30% (payment schema not included)       │   │
│  └──────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

### Implementation Patterns

#### Pattern 1: Role-Based Entity Type Access

```
┌─────────────────────────────────────────────────────────────────────┐
│  ROLE-BASED ENTITY TYPE ACCESS                                      │
│  ═══════════════════════════════════════════════════════════════════│
│                                                                      │
│  canUserQueryEntityType(userId, entityType)                         │
│    │                                                                 │
│    ├─→ Get user role                                                 │
│    │   └─→ User role: "SALES"                                       │
│    │                                                                 │
│    ├─→ Check role-based access:                                      │
│    │   └─→ if (role == "ADMIN") return true;  // Admins: all       │
│    │   └─→ if (role == "SALES") {                                  │
│    │         return ["customer", "order", "product"].contains(type);│
│    │       }                                                         │
│    │   └─→ if (role == "SUPPORT") {                                │
│    │         return ["customer", "ticket"].contains(type);           │
│    │       }                                                         │
│    │                                                                 │
│    └─→ Result: "customer" → TRUE ✅                                 │
│              "order" → TRUE ✅                                       │
│              "payment" → FALSE ❌                                   │
└─────────────────────────────────────────────────────────────────────┘
```

#### Pattern 2: Permission-Based Access Control

```
┌─────────────────────────────────────────────────────────────────────┐
│  PERMISSION-BASED ENTITY TYPE ACCESS                                 │
│  ═══════════════════════════════════════════════════════════════════│
│                                                                      │
│  User Permissions:                                                   │
│    - "relationship_query:customer"                                  │
│    - "relationship_query:order"                                     │
│    - "relationship_query:product"                                   │
│                                                                      │
│  canUserQueryEntityType(userId, entityType)                         │
│    │                                                                 │
│    ├─→ Build permission: "relationship_query:" + entityType        │
│    │   └─→ "relationship_query:customer"                            │
│    │                                                                 │
│    ├─→ Check permission:                                            │
│    │   └─→ permissionService.hasPermission(                         │
│    │         userId,                                                │
│    │         "relationship_query:customer"                          │   │
│    │       )                                                         │
│    │   └─→ Result: TRUE ✅                                          │
│    │                                                                 │
│    └─→ Result: "customer" → TRUE ✅                                 │
│              "payment" → FALSE ❌ (no permission)                   │
└─────────────────────────────────────────────────────────────────────┘
```

#### Pattern 3: Tenant-Based Access Control

```
┌─────────────────────────────────────────────────────────────────────┐
│  TENANT-BASED ENTITY TYPE ACCESS                                    │
│  ═══════════════════════════════════════════════════════════════════│
│                                                                      │
│  canUserQueryEntityType(userId, entityType)                         │
│    │                                                                 │
│    ├─→ Get user tenant:                                             │
│    │   └─→ tenantService.getTenantId(userId)                        │
│    │   └─→ User tenant: "tenant-a"                                 │
│    │                                                                 │
│    ├─→ Get tenant's accessible entity types:                         │
│    │   └─→ tenantMappingService.getAccessibleEntityTypes("tenant-a")│
│    │   └─→ Accessible: ["customer", "order", "product"]            │
│    │                                                                 │
│    ├─→ Check if entity type is accessible:                          │
│    │   └─→ accessibleTypes.contains(entityType)                      │
│    │                                                                 │
│    └─→ Result: "customer" → TRUE ✅ (tenant-a can access)           │
│              "payment" → FALSE ❌ (tenant-a can't access)           │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 🎬 Act IV: Layer 4 - Result Level (Entity-Level Filtering)

### Final Defense in Depth

**After query execution, filter individual results based on entity-level access control.**

```
┌─────────────────────────────────────────────────────────────────────┐
│  QUERY EXECUTION COMPLETE                                            │
│  ═══════════════════════════════════════════════════════════════════│
│  Results: 50 entities                                                │
│    - customer-1, customer-2, ..., customer-50                       │
└────────────────────────────┬───────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│  RESULT-LEVEL FILTERING (LAYER 4)                                    │
│  ═══════════════════════════════════════════════════════════════════│
│                                                                      │
│  for (RAGDocument doc : results) {                                  │
│    if (canUserAccessEntity(userId, doc)) {                          │
│      filteredResults.add(doc);                                       │
│    }                                                                │
│  }                                                                  │
│                                                                      │
│  STEP 1: Check Each Entity                                          │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ Entity: customer-1                                            │   │
│  │   ├─→ Build entity context:                                   │   │
│  │   │   {                                                        │   │
│  │   │     "resourceId": "rag:customer",                          │   │
│  │   │     "operationType": "READ",                               │   │
│  │   │     "entityId": "customer-1",                              │   │
│  │   │     "entityType": "customer"                               │   │
│  │   │   }                                                        │   │
│  │   │                                                            │   │
│  │   └─→ EntityAccessPolicy.canUserAccessEntity(                  │   │
│  │         "sales-user-123",                                      │   │
│  │         entityContext                                          │   │
│  │       )                                                        │   │
│  │       │                                                        │   │
│  │       ├─→ Check: Is entity in user's tenant?                  │   │
│  │       ├─→ Check: Does user have permission?                   │   │
│  │       └─→ Result: TRUE ✅                                       │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  STEP 2: Multi-Tenant Check                                          │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ Entity: customer-25 (belongs to tenant-b)                     │   │
│  │   ├─→ User tenant: "tenant-a"                                 │   │
│  │   ├─→ Entity tenant: "tenant-b"                               │   │
│  │   │                                                            │   │
│  │   └─→ Tenant mismatch → DENY ❌                                │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  STEP 3: Final Results                                               │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ Original: 50 entities                                         │   │
│  │ Filtered: 45 entities (5 denied - tenant mismatch)            │   │
│  │                                                                 │   │
│  │ Returned to user: 45 entities                                  │   │
│  └──────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

### Row-Level Security Example

**Multi-tenant scenario: Users can only see their tenant's data.**

```java
@Override
public boolean canUserAccessEntity(String userId, Map<String, Object> entity) {
    String resourceId = (String) entity.get("resourceId");
    String entityId = (String) entity.get("entityId");
    
    if (resourceId != null && resourceId.startsWith("rag:")) {
        String entityType = resourceId.substring(4);
        
        // Multi-tenant check
        String userTenant = tenantService.getTenantId(userId);
        String entityTenant = getEntityTenant(entityType, entityId);
        
        if (!userTenant.equals(entityTenant)) {
            return false;  // Tenant mismatch - deny access
        }
        
        // Additional checks (permissions, etc.)
        return permissionService.hasPermission(userId, "rag:" + entityType + ":read");
    }
    
    return false;
}
```

---

## 🎬 Act V: Complete Flow Example

### Real-World Scenario: Sales User Querying Customers

```
┌─────────────────────────────────────────────────────────────────────┐
│  COMPLETE ACCESS CONTROL FLOW                                        │
│  ═══════════════════════════════════════════════════════════════════│
│                                                                      │
│  User: "sales-user-123" (Sales Team, Tenant: "acme-corp")          │
│  Query: "Find premium customers who ordered in December"           │
└────────────────────────────┬───────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│  LAYER 1: ORCHESTRATOR LEVEL                                         │
│  ═══════════════════════════════════════════════════════════════════│
│  RAGOrchestrator.orchestrate(query, "sales-user-123")               │
│    │                                                                 │
│    ├─→ Security Analysis: ✅ PASS                                   │
│    ├─→ Access Control:                                              │
│    │   └─→ EntityAccessPolicy.canUserAccessEntity(                  │
│    │         "sales-user-123",                                      │
│    │         {resourceId: "rag:intent", operationType: "READ"}       │
│    │       )                                                         │
│    │   └─→ Check: User active? ✅                                   │
│    │   └─→ Check: Has "rag:query" permission? ✅                     │
│    │   └─→ Result: GRANT ✅                                         │
│    ├─→ PII Detection: ✅ PASS                                       │
│    └─→ Compliance: ✅ PASS                                          │
└────────────────────────────┬───────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│  INTENT EXTRACTION                                                   │
│  ═══════════════════════════════════════════════════════════════════│
│  Intent: {                                                          │
│    "type": "ACTION",                                                │
│    "action": "relationship_query",                                  │
│    "actionParams": {                                                │
│      "query": "Find premium customers who ordered in December",     │
│      "entityTypes": ["customer", "order"]                           │
│    }                                                                │
│  }                                                                  │
└────────────────────────────┬───────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│  LAYER 2: ACTION HANDLER LEVEL                                       │
│  ═══════════════════════════════════════════════════════════════════│
│  RelationshipQueryActionHandler.validateActionAllowed()             │
│    └─→ Check: Has "relationship_query:execute"? ✅                  │
│    └─→ Result: GRANT ✅                                             │
└────────────────────────────┬───────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│  LAYER 3: ENTITY TYPE LEVEL                                          │
│  ═══════════════════════════════════════════════════════════════════│
│  filterAllowedEntityTypes("sales-user-123", ["customer", "order"]) │
│    │                                                                 │
│    ├─→ "customer":                                                  │
│    │   └─→ canUserQueryEntityType("sales-user-123", "customer")    │
│    │   └─→ Check: Has "relationship_query:customer"? ✅             │
│    │   └─→ Result: ALLOW ✅                                         │
│    │                                                                 │
│    ├─→ "order":                                                      │
│    │   └─→ canUserQueryEntityType("sales-user-123", "order")       │
│    │   └─→ Check: Has "relationship_query:order"? ✅                │
│    │   └─→ Result: ALLOW ✅                                         │
│    │                                                                 │
│    └─→ Allowed Types: ["customer", "order"] ✅                      │
└────────────────────────────┬───────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│  QUERY EXECUTION                                                     │
│  ═══════════════════════════════════════════════════════════════════│
│  queryService.execute(query, ["customer", "order"], options)        │
│    └─→ LLM Query Planning (only customer & order schemas sent)      │
│    └─→ JPQL Generation                                              │
│    └─→ Database Query                                               │
│    └─→ Results: 50 customer entities                                │
└────────────────────────────┬───────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│  LAYER 4: RESULT LEVEL                                               │
│  ═══════════════════════════════════════════════════════════════════│
│  Filter 50 results:                                                 │
│    │                                                                 │
│    ├─→ customer-1 (tenant: "acme-corp"):                            │
│    │   └─→ EntityAccessPolicy.canUserAccessEntity()                │
│    │   └─→ Check: Tenant match? ✅                                  │
│    │   └─→ Result: ALLOW ✅                                         │
│    │                                                                 │
│    ├─→ customer-25 (tenant: "competitor-corp"):                     │
│    │   └─→ EntityAccessPolicy.canUserAccessEntity()                │
│    │   └─→ Check: Tenant match? ❌ (tenant mismatch)               │
│    │   └─→ Result: DENY ❌                                          │
│    │                                                                 │
│    └─→ ... (check all 50 entities)                                  │
│                                                                      │
│  Final Results: 45 entities (5 denied - tenant mismatch)            │
└────────────────────────────┬───────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│  RESULTS RETURNED TO USER                                            │
│  ═══════════════════════════════════════════════════════════════════│
│  45 premium customers who ordered in December                        │
│  (Only entities from user's tenant)                                 │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 🎬 Act VI: Implementation Patterns

### Pattern 1: Role-Based Access Control

```
┌─────────────────────────────────────────────────────────────────────┐
│  ROLE-BASED ACCESS CONTROL IMPLEMENTATION                           │
│  ═══════════════════════════════════════════════════════════════════│
│                                                                      │
│  @Component                                                          │
│  public class RoleBasedAccessPolicy implements EntityAccessPolicy { │
│                                                                      │
│    @Override                                                         │
│    public boolean canUserAccessEntity(String userId,                │
│                                       Map<String, Object> entity) { │
│      User user = userService.getUser(userId);                       │
│                                                                      │
│      // Admins: all access                                          │
│      if (user.getRole().equals("ADMIN")) {                          │
│        return true;                                                  │
│      }                                                               │
│                                                                      │
│      // Analysts: read-only                                         │
│      if (user.getRole().equals("ANALYST")) {                        │
│        return "READ".equals(entity.get("operationType"));            │
│      }                                                               │
│                                                                      │
│      // Regular users: limited access                               │
│      if (user.getRole().equals("USER")) {                           │
│        return canRegularUserAccess(entity);                          │
│      }                                                               │
│                                                                      │
│      return false;                                                   │
│    }                                                                 │
│  }                                                                   │
└─────────────────────────────────────────────────────────────────────┘
```

### Pattern 2: Permission-Based Access Control

```
┌─────────────────────────────────────────────────────────────────────┐
│  PERMISSION-BASED ACCESS CONTROL IMPLEMENTATION                     │
│  ═══════════════════════════════════════════════════════════════════│
│                                                                      │
│  @Component                                                          │
│  public class PermissionBasedAccessPolicy                           │
│      implements EntityAccessPolicy {                                 │
│                                                                      │
│    @Override                                                         │
│    public boolean canUserAccessEntity(String userId,                │
│                                       Map<String, Object> entity) { │
│      String resourceId = (String) entity.get("resourceId");         │
│      String operationType = (String) entity.get("operationType");   │
│                                                                      │
│      // Build permission: "rag:customer:read"                       │
│      String permission = buildPermission(resourceId, operationType);  │
│                                                                      │
│      return permissionService.hasPermission(userId, permission);    │
│    }                                                                 │
│                                                                      │
│    private String buildPermission(String resourceId,                │
│                                   String operationType) {            │
│      if (resourceId.startsWith("rag:")) {                           │
│        String entityType = resourceId.substring(4);                  │
│        return "rag:" + entityType + ":" +                           │
│               operationType.toLowerCase();                           │
│      }                                                               │
│      return resourceId + ":" + operationType.toLowerCase();          │
│    }                                                                 │
│  }                                                                   │
└─────────────────────────────────────────────────────────────────────┘
```

### Pattern 3: Hybrid Access Control (Recommended)

```
┌─────────────────────────────────────────────────────────────────────┐
│  HYBRID ACCESS CONTROL IMPLEMENTATION                                │
│  ═══════════════════════════════════════════════════════════════════│
│                                                                      │
│  @Component                                                          │
│  public class HybridAccessPolicy implements EntityAccessPolicy {      │
│                                                                      │
│    @Override                                                         │
│    public boolean canUserAccessEntity(String userId,                 │
│                                       Map<String, Object> entity) { │
│      // Step 1: Role-based (admins bypass)                          │
│      if (roleService.hasRole(userId, "ADMIN")) {                    │
│        return true;                                                  │
│      }                                                               │
│                                                                      │
│      // Step 2: Data classification                                  │
│      EntityClassification classification =                           │
│          classificationService.getClassification(entityType);       │
│      if (classification == EntityClassification.RESTRICTED) {        │
│        return false;  // Restricted requires admin                   │
│      }                                                               │
│                                                                      │
│      // Step 3: Tenant isolation                                     │
│      String userTenant = tenantService.getTenantId(userId);         │
│      String entityTenant = getEntityTenant(entityType, entityId);    │
│      if (!userTenant.equals(entityTenant)) {                        │
│        return false;  // Tenant mismatch                             │
│      }                                                               │
│                                                                      │
│      // Step 4: Permission check                                     │
│      String permission = buildPermission(resourceId, operationType);│
│      if (permissionService.hasPermission(userId, permission)) {     │
│        return true;                                                  │
│      }                                                               │
│                                                                      │
│      // Step 5: Role-based fallback                                  │
│      if (roleService.hasRole(userId, "ANALYST") &&                  │
│          "READ".equals(operationType)) {                            │
│        return true;  // Analysts can read                            │
│      }                                                               │
│                                                                      │
│      return false;  // Default: deny                                │
│    }                                                                 │
│  }                                                                   │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 🎬 Act VII: Fail-Closed Security Model

### What Happens When Security Checks Fail?

```
┌─────────────────────────────────────────────────────────────────────┐
│  FAIL-CLOSED SECURITY MODEL                                         │
│  ═══════════════════════════════════════════════════════════════════│
│                                                                      │
│  SCENARIO 1: EntityAccessPolicy throws exception                    │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ EntityAccessPolicy.canUserAccessEntity() throws              │   │
│  │   DatabaseConnectionException                                │   │
│  │     │                                                         │   │
│  │     ├─→ AIAccessControlService catches exception             │   │
│  │     ├─→ Logs warning: "EntityAccessPolicy threw exception"   │   │
│  │     ├─→ Returns: Decision(granted=false, hookFailed=true)   │   │
│  │     └─→ Access: DENIED ❌                                     │   │
│  │                                                               │   │
│  │  Principle: If security check fails, deny access             │   │
│  │  Result: System remains secure even if policy has bugs       │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  SCENARIO 2: EntityAccessPolicy returns false                       │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ EntityAccessPolicy.canUserAccessEntity() returns false       │   │
│  │     │                                                         │   │
│  │     ├─→ AIAccessControlService receives false                │   │
│  │     ├─→ Calls logAccessDenied() for audit                    │   │
│  │     ├─→ Returns: Decision(granted=false, hookFailed=false)  │   │
│  │     └─→ Access: DENIED ❌                                     │   │
│  │                                                               │   │
│  │  Principle: Explicit denial is respected                      │   │
│  │  Result: User gets clear "Access denied" message             │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  SCENARIO 3: EntityAccessPolicy not provided                        │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ No EntityAccessPolicy bean found                              │   │
│  │     │                                                         │   │
│  │     ├─→ AIAccessControlService.requirePolicy() throws        │   │
│  │     │   IllegalStateException                                 │   │
│  │     ├─→ Error: "No EntityAccessPolicy bean available"        │   │
│  │     └─→ Application startup fails ❌                          │   │
│  │                                                               │   │
│  │  Principle: Force explicit security implementation            │   │
│  │  Result: Can't run without access control (secure by default) │   │
│  └──────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 🎬 Act VIII: Integration Guide

### Step-by-Step Integration

```
┌─────────────────────────────────────────────────────────────────────┐
│  INTEGRATION STEPS                                                   │
│  ═══════════════════════════════════════════════════════════════════│
│                                                                      │
│  STEP 1: Add Dependency                                              │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ <dependency>                                                  │   │
│  │   <groupId>com.ai.fabric</groupId>                           │   │
│  │   <artifactId>ai-infrastructure-core</artifactId>            │   │
│  │   <version>1.0.0</version>                                   │   │
│  │ </dependency>                                                 │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  STEP 2: Implement EntityAccessPolicy                                │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ @Component                                                    │   │
│  │ public class MyAccessPolicy implements EntityAccessPolicy {   │   │
│  │   @Override                                                   │   │
│  │   public boolean canUserAccessEntity(String userId,           │   │
│  │                                      Map<String, Object> e) { │   │
│  │     // Your business logic                                    │   │
│  │     return yourPermissionService.check(userId, e);            │   │
│  │   }                                                            │   │
│  │ }                                                              │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  STEP 3: Use RAGOrchestrator                                         │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ @Service                                                      │   │
│  │ public class MyQueryService {                                 │   │
│  │   @Autowired                                                  │   │
│  │   private RAGOrchestrator orchestrator;                      │   │
│  │                                                               │   │
│  │   public OrchestrationResult query(String q, String userId) { │   │
│  │     // Access control automatically enforced                 │   │
│  │     return orchestrator.orchestrate(q, userId);               │   │
│  │   }                                                            │   │
│  │ }                                                              │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  ✅ DONE! Access control is now active at all 4 layers              │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Summary: Why Four Layers?

**Defense in Depth Principle:**

1. **Layer 1 (Orchestrator)**: Early rejection saves processing time
2. **Layer 2 (Action Handler)**: Action-specific security prevents unauthorized actions
3. **Layer 3 (Entity Type)**: Token optimization + early filtering
4. **Layer 4 (Result)**: Final check ensures row-level security

**Benefits:**
- ✅ Comprehensive security at every level
- ✅ Fail-closed architecture (secure by default)
- ✅ Flexible implementation (you define rules)
- ✅ Framework enforces (consistent security)
- ✅ Open-source friendly (easy to integrate)

**Result: Enterprise-grade security for your AI applications**

---

**Last Updated:** 2025-12-30  
**Framework Version:** 1.0.0  
**License:** Open Source  
**Related:** `ACCESS_CONTROL_MECHANICS.md` (complete technical guide)

