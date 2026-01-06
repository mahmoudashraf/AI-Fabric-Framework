# 4 Annotations That Killed My AI Boilerplate 🎯

**From 50 lines of regret to declarative bliss**

---

## The Code That Made Me Cry at 2 AM

```java
public Product saveProduct(Product product) {
    product = productRepo.save(product);  // ✅ Works
    
    try {
        String text = product.getName() + " " + product.getDescription();
        float[] embedding = embeddingService.embed(text);  // 💥 Network timeout
        vectorDb.upsert("products", point);  // Never reached
    } catch (Exception e) {
        log.error("Welcome to distributed consistency hell", e);
        // Now what? Roll back? Retry? Cry? 😭
    }
    
    return product;  // Half-indexed, unsearchable
}
```

**Result:** 847 products in DB, zero in search. 3 hours of debugging. One angry PM.

---

## The Fix: 4 Annotations

```java
@Entity
@AICapable(entityType = "product")
public class Product {
    
    @AISearchable    // Users can FIND by this
    private String name;
    
    @AISearchable    // Users can FIND by this
    private String description;
    
    @AIContext       // AI will KNOW this
    private BigDecimal price;
    
    @AIContext       // AI will KNOW this
    private String brand;
}
```

**No embedding calls. No vector DB code. No 2 AM debugging.**

---

## The Mental Model (30 Seconds)

```
┌─────────────────────────────────────────────────────────────────┐
│  WHICH ANNOTATION DO I USE?                                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Question 1: Can users SEARCH by this field's meaning?          │
│                                                                  │
│    "eco-friendly" finds "sustainable, biodegradable"            │
│    "comfortable chair" finds "ergonomic, lumbar support"        │
│                                                                  │
│    YES → @AISearchable                                          │
│    NO  → Continue to Question 2                                 │
│                                                                  │
│  ─────────────────────────────────────────────────────────────  │
│                                                                  │
│  Question 2: Does AI need to KNOW this when responding?         │
│                                                                  │
│    "How much is it?" → AI needs to know the price               │
│    "What brand?" → AI needs to know the brand                   │
│    "Is it in stock?" → AI needs to know availability            │
│                                                                  │
│    YES → @AIContext                                             │
│    NO  → Don't annotate (internal field)                        │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Real Example: E-Commerce Product

```java
@Entity
@AICapable(entityType = "product")
public class Product {
    
    @Id
    private Long id;
    
    @AISearchable   // "bluetooth speakers" finds "wireless audio"
    private String name;
    
    @AISearchable   // "noise cancelling" finds "blocks ambient sound"
    private String description;
    
    @AISearchable   // "electronics" or "audio equipment"
    private String category;
    
    @AIContext      // AI can answer "How much?"
    private BigDecimal price;
    
    @AIContext      // AI can answer "What brand?"
    private String brand;
    
    @AIContext      // AI can answer "Is it in stock?"
    private Boolean inStock;
    
    @AIContext      // AI can answer "Is it highly rated?"
    private Double rating;
    
    private String sku;           // Internal - not in AI
    private String warehouseCode; // Internal - not in AI
}
```

---

## The Service Layer (Boring, As It Should Be)

```java
@Service
@RequiredArgsConstructor
public class ProductService {
    
    private final ProductRepository repository;
    
    @AIProcess(entityType = "product", processType = "create")
    @Transactional
    public Product create(Product product) {
        return repository.save(product);
        // That's it. Framework handles the rest.
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

## What Happens When You Save

```
┌─────────────────────────────────────────────────────────────────┐
│  productService.create(product)                                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1️⃣  @AIProcess detected                                        │
│      ↓                                                           │
│  2️⃣  Method executes: repository.save()                         │
│      ↓                                                           │
│  3️⃣  Product saved to DB                                        │
│      ↓                                                           │
│  4️⃣  HTTP Response returns (+10ms, not +450ms!)                 │
│      ↓                                                           │
│  5️⃣  ASYNC: Background worker processes                         │
│      │                                                           │
│      ├─ Scan @AISearchable fields                               │
│      │  → Build searchableContent                               │
│      │                                                           │
│      ├─ Scan @AIContext fields                                  │
│      │  → Build metadata JSON                                   │
│      │                                                           │
│      ├─ Scan for PII (automatic!)                               │
│      │  → Redact if found                                       │
│      │                                                           │
│      ├─ Generate embedding                                       │
│      │  → [0.023, -0.156, 0.891, ...]                           │
│      │                                                           │
│      └─ Store in vector DB                                      │
│         → Retry on failure (exponential backoff)                │
│                                                                  │
│  Result: ✅ Searchable in 1-5 seconds                           │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## What Happens When Users Search

```
┌─────────────────────────────────────────────────────────────────┐
│  User: "eco-friendly dental products"                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1️⃣  Embed query → [0.019, -0.148, 0.887, ...]                  │
│                                                                  │
│  2️⃣  Vector search → Find similar embeddings                    │
│                                                                  │
│  3️⃣  Results (by MEANING, not keywords!):                       │
│                                                                  │
│      ┌─────────────────────────────────────────────────┐       │
│      │ "Bamboo Toothbrush"                              │       │
│      │ "Biodegradable bristles, sustainable handle..."  │       │
│      │ Similarity: 0.94 ✅                              │       │
│      └─────────────────────────────────────────────────┘       │
│                                                                  │
│      ┌─────────────────────────────────────────────────┐       │
│      │ "Natural Dental Floss"                           │       │
│      │ "Plant-based, compostable packaging..."          │       │
│      │ Similarity: 0.91 ✅                              │       │
│      └─────────────────────────────────────────────────┘       │
│                                                                  │
│  4️⃣  Build LLM context:                                         │
│      • searchableContent (@AISearchable fields)                 │
│      • metadata (@AIContext: price, brand, rating)              │
│                                                                  │
│  5️⃣  LLM generates response:                                    │
│      "I found 2 eco-friendly dental products:                   │
│       1. Bamboo Toothbrush ($29.99) ⭐ 4.8 - In Stock           │
│       2. Natural Dental Floss ($12.99) ⭐ 4.6 - In Stock"       │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

**Magic:** No product has "eco-friendly" literally. All found by *meaning*.

---

## The Difference

| | @AISearchable | @AIContext |
|---|---|---|
| **Purpose** | Users can FIND by this | AI needs to KNOW this |
| **Embedded?** | ✅ Yes (vector DB) | ❌ No |
| **Searchable?** | ✅ Yes (semantic) | ❌ No |
| **LLM Context?** | ✅ Yes | ✅ Yes |
| **Storage** | searchableContent | metadata JSON |
| **Use for** | Text with meaning | Structured data |
| **Examples** | name, description | price, rating, brand |

---

## Optional: YAML Fine-Tuning

Annotations cover 90%. YAML handles the other 10%:

```yaml
# ai-entity-config.yml — OPTIONAL overrides
ai-entities:
  product:
    searchable-fields:
      - name: "name"
        weight: 2.0        # Title more important
      - name: "description"
        weight: 1.0
    metadata-fields:
      - name: "price"
        include-in-search: true  # Enable price range filtering
```

**Annotations = what's AI-enabled**  
**YAML = how to tune it**

---

## Quick Reference

```
┌─────────────────────────────────────────────────────────────────┐
│  ANNOTATION CHEAT SHEET                                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  @AICapable(entityType = "...")                                 │
│  └── Class level: "This entity is AI-enabled"                   │
│                                                                  │
│  @AIProcess(entityType = "...", processType = "...")            │
│  └── Method level: "Trigger AI processing here"                 │
│                                                                  │
│  @AISearchable                                                  │
│  └── Field level: "Users can FIND by meaning"                   │
│      Examples: name, description, tags, content                 │
│                                                                  │
│  @AIContext                                                     │
│  └── Field level: "AI needs to KNOW this value"                 │
│      Examples: price, rating, brand, status                     │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## The Numbers

| Metric | Before | After |
|--------|--------|-------|
| Lines of code | ~50/entity | ~5 annotations |
| Failure modes | 6+ | 0 |
| PII protection | "Later" | Automatic |
| Response time | +450ms | +10ms |
| 2 AM debugging | Weekly | Never |

---

## Getting Started (2 Minutes)

### 1. Add dependency

```xml
<dependency>
    <groupId>com.ai.fabric</groupId>
    <artifactId>ai-infrastructure-core</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. Annotate entity

```java
@Entity
@AICapable(entityType = "product")
public class Product {
    @AISearchable private String name;
    @AISearchable private String description;
    @AIContext private BigDecimal price;
}
```

### 3. Annotate service

```java
@AIProcess(entityType = "product", processType = "create")
public Product create(Product product) {
    return repository.save(product);
}
```

### 4. Search semantically

```java
searchService.semanticSearch("comfortable chair", "product", 10);
```

**Done.** Zero YAML. Zero infrastructure. Zero regrets.

---

## The Bottom Line

```java
// Before: 50 lines of fragile infrastructure

// After:
@AISearchable private String name;        // Find by this
@AIContext private BigDecimal price;      // Know this
```

**Stop writing AI glue code. Start shipping features.**

---

*AI Fabric Framework — Declarative AI for Spring Boot*

*[ai-fabric.dev](https://ai-fabric.dev)*

