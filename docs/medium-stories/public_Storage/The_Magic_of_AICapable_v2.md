# The Annotation Duo That's Killing AI Boilerplate 🎯

**Remember the dark ages of JDBC?**

```java
Connection conn = null;
try {
    conn = dataSource.getConnection();
    conn.setAutoCommit(false);
    // 47 lines of paranoid error handling
    conn.commit();
} catch (SQLException e) {
    conn.rollback(); // Did you remember this?
} finally {
    conn.close(); // What if this throws too?
}
```

Then Spring dropped `@Transactional` and we collectively exhaled.

**We're at that exact moment for AI.**

The last two years have been the "JDBC era" of AI engineering: manual embedding calls, manual vector DB upserts, manual chunking strategies, manual retry logic. Your service method looks like a Jackson Pollock painting of infrastructure code.

Enter **`@AICapable`** and its partner **`@AIProcess`** — the annotation duo that makes AI pipelines declarative.

---

## 💀 The "Before": 50 Lines of Regret

You want product search by meaning. Here's what that looks like today:

```java
// The "I wrote this at 2 AM" approach
public Product saveProduct(Product product) {
    // 1. Save to DB (the easy part)
    product = productRepo.save(product);

    try {
        // 2. Build embedding text (which fields? all of them? some?)
        String textToEmbed = product.getName() + " " + product.getDescription();
        
        // 3. Call embedding API (network failure incoming)
        float[] embedding = embeddingService.embed(textToEmbed);
        
        // 4. Store in vector DB (another network failure incoming)
        VectorPoint point = VectorPoint.builder()
            .id(product.getId().toString())
            .vector(embedding)
            .metadata(Map.of(
                "category", product.getCategory(),
                "price", product.getPrice().toString()
            ))
            .build();
        vectorDb.upsert("products", point);
        
    } catch (Exception e) {
        // 5. Now what? DB says product exists, vector DB disagrees.
        // Retry? Rollback? Cry?
        log.error("Welcome to distributed consistency hell", e);
    }
    
    return product;
}
```

This code commits several crimes:
- Business logic drowning in infrastructure
- No PII protection (hope no one saved their SSN in a product description)
- Synchronous = slow user experience
- Zero retry logic = fragile
- Untestable mess

---

## ✨ The "After": Intent Over Implementation

Here's the AI Fabric way:

```java
@Entity
@AICapable(entityType = "product")
public class Product {
    
    @Id 
    private UUID id;
    
    @AISearchable            // "Users can FIND by this"
    private String name;
    
    @AISearchable            // "Users can FIND by this too"
    private String description;
    
    @AIContext               // "AI needs to KNOW this"
    private BigDecimal price;
    
    @AIContext               // "AI needs to KNOW this"
    private String brand;
    
    private String sku;      // Not annotated = not in AI system
}
```

```java
@Service
public class ProductService {
    
    @AIProcess(entityType = "product", processType = "create")
    public Product createProduct(Product product) {
        return productRepo.save(product);
        // That's it. The framework handles the rest.
    }
}
```

**No embedding calls. No vector DB code. No retry logic. No regrets.**

---

## 🔍 "But Where's the Magic?" — Let's Trace the Flow

Senior engineers get nervous when things "just work." Fair. Let's trace exactly what happens when you call `createProduct()`:

### Step 1: The AOP Intercept 🕵️

Your method has `@AIProcess`. Spring's AOP machinery intercepts it:

```
ProductService.createProduct() called
    ↓
@AIProcess detected (entityType="product", processType="create")
    ↓
Method executes: productRepo.save(product)
    ↓
Return value captured: Product entity
    ↓
AI Processing Pipeline triggered
```

### Step 2: Field Discovery 🔎

The framework scans `Product.class` for AI annotations:

```java
// Framework discovers:
@AISearchable on "name"        → Will be embedded + in LLM context
@AISearchable on "description" → Will be embedded + in LLM context
@AIContext on "price"          → Will be in LLM context (no embedding)
@AIContext on "brand"          → Will be in LLM context (no embedding)
// sku → No annotation → Ignored
```

**No YAML required.** Annotations ARE the configuration.

### Step 3: Intelligent Storage 📝

The framework builds two things:

```java
// 1. searchableContent - from @AISearchable fields
String searchableContent = product.getName() + " " + product.getDescription();

// 2. metadata JSON - from @AIContext fields
Map<String, Object> metadata = Map.of(
    "price", product.getPrice(),
    "brand", product.getBrand()
);
```

Both stored in `AISearchableEntity` for later retrieval.

### Step 4: The PII Firewall 🛡️

Before text leaves your server:

```
Input:  "Premium Widget - Contact john@email.com for bulk orders"
         ↓
PII Scanner: EMAIL detected
         ↓
Output: "Premium Widget - Contact [EMAIL] for bulk orders"
```

You can't "forget" to sanitize. It happens automatically.

### Step 5: Async by Default ⚡

```java
@AICapable(
    entityType = "product",
    indexingStrategy = IndexingStrategy.ASYNC  // Default
)
```

Your HTTP request returns immediately. Background workers:
- Generate embeddings
- Handle retries with exponential backoff
- Manage API rate limits
- Store in vector database
- Ensure eventual consistency

The user doesn't wait for OpenAI. Your P99 stays sane.

### Step 6: Vector Storage 📦

```
Embedding generated: [0.023, -0.156, 0.891, ...]
         ↓
AISearchableEntity stored:
  - entityType: "product"
  - entityId: product.getId()
  - searchableContent: "Eco-friendly Toothbrush Natural bamboo..."
  - metadata: {"price": 29.99, "brand": "EcoLife"}
  - vectorId: "vec-abc-123"
         ↓
Vector DB upsert:
  - ID: "vec-abc-123"
  - Vector: embedding
```

---

## 🎭 Four Annotations, Clear Roles

| Annotation | Level | Purpose | Mental Model |
|------------|-------|---------|--------------|
| `@AICapable` | Class | "This entity is AI-enabled" | Like `@Entity` |
| `@AIProcess` | Method | "This method triggers AI processing" | Like `@Transactional` |
| `@AISearchable` | Field | "Users can FIND by this" | Semantic search |
| `@AIContext` | Field | "AI needs to KNOW this" | LLM context |

### The Two Field Annotations Explained

**`@AISearchable`** = *"Can users find this by meaning?"*

```java
@AISearchable
private String description;
// User searches: "eco-friendly products"
// Finds: "Biodegradable bamboo toothbrush" ✓
```

**`@AIContext`** = *"Does the AI need to know this?"*

```java
@AIContext
private BigDecimal price;
// User asks: "How much is the bamboo toothbrush?"
// AI responds: "The bamboo toothbrush costs $29.99" ✓
```

**The difference?**
- `@AISearchable` → Embedded for semantic search + sent to LLM
- `@AIContext` → NOT embedded (no semantic search) + sent to LLM

Price doesn't have "meaning" to search by. But the AI needs to know it to answer "How much?"

---

## 🚀 It's Not Just Embedding: It's "Capability"

Notice the name: **`@AICapable`**, not `@AIEmbeddable`.

By marking an entity as AI-Capable, you unlock:

### 🔍 Semantic Search
```java
// Find products by meaning, not keywords
searchService.search("eco-friendly packaging")
// Returns products with "biodegradable", "sustainable", "green" — 
// even if they don't contain "eco-friendly"
```

### 🗣️ Natural Language Queries
```java
// The Relationship Query Engine translates:
"Show me expensive electronics with good reviews"
         ↓
SELECT * FROM product p 
JOIN reviews r ON p.id = r.product_id
WHERE p.category = 'electronics' 
  AND p.price > 500 
  AND r.rating >= 4
```

### 💬 RAG-Ready
```java
// Your entity is instantly available for RAG:
ragService.query("What products help with organization?")
// LLM receives:
//   - searchableContent (from @AISearchable fields)
//   - metadata (from @AIContext fields)
// And generates a helpful response
```

### 🚚 Zero-Downtime Migration
```java
// Switching from Pinecone to Milvus?
migrationService.reindexAll("product");
// Scans all @AICapable entities, re-embeds, migrates
```

---

## 🎛️ When You Need Control: YAML Override

Annotations cover 90% of cases. For the other 10%, YAML has your back:

```yaml
# ai-entity-config.yml — OPTIONAL, only for overrides
ai-entities:
  product:
    searchable-fields:
      - name: "name"
        weight: 2.0           # Title matters more
      - name: "description"
        weight: 1.0
        include-in-rag: false # Exclude from LLM context (edge case)
    
    metadata-fields:
      - name: "price"
        type: "NUMERIC"
        include-in-search: true  # Enable filtering by price
```

**Annotations = "what is AI-enabled"**  
**YAML = "how to tune it" (optional)**

Production needs different weights than dev? Different YAML per environment. Zero code changes.

---

## 📊 The Numbers Don't Lie

| Metric | Manual Approach | AI Fabric |
|--------|-----------------|-----------|
| Lines of code | ~50 per entity | ~5 annotations |
| Error handling | Manual (usually forgotten) | Built-in |
| PII protection | Hope and prayer | Automatic |
| Retry logic | DIY | Included |
| Testability | "We'll test it in prod" | Isolated, mockable |
| Configuration | Scattered | Centralized |

---

## 🏆 Why Declarative Always Wins

```
1990s: Manual memory → Garbage Collection
2000s: Manual SQL    → ORMs
2010s: Manual DOM    → React
2020s: Manual AI     → @AICapable
```

**The pattern is clear: declarative always wins.**

Why? Because it separates *intent* from *implementation*.

When you write `@AISearchable`, you're saying "users can find this by meaning." You're not saying how to chunk it, which model to use, or how to handle rate limits. That's the framework's job.

When you write `@AIContext`, you're saying "the AI needs to know this value." You're not managing JSON serialization or metadata schemas. That's infrastructure.

When you write `@AIProcess`, you're saying "after this method, process the AI stuff." You're not managing async queues, retry policies, or vector DB connections.

**You declare intent. The framework implements.**

---

## 🎬 Getting Started

### 1. Add the dependency

```xml
<dependency>
    <groupId>com.ai.fabric</groupId>
    <artifactId>ai-infrastructure-core</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. Annotate your entity

```java
@Entity
@AICapable(entityType = "product")
public class Product {
    @Id 
    private Long id;
    
    @AISearchable              // Find by name
    private String name;
    
    @AISearchable              // Find by description
    private String description;
    
    @AIContext                 // AI knows the price
    private BigDecimal price;
    
    @AIContext                 // AI knows the brand
    private String brand;
}
```

### 3. Annotate your service method

```java
@AIProcess(entityType = "product", processType = "create")
public Product create(Product product) {
    return repository.save(product);
}
```

### 4. Search semantically

```java
List<Product> results = searchService.semanticSearch(
    "comfortable office chair",
    "product",
    10
);
```

**That's it.** No YAML required. No embedding service wiring. No vector DB configuration. The framework handles it.

---

## 🧠 Quick Decision Guide

```
┌─────────────────────────────────────────────┐
│ Should this field be in the AI system?      │
└─────────────────────┬───────────────────────┘
                      │
              ┌───────┴───────┐
              │               │
             Yes             No
              │               │
              │         Don't annotate
              │
              ▼
┌─────────────────────────────────────────────┐
│ Can users SEARCH by this field's meaning?   │
│                                             │
│ "sustainable products" finds "eco-friendly" │
│ "comfortable seating" finds "ergonomic"     │
└─────────────────────┬───────────────────────┘
                      │
              ┌───────┴───────┐
              │               │
             Yes             No
              │               │
              ▼               ▼
       @AISearchable    ┌─────────────────────┐
                        │ Does AI need to     │
                        │ KNOW this value?    │
                        │                     │
                        │ "How much is it?"   │
                        │ "What brand?"       │
                        │ "Is it in stock?"   │
                        └──────────┬──────────┘
                                   │
                           ┌───────┴───────┐
                           │               │
                          Yes             No
                           │               │
                           ▼               ▼
                      @AIContext      Don't annotate
```

---

## 🤔 FAQ

**Q: Do I need YAML config?**  
A: No. Annotations are auto-discovered. YAML is optional for fine-tuning (weights, filtering).

**Q: What's the difference between `@AISearchable` and `@AIContext`?**  
A: `@AISearchable` = users can find by meaning (embedded). `@AIContext` = AI knows the value (not embedded).

**Q: When do I use `@AIContext`?**  
A: For structured data like prices, ratings, dates, IDs — things with no "meaning" to search by, but the AI needs to know.

**Q: What about PII?**  
A: Automatic detection and redaction before any text leaves your server.

**Q: Is it synchronous?**  
A: ASYNC by default. Your HTTP response doesn't wait for embeddings.

**Q: Can I use my own vector DB?**  
A: Yes. SPI-based architecture. Plug in any provider.

---

## 🎯 The Bottom Line

Stop writing AI glue code.

```java
// Before: 50 lines of fragile infrastructure

// After:
@AISearchable    // Find by this
private String name;

@AIContext       // AI knows this
private BigDecimal price;
```

**That's the entire pitch.**

Declarative AI is here. Your infrastructure belongs in the framework, not your service layer.

Now go delete some code.

---

*The AI Fabric Framework — Because your code should describe business logic, not embedding pipelines.*

*[ai-fabric.dev](https://ai-fabric.dev)*
