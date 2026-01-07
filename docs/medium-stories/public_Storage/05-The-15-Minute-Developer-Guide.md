# ⚡ The 15-Minute Developer Guide: Semantic Search From Zero to Production

**Subtitle:** *4 annotations, 3 code examples, 1 decision tree—everything you need to ship AI search today*

---

## 🎯 TL;DR

**📚 Annotations to learn:** 4  
**⏱️ Time to productivity:** 15 minutes  
**📝 Infrastructure code you write:** 0  
**🎓 ML expertise required:** None

**This is the guide I wish existed when I started.**

---

## 🚀 The Impatient Developer's Quickstart

Skip the theory. Here's what you need:

```java
@Entity
@AICapable(entityType = "product")  // ← Entity is AI-enabled
public class Product {
    
    @AISearchable  // 🔍 Users FIND by meaning
    private String name;
    
    @AISearchable  // 🔍 Rich semantic content
    private String description;
    
    @AIContext  // 💡 AI KNOWS this value
    private BigDecimal price;
}

@AIProcess(entityType = "product", processType = "create")
public Product create(Product p) {
    return repo.save(p);
    // ✨ Done. Framework handles embedding, vector DB, retries
}
```

**That's the entire integration.**

Now let's understand what each part does.

---

## 1️⃣ Annotation #1: @AICapable

**📍 Where:** Class level (on your `@Entity`)  
**🎯 Purpose:** Declares entity as AI-enabled  
**✅ Required:** Yes (for any AI features)

### Basic Usage

```java
@Entity
@AICapable(entityType = "product")
public class Product { }
```

### Advanced Usage

```java
@Entity
@AICapable(
    entityType = "product",
    indexingStrategy = IndexingStrategy.ASYNC,     // Default: async
    onCreateStrategy = IndexingStrategy.SYNC,      // Override: immediate
    onDeleteStrategy = IndexingStrategy.SYNC       // Override: immediate
)
public class Product { }
```

### Attributes Reference

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `entityType` | String | — | **Required.** Unique ID in AI system |
| `indexingStrategy` | IndexingStrategy | ASYNC | Default for all operations |
| `onCreateStrategy` | IndexingStrategy | (inherits) | Override for creates |
| `onUpdateStrategy` | IndexingStrategy | (inherits) | Override for updates |
| `onDeleteStrategy` | IndexingStrategy | (inherits) | Override for deletes |

### Indexing Strategies

- **ASYNC** ⚡ Fire and forget (best performance)
- **SYNC** 🔒 Wait for indexing (guaranteed consistency)
- **BATCH** 📦 Queue for bulk processing (best for imports)

---

## 2️⃣ Annotation #2: @AISearchable

**📍 Where:** Field level  
**🎯 Purpose:** Users can FIND by semantic meaning  
**🧬 Effect:** Field value is embedded (vectorized) and indexed

### What Happens

```java
@AISearchable
private String name;  // "bluetooth speakers"

// ↓ Framework converts to vector:
// [0.23, -0.14, 0.87, 0.45, -0.32, ...]
//
// ↓ User searches "wireless audio"
// ↓ Similarity match: 94%
// ✅ FOUND
```

### Storage

- ✅ Included in embedding vector (for similarity search)
- ✅ Stored in `searchableContent`
- ✅ Included in LLM context during RAG

### Best For

```java
@AISearchable
private String productName;      // ✅ Semantic content

@AISearchable
private String description;      // ✅ Rich text

@AISearchable
private String category;         // ✅ Concepts/topics

@AISearchable
@Column(columnDefinition = "TEXT")
private String content;          // ✅ Long text
```

### NOT For

```java
@AISearchable
private BigDecimal price;        // ❌ Use @AIContext

@AISearchable
private Long id;                 // ❌ Use regular SQL

@AISearchable
private String internalSku;      // ❌ Don't annotate
```

---

## 3️⃣ Annotation #3: @AIContext

**📍 Where:** Field level  
**🎯 Purpose:** AI KNOWS this value (not searchable)  
**💾 Effect:** Stored as metadata, NOT embedded

### What Happens

```java
@AIContext
private BigDecimal price;  // $29.99

// ↓ Stored in metadata JSON:
// { "price": 29.99 }
//
// ↓ User asks: "How much does it cost?"
// ✅ LLM can answer: "$29.99"
//
// ❌ But can't search by "similar price"
```

### Storage

- ❌ NOT included in embedding vector
- ✅ Stored in `metadata` JSON
- ✅ Included in LLM context during RAG

### Best For

```java
@AIContext
private BigDecimal price;        // ✅ Structured number

@AIContext
private Boolean inStock;         // ✅ Boolean flag

@AIContext
private Double rating;           // ✅ Numeric value

@AIContext
private String status;           // ✅ Enum/category

@AIContext
private LocalDateTime createdAt; // ✅ Date/time
```

---

## 4️⃣ Annotation #4: @AIProcess

**📍 Where:** Method level (service layer)  
**🎯 Purpose:** Triggers AI pipeline on method execution  
**⚡ Effect:** Intercepts method, processes entity through AI

### Basic Usage

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

@AIProcess(
    entityType = "product", 
    processType = "delete",
    generateEmbedding = false  // ← No embedding for deletes
)
@Transactional
public void delete(Long id) {
    repository.deleteById(id);
}
```

### Attributes Reference

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `entityType` | String | (inferred) | Can infer from return type |
| `processType` | String | "create" | "create", "update", "delete" |
| `generateEmbedding` | boolean | true | Set false for deletes |
| `indexForSearch` | boolean | true | Set false to skip indexing |
| `indexingStrategy` | IndexingStrategy | (inherits) | Override strategy |

---

## 🌲 The Decision Tree

```
❓ Should this field be in the AI system?
├─ ❌ NO (internal/sensitive) → Don't annotate
└─ ✅ YES
   │
   ❓ Can users SEARCH by this field's meaning?
   ├─ ✅ YES ("bluetooth" finds "wireless") → @AISearchable
   └─ ❌ NO
      │
      ❓ Does AI need to KNOW this value?
      ├─ ✅ YES ("How much?" needs price) → @AIContext
      └─ ❌ NO → Don't annotate
```

### Simplified

| Field Contains | Annotation |
|----------------|------------|
| Text with semantic meaning | `@AISearchable` |
| Structured data AI needs | `@AIContext` |
| Internal/private data | Nothing |

---

## 📦 Complete Example: E-Commerce Product

```java
@Entity
@AICapable(entityType = "product")
public class Product {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // 🔍 SEARCHABLE: Find by meaning
    @AISearchable
    private String name;
    
    @AISearchable
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @AISearchable
    private String category;
    
    // 💡 CONTEXT: AI knows for responses
    @AIContext
    private BigDecimal price;
    
    @AIContext
    private Double rating;
    
    @AIContext
    private Boolean inStock;
    
    @AIContext
    private String brand;
    
    // 🔒 INTERNAL: Not in AI
    private String sku;
    private BigDecimal costPrice;        // Sensitive!
    private LocalDateTime createdAt;
}
```

### Service Layer

```java
@Service
@RequiredArgsConstructor
public class ProductService {
    
    private final ProductRepository repository;
    // Note: No EmbeddingService, VectorDb, RetryTemplate!
    
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
}
```

---

## 🎫 Complete Example: Support Ticket

```java
@Entity
@AICapable(entityType = "support-ticket")
public class SupportTicket {
    
    @Id
    private Long id;
    
    // 🔍 SEARCHABLE: Find similar issues
    @AISearchable
    private String subject;
    
    @AISearchable
    @Column(columnDefinition = "TEXT")
    private String issueDescription;
    
    @AISearchable
    private String resolution;  // 💎 GOLD: Previous solutions!
    
    // 💡 CONTEXT: AI knows status/priority
    @AIContext
    private String status;
    
    @AIContext
    private String priority;
    
    @AIContext
    private Duration resolutionTime;
    
    // 🔒 PRIVACY: Never in AI
    private String customerId;
    private String customerEmail;
    private String internalNotes;
}
```

---

## 📚 Complete Example: Knowledge Article

```java
@Entity
@AICapable(
    entityType = "kb-article",
    onCreateStrategy = IndexingStrategy.SYNC  // ⚡ Immediately searchable
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

## ✅ DO's

```java
// ✅ Always specify entityType
@AICapable(entityType = "product")

// ✅ Use @AISearchable for text with meaning
@AISearchable
private String description;

// ✅ Use @AIContext for structured data
@AIContext
private BigDecimal price;

// ✅ Disable embedding on deletes
@AIProcess(processType = "delete", generateEmbedding = false)

// ✅ Use BATCH for bulk imports
@AIProcess(indexingStrategy = IndexingStrategy.BATCH)
public List<Product> bulkImport(List<Product> products)
```

## ❌ DON'Ts

```java
// ❌ Forgetting entityType
@AICapable  // Missing entityType!

// ❌ Using @AISearchable on numbers
@AISearchable
private BigDecimal price;  // Use @AIContext!

// ❌ Annotating sensitive fields
@AISearchable
private String customerEmail;  // Privacy violation!

// ❌ Using SYNC everywhere
@AICapable(indexingStrategy = IndexingStrategy.SYNC)  // Performance killer!

// ❌ Putting @AIProcess on classes
@AIProcess  // Goes on METHODS, not classes!
public class ProductService { }
```

---

## 🎯 Quick Reference Cheat Sheet

### Question → Annotation

| Question | Annotation |
|----------|------------|
| Can users find by meaning? | `@AISearchable` |
| Does AI need to know? | `@AIContext` |
| Both searchable AND contextual? | `@AISearchable` |
| Neither? | Don't annotate |

### Field Type → Annotation

| Type | Examples | Annotation |
|------|----------|------------|
| Semantic text | name, description | `@AISearchable` |
| Structured data | price, rating, status | `@AIContext` |
| Internal | sku, costPrice | None |
| Sensitive | email, PII | None |

### Process Type → Settings

| Operation | processType | generateEmbedding |
|-----------|-------------|-------------------|
| Create | "create" | true |
| Update | "update" | true |
| Delete | "delete" | **false** |

---

## 🔧 What the Framework Handles

When you use these annotations, the framework automatically:

- ✅ Extracts `@AISearchable` fields → builds embedding text
- ✅ Extracts `@AIContext` fields → builds metadata JSON
- ✅ Scans for PII → automatic redaction before embedding
- ✅ Generates embeddings → configurable provider (OpenAI, ONNX, etc.)
- ✅ Stores in vector database → Qdrant, Pinecone, etc.
- ✅ Handles retries → exponential backoff
- ✅ Records metrics → latency, count, cost
- ✅ Provides tracing → distributed observability
- ✅ Manages consistency → SQL ↔ vector DB sync

**You write business logic. Framework writes infrastructure.**

---

## ⚡ 15-Minute Implementation Checklist

1. **Add @AICapable to entity** (2 minutes)
2. **Mark fields with @AISearchable/@AIContext** (8 minutes)
3. **Add @AIProcess to service methods** (3 minutes)
4. **Test with sample records** (2 minutes)

```java
// ✅ That's it:
@AICapable(entityType = "product")
public class Product {
    @AISearchable private String name;
    @AISearchable private String description;
    @AIContext private BigDecimal price;
}

@AIProcess(entityType = "product", processType = "create")
public Product create(Product p) { return repo.save(p); }
```

**🎉 Welcome to semantic search. You're done.**

---

## 🐛 Common Gotchas & Solutions

### Gotcha #1: Missing entityType

```java
❌ @AICapable  // Silent failure!
✅ @AICapable(entityType = "product")
```

### Gotcha #2: Wrong annotation for numbers

```java
❌ @AISearchable private BigDecimal price;
✅ @AIContext private BigDecimal price;
```

### Gotcha #3: Embedding on deletes

```java
❌ @AIProcess(processType = "delete")  // Wastes API calls
✅ @AIProcess(processType = "delete", generateEmbedding = false)
```

### Gotcha #4: SYNC everywhere

```java
❌ @AICapable(indexingStrategy = SYNC)  // Slow!
✅ @AICapable  // ASYNC is default
```

### Gotcha #5: Annotating sensitive data

```java
❌ @AISearchable private String customerEmail;  // PII leak!
✅ private String customerEmail;  // Don't annotate
```

---

## 💡 Pro Tips

### Tip #1: Bulk Operations

```java
@AIProcess(
    entityType = "product",
    processType = "create",
    indexingStrategy = IndexingStrategy.BATCH  // ← Much faster
)
public List<Product> bulkImport(List<Product> products) {
    return repository.saveAll(products);
}
```

### Tip #2: Critical Data

```java
@AICapable(
    entityType = "product",
    onCreateStrategy = IndexingStrategy.SYNC  // ← Immediate
)
```

### Tip #3: Read-Only Search

```java
// No @AIProcess needed if you don't want auto-indexing
public List<Product> search(String query) {
    // Use search API directly
}
```

---

## 🎯 Title Options

1. **⚡ The 15-Minute Developer Guide** *(chosen)*
2. Semantic Search: Zero to Production in 15 Minutes
3. The Only AI Annotations Guide You Need
4. 4 Annotations, 3 Examples, 1 Decision Tree
5. Ship Semantic Search Today: A Developer's Quickstart

---

## 🏷️ Tags

`#Java` `#AI` `#SemanticSearch` `#SpringBoot` `#Tutorial` `#DeveloperGuide` `#Annotations` `#QuickStart`

---

## 🖼️ Suggested Header Images

1. **Code screenshot:** Clean annotation example with syntax highlighting
2. **Decision tree:** Visual flowchart for which annotation to use
3. **Developer at work:** Person coding with satisfied expression (stock photo)

---

**📖 Reading Time:** 12 minutes

---

*Bookmark this. You'll reference it every time you add semantic search to a new entity.* 🔖👏


