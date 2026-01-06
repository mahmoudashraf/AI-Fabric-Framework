# AI Annotations User Guide

> **Source of Truth:** This guide is based on source code in `com.ai.infrastructure.annotation.*`

---

## Quick Reference

| Annotation | Level | Purpose |
|------------|-------|---------|
| `@AICapable` | Class | Enable AI features for an entity |
| `@AIProcess` | Method | Trigger AI processing on method execution |
| `@AIEmbedding` | Field | Configure embedding generation for a field |
| `@AIKnowledge` | Field | Mark field as knowledge for RAG |
| `@AISmartValidation` | Field/Method | Enable AI-powered validation |

---

## 1. `@AICapable` — Entity-Level AI Enablement

**Target:** `@Target(ElementType.TYPE)` — Classes only

**Purpose:** Declares an entity as AI-enabled and configures its default behaviors.

### Attributes

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `entityType` | String | `""` | **Required.** Unique identifier for this entity in AI system |
| `autoProcess` | boolean | `true` | Enable automatic AI processing |
| `autoEmbedding` | boolean | `true` | Auto-generate embeddings |
| `indexable` | boolean | `true` | Enable search indexing |
| `enableSearch` | boolean | `true` | Enable semantic search |
| `enableRecommendations` | boolean | `false` | Enable recommendations |
| `features` | String[] | `{"embedding", "search"}` | Features to enable |
| `indexingStrategy` | IndexingStrategy | `ASYNC` | Default indexing strategy |
| `onCreateStrategy` | IndexingStrategy | `AUTO` | Override for create ops |
| `onUpdateStrategy` | IndexingStrategy | `AUTO` | Override for update ops |
| `onDeleteStrategy` | IndexingStrategy | `AUTO` | Override for delete ops |
| `migrationRepository` | Class | `NoMigrationRepository` | JPA repo for data migration |
| `configFile` | String | `ai-entity-config.yml` | External config file path |

### Usage

```java
// Minimal
@Entity
@AICapable(entityType = "product")
public class Product { }

// Full configuration
@Entity
@AICapable(
    entityType = "product",
    autoEmbedding = true,
    indexable = true,
    enableSearch = true,
    features = {"embedding", "search", "rag"},
    indexingStrategy = IndexingStrategy.ASYNC,
    onCreateStrategy = IndexingStrategy.SYNC,  // Immediate on create
    onDeleteStrategy = IndexingStrategy.SYNC,  // Immediate on delete
    migrationRepository = ProductRepository.class
)
public class Product {
    @Id
    private Long id;
    private String name;
    private String description;
}
```

---

## 2. `@AIProcess` — Method-Level Processing Trigger

**Target:** `@Target(ElementType.METHOD)` — Methods only

**Purpose:** Marks a method as a trigger for AI processing. When the method executes, AI operations run automatically via AOP.

### Attributes

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `entityType` | String | `""` | Entity type (can be inferred from return type) |
| `processType` | String | `"create"` | Operation: `create`, `update`, `delete`, `search`, `analyze` |
| `generateEmbedding` | boolean | `true` | Generate embeddings for this operation |
| `indexForSearch` | boolean | `true` | Index for search |
| `enableAnalysis` | boolean | `false` | Enable AI analysis |
| `indexingStrategy` | IndexingStrategy | `AUTO` | Override entity strategy |

### Usage

```java
@Service
public class ProductService {
    
    // Basic - uses entity defaults
    @AIProcess(entityType = "product", processType = "create")
    @Transactional
    public Product createProduct(Product product) {
        return repository.save(product);
    }
    
    // Disable embedding for updates (performance)
    @AIProcess(entityType = "product", processType = "update", generateEmbedding = false)
    @Transactional
    public Product updateMetadataOnly(Product product) {
        return repository.save(product);
    }
    
    // Delete - no embedding, no indexing (just remove from index)
    @AIProcess(
        entityType = "product", 
        processType = "delete", 
        generateEmbedding = false, 
        indexForSearch = false
    )
    @Transactional
    public void deleteProduct(Long id) {
        repository.deleteById(id);
    }
    
    // Bulk import - use BATCH strategy
    @AIProcess(
        entityType = "product", 
        processType = "create", 
        indexingStrategy = IndexingStrategy.BATCH
    )
    @Transactional
    public List<Product> bulkImport(List<Product> products) {
        return repository.saveAll(products);
    }
    
    // Enable AI analysis
    @AIProcess(
        entityType = "product", 
        processType = "analyze", 
        enableAnalysis = true
    )
    public Product analyzeProduct(Long id) {
        return repository.findById(id).orElseThrow();
    }
}
```

---

## 3. `@AIEmbedding` — Field-Level Embedding Configuration

**Target:** `@Target(ElementType.FIELD)` — Fields only

**Purpose:** Configures how a field contributes to embedding generation.

### Key Attributes (Most Used)

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `weight` | double | `1.0` | Importance weight (higher = more influence) |
| `type` | String | `"text"` | Embedding type: `text`, `image`, `audio` |
| `autoGenerate` | boolean | `true` | Auto-generate embeddings |
| `indexable` | boolean | `true` | Include in vector index |
| `similarityThreshold` | double | `0.7` | Matching threshold (0.0-1.0) |
| `similarityMetric` | String | `"cosine"` | Metric: `cosine`, `euclidean`, `dot_product` |
| `dimension` | int | `1536` | Embedding vector dimension |
| `cacheable` | boolean | `true` | Enable caching |
| `cacheTtlSeconds` | long | `3600` | Cache TTL |
| `chunkingStrategy` | String | `"sentence"` | Text chunking strategy |
| `maxChunkSize` | int | `1000` | Max chunk size |
| `includeInSimilarity` | boolean | `true` | Include in similarity calculations |

### Usage

```java
@Entity
@AICapable(entityType = "article")
public class Article {
    
    @Id
    private Long id;
    
    @AIEmbedding(weight = 2.0)  // Title is 2x more important
    private String title;
    
    @AIEmbedding(weight = 1.0, chunkingStrategy = "paragraph", maxChunkSize = 500)
    private String content;
    
    @AIEmbedding(weight = 0.5)  // Summary is less important
    private String summary;
    
    @AIEmbedding(autoGenerate = false)  // Don't embed this field
    private String internalNotes;
}
```

---

## 4. `@AIKnowledge` — Field-Level Knowledge Configuration

**Target:** `@Target(ElementType.FIELD)` — Fields only

**Purpose:** Marks a field as knowledge for RAG (Retrieval-Augmented Generation) operations.

### Key Attributes (Most Used)

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `type` | String | `"text"` | Knowledge type: `text`, `structured`, `unstructured` |
| `category` | String | `""` | Category for organization |
| `priority` | int | `5` | Priority 1-10 (higher = more priority) |
| `importance` | int | `1` | Importance level |
| `indexable` | boolean | `true` | Enable indexing |
| `searchable` | boolean | `true` | Enable search |
| `retrievable` | boolean | `true` | Retrievable for RAG |
| `includeInRAG` | boolean | `true` | Include in RAG responses |
| `enableSemanticSearch` | boolean | `true` | Enable semantic search |
| `enableKeywordSearch` | boolean | `true` | Enable keyword search |
| `cacheable` | boolean | `true` | Enable caching |
| `cacheTtlSeconds` | long | `3600` | Cache TTL |
| `keywords` | String[] | `{}` | Keywords for this knowledge |

### Usage

```java
@Entity
@AICapable(entityType = "support-ticket")
public class SupportTicket {
    
    @Id
    private Long id;
    
    @AIKnowledge(category = "issue", priority = 8)
    private String problemDescription;
    
    @AIKnowledge(category = "resolution", priority = 10, importance = 2)
    private String resolution;
    
    @AIKnowledge(
        category = "context",
        includeInRAG = true,
        keywords = {"error", "troubleshooting"}
    )
    private String technicalDetails;
    
    @AIKnowledge(retrievable = false)  // Don't retrieve this in RAG
    private String internalComments;
}
```

---

## 5. `@AISmartValidation` — AI-Powered Validation

**Target:** `@Target({ElementType.FIELD, ElementType.METHOD})` — Fields or Methods

**Purpose:** Enable AI-powered intelligent validation.

### Attributes

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `rules` | String[] | `{}` | Validation rules |
| `validateContent` | boolean | `true` | Validate content |
| `validateFormat` | boolean | `true` | Validate format |
| `validateSemantic` | boolean | `true` | Semantic validation |
| `prompt` | String | `""` | Custom validation prompt |
| `required` | boolean | `true` | Is validation required |
| `severity` | SeverityLevel | `ERROR` | `INFO`, `WARNING`, `ERROR`, `CRITICAL` |
| `realTime` | boolean | `false` | Real-time validation |
| `crossField` | boolean | `false` | Cross-field validation |
| `context` | String | `""` | Validation context |

### Usage

```java
@Entity
@AICapable(entityType = "user-profile")
public class UserProfile {
    
    @AISmartValidation(
        validateContent = true,
        validateSemantic = true,
        rules = {"no_profanity", "no_spam"}
    )
    private String bio;
    
    @AISmartValidation(
        prompt = "Validate this is a professional job title",
        severity = AISmartValidation.SeverityLevel.WARNING
    )
    private String jobTitle;
    
    @AISmartValidation(
        validateFormat = true,
        rules = {"valid_url", "https_only"},
        severity = AISmartValidation.SeverityLevel.ERROR
    )
    private String websiteUrl;
}
```

---

## 6. `IndexingStrategy` — When to Index

| Strategy | Description | Use Case |
|----------|-------------|----------|
| `AUTO` | Inherit from parent | Method inherits from entity |
| `SYNC` | Immediate, same transaction | Compliance-critical, immediate consistency |
| `ASYNC` | Background, near-real-time | **Default.** Most CRUD operations |
| `BATCH` | Scheduled batch processing | High-volume imports, eventual consistency OK |

### Strategy Resolution Order

```
Method (@AIProcess.indexingStrategy)
    ↓ if AUTO
Entity Operation (@AICapable.onCreate/Update/DeleteStrategy)
    ↓ if AUTO
Entity Default (@AICapable.indexingStrategy)
    ↓ if not set
Framework Default (ASYNC)
```

---

## Complete Example

```java
// Entity with full AI configuration
@Entity
@AICapable(
    entityType = "product",
    autoEmbedding = true,
    indexable = true,
    enableSearch = true,
    features = {"embedding", "search", "rag"},
    indexingStrategy = IndexingStrategy.ASYNC,
    onCreateStrategy = IndexingStrategy.SYNC,
    onDeleteStrategy = IndexingStrategy.SYNC,
    migrationRepository = ProductRepository.class
)
public class Product {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @AIEmbedding(weight = 2.0)
    @AIKnowledge(category = "product", priority = 9)
    private String name;
    
    @AIEmbedding(weight = 1.5, chunkingStrategy = "paragraph")
    @AIKnowledge(category = "product", priority = 7, includeInRAG = true)
    private String description;
    
    @AIKnowledge(category = "metadata", searchable = true)
    private String category;
    
    @AISmartValidation(
        validateContent = true,
        rules = {"no_profanity", "product_appropriate"}
    )
    private String userReview;
    
    private BigDecimal price;  // No AI annotations - excluded from AI processing
}

// Service with AI processing triggers
@Service
@RequiredArgsConstructor
public class ProductService {
    
    private final ProductRepository repository;
    
    @AIProcess(entityType = "product", processType = "create")
    @Transactional
    public Product create(Product product) {
        return repository.save(product);
    }
    
    @AIProcess(entityType = "product", processType = "update")
    @Transactional
    public Product update(Product product) {
        return repository.save(product);
    }
    
    @AIProcess(
        entityType = "product", 
        processType = "delete",
        generateEmbedding = false,
        indexForSearch = false
    )
    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
    
    @AIProcess(
        entityType = "product",
        processType = "create",
        indexingStrategy = IndexingStrategy.BATCH
    )
    @Transactional
    public List<Product> bulkImport(List<Product> products) {
        return repository.saveAll(products);
    }
}
```

---

## Annotation Relationships

```
┌─────────────────────────────────────────────────────────────────┐
│                        @AICapable                               │
│                    (Entity/Class Level)                         │
│  • Declares entity as AI-enabled                                │
│  • Sets default indexing strategies                             │
│  • Configures features (embedding, search, RAG)                 │
└───────────────────────┬─────────────────────────────────────────┘
                        │
        ┌───────────────┼───────────────┐
        │               │               │
        ▼               ▼               ▼
┌───────────────┐ ┌───────────────┐ ┌───────────────┐
│ @AIEmbedding  │ │ @AIKnowledge  │ │@AISmartValid. │
│ (Field Level) │ │ (Field Level) │ │(Field/Method) │
│               │ │               │ │               │
│ • Weight      │ │ • Category    │ │ • Rules       │
│ • Dimension   │ │ • Priority    │ │ • Severity    │
│ • Chunking    │ │ • RAG config  │ │ • Prompt      │
└───────────────┘ └───────────────┘ └───────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────────┐
│                        @AIProcess                               │
│                      (Method Level)                             │
│  • Triggers AI processing on method execution                   │
│  • Can override entity defaults                                 │
│  • Specifies operation type (create/update/delete)              │
└─────────────────────────────────────────────────────────────────┘
```

---

## Best Practices

### DO ✅

```java
// 1. Always specify entityType
@AICapable(entityType = "product")

// 2. Use appropriate indexing strategies
@AICapable(
    indexingStrategy = IndexingStrategy.ASYNC,      // Default background
    onCreateStrategy = IndexingStrategy.SYNC,       // Immediate for creates
    onDeleteStrategy = IndexingStrategy.SYNC        // Immediate for deletes
)

// 3. Weight important fields higher
@AIEmbedding(weight = 2.0)  // Title
@AIEmbedding(weight = 1.0)  // Description

// 4. Disable unnecessary processing
@AIProcess(processType = "delete", generateEmbedding = false, indexForSearch = false)

// 5. Use BATCH for bulk operations
@AIProcess(indexingStrategy = IndexingStrategy.BATCH)
public List<Product> bulkImport(List<Product> products)
```

### DON'T ❌

```java
// 1. Don't forget entityType
@AICapable  // Missing entityType!

// 2. Don't use SYNC for everything (performance impact)
@AICapable(indexingStrategy = IndexingStrategy.SYNC)  // Only if needed

// 3. Don't over-annotate
@AIEmbedding  // Every field doesn't need this
@AIKnowledge  // Be selective

// 4. Don't mix up entity vs method annotations
@AIProcess  // This goes on methods, not classes!
public class Product { }
```

---

## Configuration Reference

```yaml
# application.yml
ai:
  enabled: true
  providers:
    embedding-provider: onnx  # or openai, cohere
  vector:
    database-type: lucene     # or milvus, qdrant
  indexing:
    default-strategy: ASYNC
    batch:
      size: 100
      interval-ms: 5000
```

---

*Generated from source code in `com.ai.infrastructure.annotation.*`*

