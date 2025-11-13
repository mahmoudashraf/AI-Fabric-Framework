# Architectural Decisions: Relationship-Aware Query System

## 📋 Document Purpose

This document captures **all architectural decisions** made for the `ai-infrastructure-relationship-query` module. These decisions are **finalized** and should guide implementation.

---

## 🎯 Decision 1: ai-core is Mandatory

### **Decision:**
**Make `ai-infrastructure-core` a required dependency** (not optional)

### **Rationale:**

#### **Benefits:**
1. ✅ **Simplified LLM Integration**
   - No direct LLM API calls needed
   - Clean, consistent API via `AICoreService`
   - Handles retries, error handling, provider abstraction

2. ✅ **Consistent Infrastructure**
   - Same LLM provider configuration across all features
   - Same error handling and retry logic
   - Same monitoring and observability

3. ✅ **Provider Abstraction**
   - Works with OpenAI, Anthropic, Azure, local models
   - Just change config, code stays same
   - Future-proof

4. ✅ **Built-in Features**
   - Retry logic
   - Rate limiting
   - Error handling
   - Monitoring
   - Caching (if configured)

#### **Trade-offs:**
- ⚠️ Cannot be truly standalone (but ai-core is foundational anyway)
- ⚠️ Larger dependency footprint (acceptable)

### **Implementation:**
```xml
<!-- pom.xml - Required dependency -->
<dependency>
    <groupId>com.ai.infrastructure</groupId>
    <artifactId>ai-infrastructure-core</artifactId>
    <!-- NOT optional -->
</dependency>
```

### **Impact:**
- ✅ Simpler code (no direct LLM API integration)
- ✅ Consistent with rest of AI infrastructure
- ✅ Better maintainability
- ✅ Easier to extend

---

## 🎯 Decision 2: Relationships Work Without Vectors

### **Decision:**
**Use JPA Metamodel to discover relationships** - No vectors or annotations required

### **Rationale:**

#### **Key Insight:**
- ✅ **JPA relationships exist independently of vectors**
- ✅ **Metamodel can discover them automatically**
- ✅ **No @AICapable annotation needed**
- ✅ **No metadata storage needed**

#### **How It Works:**

```java
// Pure JPA entity (NO @AICapable needed!)
@Entity
public class Document {
    @ManyToOne
    private User createdBy;  // JPA relationship exists!
    
    @ManyToOne
    private Project project;  // JPA relationship exists!
}

// Discover via JPA Metamodel
Metamodel metamodel = entityManager.getMetamodel();
EntityType<?> docEntity = metamodel.entity(Document.class);

// Find relationships automatically
for (Attribute<?, ?> attr : docEntity.getAttributes()) {
    if (attr.isAssociation()) {
        // Found relationship!
        // Can build: JOIN d.createdBy u
    }
}
```

#### **Two Modes:**

**Mode 1: Without Vectors (Standalone)**
- Uses JPA Metamodel to discover relationships
- Builds JPQL queries using actual JPA relationships
- No vectors needed
- No annotations needed

**Mode 2: With Vectors (Enhanced)**
- Same as Mode 1 (JPA relationships)
- PLUS: Semantic ranking via vectors
- Optional enhancement

### **Implementation:**
```java
// DynamicJPAQueryBuilder uses Metamodel
Metamodel metamodel = entityManager.getMetamodel();
EntityType<?> entity = metamodel.entity(Document.class);

// Discover relationships
String fieldName = discoverRelationshipField(entity, "createdBy");
// Returns: "createdBy" (actual JPA field name)

// Build JPQL using actual relationships
String jpql = "SELECT d FROM Document d JOIN d.createdBy u WHERE u.status = :status";
```

### **Impact:**
- ✅ Works with pure JPA entities
- ✅ No special annotations needed
- ✅ No metadata storage required
- ✅ Vectors are optional enhancement

---

## 🎯 Decision 2.1: Automatic Schema Introspection via JPA Metamodel

### **Decision:**
**Automatic schema introspection at startup** - Discover all @AICapable entities and their relationships, cache schema info, and provide full entity context to LLM

### **Rationale:**

#### **Key Requirements:**
- ✅ **Only @AICapable entities** - Only include entities that are AI-searchable
- ✅ **Only @AICapable relationships** - Only traverse to other @AICapable entities
- ✅ **Startup loading** - Build schema cache at system startup (performance)
- ✅ **Full entity context** - Pass complete entity structure with relationships to LLM

#### **Why This Matters:**

**Current Limitation:**
- Schema info is hardcoded or basic
- LLM doesn't have full context about entities
- Relationships discovered at runtime (slower)

**With Automatic Introspection:**
- ✅ **Accurate schema** - Discovered from actual entities (no hardcoding)
- ✅ **Complete context** - LLM sees full entity structure
- ✅ **Better planning** - LLM makes better decisions with full info
- ✅ **Performance** - Cached at startup (no runtime discovery overhead)
- ✅ **Filtered scope** - Only AI-capable entities (cleaner, more relevant)

### **How It Works:**

#### **1. Startup Schema Discovery:**

```java
@PostConstruct
public void initializeSchema() {
    Metamodel metamodel = entityManager.getMetamodel();
    
    // Step 1: Discover all @AICapable entities
    Set<Class<?>> aiCapableEntities = new HashSet<>();
    for (EntityType<?> entityType : metamodel.getEntityTypes()) {
        Class<?> javaType = entityType.getJavaType();
        if (javaType.isAnnotationPresent(AICapable.class)) {
            aiCapableEntities.add(javaType);
        }
    }
    
    // Step 2: Build schema for each entity
    Map<String, EntitySchema> schemaMap = new HashMap<>();
    for (Class<?> entityClass : aiCapableEntities) {
        EntitySchema schema = buildEntitySchema(entityClass, metamodel, aiCapableEntities);
        String entityType = getEntityType(entityClass);
        schemaMap.put(entityType, schema);
    }
    
    // Step 3: Cache schema
    this.cachedSchema = schemaMap;
}
```

#### **2. Build Entity Schema:**

```java
private EntitySchema buildEntitySchema(Class<?> entityClass, 
                                       Metamodel metamodel,
                                       Set<Class<?>> aiCapableEntities) {
    EntityType<?> entityType = metamodel.entity(entityClass);
    EntitySchema schema = new EntitySchema();
    
    schema.setEntityType(getEntityType(entityClass));
    schema.setClassName(entityClass.getSimpleName());
    schema.setFullClassName(entityClass.getName());
    
    // Discover relationships
    List<RelationshipInfo> relationships = new ArrayList<>();
    for (Attribute<?, ?> attr : entityType.getAttributes()) {
        if (attr.isAssociation()) {
            Class<?> targetType = attr.getJavaType();
            
            // Only include relationships to @AICapable entities
            if (aiCapableEntities.contains(targetType)) {
                RelationshipInfo rel = new RelationshipInfo();
                rel.setFieldName(attr.getName());
                rel.setTargetEntityType(getEntityType(targetType));
                rel.setTargetClassName(targetType.getSimpleName());
                rel.setRelationshipType(determineRelationshipType(attr));
                rel.setDirection(determineDirection(attr));
                
                relationships.add(rel);
            }
        }
    }
    
    schema.setRelationships(relationships);
    
    // Discover fields (for filters)
    List<FieldInfo> fields = new ArrayList<>();
    for (Attribute<?, ?> attr : entityType.getAttributes()) {
        if (!attr.isAssociation()) {
            FieldInfo field = new FieldInfo();
            field.setName(attr.getName());
            field.setType(attr.getJavaType().getSimpleName());
            fields.add(field);
        }
    }
    schema.setFields(fields);
    
    return schema;
}
```

#### **3. Generate LLM Schema Description:**

```java
public String getSchemaDescription(List<String> entityTypes) {
    StringBuilder description = new StringBuilder();
    
    description.append("Available AI-Capable Entities:\n\n");
    
    for (String entityType : entityTypes) {
        EntitySchema schema = cachedSchema.get(entityType);
        if (schema == null) continue;
        
        description.append("Entity: ").append(entityType)
                   .append(" (Class: ").append(schema.getClassName()).append(")\n");
        
        // Fields
        description.append("  Fields:\n");
        for (FieldInfo field : schema.getFields()) {
            description.append("    - ").append(field.getName())
                      .append(" (").append(field.getType()).append(")\n");
        }
        
        // Relationships (only to @AICapable entities)
        description.append("  Relationships:\n");
        for (RelationshipInfo rel : schema.getRelationships()) {
            description.append("    - ").append(rel.getFieldName())
                      .append(" -> ").append(rel.getTargetEntityType())
                      .append(" (").append(rel.getRelationshipType()).append(")\n");
        }
        
        description.append("\n");
    }
    
    return description.toString();
}
```

### **Example Output to LLM:**

```
Available AI-Capable Entities:

Entity: document (Class: Document)
  Fields:
    - id (Long)
    - title (String)
    - content (String)
    - status (String)
    - createdAt (LocalDateTime)
  Relationships:
    - createdBy -> user (@ManyToOne)
    - project -> project (@ManyToOne)
    - tags -> tag (@ManyToMany)

Entity: user (Class: User)
  Fields:
    - id (Long)
    - email (String)
    - name (String)
    - status (String)
  Relationships:
    - documents -> document (@OneToMany)
    - projects -> project (@OneToMany)

Entity: project (Class: Project)
  Fields:
    - id (Long)
    - name (String)
    - category (String)
    - status (String)
  Relationships:
    - documents -> document (@OneToMany)
    - owner -> user (@ManyToOne)
```

### **Implementation Details:**

#### **Schema Cache Structure:**

```java
public class EntitySchema {
    private String entityType;           // "document"
    private String className;            // "Document"
    private String fullClassName;        // "com.example.Document"
    private List<FieldInfo> fields;      // All non-relationship fields
    private List<RelationshipInfo> relationships;  // Only to @AICapable entities
}

public class RelationshipInfo {
    private String fieldName;            // "createdBy"
    private String targetEntityType;    // "user"
    private String targetClassName;      // "User"
    private String relationshipType;     // "@ManyToOne", "@OneToMany", etc.
    private RelationshipDirection direction;  // FORWARD, REVERSE, BIDIRECTIONAL
}
```

#### **Startup Initialization:**

```java
@Component
public class RelationshipSchemaProvider implements InitializingBean {
    
    private final EntityManager entityManager;
    private Map<String, EntitySchema> cachedSchema;
    
    @Override
    public void afterPropertiesSet() {
        log.info("Initializing relationship schema cache...");
        initializeSchema();
        log.info("Schema cache initialized with {} entities", cachedSchema.size());
    }
    
    private void initializeSchema() {
        // Discovery logic here
    }
}
```

### **Benefits:**

1. ✅ **Accurate** - Discovered from actual entities (no hardcoding)
2. ✅ **Complete** - Full entity structure with all fields and relationships
3. ✅ **Filtered** - Only @AICapable entities (cleaner, more relevant)
4. ✅ **Performance** - Cached at startup (no runtime overhead)
5. ✅ **Better LLM Planning** - LLM has full context for better decisions
6. ✅ **Automatic** - No manual configuration needed

### **Configuration:**

```yaml
ai:
  infrastructure:
    relationship:
      schema:
        # Auto-discovery enabled by default
        auto-discover: true
        
        # Refresh schema on startup (default: true)
        refresh-on-startup: true
        
        # Log discovered schema (for debugging)
        log-schema: false
```

### **Impact:**
- ✅ **Better LLM understanding** - Full entity context
- ✅ **More accurate queries** - LLM knows actual relationships
- ✅ **Faster queries** - No runtime schema discovery
- ✅ **Cleaner scope** - Only AI-capable entities
- ✅ **Automatic** - No manual schema configuration

---

## 🎯 Decision 3: Return Strategy - Hybrid

### **Decision:**
**Return IDs by default, allow full data via option**

### **Rationale:**

#### **Default: Return IDs**

**Why:**
- ✅ **Faster** - Less data transferred
- ✅ **More flexible** - Caller fetches what they need
- ✅ **Better for pagination** - Can page IDs
- ✅ **Lighter responses** - Smaller payload

**Usage:**
```java
// Default: Returns IDs
RAGResponse response = queryService.executeQuery(query);
List<String> ids = response.getEntityIds();
// ["doc-123", "doc-456"]

// Caller fetches what they need
List<Document> docs = documentRepository.findAllById(ids);
```

#### **Option: Return Full Data**

**When to Use:**
- Small result sets (< 100 items)
- Need immediate data
- Don't want extra query

**Usage:**
```java
// Request full data
RAGResponse response = queryService.executeQuery(
    query,
    QueryOptions.builder()
        .returnMode(ReturnMode.FULL)
        .build()
);
List<DocumentDTO> docs = response.getDocuments();
```

### **Configuration:**
```yaml
ai:
  infrastructure:
    relationship:
      default-return-mode: ids  # or "full"
```

### **API Design:**
```java
public enum ReturnMode {
    IDS,   // Return only entity IDs (default)
    FULL   // Return full entity data
}

public class QueryOptions {
    private ReturnMode returnMode = ReturnMode.IDS;  // Default
    // ...
}
```

### **Impact:**
- ✅ Efficient by default (IDs)
- ✅ Flexible when needed (full data)
- ✅ Better performance for large result sets
- ✅ Caller controls what to fetch

---

## 🎯 Decision 4: Mode Selection - LLM-Based Auto-Detection with User Override

### **Decision:**
**LLM-based auto-detection by default, with explicit user override option**

### **Priority Order:**

1. **Explicit User Override** (highest priority)
   ```java
   QueryOptions.builder().forceMode(QueryMode.ENHANCED).build()
   ```

2. **LLM Auto-Detection** (default)
   ```java
   // LLM decides based on query analysis
   ```

### **Rationale:**

#### **Key Insight:**
- ✅ **LLM already analyzes query** - Can determine if semantic search needed
- ✅ **Simpler configuration** - No mode selection needed by default
- ✅ **Intelligent decision** - LLM understands query intent
- ✅ **User control** - Can override LLM decision when needed
- ✅ **Cost efficient** - Only uses vectors when needed
- ✅ **Flexible** - Best of both worlds (auto + manual)

### **How It Works:**

```java
// LLM analyzes query and generates plan
RelationshipQueryPlan plan = llmPlanner.planQuery(query);

// Plan includes: needsSemanticSearch flag
{
  "primaryEntityType": "document",
  "needsSemanticSearch": true,  // LLM decides!
  "semanticQuery": "data privacy regulations",
  "relationshipPaths": [...],
  "relationshipFilters": {...}
}

// Service uses plan to determine mode
if (plan.isNeedsSemanticSearch() && isVectorSearchAvailable()) {
    // Use enhanced mode (relational + semantic)
    return executeEnhanced(query, plan);
} else {
    // Use standalone mode (relational only)
    return executeStandalone(query, plan);
}
```

### **LLM Decision Logic:**

**LLM analyzes query and determines:**

**Needs Semantic Search (ENHANCED):**
- "Find similar products"
- "Show me documents like this"
- "Find related cases"
- Queries about similarity, recommendations, content matching

**Relational Only (STANDALONE):**
- "Find orders from active customers"
- "Show me users who haven't logged in"
- "List products in category X"
- Structured queries with exact filters

### **Configuration:**
```yaml
ai:
  infrastructure:
    relationship:
      # Auto-detection always enabled (no config needed)
      # LLM decides if semantic search is needed
      
      # Vector search settings (only if LLM decides to use vectors)
      default-similarity-threshold: 0.7
      enable-vector-search: true  # Enable vector capability (LLM decides when to use)
```

### **Query Options (User Override):**

```java
public class QueryOptions {
    // Optional: Force mode (overrides LLM decision)
    private QueryMode forceMode;  // null = auto-detect via LLM (default)
    
    // Other options...
}

// Usage examples:

// 1. Default: LLM auto-detects (most common)
queryService.executeQuery(query, entityTypes);
// LLM decides: "Find similar products" → ENHANCED

// 2. Override: Force standalone (relational only)
queryService.executeQuery(query, entityTypes,
    QueryOptions.builder()
        .forceMode(QueryMode.STANDALONE)  // Override LLM: use relational only
        .build()
);

// 3. Override: Force enhanced (relational + semantic)
queryService.executeQuery(query, entityTypes,
    QueryOptions.builder()
        .forceMode(QueryMode.ENHANCED)  // Override LLM: always use vectors
        .build()
);
```

### **Implementation:**
```java
private QueryMode determineMode(RelationshipQueryPlan plan, QueryOptions options) {
    // Priority 1: Explicit user override (highest priority)
    if (options.getForceMode() != null) {
        return options.getForceMode();  // User knows best, respect their choice
    }
    
    // Priority 2: LLM decision (default - auto-detection)
    return plan.isNeedsSemanticSearch() ? QueryMode.ENHANCED : QueryMode.STANDALONE;
}
```

### **LLM Prompt Enhancement:**

```java
// Enhanced prompt for LLM
String prompt = """
    Analyze this query and determine:
    1. What entities are involved?
    2. What relationships need to be traversed?
    3. Does this query need semantic similarity search?
       - YES if query asks for "similar", "like", "related", "recommend"
       - NO if query asks for exact matches, filters, aggregations
    
    Query: "Find documents similar to this one from active users"
    
    Response:
    {
      "needsSemanticSearch": true,
      "semanticQuery": "documents similar to this one",
      "relationshipPaths": [...],
      "relationshipFilters": {"user.status": "active"}
    }
    """;
```

### **Impact:**
- ✅ **Simpler** - No mode configuration needed by default
- ✅ **Intelligent** - LLM decides automatically
- ✅ **User control** - Can override LLM decision when needed
- ✅ **Cost efficient** - Only uses vectors when needed
- ✅ **User-friendly** - Just describe what you want (or override if you know better)
- ✅ **Flexible** - Best of both worlds (auto-detection + manual override)

---

## 🎯 Decision 5: JPQL Generation - Hybrid

### **Decision:**
**LLM plans, Builder generates JPQL** (not pure LLM generation)

### **Rationale:**

#### **Architecture:**
```
User Query
    ↓
[LLM] → RelationshipQueryPlan (intelligent planning)
    ↓
[Internal Builder] → JPQL Query (reliable generation)
    ↓
Execute
```

#### **Why Hybrid:**

**LLM Strength:**
- ✅ Understands natural language
- ✅ Extracts relationships
- ✅ Identifies filters
- ✅ Understands intent

**Builder Strength:**
- ✅ Generates queries deterministically
- ✅ Uses JPA Metamodel for actual field names
- ✅ Type-safe and validated
- ✅ Fast (no LLM call)

**Combined:**
- ✅ Intelligence + Reliability
- ✅ Cost efficient (one LLM call for plan)
- ✅ Highly reliable (deterministic generation)

### **Implementation:**
```java
// Step 1: LLM generates plan
RelationshipQueryPlan plan = llmPlanner.planQuery(query);
// Returns: Structured plan with relationships, filters

// Step 2: Builder generates JPQL
String jpql = jpqlBuilder.buildQuery(plan);
// Uses Metamodel to discover actual field names
// Generates reliable JPQL deterministically
```

### **Why Not Pure LLM:**
- ❌ Unreliable (can generate wrong queries)
- ❌ Expensive (LLM call per JPQL)
- ❌ Hard to debug (black box)
- ❌ Security risk (could generate malicious queries)

### **Impact:**
- ✅ Smart planning (LLM understands intent)
- ✅ Reliable execution (Builder generates correctly)
- ✅ Cost efficient (one LLM call)
- ✅ Debuggable (can inspect plan and JPQL)

---

## 🏗️ Consolidated Architecture

### **Module Dependencies:**

```
ai-infrastructure-relationship-query
    ↓ depends on (required)
ai-infrastructure-core
    ↓ provides
- AICoreService ✅ (for LLM - always needed)
- VectorDatabaseService ✅ (optional to USE)
- AIEmbeddingService ✅ (optional to USE)
```

### **Relationship Traversal:**

```
Two Options:
1. JPA Relationships (via Metamodel) ✅ Always works
2. Metadata (if entities indexed) ✅ Optional enhancement
```

### **Query Flow:**

```
User Query
    ↓
[LLM Planning] → RelationshipQueryPlan
    ↓
[Mode Selection] → STANDALONE or ENHANCED
    ↓
[JPA Query] → Relational filtering
    ↓ (if ENHANCED)
[Vector Ranking] → Semantic similarity
    ↓
[Return Strategy] → IDs or Full Data
    ↓
Results
```

---

## 📋 Complete Configuration

```yaml
ai:
  infrastructure:
    relationship:
      # Enable relationship queries
      enabled: true
      
      # Mode selection: ALWAYS auto-detect via LLM (default)
      # Users can override via QueryOptions.forceMode() if needed
      # LLM decides if semantic search is needed, unless user explicitly overrides
      
      # Vector search settings (only used if LLM decides semantic search is needed)
      enable-vector-search: true  # Enable vector capability
      default-similarity-threshold: 0.7
      
      # Return strategy
      default-return-mode: ids  # or "full"
      
      # Query settings
      max-traversal-depth: 3
      enable-query-caching: true
      query-cache-ttl-seconds: 3600
      enable-query-validation: true
      fallback-to-metadata: true
```

---

## 🎯 API Design

### **Main Service:**

```java
@Service
public class RelationshipQueryService {
    
    // Simple (uses defaults)
    public RAGResponse executeQuery(String query, List<String> entityTypes);
    
    // With options
    public RAGResponse executeQuery(String query, List<String> entityTypes, 
                                   QueryOptions options);
}
```

### **Query Options:**

```java
public class QueryOptions {
    private QueryMode forceMode;  // null = auto-detect via LLM (default)
    private ReturnMode returnMode = ReturnMode.IDS;  // Default: IDs
    private Double similarityThreshold;  // null = use default
    
    public static QueryOptions defaults() {
        return QueryOptions.builder().build();
    }
    
    // Usually not needed - LLM auto-detects
    public static QueryOptions forceStandalone() {
        return QueryOptions.builder()
            .forceMode(QueryMode.STANDALONE)
            .build();
    }
    
    public static QueryOptions forceEnhanced() {
        return QueryOptions.builder()
            .forceMode(QueryMode.ENHANCED)
            .build();
    }
}
```

### **Usage Examples:**

```java
// 1. Default: LLM auto-detects mode (most common)
RAGResponse response = queryService.executeQuery(query, entityTypes);
// LLM analyzes query and decides:
// - "Find similar products" → ENHANCED (needs vectors)
// - "Find orders from customers" → STANDALONE (relational only)

// 2. Override LLM: Force standalone mode
response = queryService.executeQuery(query, entityTypes,
    QueryOptions.builder()
        .forceMode(QueryMode.STANDALONE)  // Override LLM: relational only
        .build()
);

// 3. Override LLM: Force enhanced mode
response = queryService.executeQuery(query, entityTypes,
    QueryOptions.builder()
        .forceMode(QueryMode.ENHANCED)  // Override LLM: always use vectors
        .build()
);

// 4. Request full data (works with any mode)
response = queryService.executeQuery(query, entityTypes,
    QueryOptions.builder()
        .returnMode(ReturnMode.FULL)
        .forceMode(QueryMode.ENHANCED)  // Can combine options
        .build()
);
```

---

## ✅ Summary of All Decisions

### **1. ai-core Mandatory** ✅
- **Decision:** Required dependency
- **Why:** Simplifies LLM integration, consistent infrastructure
- **Impact:** Simpler code, better maintainability

### **2. Relationships Without Vectors** ✅
- **Decision:** Use JPA Metamodel
- **Why:** Relationships exist independently, no annotations needed
- **Impact:** Works with pure JPA entities

### **2.1. Automatic Schema Introspection** ✅
- **Decision:** Discover @AICapable entities and relationships at startup, cache schema, provide full context to LLM
- **Why:** Accurate schema discovery, better LLM planning, performance (cached), filtered scope (only AI-capable)
- **Impact:** Better LLM understanding, more accurate queries, automatic (no manual config)

### **3. Return Strategy** ✅
- **Decision:** IDs by default, full data optional
- **Why:** Efficient by default, flexible when needed
- **Impact:** Better performance, caller control

### **4. Mode Selection** ✅
- **Decision:** LLM-based auto-detection (default) with user override option
- **Why:** LLM already analyzes query, can determine if semantic search needed. Users can override when they know better.
- **Impact:** Simpler configuration by default, intelligent automatic decisions, flexible user control

### **5. JPQL Generation** ✅
- **Decision:** LLM plans, Builder generates
- **Why:** Intelligence + Reliability
- **Impact:** Smart planning, reliable execution

---

## 🎯 Design Principles

1. **Simplicity First** - Sensible defaults, no parameters needed
2. **Intelligence** - LLM auto-detects what's needed (semantic vs relational)
3. **Reliability** - Deterministic generation where possible
4. **Separation of Concerns** - LLM understands, code executes
5. **Performance** - Efficient by default, optimize when needed
6. **Cost Efficiency** - LLM decides when to use expensive operations (vectors)

---

## 📊 Architecture Summary

```
User Query
    ↓
[LLM Planning] → RelationshipQueryPlan (intelligent)
    ↓ (LLM decides)
[Mode Selection] → STANDALONE or ENHANCED (auto-detected)
    ↓
[JPA Query Builder] → JPQL Query (reliable)
    ↓
[Execute JPA Query] → Entity IDs (efficient)
    ↓ (if LLM decided ENHANCED)
[Vector Ranking] → Semantic similarity (if needed)
    ↓
[Return Strategy] → IDs or Full Data (flexible)
    ↓
Results
```

---

## ✅ Final Architecture

### **Dependencies:**
- ✅ `ai-infrastructure-core` (required)
- ✅ Spring Data JPA (required)
- ✅ LLM API (via ai-core)

### **Relationship Traversal:**
- ✅ JPA Metamodel (always works)
- ✅ Automatic schema introspection (startup discovery)
- ✅ Only @AICapable entities and relationships
- ✅ Metadata (optional enhancement)

### **Schema Discovery:**
- ✅ Automatic at startup (via JPA Metamodel)
- ✅ Only @AICapable entities included
- ✅ Only relationships to @AICapable entities
- ✅ Cached for performance
- ✅ Full entity context provided to LLM

### **Query Generation:**
- ✅ LLM plans (intelligent)
- ✅ Builder generates JPQL (reliable)

### **Mode Selection:**
- ✅ Configuration default
- ✅ Query override
- ✅ Auto-detection

### **Return Strategy:**
- ✅ IDs by default
- ✅ Full data optional

---

## 🚀 Implementation Ready

**All architectural decisions finalized!**

- ✅ Dependencies decided
- ✅ Relationship traversal decided
- ✅ Query generation decided
- ✅ Mode selection decided
- ✅ Return strategy decided

**Ready to implement!** 🎯
