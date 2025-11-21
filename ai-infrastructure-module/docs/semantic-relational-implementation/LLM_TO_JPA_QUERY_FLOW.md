# LLM → JPA Query Flow: Complete Guide

## 🎯 The Problem

**User Query:** *"Find documents created by active users"*

**Challenge:** How do we know:
1. User is asking about documents?
2. Need to filter by `createdBy` relationship?
3. Need to check if user status is "active"?

**Solution:** Use LLM to extract intent, then dynamically generate JPA queries!

---

## 🔄 Complete Flow

```
┌─────────────────────────────────────────────────────────────────┐
│ STEP 1: User Query (Natural Language)                          │
│                                                                 │
│ "Find documents created by active users"                        │
└─────────────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────────────┐
│ STEP 2: LLM Analysis (RelationshipQueryPlanner)               │
│                                                                 │
│ LLM analyzes query and generates RelationshipQueryPlan:        │
│                                                                 │
│ {                                                               │
│   "semanticQuery": "documents",                                │
│   "primaryEntityType": "document",                              │
│   "relationshipPaths": [{                                      │
│     "fromEntityType": "document",                               │
│     "relationshipType": "createdBy",                           │
│     "toEntityType": "user",                                    │
│     "direction": "REVERSE",                                    │
│     "conditions": {"status": "active"}                         │
│   }],                                                           │
│   "relationshipFilters": {"user.status": "active"},            │
│   "strategy": "RELATIONSHIP_TRAVERSAL"                         │
│ }                                                               │
└─────────────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────────────┐
│ STEP 3: Dynamic JPA Query Generation (DynamicJPAQueryBuilder)   │
│                                                                 │
│ Translates plan → JPQL:                                         │
│                                                                 │
│ SELECT DISTINCT e FROM Document e                              │
│ JOIN e.createdBy u                                             │
│ WHERE u.status = :user_status                                  │
└─────────────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────────────┐
│ STEP 4: Execute JPA Query                                       │
│                                                                 │
│ Query query = entityManager.createQuery(jpql);                  │
│ query.setParameter("user_status", "active");                    │
│ List<Document> results = query.getResultList();                │
│                                                                 │
│ SQL Generated:                                                  │
│ SELECT d.* FROM documents d                                     │
│ JOIN users u ON d.created_by = u.id                            │
│ WHERE u.status = 'active'                                      │
└─────────────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────────────┐
│ STEP 5: Extract Entity IDs                                     │
│                                                                 │
│ entityIds = ["doc-123", "doc-999"]                              │
└─────────────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────────────┐
│ STEP 6: Optionally Rank by Vector Similarity                    │
│                                                                 │
│ Compute similarity scores and re-rank                           │
└─────────────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────────────┐
│ STEP 7: Return Results                                         │
│                                                                 │
│ RAGResponse with documents ranked by relevance                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 📝 Code Example: Complete Usage

```java
@Service
public class DocumentSearchService {
    
    @Autowired
    private LLMDrivenJPAQueryService llmQueryService;
    
    /**
     * User asks: "Find documents created by active users"
     */
    public RAGResponse searchDocuments(String userQuery) {
        // Get available entity types
        List<String> entityTypes = Arrays.asList("document", "user", "project");
        
        // Execute LLM-driven query
        RAGResponse response = llmQueryService.executeRelationshipQuery(
            userQuery,  // "Find documents created by active users"
            entityTypes
        );
        
        return response;
    }
}
```

---

## 🔍 How LLM Extracts Intent

### LLM Prompt (in RelationshipQueryPlanner)

```
You are a database query planner that understands user intent.

User Query: "Find documents created by active users"

Analyze and extract:
1. What entity type is being queried? → "document"
2. What relationships are involved? → document.createdBy → user
3. What filters are needed? → user.status = "active"

Generate structured plan...
```

### LLM Response (RelationshipQueryPlan)

```json
{
  "originalQuery": "Find documents created by active users",
  "semanticQuery": "documents",
  "primaryEntityType": "document",
  "relationshipPaths": [{
    "fromEntityType": "document",
    "relationshipType": "createdBy",
    "toEntityType": "user",
    "direction": "REVERSE",
    "conditions": {"status": "active"}
  }],
  "relationshipFilters": {
    "user.status": "active"
  },
  "strategy": "RELATIONSHIP_TRAVERSAL"
}
```

---

## 🛠️ How Dynamic Query Builder Works

### Step 1: Discover Entity Classes

```java
// EntityRelationshipMapper maps:
"document" → "Document" (entity class name)
"user" → "User"
```

### Step 2: Discover Relationship Fields

```java
// Uses JPA Metamodel to find:
Document.createdBy → User

// Or uses mapping:
relationshipFieldMap.get("Document:User:createdBy") → "createdBy"
```

### Step 3: Build JPQL

```java
// From plan:
- Primary entity: Document
- Relationship: createdBy → User
- Filter: user.status = "active"

// Generates:
SELECT DISTINCT e FROM Document e
JOIN e.createdBy u
WHERE u.status = :user_status
```

### Step 4: Set Parameters

```java
query.setParameter("user_status", "active");
```

---

## 🎯 More Examples

### Example 1: Multi-Hop Query

**User Query:** *"Show me documents from projects owned by user-123"*

**LLM Plan:**
```json
{
  "relationshipPaths": [
    {"fromEntityType": "document", "relationshipType": "belongsTo", "toEntityType": "project"},
    {"fromEntityType": "project", "relationshipType": "owner", "toEntityType": "user"}
  ],
  "relationshipFilters": {"user.id": "user-123"}
}
```

**Generated JPQL:**
```sql
SELECT DISTINCT e FROM Document e
JOIN e.project p
JOIN p.owner u
WHERE u.id = :user_id
```

### Example 2: Complex Filter

**User Query:** *"Find AI documents created by active users this month"*

**LLM Plan:**
```json
{
  "relationshipPaths": [{
    "fromEntityType": "document",
    "relationshipType": "createdBy",
    "toEntityType": "user"
  }],
  "relationshipFilters": {"user.status": "active"},
  "directFilters": {
    "category": "ai",
    "createdAt": "2024-01-01"
  }
}
```

**Generated JPQL:**
```sql
SELECT DISTINCT e FROM Document e
JOIN e.createdBy u
WHERE u.status = :user_status
  AND e.category = :category
  AND e.createdAt >= :createdAt
```

---

## 🔧 Configuration: Register Your Entities

```java
@Configuration
public class EntityMappingConfiguration {
    
    @Autowired
    private EntityRelationshipMapper entityMapper;
    
    @PostConstruct
    public void registerEntities() {
        // Register entity types
        entityMapper.registerEntityType("document", "Document");
        entityMapper.registerEntityType("user", "User");
        entityMapper.registerEntityType("project", "Project");
        
        // Register relationships
        entityMapper.registerRelationship(
            "Document", "User", "createdBy", "createdBy"
        );
        entityMapper.registerRelationship(
            "Document", "Project", "belongsTo", "project"
        );
        entityMapper.registerRelationship(
            "Project", "User", "owner", "owner"
        );
    }
}
```

---

## 🚀 Usage in Your Service

```java
@RestController
@RequestMapping("/api/search")
public class SearchController {
    
    @Autowired
    private LLMDrivenJPAQueryService queryService;
    
    @PostMapping("/relationship")
    public RAGResponse search(@RequestBody SearchRequest request) {
        // User query: "Find documents created by active users"
        return queryService.executeRelationshipQuery(
            request.getQuery(),
            Arrays.asList("document", "user", "project")
        );
    }
}
```

---

## ✅ Benefits

1. **No Hardcoding**: LLM extracts intent dynamically
2. **Type-Safe**: Uses JPA entities and relationships
3. **Efficient**: Single database query with joins
4. **Flexible**: Handles any relationship query
5. **Intelligent**: Understands natural language

---

## 🔄 Fallback Strategy

If JPA query cannot be built:
1. Falls back to metadata-based approach
2. Or falls back to pure vector search
3. Always returns results (graceful degradation)

---

## 📊 Performance

**LLM Query Planning:** ~200-500ms (one-time per query)
**JPA Query Execution:** ~10-50ms (database join)
**Total:** ~250-550ms (much faster than multiple queries!)

---

## 🎯 Summary

**The Magic:**
1. LLM understands natural language → extracts relationship patterns
2. Dynamic builder translates patterns → generates JPQL
3. JPA executes query → returns results efficiently

**Result:** Natural language queries → Efficient database queries! 🚀
