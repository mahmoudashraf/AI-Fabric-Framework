# AI Annotations User Guide

> **Source of Truth:** This guide reflects the redesigned annotation system.  
> See `AI_ANNOTATION_REDESIGN_SPEC.md` for implementation details.

---

## Quick Reference

| Annotation | Level | Purpose |
|------------|-------|---------|
| `@AICapable` | Class | Enable AI features for an entity |
| `@AIProcess` | Method | Trigger AI processing on method execution |
| `@AISearchable` | Field | Mark field for semantic search |
| `@AIContext` | Field | Mark field for LLM context (not embedded) |

### At a Glance

```java
@Entity
@AICapable(entityType = "product")
public class Product {
    
    @AISearchable   // Users can FIND by this (semantic search)
    private String name;
    
    @AISearchable
    private String description;
    
    @AIContext      // AI will KNOW this when responding
    private BigDecimal price;
    
    @AIContext
    private String brand;
}
```

---

## How It Works

### Storage Model

When an entity is saved, the framework creates an `AISearchableEntity`:

```
┌─────────────────────────────────────────────────────────────────┐
│  Your Entity                                                    │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  @AISearchable name = "Bamboo Toothbrush"               │    │
│  │  @AISearchable description = "Eco-friendly dental..."  │    │
│  │  @AIContext price = 29.99                               │    │
│  │  @AIContext brand = "EcoLife"                           │    │
│  └─────────────────────────────────────────────────────────┘    │
│                              │                                  │
│                              ▼                                  │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  AISearchableEntity                                     │    │
│  │                                                         │    │
│  │  searchableContent: "Bamboo Toothbrush Eco-friendly..." │    │
│  │  metadata: {"price": 29.99, "brand": "EcoLife"}         │    │
│  │  vectorId: "vec-abc-123" → [0.023, -0.156, ...]         │    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
```

### Query Flow

```
User: "eco-friendly dental products"
           │
           ▼
    Embed query → Vector search
           │
           ▼
    Find matching AISearchableEntity
           │
           ▼
    Build LLM context from:
    • searchableContent (@AISearchable fields)
    • metadata (@AIContext fields)
           │
           ▼
    LLM generates response:
    "The Bamboo Toothbrush by EcoLife ($29.99) is an eco-friendly..."
```

---

## 1. `@AICapable` — Entity-Level

**Target:** Classes only

**Purpose:** Declares an entity as AI-enabled. Field annotations (`@AISearchable`, `@AIContext`) are auto-discovered when this is present.

### Attributes

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `entityType` | String | `""` | **Required.** Unique identifier in AI system |
| `indexingStrategy` | IndexingStrategy | `ASYNC` | Default indexing strategy |
| `onCreateStrategy` | IndexingStrategy | `AUTO` | Override for create ops |
| `onUpdateStrategy` | IndexingStrategy | `AUTO` | Override for update ops |
| `onDeleteStrategy` | IndexingStrategy | `AUTO` | Override for delete ops |

### Usage

```java
// Minimal — recommended for most cases
@Entity
@AICapable(entityType = "product")
public class Product {
    @Id
    private Long id;
    
    @AISearchable
    private String name;
    
    @AIContext
    private BigDecimal price;
}

// With indexing strategy overrides
@Entity
@AICapable(
    entityType = "product",
    indexingStrategy = IndexingStrategy.ASYNC,
    onCreateStrategy = IndexingStrategy.SYNC,   // Immediate indexing on create
    onDeleteStrategy = IndexingStrategy.SYNC    // Immediate removal on delete
)
public class Product {
    // ...
}
```

---

## 2. `@AIProcess` — Method-Level

**Target:** Methods only

**Purpose:** Triggers AI processing when the method executes (via AOP).

### Attributes

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `entityType` | String | `""` | Entity type (can be inferred from return type) |
| `processType` | String | `"create"` | Operation: `create`, `update`, `delete` |
| `generateEmbedding` | boolean | `true` | Generate embeddings |
| `indexForSearch` | boolean | `true` | Index for search |
| `indexingStrategy` | IndexingStrategy | `AUTO` | Override strategy |

### Usage

```java
@Service
@RequiredArgsConstructor
public class ProductService {
    
    private final ProductRepository repository;
    
    // Create — full processing
    @AIProcess(entityType = "product", processType = "create")
    @Transactional
    public Product create(Product product) {
        return repository.save(product);
    }
    
    // Update — full processing
    @AIProcess(entityType = "product", processType = "update")
    @Transactional
    public Product update(Product product) {
        return repository.save(product);
    }
    
    // Delete — no embedding needed, just remove from index
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
    
    // Bulk import — use BATCH for performance
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

## 3. `@AISearchable` — Field-Level

**Target:** Fields only

**Purpose:** Mark a field for **semantic search**. Users can find entities by searching for similar meaning.

### What It Does

- ✅ Included in embedding vector (for similarity search)
- ✅ Stored in `AISearchableEntity.searchableContent`
- ✅ Included in LLM context during RAG

### Usage

```java
@Entity
@AICapable(entityType = "article")
public class Article {
    
    @Id
    private Long id;
    
    @AISearchable   // Can find by "machine learning basics"
    private String title;
    
    @AISearchable   // Can find by concepts in content
    private String content;
    
    @AISearchable   // Can find by topics
    private String tags;
    
    private String authorId;  // Not searchable, not in AI
}
```

### Mental Model

> **"Can users FIND this entity by searching for words related to this field?"**
> 
> If yes → `@AISearchable`

---

## 4. `@AIContext` — Field-Level

**Target:** Fields only

**Purpose:** Mark a field for **LLM context** (without embedding). The AI will know this value when responding.

### What It Does

- ❌ NOT included in embedding vector (no semantic search)
- ✅ Stored in `AISearchableEntity.metadata` (JSON)
- ✅ Included in LLM context during RAG

### When to Use

Use for structured data that:
- Has no semantic meaning (prices, ratings, IDs)
- Shouldn't influence search results
- But the LLM needs to know when generating responses

### Usage

```java
@Entity
@AICapable(entityType = "product")
public class Product {
    
    @Id
    private Long id;
    
    @AISearchable
    private String name;
    
    @AISearchable
    private String description;
    
    @AIContext      // LLM knows the price (can answer "How much?")
    private BigDecimal price;
    
    @AIContext      // LLM knows the brand
    private String brand;
    
    @AIContext      // LLM knows the rating
    private Double rating;
    
    @AIContext      // LLM knows availability
    private Boolean inStock;
    
    private String internalSku;  // Not in AI system at all
}
```

### Mental Model

> **"Does the AI need to KNOW this value when answering questions?"**
> 
> If yes → `@AIContext`

---

## 5. `IndexingStrategy` — When to Index

| Strategy | Description | Use Case |
|----------|-------------|----------|
| `AUTO` | Inherit from parent | Default for `@AIProcess` |
| `SYNC` | Immediate, blocking | Critical data, compliance |
| `ASYNC` | Background, non-blocking | **Default.** Most operations |
| `BATCH` | Scheduled batch | High-volume imports |

### Resolution Order

```
@AIProcess.indexingStrategy
    ↓ if AUTO
@AICapable.onCreateStrategy (or onUpdate/onDelete)
    ↓ if AUTO
@AICapable.indexingStrategy
    ↓ if not set
Framework Default (ASYNC)
```

---

## YAML Override (Optional)

Annotations provide sensible defaults. Use YAML **only** when you need fine-tuned control.

### When to Use YAML

| Scenario | Annotation | YAML Override |
|----------|------------|---------------|
| Basic field marking | ✅ `@AISearchable` | Not needed |
| Custom weight | `@AISearchable` | ✅ `weight: 2.0` |
| Exclude from RAG | `@AISearchable` | ✅ `include-in-rag: false` |
| Enable filtering | `@AIContext` | ✅ `include-in-search: true` |

### Example

```java
@Entity
@AICapable(entityType = "article")
public class Article {
    
    @AISearchable
    private String title;
    
    @AISearchable
    private String content;
    
    @AIContext
    private String author;
    
    @AIContext
    private LocalDateTime publishDate;
}
```

```yaml
# ai-entity-config.yml — Optional overrides
ai-entities:
  article:
    searchable-fields:
      - name: "title"
        weight: 3.0           # Title is 3x more important
        include-in-rag: true
      - name: "content"
        weight: 1.0
        include-in-rag: true
    
    metadata-fields:
      - name: "author"
        type: "TEXT"
        include-in-search: true  # Enable filtering by author
      - name: "publishDate"
        type: "DATE"
        include-in-search: true  # Enable filtering by date
```

---

## Complete Examples

### Example 1: E-Commerce Product

```java
@Entity
@AICapable(entityType = "product")
public class Product {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @AISearchable   // "Find running shoes"
    private String name;
    
    @AISearchable   // "Find shoes with good arch support"
    private String description;
    
    @AISearchable   // "Find athletic footwear"
    private String category;
    
    @AIContext      // "How much does it cost?"
    private BigDecimal price;
    
    @AIContext      // "What brand is it?"
    private String brand;
    
    @AIContext      // "Is it highly rated?"
    private Double rating;
    
    @AIContext      // "Is it in stock?"
    private Integer stockQuantity;
    
    private String sku;           // Internal only
    private String warehouseCode; // Internal only
}

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
    
    @AIProcess(entityType = "product", processType = "delete",
               generateEmbedding = false, indexForSearch = false)
    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
```

### Example 2: Knowledge Base Article

```java
@Entity
@AICapable(
    entityType = "kb-article",
    indexingStrategy = IndexingStrategy.ASYNC,
    onCreateStrategy = IndexingStrategy.SYNC  // Immediate for new articles
)
public class KnowledgeBaseArticle {
    
    @Id
    private Long id;
    
    @AISearchable   // Find by title
    private String title;
    
    @AISearchable   // Find by content meaning
    @Column(columnDefinition = "TEXT")
    private String content;
    
    @AISearchable   // Find by keywords
    private String tags;
    
    @AIContext      // Show author in response
    private String author;
    
    @AIContext      // Show last updated date
    private LocalDateTime lastUpdated;
    
    @AIContext      // Show view count
    private Integer viewCount;
    
    @AIContext      // Show helpfulness rating
    private Double helpfulnessRating;
}
```

### Example 3: Support Ticket

```java
@Entity
@AICapable(entityType = "support-ticket")
public class SupportTicket {
    
    @Id
    private Long id;
    
    @AISearchable   // Find similar issues
    private String subject;
    
    @AISearchable   // Find by problem description
    private String issueDescription;
    
    @AISearchable   // Find by solution
    private String resolution;
    
    @AIContext      // Know the status
    private String status;
    
    @AIContext      // Know the priority
    private String priority;
    
    @AIContext      // Know when created
    private LocalDateTime createdAt;
    
    @AIContext      // Know resolution time
    private LocalDateTime resolvedAt;
    
    private String customerId;     // Not in AI (privacy)
    private String internalNotes;  // Not in AI (internal)
}
```

---

## Decision Flowchart

```
┌─────────────────────────────────────────┐
│ Should this field be in the AI system? │
└─────────────────────┬───────────────────┘
                      │
              ┌───────┴───────┐
              │               │
              ▼               ▼
           Yes              No
              │               │
              │               ▼
              │         Don't annotate
              │
              ▼
┌─────────────────────────────────────────┐
│ Can users SEARCH by this field's        │
│ meaning/concepts?                       │
│                                         │
│ "sustainable products" → description    │
│ "machine learning" → content            │
└─────────────────────┬───────────────────┘
                      │
              ┌───────┴───────┐
              │               │
              ▼               ▼
           Yes              No
              │               │
              ▼               ▼
       @AISearchable    ┌─────────────────┐
                        │ Does AI need to │
                        │ KNOW this value │
                        │ when responding?│
                        │                 │
                        │ "How much?"     │
                        │ "What brand?"   │
                        └────────┬────────┘
                                 │
                         ┌───────┴───────┐
                         │               │
                         ▼               ▼
                       Yes              No
                         │               │
                         ▼               ▼
                    @AIContext      Don't annotate
```

---

## Best Practices

### DO ✅

```java
// 1. Always specify entityType
@AICapable(entityType = "product")

// 2. Use @AISearchable for semantic content
@AISearchable
private String description;

// 3. Use @AIContext for structured data the AI needs
@AIContext
private BigDecimal price;

// 4. Disable unnecessary processing on delete
@AIProcess(processType = "delete", generateEmbedding = false, indexForSearch = false)

// 5. Use BATCH for bulk operations
@AIProcess(indexingStrategy = IndexingStrategy.BATCH)
public List<Product> bulkImport(List<Product> products)

// 6. Use YAML for fine-tuning only
// ai-entity-config.yml
searchable-fields:
  - name: "title"
    weight: 2.0  # Override weight
```

### DON'T ❌

```java
// 1. Don't forget entityType
@AICapable  // ❌ Missing entityType!

// 2. Don't annotate internal/private fields
@AISearchable
private String internalNotes;  // ❌ Should not be searchable

// 3. Don't use @AISearchable for non-semantic data
@AISearchable
private BigDecimal price;  // ❌ Use @AIContext instead

// 4. Don't over-annotate (be selective)
@AISearchable  // Every field?
@AIContext     // Every field?

// 5. Don't use SYNC everywhere (performance impact)
@AICapable(indexingStrategy = IndexingStrategy.SYNC)  // ❌ Only if needed

// 6. Don't put @AIProcess on classes
@AIProcess  // ❌ This goes on methods!
public class Product { }
```

---

## Migration from Old Annotations

| Old Annotation | New Annotation | Notes |
|----------------|----------------|-------|
| `@AIEmbedding` | `@AISearchable` | Remove all attributes, use YAML if needed |
| `@AIKnowledge` | Remove or `@AISearchable` | Usually redundant with `@AISearchable` |
| `@AISmartValidation` | Remove | Feature deprecated |

### Before

```java
@AIEmbedding(weight = 1.5, chunkingStrategy = "sentence", maxChunkSize = 500)
@AIKnowledge(category = "product", priority = 8, includeInRAG = true)
private String description;
```

### After

```java
@AISearchable
private String description;
```

```yaml
# If you need weight override
ai-entities:
  product:
    searchable-fields:
      - name: "description"
        weight: 1.5
```

---

## Summary

| Question | Answer | Annotation |
|----------|--------|------------|
| Can users find this by meaning? | Yes | `@AISearchable` |
| Does AI need to know this value? | Yes | `@AIContext` |
| Both searchable AND contextual? | — | `@AISearchable` (includes both) |
| Neither? | — | Don't annotate |

```java
@Entity
@AICapable(entityType = "product")
public class Product {
    
    @AISearchable   // Search + Context
    private String name;
    
    @AISearchable   // Search + Context
    private String description;
    
    @AIContext      // Context only (no search)
    private BigDecimal price;
    
    @AIContext      // Context only (no search)  
    private String brand;
    
    private String sku;  // Not in AI system
}
```

---

*Last Updated: January 2026*
