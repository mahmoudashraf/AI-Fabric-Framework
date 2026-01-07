# Mastering AI Annotations in 15 Minutes: A Developer's Deep Dive

*Everything you need to implement semantic search—code examples, gotchas, and the patterns that actually work.*

---

**TL;DR:** There are 4 annotations. `@AICapable` enables AI features on an entity. `@AISearchable` makes fields findable by meaning. `@AIContext` gives AI access to metadata. `@AIProcess` triggers the AI pipeline. That's it. You can be productive in 15 minutes.

---

## The Impatient Developer's Guide

Skip the philosophy. Here's what you need:

```java
@Entity
@AICapable(entityType = "product")
public class Product {
    
    @AISearchable   // Users can FIND by this (embedded, searchable)
    private String name;
    
    @AISearchable   // Rich text? AI finds by meaning
    private String description;
    
    @AIContext      // AI KNOWS this (metadata, not embedded)
    private BigDecimal price;
}

@AIProcess(entityType = "product", processType = "create")
public Product create(Product p) {
    return repo.save(p);
    // Done. Framework handles embedding, vector DB, retries.
}
```

That's the whole thing. Now let's understand what each part does.

---

## Annotation 1: @AICapable

**What:** Declares an entity as AI-enabled  
**Where:** Class level (on your @Entity)  
**Required:** Yes, for any entity that uses other AI annotations

```java
// Minimal usage:
@Entity
@AICapable(entityType = "product")
public class Product { }

// Full control:
@Entity
@AICapable(
    entityType = "product",
    indexingStrategy = IndexingStrategy.ASYNC,     // Default
    onCreateStrategy = IndexingStrategy.SYNC,      // Immediately searchable
    onDeleteStrategy = IndexingStrategy.SYNC       // Immediately removed
)
public class Product { }
```

**Attributes:**

| Attribute | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `entityType` | String | ✅ Yes | — | Unique identifier in AI system |
| `indexingStrategy` | IndexingStrategy | No | ASYNC | Default for all operations |
| `onCreateStrategy` | IndexingStrategy | No | (inherits) | Override for creates |
| `onUpdateStrategy` | IndexingStrategy | No | (inherits) | Override for updates |
| `onDeleteStrategy` | IndexingStrategy | No | (inherits) | Override for deletes |

**Indexing Strategies:**
- `ASYNC` — Fire and forget, best performance
- `SYNC` — Wait for indexing, guaranteed consistency
- `BATCH` — Queue for bulk processing, best for imports

---

## Annotation 2: @AISearchable

**What:** Marks a field as searchable by semantic meaning  
**Where:** Field level  
**Effect:** Field value is embedded (vectorized) and indexed for similarity search

```java
@AISearchable
private String name;

@AISearchable
@Column(columnDefinition = "TEXT")
private String description;
```

**What happens when you use @AISearchable:**
- ✅ Included in embedding vector (for similarity search)
- ✅ Stored in searchableContent
- ✅ Included in LLM context during RAG

**Key insight:** The embedding captures *meaning*, not just words. "comfortable office chair" will find products with "ergonomic executive seating" because the meanings are similar.

**Best for:**
- Product names and descriptions
- Article titles and content
- Problem descriptions and solutions
- Anything with semantic meaning that users might search for

---

## Annotation 3: @AIContext

**What:** Marks a field as metadata that AI needs to know  
**Where:** Field level  
**Effect:** Field value is stored as metadata, NOT embedded

```java
@AIContext
private BigDecimal price;

@AIContext
private Boolean inStock;

@AIContext
private Double rating;
```

**What happens when you use @AIContext:**
- ❌ NOT included in embedding vector (can't search by "similar price")
- ✅ Stored in metadata JSON
- ✅ Included in LLM context during RAG

**Key insight:** Use this for structured data that AI needs to answer questions like "How much does it cost?" or "Is it in stock?" — but that shouldn't affect similarity search.

**Best for:**
- Prices, ratings, counts
- Status fields, categories
- Dates, boolean flags
- Any structured data the AI needs for responses

---

## Annotation 4: @AIProcess

**What:** Triggers AI processing when a method executes  
**Where:** Method level (services)  
**Effect:** Intercepts method, processes entity through AI pipeline

```java
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
           generateEmbedding = false)  // No embedding needed for delete
@Transactional
public void delete(Long id) {
    repository.deleteById(id);
}
```

**Attributes:**

| Attribute | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `entityType` | String | No | (inferred) | Can infer from return type |
| `processType` | String | ✅ Yes | — | "create", "update", "delete" |
| `generateEmbedding` | boolean | No | true | Set false for deletes |
| `indexForSearch` | boolean | No | true | Set false to skip indexing |
| `indexingStrategy` | IndexingStrategy | No | (inherits) | Override strategy |

---

## The Decision Tree: Which Annotation Do I Use?

Here's the flowchart that makes this trivial:

```
Question 1: Should this field be in the AI system at all?
├── NO (internal, sensitive) → Don't annotate
└── YES → Continue to Question 2

Question 2: Can users SEARCH by this field's meaning?
├── YES ("comfortable" should find "ergonomic") → @AISearchable
└── NO → Continue to Question 3

Question 3: Does AI need to KNOW this value?
├── YES (to answer "How much?" or "In stock?") → @AIContext  
└── NO → Don't annotate
```

**Simplified:**
- Text with semantic meaning → `@AISearchable`
- Structured data AI needs → `@AIContext`
- Internal fields → Nothing

---

## Complete Example: E-Commerce Product

```java
@Entity
@AICapable(entityType = "product")
public class Product {
    
    @Id
    private Long id;
    
    // SEARCHABLE: Users find by meaning
    @AISearchable
    private String name;
    
    @AISearchable
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @AISearchable
    private String category;
    
    // CONTEXT: AI knows for responses
    @AIContext
    private BigDecimal price;
    
    @AIContext
    private Double rating;
    
    @AIContext
    private Boolean inStock;
    
    @AIContext
    private String brand;
    
    // INTERNAL: Not in AI system
    private String sku;
    private BigDecimal costPrice;  // Sensitive!
    private LocalDateTime createdAt;
}
```

---

## Complete Example: Support Ticket

```java
@Entity
@AICapable(entityType = "support-ticket")
public class SupportTicket {
    
    @Id
    private Long id;
    
    // SEARCHABLE: Find similar issues
    @AISearchable
    private String subject;
    
    @AISearchable
    @Column(columnDefinition = "TEXT")
    private String issueDescription;
    
    @AISearchable
    private String resolution;  // GOLD! Previous solutions are searchable
    
    // CONTEXT: AI knows for responses
    @AIContext
    private String status;
    
    @AIContext
    private String priority;
    
    @AIContext
    private Duration resolutionTime;
    
    // NEVER IN AI: Privacy
    private String customerId;
    private String customerEmail;
    private String internalNotes;
}
```

---

## Complete Example: Knowledge Article

```java
@Entity
@AICapable(
    entityType = "kb-article",
    onCreateStrategy = IndexingStrategy.SYNC  // Immediately searchable
)
public class KnowledgeBaseArticle {
    
    @Id
    private Long id;
    
    @AISearchable
    private String title;
    
    @AISearchable
    @Column(columnDefinition = "TEXT")
    private String content;
    
    @AISearchable
    private String problemDescription;
    
    @AISearchable
    private String solution;
    
    @AIContext
    private Double helpfulnessRating;
    
    @AIContext
    private Integer viewCount;
    
    @AIContext
    private String category;
    
    @AIContext
    private LocalDateTime lastUpdated;
    
    private String internalNotes;  // Internal only
}
```

---

## Common Gotchas

### ✅ DO:

- **Always specify `entityType`** — It's required and won't work without it
- **Use `@AISearchable` for text with semantic meaning** — That's what embeddings are for
- **Use `@AIContext` for structured data** — Numbers, booleans, status fields
- **Disable embedding on delete operations** — `generateEmbedding = false`
- **Use BATCH for bulk imports** — Much more efficient than individual SYNC calls

### ❌ DON'T:

- **Don't forget `entityType`** — Common mistake, causes silent failures
- **Don't use `@AISearchable` on numbers** — Use `@AIContext` instead
- **Don't annotate sensitive fields** — PII, internal data stays out
- **Don't use SYNC everywhere** — Performance killer for high-volume ops
- **Don't put `@AIProcess` on classes** — It goes on methods only!

---

## Quick Reference Cheat Sheet

### Question → Annotation

| Question | Annotation |
|----------|------------|
| Can users find by meaning? | `@AISearchable` |
| Does AI need to know? | `@AIContext` |
| Both? | `@AISearchable` (includes in context) |
| Neither? | Don't annotate |

### Field Type → Annotation

| Field Type | Examples | Annotation |
|------------|----------|------------|
| Text with meaning | name, description | `@AISearchable` |
| Structured data | price, rating | `@AIContext` |
| Internal fields | sku, costPrice | None |
| Sensitive data | email, PII | None |

### Process Type → Settings

| Operation | processType | generateEmbedding |
|-----------|-------------|-------------------|
| Create | "create" | true (default) |
| Update | "update" | true (default) |
| Delete | "delete" | **false** |

---

## Service Layer Pattern

```java
@Service
@RequiredArgsConstructor
public class ProductService {
    
    private final ProductRepository repository;
    // Note: No embedding service, no vector DB client, no retry template
    // Framework handles all of that
    
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
               generateEmbedding = false)
    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
    
    // Non-AI operations don't need @AIProcess
    public List<Product> findByBrand(String brand) {
        return repository.findByBrand(brand);
    }
}
```

---

## What the Framework Handles For You

When you use these annotations, the framework automatically:

- ✅ Extracts @AISearchable fields into embedding text
- ✅ Builds metadata from @AIContext fields
- ✅ Scans for PII before embedding (automatic redaction)
- ✅ Generates embeddings (with configurable provider)
- ✅ Stores in vector database (Qdrant, Pinecone, etc.)
- ✅ Handles retries with exponential backoff
- ✅ Records metrics (latency, count, cost)
- ✅ Provides distributed tracing
- ✅ Manages consistency between SQL and vector DB

You write business logic. The framework writes infrastructure.

---

## 15 Minutes to Production

Here's your implementation checklist:

1. **Add @AICapable to your entity** (1 minute)
2. **Mark fields with @AISearchable or @AIContext** (5 minutes)
3. **Add @AIProcess to your service methods** (5 minutes)
4. **Test with a few records** (4 minutes)

```java
// That's literally it:
@AICapable(entityType = "product")
public class Product {
    @AISearchable private String name;
    @AISearchable private String description;
    @AIContext private BigDecimal price;
}

@AIProcess(entityType = "product", processType = "create")
public Product create(Product p) { return repo.save(p); }
```

**Welcome to semantic search. You're done.**

---

*Part of the AI Annotations Story Series. See also: [Architect's Guide](/docs/ai-annotations-architect), [Killing Boilerplate](/docs/ai-annotations-killing-boilerplate).*

---

**Tags:** #Java #AI #SemanticSearch #SpringBoot #Tutorial #Engineering #Annotations

**Reading Time:** 15 minutes

---

*Bookmark this. You'll reference it every time you add a new entity to your AI-powered search. 👏*
