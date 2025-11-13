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

## 🎯 Decision 4: Mode Selection - Hybrid with Priority

### **Decision:**
**Hybrid approach: Configuration default + Query override + Auto-detection**

### **Priority Order:**

1. **Explicit Query Option** (highest priority)
   ```java
   QueryOptions.builder().mode(QueryMode.ENHANCED).build()
   ```

2. **Auto-Detection** (if enabled)
   ```java
   // Keyword or LLM-based detection
   ```

3. **Configuration Default** (fallback)
   ```yaml
   default-mode: standalone
   ```

### **Rationale:**

#### **Benefits:**
- ✅ **Simple by default** - No parameters needed
- ✅ **Flexible when needed** - Can override
- ✅ **Smart auto-detection** - Optimizes automatically
- ✅ **Cost optimization** - Use vectors selectively
- ✅ **Performance tuning** - Choose best mode per query

### **Configuration:**
```yaml
ai:
  infrastructure:
    relationship:
      # Default mode for all queries
      default-mode: standalone  # or "enhanced", "auto"
      
      # Auto-detection
      auto-detect-mode: true
      auto-detect-strategy: keyword  # or "llm"
      
      # Vector search (only if enhanced)
      enable-vector-search: false
```

### **Query Modes:**
```java
public enum QueryMode {
    STANDALONE,  // Relational only (no vectors)
    ENHANCED,    // Relational + semantic (with vectors)
    AUTO         // Auto-detect based on query
}
```

### **Implementation:**
```java
private QueryMode determineMode(String query, QueryOptions options) {
    // Priority 1: Explicit query option
    if (options.getMode() != null && options.getMode() != QueryMode.AUTO) {
        return options.getMode();
    }
    
    // Priority 2: Auto-detection (if enabled)
    if (config.isAutoDetectMode() || options.getMode() == QueryMode.AUTO) {
        QueryMode detected = autoDetectMode(query);
        if (detected != null) {
            return detected;
        }
    }
    
    // Priority 3: Configuration default
    return config.getDefaultMode();
}
```

### **Auto-Detection Strategy:**
```java
private QueryMode autoDetectMode(String query) {
    String lower = query.toLowerCase();
    
    // Semantic search indicators
    if (lower.matches(".*(similar|like|related|recommend).*")) {
        return QueryMode.ENHANCED;  // Needs vectors
    }
    
    // Structured query indicators
    if (lower.matches(".*(from|where|with|by|in|for).*")) {
        return QueryMode.STANDALONE;  // Pure relational
    }
    
    return null;  // Fall back to default
}
```

### **Impact:**
- ✅ Simple defaults for basic users
- ✅ Maximum flexibility for advanced users
- ✅ Smart auto-detection for everyone
- ✅ Cost optimization (use vectors selectively)

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
      
      # Mode selection
      default-mode: standalone  # or "enhanced", "auto"
      auto-detect-mode: true
      auto-detect-strategy: keyword  # or "llm"
      
      # Vector search (only if enhanced)
      enable-vector-search: false
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
    private QueryMode mode;  // null = use default/auto
    private ReturnMode returnMode = ReturnMode.IDS;  // Default: IDs
    private Boolean enableVectorSearch;  // null = use default
    private Double similarityThreshold;  // null = use default
    
    public static QueryOptions defaults() {
        return QueryOptions.builder().build();
    }
    
    public static QueryOptions auto() {
        return QueryOptions.builder()
            .mode(QueryMode.AUTO)
            .build();
    }
}
```

### **Usage Examples:**

```java
// Simple (uses all defaults)
RAGResponse response = queryService.executeQuery(query, entityTypes);

// Override mode
response = queryService.executeQuery(query, entityTypes,
    QueryOptions.builder()
        .mode(QueryMode.ENHANCED)
        .build()
);

// Request full data
response = queryService.executeQuery(query, entityTypes,
    QueryOptions.builder()
        .returnMode(ReturnMode.FULL)
        .build()
);

// Auto-detect mode
response = queryService.executeQuery(query, entityTypes,
    QueryOptions.auto()
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

### **3. Return Strategy** ✅
- **Decision:** IDs by default, full data optional
- **Why:** Efficient by default, flexible when needed
- **Impact:** Better performance, caller control

### **4. Mode Selection** ✅
- **Decision:** Hybrid (config default + query override + auto-detect)
- **Why:** Simple defaults, flexible when needed, smart detection
- **Impact:** Best of all worlds

### **5. JPQL Generation** ✅
- **Decision:** LLM plans, Builder generates
- **Why:** Intelligence + Reliability
- **Impact:** Smart planning, reliable execution

---

## 🎯 Design Principles

1. **Simplicity First** - Sensible defaults, no parameters needed
2. **Flexibility When Needed** - Can override defaults
3. **Reliability** - Deterministic generation where possible
4. **Intelligence** - LLM for understanding, code for execution
5. **Performance** - Efficient by default, optimize when needed
6. **Cost Efficiency** - Use expensive operations (LLM, vectors) selectively

---

## 📊 Architecture Summary

```
User Query
    ↓
[LLM Planning] → RelationshipQueryPlan (intelligent)
    ↓
[Mode Selection] → STANDALONE or ENHANCED (hybrid)
    ↓
[JPA Query Builder] → JPQL Query (reliable)
    ↓
[Execute JPA Query] → Entity IDs (efficient)
    ↓ (if ENHANCED)
[Vector Ranking] → Semantic similarity (optional)
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
- ✅ Metadata (optional enhancement)

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
