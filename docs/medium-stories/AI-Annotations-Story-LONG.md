# From 50 Lines of Regret to 4 Annotations of Bliss 🎯

**The Story of How We Killed AI Boilerplate Forever**

---

## The 2 AM Incident That Changed Everything

Picture this: It's 2 AM. You're debugging production. The logs say "embedding failed," but your database says the product exists. Your vector DB? It doesn't know the product ever existed.

Sound familiar?

```java
// The crime scene (circa 2024)
public Product saveProduct(Product product) {
    product = productRepo.save(product);  // ✅ Works
    
    try {
        String text = product.getName() + " " + product.getDescription();
        float[] embedding = embeddingService.embed(text);  // 💥 Network timeout
        vectorDb.upsert("products", point);  // Never reached
    } catch (Exception e) {
        log.error("Welcome to distributed consistency hell", e);
        // Now what? Roll back the DB save? Retry? Cry?
    }
    
    return product;  // Returns a product that's half-indexed
}
```

This code was deployed in production. By someone who thought they knew what they were doing. 

That someone was me.

The fallout:
- 847 products existed in the database but couldn't be found via search
- 3 hours of debugging at 2 AM
- 1 very angry PM asking why "bluetooth speakers" returned zero results
- 0 stars on my performance review

**That night, I swore: "Never again."**

---

## The Revelation: Declarative Always Wins

Remember when we wrote JDBC manually?

```java
Connection conn = null;
PreparedStatement ps = null;
ResultSet rs = null;
try {
    conn = dataSource.getConnection();
    ps = conn.prepareStatement("SELECT * FROM users WHERE id = ?");
    ps.setLong(1, userId);
    rs = ps.executeQuery();
    // 40 more lines of closing resources correctly
} finally {
    if (rs != null) rs.close();  // Did you remember all three?
    if (ps != null) ps.close();
    if (conn != null) conn.close();
}
```

Then Spring dropped `@Transactional` and we collectively exhaled.

**We're at that exact moment for AI.**

The last two years have been the "JDBC era" of AI engineering. Manual embedding calls. Manual vector DB operations. Manual retry logic. Manual PII protection (i.e., none).

What if I told you the entire mess above could become:

```java
@Entity
@AICapable(entityType = "product")
public class Product {
    
    @AISearchable
    private String name;
    
    @AISearchable  
    private String description;
    
    @AIContext
    private BigDecimal price;
}
```

No embedding calls. No vector DB code. No consistency nightmares.

**Welcome to declarative AI.**

---

## The Four Horsemen of AI Simplicity

Let me introduce you to the annotation family that's about to change your life:

```
┌─────────────────────────────────────────────────────────────────┐
│  THE AI ANNOTATION FAMILY                                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ @AICapable                                              │    │
│  │ Level: Class                                            │    │
│  │ Purpose: "This entity is AI-enabled"                    │    │
│  │ Think: Like @Entity for JPA                             │    │
│  └─────────────────────────────────────────────────────────┘    │
│                              │                                   │
│                              ▼                                   │
│  ┌──────────────────┐   ┌──────────────────┐                    │
│  │ @AISearchable    │   │ @AIContext       │                    │
│  │ Level: Field     │   │ Level: Field     │                    │
│  │ "Find by this"   │   │ "Know this"      │                    │
│  │ ↓                │   │ ↓                │                    │
│  │ Embedded +       │   │ Metadata JSON    │                    │
│  │ Searchable +     │   │ (not embedded)   │                    │
│  │ LLM Context      │   │ + LLM Context    │                    │
│  └──────────────────┘   └──────────────────┘                    │
│                              │                                   │
│                              ▼                                   │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ @AIProcess                                              │    │
│  │ Level: Method                                           │    │
│  │ Purpose: "When this runs, trigger AI processing"        │    │
│  │ Think: Like @Transactional for databases                │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### The Mental Model

Here's all you need to remember:

| Annotation | Question It Answers |
|------------|---------------------|
| `@AICapable` | "Is this entity AI-enabled?" |
| `@AIProcess` | "When does AI processing happen?" |
| `@AISearchable` | "Can users FIND this by meaning?" |
| `@AIContext` | "Does AI need to KNOW this value?" |

That's it. Four annotations. Zero infrastructure code.

---

## Real Scenario #1: The E-Commerce Search That Actually Works

### The Problem

ShopMart has 50,000 products. Users search for "comfortable office chair" and get zero results because no product has those exact words. Meanwhile, they have 847 ergonomic chairs with descriptions like "lumbar support" and "all-day sitting comfort."

### The Old Way (aka The Pain)

```java
@Service
public class ProductSearchService {
    
    private final ProductRepository productRepo;
    private final EmbeddingService embeddingService;
    private final VectorDatabase vectorDb;
    private final PIIScanner piiScanner;
    
    public Product createProduct(Product product) {
        // 1. Save to database
        product = productRepo.save(product);
        
        try {
            // 2. Build text for embedding (which fields? who knows!)
            String text = buildEmbeddingText(product);
            
            // 3. Scan for PII (hope we didn't forget!)
            if (piiScanner.containsPII(text)) {
                text = piiScanner.redact(text);
            }
            
            // 4. Generate embedding (network call #1)
            float[] embedding = embeddingService.embed(text);
            
            // 5. Build vector point with metadata
            VectorPoint point = VectorPoint.builder()
                .id(product.getId().toString())
                .vector(embedding)
                .metadata(Map.of(
                    "price", product.getPrice().toString(),
                    "brand", product.getBrand(),
                    "category", product.getCategory()
                ))
                .build();
            
            // 6. Store in vector DB (network call #2)
            vectorDb.upsert("products", point);
            
        } catch (EmbeddingException e) {
            // Retry? Give up? The product is already saved...
            log.error("Embedding failed for product {}", product.getId(), e);
            // Good luck finding this product via search!
        } catch (VectorDbException e) {
            // The embedding worked but storage failed. Now what?
            log.error("Vector storage failed for product {}", product.getId(), e);
            // Orphaned embedding in memory, inconsistent state
        }
        
        return product;
    }
    
    private String buildEmbeddingText(Product product) {
        // Is this right? Should we include more fields? Fewer?
        // What about weighting? What if description is 10x longer than name?
        return product.getName() + " " + product.getDescription();
    }
}
```

**Lines of code:** ~50  
**Failure modes:** 6+  
**PII protection:** "We'll add it later" (we didn't)  
**Testability:** "We test in production"

### The New Way (aka The Joy)

```java
@Entity
@AICapable(entityType = "product")
public class Product {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @AISearchable   // Users can find by name
    private String name;
    
    @AISearchable   // Users can find by description meaning
    private String description;
    
    @AISearchable   // Users can find by category
    private String category;
    
    @AIContext      // AI knows the price when responding
    private BigDecimal price;
    
    @AIContext      // AI knows the brand
    private String brand;
    
    @AIContext      // AI knows if it's in stock
    private Boolean inStock;
    
    @AIContext      // AI knows the rating
    private Double rating;
    
    private String sku;           // Internal - not in AI system
    private String warehouseCode; // Internal - not in AI system
}

@Service
@RequiredArgsConstructor
public class ProductService {
    
    private final ProductRepository repository;
    
    @AIProcess(entityType = "product", processType = "create")
    @Transactional
    public Product create(Product product) {
        return repository.save(product);
        // That's it. The framework handles everything else.
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

**Lines of code:** ~15 annotations  
**Failure modes:** 0 (framework handles retries)  
**PII protection:** Automatic  
**Testability:** Isolated, mockable

### What Happens Under the Hood

```
┌─────────────────────────────────────────────────────────────────┐
│  WHEN YOU CALL createProduct()                                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Step 1: AOP Intercept                                          │
│  ══════════════════════                                         │
│  @AIProcess detected on ProductService.create()                 │
│  entityType = "product", processType = "create"                 │
│                                                                  │
│  Step 2: Method Executes                                        │
│  ══════════════════════                                         │
│  repository.save(product) → Product saved to DB                 │
│  Return value captured                                          │
│                                                                  │
│  Step 3: Field Discovery                                        │
│  ══════════════════════                                         │
│  Scan Product.class for annotations:                            │
│  ┌─────────────────────────────────────────────────────┐       │
│  │ @AISearchable: name, description, category          │       │
│  │ @AIContext: price, brand, inStock, rating           │       │
│  │ Not annotated: sku, warehouseCode (ignored)         │       │
│  └─────────────────────────────────────────────────────┘       │
│                                                                  │
│  Step 4: Build Storage Payloads                                 │
│  ═══════════════════════════════                                │
│  searchableContent = "ErgoPro Chair Premium ergonomic..."       │
│  metadata = {                                                   │
│    "price": 299.99,                                             │
│    "brand": "ErgoPro",                                          │
│    "inStock": true,                                             │
│    "rating": 4.8                                                │
│  }                                                              │
│                                                                  │
│  Step 5: PII Scan (Automatic!)                                  │
│  ══════════════════════════════                                 │
│  searchableContent scanned for:                                 │
│  - Credit cards: ❌ None                                        │
│  - SSNs: ❌ None                                                │
│  - Emails: ❌ None                                              │
│  Result: Clean ✅                                                │
│                                                                  │
│  Step 6: Async Processing (Your request returns immediately!)   │
│  ══════════════════════════════════════════════════════════════│
│  Background worker:                                             │
│  1. Generate embedding [0.023, -0.156, 0.891, ...]             │
│  2. Store AISearchableEntity                                    │
│  3. Upsert to vector DB                                         │
│  4. Retry on failure (exponential backoff)                      │
│                                                                  │
│  Step 7: HTTP Response                                          │
│  ══════════════════════                                         │
│  Return Product to caller (+10ms, not +450ms!)                  │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### The Search Experience

Now when users search for "comfortable office chair":

```
┌─────────────────────────────────────────────────────────────────┐
│  SEMANTIC SEARCH FLOW                                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  User Query: "comfortable office chair"                         │
│                         │                                        │
│                         ▼                                        │
│  ┌─────────────────────────────────────────────────────┐       │
│  │ 1. Embed Query                                       │       │
│  │    [0.019, -0.148, 0.887, ...]                       │       │
│  └─────────────────────────────────────────────────────┘       │
│                         │                                        │
│                         ▼                                        │
│  ┌─────────────────────────────────────────────────────┐       │
│  │ 2. Vector Similarity Search                          │       │
│  │    Find nearest neighbors in vector DB               │       │
│  └─────────────────────────────────────────────────────┘       │
│                         │                                        │
│                         ▼                                        │
│  ┌─────────────────────────────────────────────────────┐       │
│  │ 3. Results (by MEANING, not keywords!)               │       │
│  │                                                      │       │
│  │ ✅ "ErgoPro Chair" - "lumbar support, all-day..."   │       │
│  │    Similarity: 0.94                                  │       │
│  │                                                      │       │
│  │ ✅ "Executive Mesh" - "breathable, ergonomic..."    │       │
│  │    Similarity: 0.91                                  │       │
│  │                                                      │       │
│  │ ✅ "Standing Desk Chair" - "comfort, posture..."    │       │
│  │    Similarity: 0.87                                  │       │
│  └─────────────────────────────────────────────────────┘       │
│                         │                                        │
│                         ▼                                        │
│  ┌─────────────────────────────────────────────────────┐       │
│  │ 4. Build LLM Context                                 │       │
│  │    @AISearchable fields → searchableContent         │       │
│  │    @AIContext fields → metadata JSON                 │       │
│  └─────────────────────────────────────────────────────┘       │
│                         │                                        │
│                         ▼                                        │
│  ┌─────────────────────────────────────────────────────┐       │
│  │ 5. LLM Response                                      │       │
│  │                                                      │       │
│  │ "I found 3 great options for comfortable office      │       │
│  │  chairs:                                             │       │
│  │                                                      │       │
│  │  1. ErgoPro Chair ($299.99) ⭐ 4.8                   │       │
│  │     Features lumbar support for all-day comfort.     │       │
│  │     Currently in stock.                              │       │
│  │                                                      │       │
│  │  2. Executive Mesh ($399.99) ⭐ 4.7                  │       │
│  │     Breathable mesh with ergonomic design..."        │       │
│  └─────────────────────────────────────────────────────┘       │
│                                                                  │
│  Note: The LLM knows prices, ratings, stock status              │
│        because of @AIContext fields!                            │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

**The magic:** No product contains "comfortable office chair" literally. But they all match *semantically*.

---

## Real Scenario #2: The Knowledge Base That Learns

### The Problem

TechCorp has 5,000 support articles. Engineers spend 30% of their time answering the same questions that are already documented. The search is keyword-based, so "how to reset password" doesn't find "account recovery steps."

### The Solution

```java
@Entity
@AICapable(
    entityType = "kb-article",
    indexingStrategy = IndexingStrategy.ASYNC,
    onCreateStrategy = IndexingStrategy.SYNC  // New articles searchable immediately
)
public class KnowledgeBaseArticle {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @AISearchable   // Find by title concepts
    private String title;
    
    @AISearchable   // Find by content meaning
    @Column(columnDefinition = "TEXT")
    private String content;
    
    @AISearchable   // Find by problem description
    private String problemDescription;
    
    @AISearchable   // Find by solution steps
    private String solution;
    
    @AISearchable   // Find by related topics
    private String tags;
    
    @AIContext      // AI knows the author
    private String author;
    
    @AIContext      // AI knows when it was updated
    private LocalDateTime lastUpdated;
    
    @AIContext      // AI knows how helpful it is
    private Double helpfulnessRating;
    
    @AIContext      // AI knows how many times it was viewed
    private Integer viewCount;
    
    @AIContext      // AI knows the category
    private String category;
    
    private String internalNotes;  // Not in AI - internal only
}
```

### The Developer Experience

```java
@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {
    
    private final KBArticleRepository repository;
    private final AISearchService searchService;
    
    @AIProcess(entityType = "kb-article", processType = "create")
    @Transactional
    public KnowledgeBaseArticle publish(KnowledgeBaseArticle article) {
        article.setLastUpdated(LocalDateTime.now());
        return repository.save(article);
        // Indexed synchronously - searchable immediately!
    }
    
    public List<KnowledgeBaseArticle> findSimilar(String question) {
        // Semantic search - finds by MEANING
        return searchService.semanticSearch(
            question,
            "kb-article",
            5  // top 5 results
        );
    }
}
```

### The Search Results

```
┌─────────────────────────────────────────────────────────────────┐
│  USER QUERY: "how to reset password"                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ❌ OLD KEYWORD SEARCH:                                         │
│     No results (no article has "reset password" in title)       │
│                                                                  │
│  ✅ NEW SEMANTIC SEARCH:                                        │
│                                                                  │
│  1. "Account Recovery Steps" (similarity: 0.92)                 │
│     Category: Authentication                                    │
│     Rating: ⭐ 4.9 | Views: 12,847                              │
│     Last Updated: 2 days ago                                    │
│                                                                  │
│  2. "Password Reset via Email" (similarity: 0.89)               │
│     Category: Authentication                                    │
│     Rating: ⭐ 4.7 | Views: 8,234                               │
│     Last Updated: 1 week ago                                    │
│                                                                  │
│  3. "Forgot Credentials Guide" (similarity: 0.85)               │
│     Category: Getting Started                                   │
│     Rating: ⭐ 4.5 | Views: 5,621                               │
│     Last Updated: 2 weeks ago                                   │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

**Impact:** Support tickets dropped 40%. Engineers reclaimed 12 hours/week.

---

## Real Scenario #3: The Support Ticket Intelligence

### The Problem

SaaS Corp receives 500+ support tickets daily. Agents spend 15 minutes per ticket just finding similar past issues. Resolution knowledge exists but is trapped in closed tickets.

### The Solution

```java
@Entity
@AICapable(entityType = "support-ticket")
public class SupportTicket {
    
    @Id
    private Long id;
    
    @AISearchable   // Find similar subjects
    private String subject;
    
    @AISearchable   // Find similar problems
    @Column(columnDefinition = "TEXT")
    private String issueDescription;
    
    @AISearchable   // Find by error messages
    private String errorMessages;
    
    @AISearchable   // GOLD MINE: How it was solved!
    @Column(columnDefinition = "TEXT")
    private String resolution;
    
    @AISearchable   // Find by product area
    private String productArea;
    
    @AIContext      // AI knows the status
    private String status;
    
    @AIContext      // AI knows the priority
    private String priority;
    
    @AIContext      // AI knows resolution time
    private Duration resolutionTime;
    
    @AIContext      // AI knows if it escalated
    private Boolean wasEscalated;
    
    @AIContext      // AI knows the resolution type
    private String resolutionType;  // "bug_fix", "config_change", "user_education"
    
    private String customerId;      // Not in AI - privacy!
    private String agentNotes;      // Not in AI - internal
}
```

### The Agent Experience

When a new ticket comes in:

```
┌─────────────────────────────────────────────────────────────────┐
│  NEW TICKET: "App crashes when uploading large files"            │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  🤖 AI ASSISTANT FOUND SIMILAR RESOLVED TICKETS:                │
│                                                                  │
│  ┌─────────────────────────────────────────────────────┐       │
│  │ #4521 - "Upload fails with OutOfMemory error"       │       │
│  │ Similarity: 0.94                                    │       │
│  │ Status: ✅ Resolved | Time: 2h 15m                  │       │
│  │ Resolution Type: config_change                      │       │
│  │                                                     │       │
│  │ RESOLUTION:                                         │       │
│  │ "Increased JVM heap size from 512MB to 2GB in      │       │
│  │  application.yml. Customer was uploading 500MB     │       │
│  │  files which exceeded default memory limits."      │       │
│  └─────────────────────────────────────────────────────┘       │
│                                                                  │
│  ┌─────────────────────────────────────────────────────┐       │
│  │ #4398 - "Large file upload timeout"                 │       │
│  │ Similarity: 0.91                                    │       │
│  │ Status: ✅ Resolved | Time: 45m                     │       │
│  │ Resolution Type: config_change                      │       │
│  │                                                     │       │
│  │ RESOLUTION:                                         │       │
│  │ "Increased upload timeout from 30s to 300s and     │       │
│  │  chunk size from 5MB to 50MB."                     │       │
│  └─────────────────────────────────────────────────────┘       │
│                                                                  │
│  🎯 SUGGESTED RESPONSE:                                         │
│  "This appears to be a known issue with large file uploads.     │
│   Based on similar tickets, try:                                │
│   1. Increase JVM heap size to 2GB                              │
│   2. Increase upload timeout to 300s                            │
│   3. Increase chunk size to 50MB"                               │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

**Impact:** Average resolution time dropped from 4 hours to 45 minutes.

---

## The Decision Flowchart

Still not sure when to use `@AISearchable` vs `@AIContext`? Here's your cheat sheet:

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                  │
│  START: Should this field be in the AI system?                  │
│                                                                  │
│                         │                                        │
│                         ▼                                        │
│                    ┌────────┐                                   │
│                    │  Yes?  │                                   │
│                    └───┬────┘                                   │
│                        │                                        │
│              ┌─────────┴─────────┐                              │
│              │                   │                              │
│             YES                  NO                             │
│              │                   │                              │
│              ▼                   ▼                              │
│  ┌───────────────────┐   ┌───────────────┐                     │
│  │ Can users SEARCH  │   │ Don't annotate│                     │
│  │ by this field's   │   │ (internal,    │                     │
│  │ meaning?          │   │ private, etc) │                     │
│  │                   │   └───────────────┘                     │
│  │ "eco-friendly"    │                                         │
│  │ finds "sustainable"│                                         │
│  └─────────┬─────────┘                                         │
│            │                                                    │
│    ┌───────┴───────┐                                           │
│    │               │                                           │
│   YES             NO                                           │
│    │               │                                           │
│    ▼               ▼                                           │
│ ┌──────────────┐  ┌───────────────────────┐                   │
│ │ @AISearchable│  │ Does AI need to KNOW  │                   │
│ │              │  │ this value when       │                   │
│ │ Embedded +   │  │ responding?           │                   │
│ │ Searchable + │  │                       │                   │
│ │ LLM Context  │  │ "How much is it?"     │                   │
│ └──────────────┘  │ "What's the rating?"  │                   │
│                   └───────────┬───────────┘                   │
│                               │                                │
│                       ┌───────┴───────┐                        │
│                       │               │                        │
│                      YES             NO                        │
│                       │               │                        │
│                       ▼               ▼                        │
│                ┌───────────┐  ┌───────────────┐               │
│                │ @AIContext│  │ Don't annotate│               │
│                │           │  │               │               │
│                │ Metadata  │  │ Not needed    │               │
│                │ (no embed)│  │ in AI system  │               │
│                │ + LLM     │  │               │               │
│                │ Context   │  │               │               │
│                └───────────┘  └───────────────┘               │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘

EXAMPLES:
───────────────────────────────────────────────────────────────────
Field             Has Meaning?    AI Needs?    Annotation
───────────────────────────────────────────────────────────────────
name              ✅ Yes          ✅ Yes       @AISearchable
description       ✅ Yes          ✅ Yes       @AISearchable
price             ❌ No (number)  ✅ Yes       @AIContext
rating            ❌ No (number)  ✅ Yes       @AIContext
brand             ❌ No (name)    ✅ Yes       @AIContext
sku               ❌ No           ❌ No        (none)
internalNotes     ❌ No           ❌ No        (none)
───────────────────────────────────────────────────────────────────
```

---

## YAML Override: When You Need Fine-Tuned Control

Annotations cover 90% of cases. For the other 10%, YAML has your back:

```yaml
# ai-entity-config.yml — OPTIONAL, only for overrides
ai-entities:
  product:
    searchable-fields:
      - name: "name"
        weight: 2.0           # Title is 2x more important
      - name: "description"
        weight: 1.0
        include-in-rag: true
      - name: "category"
        weight: 0.5           # Less important
        
    metadata-fields:
      - name: "price"
        type: "NUMERIC"
        include-in-search: true  # Enable filtering by price range
      - name: "brand"
        type: "TEXT"
        include-in-search: true  # Enable filtering by brand
```

**Annotations = "what is AI-enabled"**  
**YAML = "how to tune it" (optional)**

Production needs different weights than dev? Different YAML per environment. Zero code changes.

---

## The Storage Model: What Actually Gets Stored

When you save an entity, the framework creates an `AISearchableEntity`:

```
┌─────────────────────────────────────────────────────────────────┐
│  YOUR ENTITY                                                     │
│  ═══════════════════════════════════════════════════════════════│
│                                                                  │
│  @Entity                                                        │
│  @AICapable(entityType = "product")                             │
│  public class Product {                                         │
│      @AISearchable name = "Bamboo Toothbrush"                   │
│      @AISearchable description = "Eco-friendly dental care..."  │
│      @AIContext price = 29.99                                   │
│      @AIContext brand = "EcoLife"                               │
│      private String sku = "SKU-123"  // not annotated           │
│  }                                                              │
│                                                                  │
│                              │                                   │
│                              ▼                                   │
│                                                                  │
│  AISearchableEntity (what gets stored)                          │
│  ═══════════════════════════════════════════════════════════════│
│  {                                                              │
│    entityType: "product",                                       │
│    entityId: "123",                                             │
│    searchableContent: "Bamboo Toothbrush Eco-friendly dental...",│
│    metadata: {                                                  │
│      "price": 29.99,                                            │
│      "brand": "EcoLife"                                         │
│    },                                                           │
│    vectorId: "vec-abc-123"                                      │
│  }                                                              │
│                                                                  │
│                              │                                   │
│                              ▼                                   │
│                                                                  │
│  Vector Database                                                │
│  ═══════════════════════════════════════════════════════════════│
│  ID: "vec-abc-123"                                              │
│  Vector: [0.023, -0.156, 0.891, 0.234, -0.456, ...]            │
│  (384 dimensions from all-MiniLM-L6-v2)                        │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘

KEY INSIGHT:
────────────────────────────────────────────────────────────────────
@AISearchable fields → searchableContent (text) + embedding (vector)
@AIContext fields → metadata (JSON)
Not annotated → NOT stored in AI system
────────────────────────────────────────────────────────────────────
```

---

## The Numbers

| Metric | Before (Manual) | After (Annotations) |
|--------|-----------------|---------------------|
| Lines of code per entity | ~50 | ~5 annotations |
| Failure modes | 6+ | 0 (framework handles) |
| PII protection | "We'll add it later" | Automatic |
| Retry logic | DIY (usually none) | Built-in exponential backoff |
| Response time impact | +450ms | +10ms |
| Consistency | "Sometimes" | Guaranteed eventual |
| Testability | "We test in production" | Isolated, mockable |

---

## Getting Started

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
    @Id private Long id;
    
    @AISearchable
    private String name;
    
    @AISearchable
    private String description;
    
    @AIContext
    private BigDecimal price;
}
```

### 3. Annotate your service

```java
@AIProcess(entityType = "product", processType = "create")
public Product create(Product product) {
    return repository.save(product);
}
```

### 4. Search semantically

```java
List<Product> results = searchService.semanticSearch(
    "eco-friendly products",
    "product",
    10
);
```

**That's it.** Zero YAML required. Zero infrastructure code. Zero regrets.

---

## The Bottom Line

```java
// Before: 50 lines of fragile infrastructure per entity

// After:
@AISearchable   // Users can FIND by this
private String name;

@AIContext      // AI will KNOW this
private BigDecimal price;
```

**That's the entire pitch.**

Declarative AI is here. Your infrastructure belongs in the framework, not your service layer.

Now go delete some code.

---

*The AI Fabric Framework — Because your code should describe business logic, not embedding pipelines.*

*[ai-fabric.dev](https://ai-fabric.dev)*

