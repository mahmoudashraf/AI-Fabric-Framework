# Module Architecture Guide: Relationship Query System

## 🏗️ Architecture Decision: Separate Module ✅

### **Structure:**

```
ai-infrastructure-module/
├── ai-infrastructure-core/                    (foundational - required)
│   └── Core AI capabilities (RAG, embeddings, vector search)
│
├── ai-infrastructure-relationship-query/      (NEW - optional)
│   └── Relationship-aware query system
│
└── ai-infrastructure-onnx-starter/            (existing - optional)
    └── ONNX model support
```

---

## 📦 Module Overview

### **ai-infrastructure-relationship-query**

**Purpose:** Optional module for relationship-aware natural language queries

**Dependencies:**
- `ai-infrastructure-core` (required)
- Spring Data JPA (for JPA queries)
- Jackson (for JSON)

**Key Features:**
- LLM-driven query planning
- Dynamic JPA query generation
- Unified relational + semantic search
- Automatic schema understanding

---

## 🔗 Integration Points

### **What Core Provides:**

```java
// Core module provides these services:
- AICoreService → LLM calls
- AIEmbeddingService → Embedding generation
- VectorDatabaseService → Vector operations
- AISearchableEntityRepository → Metadata access
- AISearchableEntity → Entity model
```

### **What Relationship Module Uses:**

```java
// Relationship module uses core services:
- AICoreService.generateContent() → Query planning
- AIEmbeddingService.generateEmbedding() → Query embeddings
- VectorDatabaseService.getVector() → Document vectors
- AISearchableEntityRepository → Metadata queries
```

### **What Relationship Module Adds:**

```java
// New services in relationship module:
- RelationshipQueryPlanner → LLM query planning
- DynamicJPAQueryBuilder → JPQL generation
- LLMDrivenJPAQueryService → Main orchestration
- EntityRelationshipMapper → Entity mapping
```

---

## 🎯 Usage Patterns

### **Pattern 1: Core Only (Basic Users)**

```xml
<!-- pom.xml -->
<dependency>
    <groupId>com.ai.infrastructure</groupId>
    <artifactId>ai-infrastructure-core</artifactId>
    <version>2.0.0</version>
</dependency>
```

```java
// Use standard RAG
@Autowired
private RAGService ragService;

public RAGResponse search(String query) {
    return ragService.performRag(request);
}
```

**Use Case:** Basic semantic search, no relationship queries needed

---

### **Pattern 2: Core + Relationship Query (Advanced Users)**

```xml
<!-- pom.xml -->
<dependency>
    <groupId>com.ai.infrastructure</groupId>
    <artifactId>ai-infrastructure-core</artifactId>
    <version>2.0.0</version>
</dependency>
<dependency>
    <groupId>com.ai.infrastructure</groupId>
    <artifactId>ai-infrastructure-relationship-query</artifactId>
    <version>2.0.0</version>
</dependency>
```

```yaml
# application.yml
ai:
  infrastructure:
    relationship:
      enabled: true  # Opt-in
```

```java
// Use relationship-aware queries
@Autowired
private LLMDrivenJPAQueryService relationshipQueryService;

public RAGResponse search(String query) {
    return relationshipQueryService.executeRelationshipQuery(
        query,
        Arrays.asList("document", "user", "project")
    );
}
```

**Use Case:** Need relationship-aware queries with natural language

---

### **Pattern 3: All Modules (Full Features)**

```xml
<!-- pom.xml -->
<dependency>
    <groupId>com.ai.infrastructure</groupId>
    <artifactId>ai-infrastructure-core</artifactId>
</dependency>
<dependency>
    <groupId>com.ai.infrastructure</groupId>
    <artifactId>ai-infrastructure-relationship-query</artifactId>
</dependency>
<dependency>
    <groupId>com.ai.infrastructure</groupId>
    <artifactId>ai-infrastructure-onnx-starter</artifactId>
</dependency>
```

**Use Case:** Full AI infrastructure with all features

---

## 🔧 Auto-Configuration

### **How It Works:**

1. **Module on Classpath**
   ```xml
   <dependency>
       <artifactId>ai-infrastructure-relationship-query</artifactId>
   </dependency>
   ```

2. **Spring Boot Auto-Detection**
   ```
   META-INF/spring.factories
   → RelationshipQueryAutoConfiguration
   ```

3. **Conditional Loading**
   ```java
   @ConditionalOnProperty(
       prefix = "ai.infrastructure.relationship",
       name = "enabled",
       matchIfMissing = true  // Enabled by default
   )
   ```

4. **Beans Created**
   - RelationshipQueryPlanner
   - DynamicJPAQueryBuilder
   - LLMDrivenJPAQueryService
   - etc.

---

## 📝 Configuration

### **application.yml:**

```yaml
ai:
  infrastructure:
    relationship:
      enabled: true                    # Enable relationship queries
      default-similarity-threshold: 0.7 # Semantic search threshold
      max-traversal-depth: 3           # Max relationship hops
      enable-query-caching: true       # Cache query plans
      query-cache-ttl-seconds: 3600    # Cache TTL
      enable-query-validation: true    # Validate queries
      fallback-to-metadata: true       # Fallback if JPA fails
```

---

## 🎨 Package Structure

```
com.ai.infrastructure.relationship/
├── RelationshipQueryPlanner.java          (LLM query planning)
├── DynamicJPAQueryBuilder.java           (JPQL generation)
├── LLMDrivenJPAQueryService.java        (Main service)
├── RelationshipTraversalService.java     (Metadata-based traversal)
├── JPARelationshipTraversalService.java  (JPA-based traversal)
├── EntityRelationshipMapper.java         (Entity mapping)
├── RelationshipSchemaProvider.java       (Schema info)
│
├── config/
│   ├── RelationshipQueryAutoConfiguration.java
│   └── RelationshipQueryProperties.java
│
└── dto/
    └── RelationshipQueryPlan.java        (Query plan DTO)
```

---

## ✅ Benefits of This Architecture

### **1. Separation of Concerns**
- Core = Foundational capabilities
- Relationship Query = Advanced feature
- Clear boundaries

### **2. Optional Dependency**
- Users opt-in if needed
- Smaller footprint for basic users
- Better performance

### **3. Follows Existing Pattern**
- Matches `ai-infrastructure-onnx-starter` pattern
- Consistent architecture
- Easy to understand

### **4. Easy Evolution**
- Can evolve independently
- Version separately
- Backward compatible

### **5. Better Testing**
- Test relationship features independently
- Mock core dependencies
- Isolated test suites

---

## 🚀 Migration Path

### **For Existing Users:**

**Step 1:** Add dependency (optional)
```xml
<dependency>
    <artifactId>ai-infrastructure-relationship-query</artifactId>
</dependency>
```

**Step 2:** Enable in config
```yaml
ai.infrastructure.relationship.enabled: true
```

**Step 3:** Use new service
```java
@Autowired
private LLMDrivenJPAQueryService relationshipQueryService;
```

**No breaking changes** - Core remains unchanged!

---

## 📊 Module Comparison

| Module | Purpose | Required? | Dependencies |
|--------|---------|-----------|--------------|
| **ai-infrastructure-core** | Foundational AI capabilities | ✅ Yes | Spring Boot |
| **ai-infrastructure-relationship-query** | Relationship queries | ⚠️ Optional | Core + JPA |
| **ai-infrastructure-onnx-starter** | ONNX models | ⚠️ Optional | Core + ONNX |

---

## 🎯 Summary

**Architecture:** Separate Module ✅

**Structure:**
```
ai-infrastructure-module/
├── ai-infrastructure-core/              (foundational)
└── ai-infrastructure-relationship-query/ (optional advanced feature)
```

**Benefits:**
- ✅ Clean separation
- ✅ Optional dependency
- ✅ Follows existing pattern
- ✅ Easy to evolve
- ✅ Better testing

**This is the right architectural choice!** 🎯
