# Killing Boilerplate: A Murder Mystery (The Victim Deserved It)

*How 4 annotations eliminated 2,400 lines of repetitive AI infrastructure code across 12 services. No regrets.*

---

**TL;DR:** We deleted 2,400 lines of code in one PR. The victim? Boilerplate AI infrastructure—embedding services, vector DB clients, retry templates, PII scanners, metrics instrumentation. The murder weapon? Four annotations. We'd do it again.

---

## The Crime Scene

**Location:** ProductService.java  
**Time of Death:** Wednesday, 2:47 PM  
**Victim:** 187 lines of code  
**Cause of Death:** Annotation-induced irrelevance

Let me show you what we killed.

### Before: The Victim

```java
@Service
@RequiredArgsConstructor
public class ProductService {
    
    // 6 infrastructure dependencies
    private final ProductRepository productRepository;
    private final EmbeddingService embeddingService;
    private final VectorDbClient vectorDb;
    private final PIIScanner piiScanner;
    private final MetricsService metrics;
    private final RetryTemplate retryTemplate;
    
    @Transactional
    public Product createProduct(Product product) {
        // The 3 lines of actual business logic
        Product saved = productRepository.save(product);
        
        // The 50+ lines of infrastructure noise that follows...
        try {
            // Manual text building (fragile, error-prone)
            StringBuilder searchableText = new StringBuilder();
            searchableText.append(product.getName()).append(" ");
            searchableText.append(product.getDescription()).append(" ");
            searchableText.append(product.getCategory());
            
            // PII scanning (hope you didn't forget!)
            String cleanText = piiScanner.redact(searchableText.toString());
            
            // Embedding generation with retry logic
            float[] embedding = retryTemplate.execute(ctx -> {
                metrics.increment("embedding.attempt");
                return embeddingService.embed(cleanText);
            });
            
            // Manual metadata building (another place to make mistakes)
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("price", product.getPrice());
            metadata.put("rating", product.getRating());
            metadata.put("inStock", product.getInStock());
            metadata.put("brand", product.getBrand());
            
            // Vector DB storage with more retry logic
            retryTemplate.execute(ctx -> {
                vectorDb.upsert(
                    "product-" + saved.getId(),
                    embedding,
                    metadata
                );
                return null;
            });
            
            // Metrics (easy to forget, hard to debug when missing)
            metrics.increment("product.indexed.success");
            
        } catch (Exception e) {
            metrics.increment("product.indexed.failure");
            log.error("Failed to index product", e);
            // Product is saved but not indexed. Consistency? What consistency?
        }
        
        return saved;
    }
    
    // And then update() - another 50+ lines, mostly copy-pasted
    // And then delete() - another 30+ lines
    // Total: 187 lines per service
}
```

This code existed in 12 services. Each one a slightly different variation. Each one a maintenance nightmare.

---

## The Murder Weapon: 4 Annotations

```java
@AICapable
@AISearchable
@AIContext
@AIProcess
```

That's it. That's what killed 2,400 lines of code.

---

## After: The Beautiful Aftermath

```java
@Service
@RequiredArgsConstructor
public class ProductService {
    
    private final ProductRepository productRepository;
    // That's it. No infrastructure clients.
    
    @AIProcess(entityType = "product", processType = "create")
    @Transactional
    public Product createProduct(Product product) {
        return productRepository.save(product);
        // Framework handles EVERYTHING else
    }
    
    @AIProcess(entityType = "product", processType = "update")
    @Transactional
    public Product updateProduct(Product product) {
        return productRepository.save(product);
    }
    
    @AIProcess(entityType = "product", processType = "delete",
               generateEmbedding = false)
    @Transactional
    public void deleteProduct(Long id) {
        productRepository.deleteById(id);
    }
}
```

**Before:** 187 lines  
**After:** 15 lines  
**Reduction:** 92%

And the entity tells the framework what to process:

```java
@Entity
@AICapable(entityType = "product")
public class Product {
    
    @AISearchable private String name;
    @AISearchable private String description;
    @AIContext private BigDecimal price;
    @AIContext private Boolean inStock;
    
    private String sku;  // Internal, not in AI
}
```

---

## The Kill List

Here's everyone we eliminated:

| File | Lines Deleted | Cause of Death |
|------|---------------|----------------|
| EmbeddingTextBuilder.java | 45 | @AISearchable handles it |
| MetadataMapper.java | 32 | @AIContext handles it |
| RetryConfiguration.java | 28 | Framework has smart retries |
| PIIScannerIntegration.java | 24 | Automatic before embedding |
| MetricsInstrumentation.java | 35 | Observable by default |
| VectorDbSyncManager.java | 56 | @AIProcess lifecycle |
| **Total per service** | **220** | **Framework handles all** |

**Across 12 services: 2,640 lines of code eliminated.**

---

## The PR That Made Everyone Cry (Tears of Joy)

```
$ git diff --stat HEAD~1

ProductService.java      | 172 deletions
ArticleService.java      | 168 deletions  
TicketService.java       | 155 deletions
EmbeddingTextBuilder.java| 45 deletions (file removed)
MetadataMapper.java      | 32 deletions (file removed)
RetryConfig.java         | 28 deletions (file removed)
PIIScannerConfig.java    | 24 deletions (file removed)
...
Product.java             | 8 additions (annotations)
Article.java             | 10 additions (annotations)

+48 / -2,400 lines changed
```

The PR summary:

```markdown
## Summary
- Migrated 12 services to declarative AI annotations
- Deleted 2,400 lines of infrastructure code
- Added 48 lines of annotations
- Net: -2,352 lines

## What the framework now handles automatically:
- Embedding text extraction (@AISearchable)
- Metadata mapping (@AIContext)
- PII redaction (before embedding)
- Retry logic (exponential backoff)
- Metrics/observability (built-in)
- Vector DB sync (@AIProcess lifecycle)

## Testing
- All existing tests pass
- Semantic search working as before
- Latency unchanged (framework is optimized)
```

**Reviewer comment:** "This is beautiful. Approved."

---

## Why The Victim Deserved It

Let me be clear: this code needed to die.

### It Was Copy-Pasted Everywhere

Same 50 lines in 12 services. Each one slightly different because different developers wrote them. Each one with different bugs.

When we found a bug in the retry logic, we had to fix it in 12 places. We missed 3 of them.

### It Was Error-Prone

Forgetting to call the PII scanner? Easy to do. We found 2 services that weren't redacting PII before embedding. Compliance nightmare.

### It Was Brittle

The text building logic broke when we added a new field. We had to update 12 services. We only remembered 9 of them.

### It Was Invisible

When something failed, we had no idea why. No consistent logging. No metrics in half the services. Debugging was archaeology.

---

## The Autopsy Report

**Subject:** Infrastructure boilerplate  
**Condition:** Deceased  
**Cause of death:** Replacement by declarative annotations

**Summary of findings:**

The deceased code exhibited several pathological patterns:
- Duplication across 12 codebases
- Inconsistent error handling
- Missing observability in 6 services
- PII leakage in 2 services
- Retry logic that actually made things worse in 3 services

**Conclusion:** Death was justified. Patient had no quality of life.

---

## The Benefits of Murder

### More Coffee Breaks
Less code to write = more time for important things.

### Fewer 3 AM Pages
No more "embedding service timeout" alerts that require understanding 200 lines of retry logic.

### Faster Code Reviews
PRs with 15 lines get approved faster than PRs with 200 lines. Go figure.

### Happier Developers
Nobody—and I mean nobody—misses debugging retry configuration.

### Actual Consistency
One framework. One behavior. One set of bugs (that get fixed once).

---

## Before/After Side by Side

### Before: The Horror

```java
// Every. Single. Service.
private final EmbeddingService embedding;
private final VectorDbClient vectorDb;
private final PIIScanner piiScanner;
private final MetricsService metrics;
private final RetryTemplate retryTemplate;

// 50+ lines of infrastructure noise
StringBuilder text = new StringBuilder();
text.append(entity.getName())...
String clean = piiScanner.redact(text);
float[] vec = retryTemplate.execute(...);
Map<String, Object> meta = new HashMap<>();
meta.put("field1", entity.getField1());
vectorDb.upsert(id, vec, meta);
metrics.increment("entity.indexed");
```

*Repeated in 12 services. 2,400 lines total. Maintained by whoever drew the short straw.*

### After: The Dream

```java
// Entity tells framework WHAT
@Entity
@AICapable(entityType = "product")
public class Product {
    @AISearchable private String name;
    @AISearchable private String description;
    @AIContext private BigDecimal price;
}

// Service tells framework WHEN
@AIProcess(entityType = "product", processType = "create")
public Product create(Product p) {
    return repo.save(p);
}
```

*Framework handles embedding, PII, retry, metrics, sync. 48 lines total. Maintained by the framework.*

---

## The Confession

I killed 2,400 lines of code yesterday.

Were they innocent? No. They were boilerplate.

They had to die so our codebase could live.

Every time I see someone writing manual embedding logic, I feel the urge to kill again.

**I would do it again.**

---

## Ready to Commit Murder?

If your codebase has:
- Embedding logic copy-pasted across services
- Vector DB calls in business logic
- Inconsistent PII handling
- Retry logic that nobody understands
- Metrics that exist in some services but not others

The solution is clear.

```java
@Entity
@AICapable(entityType = "your-entity")
public class YourEntity {
    @AISearchable private String name;
    @AIContext private BigDecimal price;
}

@AIProcess(entityType = "your-entity", processType = "create")
public YourEntity create(YourEntity e) { return repo.save(e); }
```

Four annotations. Two thousand four hundred lines eliminated.

**The victim deserves it.**

---

*Part of the AI Annotations Story Series. See also: [Semantic Search](/docs/ai-annotations-semantic-search), [Developer's Guide](/docs/ai-annotations-developer-guide).*

---

**Tags:** #Refactoring #CodeQuality #AI #Boilerplate #Engineering #CleanCode #TechDebt

**Reading Time:** 8 minutes

---

*If you've ever copy-pasted embedding logic and felt bad about it, clap 👏 and share with someone who's still suffering. There's a better way.*
