# Why Declarative AI Wins Every Time: An Architect's Guide

*Imperative vs Declarative AI—technical debt, scalability, and the architectural decision that changes everything.*

---

**TL;DR:** Imperative AI integration means 150+ lines of infrastructure code per entity, multiplied across every service. Declarative means 15 lines of annotations. Same capabilities. 90% less code. Zero maintenance burden. Here's why every architect I know is switching.

---

## The Question Every Tech Lead Asks

At some point in your AI journey, someone in a meeting will ask:

> "Should we build AI infrastructure ourselves, or use something declarative?"

I've been in that meeting a hundred times. I've seen both paths play out.

Let me show you what each looks like. Then you decide.

---

## The Imperative Approach: The Old Way

With imperative AI integration, your services know about everything. They manage embeddings, vector databases, retries, PII, metrics—the whole stack.

Here's what a typical service looks like:

```java
@Service
@RequiredArgsConstructor
public class ProductService {
    
    // Dependencies for AI infrastructure
    private final ProductRepository productRepository;
    private final EmbeddingService embeddingService;
    private final VectorDbClient vectorDb;
    private final PIIScanner piiScanner;
    private final MetricsService metrics;
    private final RetryTemplate retryTemplate;
    
    @Transactional
    public Product createProduct(Product product) {
        // Business logic: 3 lines
        Product saved = productRepository.save(product);
        
        // Infrastructure code: 50+ lines
        try {
            // Build searchable text manually
            StringBuilder searchableText = new StringBuilder();
            searchableText.append(product.getName()).append(" ");
            searchableText.append(product.getDescription()).append(" ");
            searchableText.append(product.getCategory());
            
            // PII scanning (hope you didn't forget)
            String cleanText = piiScanner.redact(searchableText.toString());
            
            // Generate embedding with retries
            float[] embedding = retryTemplate.execute(ctx -> {
                metrics.increment("embedding.attempt");
                return embeddingService.embed(cleanText);
            });
            
            // Build metadata manually (error-prone)
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("price", product.getPrice());
            metadata.put("rating", product.getRating());
            metadata.put("inStock", product.getInStock());
            metadata.put("brand", product.getBrand());
            
            // Store in vector DB with retries
            retryTemplate.execute(ctx -> {
                vectorDb.upsert(
                    "product-" + saved.getId(),
                    embedding,
                    metadata
                );
                return null;
            });
            
            metrics.increment("product.indexed.success");
        } catch (Exception e) {
            metrics.increment("product.indexed.failure");
            log.error("Failed to index product", e);
            // Product is saved but not indexed. Now what?
        }
        
        return saved;
    }
    
    // Now imagine update() and delete()...
    // Same 50 lines. Copy-pasted. Maintained separately.
}
```

**Problems with this approach:**
- 50+ lines of infrastructure per method
- Copy-pasted across 12 services
- Easy to forget PII scanning
- No consistency guarantee between SQL and vector DB
- Every developer needs to understand the whole stack

---

## The Declarative Approach: The New Way

With declarative AI integration, you describe WHAT you want. The framework handles HOW.

Same functionality, completely different code:

```java
@Service
@RequiredArgsConstructor
public class ProductService {
    
    private final ProductRepository productRepository;
    // That's it. No embedding client. No vector DB. No retry template.
    
    @AIProcess(entityType = "product", processType = "create")
    @Transactional
    public Product createProduct(Product product) {
        return productRepository.save(product);
        // Done. Framework handles EVERYTHING else.
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

// Entity tells framework WHAT to process
@Entity
@AICapable(entityType = "product")
public class Product {
    @AISearchable private String name;        // In embedding
    @AISearchable private String description; // In embedding
    @AIContext private BigDecimal price;      // In metadata
    @AIContext private Boolean inStock;       // In metadata
    private String sku;                       // Internal only
}
```

**Benefits of this approach:**
- 5 lines per method (not 50)
- Consistency guaranteed by framework
- PII handled automatically
- Observable by default
- New developers productive in hours, not weeks

---

## The Architecture Comparison

### Imperative: You Write All The Infrastructure

```
┌─────────────────────────────────────────────────────────────────┐
│                         ProductService                          │
│                                                                 │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐       │
│  │Repository│  │Embedding │  │ VectorDB │  │   PII    │       │
│  │  Client  │  │ Service  │  │  Client  │  │ Scanner  │       │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘       │
│  ┌──────────┐  ┌──────────┐                                    │
│  │  Retry   │  │ Metrics  │  + Consistency logic               │
│  │ Template │  │ Service  │  + Error handling                  │
│  └──────────┘  └──────────┘  + Text building                   │
│                              + Metadata mapping                 │
└─────────────────────────────────────────────────────────────────┘

× Repeated in 12 services
× 150+ lines per service  
× Different implementations = different bugs
```

### Declarative: You Declare What, Framework Handles How

```
┌─────────────────────────────────────────────────────────────────┐
│                         ProductService                          │
│                                                                 │
│                    @AIProcess ──────────────┐                   │
│                         │                   │                   │
│                    ┌────▼────┐              │                   │
│                    │ Save to │              │                   │
│                    │   DB    │              │                   │
│                    └─────────┘              │                   │
│                                             │                   │
└─────────────────────────────────────────────┼───────────────────┘
                                              │
                                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      AI FABRIC FRAMEWORK                         │
│                                                                  │
│  ✓ Extract @AISearchable → Build embedding text                 │
│  ✓ Extract @AIContext → Build metadata                          │
│  ✓ PII scanning → Automatic redaction                           │
│  ✓ Generate embedding → Configurable provider                   │
│  ✓ Store in vector DB → Automatic sync                          │
│  ✓ Retry logic → Exponential backoff                            │
│  ✓ Metrics → Latency, counts, costs                             │
│  ✓ Tracing → Distributed observability                          │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Why Architects Choose Declarative

### 1. Separation of Concerns

Business logic stays clean. AI infrastructure is invisible.

**Imperative:** Service knows about embeddings, vector DBs, retries, PII...  
**Declarative:** Service knows about domain logic. Period.

### 2. Consistency at Scale

100 entities, 100 developers, same behavior.

**Imperative:** Each implementation is slightly different. Bugs vary by service.  
**Declarative:** One framework. One behavior. One set of bugs to fix.

### 3. Pluggable Providers

Swap OpenAI for ONNX? Change one config.

```yaml
# Switch from OpenAI to local ONNX
ai:
  embedding:
    provider: onnx  # Was: openai
    model: all-MiniLM-L6-v2
```

No code changes. Every service now uses the new provider.

### 4. Observable by Default

Metrics, traces, logs—automatically.

- Embedding latency tracked
- Vector DB operations traced
- Retry attempts logged
- Cost attribution built-in

### 5. Security Baked In

PII detection isn't optional—it's automatic.

**Imperative:** Hope every developer remembers to call the PII scanner.  
**Declarative:** Framework scans before embedding. Always. No exceptions.

### 6. Migration Ready

Future-proof architecture.

New AI capabilities = new annotations. Provider changes don't touch services. Schema evolution handled. Backward compatible.

---

## The Numbers

| Metric | Imperative | Declarative | Impact |
|--------|------------|-------------|--------|
| Lines of code per entity | 150-200 | 15-20 | **-90%** |
| Time to add new entity | 4-6 hours | 15 minutes | **-96%** |
| Onboarding time | 2-3 weeks | 2-3 days | **-85%** |
| Consistency bugs | 5-10/month | ~0 | **-100%** |
| Provider swap time | 2-4 weeks | 1 config change | **-99%** |

These aren't hypothetical. These are from teams who migrated from imperative to declarative.

---

## The ADR That Sells Itself

Here's the Architecture Decision Record template:

```markdown
# ADR-2024-003: AI Integration Architecture

## Status
ACCEPTED

## Context
We need to add semantic search capabilities to 50+ entities across 
12 services. Two approaches considered:
- Imperative: Manual integration with embedding/vector services
- Declarative: Annotation-based with AI Fabric Framework

## Decision
Adopt declarative AI annotations using the AI Fabric Framework.

## Consequences

### Positive
- 90% reduction in AI integration code
- Consistent behavior across all services
- Built-in PII protection and observability
- Provider-agnostic (can swap embeddings/vector DBs)
- New entities in 15 minutes vs. 4-6 hours

### Negative
- Requires team training on annotation patterns (1-2 days)
- Framework dependency (acceptable trade-off)

### Neutral
- Migration of existing entities: ~2 hours each
```

---

## System Architecture

Here's what the full system looks like:

```
┌─────────────────────────────────────────────────────────────────┐
│                        YOUR APPLICATION                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │  ProductService │  │  ArticleService │  │  TicketService  │ │
│  │ @AIProcess      │  │ @AIProcess      │  │ @AIProcess      │ │
│  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘ │
│           │                    │                    │           │
│           └────────────────────┼────────────────────┘           │
│                                │                                 │
│                                ▼                                 │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │                    AI FABRIC FRAMEWORK                       ││
│  │  ┌───────────────────────────────────────────────────────┐  ││
│  │  │ ✓ @AISearchable → Embedding Text Builder              │  ││
│  │  │ ✓ @AIContext    → Metadata Mapper                     │  ││
│  │  │ ✓ @AIProcess    → Lifecycle Interceptor               │  ││
│  │  │ ✓ Auto PII      → Redaction Before Embedding          │  ││
│  │  │ ✓ Auto Retry    → Exponential Backoff                 │  ││
│  │  │ ✓ Auto Metrics  → Latency, Counts, Costs              │  ││
│  │  └───────────────────────────────────────────────────────┘  ││
│  └─────────────────────────────────────────────────────────────┘│
│                                │                                 │
└────────────────────────────────┼─────────────────────────────────┘
                                 │
         ┌───────────────────────┼───────────────────────┐
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────┐   ┌─────────────────┐   ┌─────────────────┐
│   OpenAI API    │   │    Qdrant       │   │   Prometheus    │
│  (Embeddings)   │   │  (Vector DB)    │   │   (Metrics)     │
└─────────────────┘   └─────────────────┘   └─────────────────┘
         ↓                       ↓                       ↓
   Pluggable!              Pluggable!              Observable!
```

---

## The Architect's Insight

I've seen teams spend 6 months building AI infrastructure.

Then spend another 6 months maintaining it.

Then spend 3 months debugging inconsistencies between services.

Then spend 2 months adding observability they forgot.

Then spend 1 month adding PII detection they also forgot.

**Now I see teams do the same thing in a weekend with annotations.**

Same capabilities. 1/10th the code. Zero maintenance burden.

The architectural choice is obvious:

**Declare WHAT you want. Let the framework handle HOW.**

---

## Getting Started

If you're evaluating AI integration approaches for your platform:

```java
// This is the entire integration:

@Entity
@AICapable(entityType = "product")
public class Product {
    @AISearchable private String name;
    @AISearchable private String description;
    @AIContext private BigDecimal price;
}

@AIProcess(entityType = "product", processType = "create")
public Product create(Product p) { 
    return repo.save(p); 
}
```

Compare that to the imperative alternative and make your call.

The math does itself.

---

*Part of the AI Annotations Story Series. See also: [Killing Boilerplate](/docs/ai-annotations-killing-boilerplate), [Developer's Guide](/docs/ai-annotations-developer-guide).*

---

**Tags:** #Architecture #AI #SystemDesign #TechLead #Engineering #Patterns #Microservices

**Reading Time:** 12 minutes

---

*If you're tired of writing the same AI infrastructure code in every service, share this with your team. The architectural decision makes itself. 👏*
