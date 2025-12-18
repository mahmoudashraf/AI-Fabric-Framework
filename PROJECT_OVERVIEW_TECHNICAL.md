# AI Infrastructure for Spring Boot - Technical Overview

**Last Updated:** December 2024  
**Status:** Production-Ready Architecture

This document contains the detailed technical architecture of the AI Infrastructure framework. For a marketing-focused overview, see the main README.

---

## Architecture in 30 seconds

```
┌─────────────────────────────────────────────────────┐
│ YOUR SPRING BOOT APP                                │
│                                                     │
│  @Entity                                            │
│  @AICapable(entityType = "product")                │
│  class Product {                                    │
│    String description;                              │
│  }                                                  │
│                                                     │
│  @Bean                                              │
│  EntityAccessPolicy accessPolicy() {                │
│    return (userId, ctx) -> checkYourRules(userId); │
│  }                                                  │
└──────────────────┬──────────────────────────────────┘
                   │
                   ↓
┌─────────────────────────────────────────────────────┐
│ AI INFRASTRUCTURE (this framework)                  │
├─────────────────────────────────────────────────────┤
│                                                     │
│ 1. AICapableAspect intercepts @Entity save()       │
│ 2. IndexingCoordinator decides sync/async          │
│ 3. AICapabilityService extracts configured fields  │
│ 4. EmbeddingProvider generates vectors             │
│ 5. VectorDatabase stores for similarity search     │
│ 6. RAGOrchestrator: security → PII → intent →      │
│    access control (YOUR hook) → LLM → sanitize     │
│                                                     │
└─────────────────────────────────────────────────────┘
                   │
                   ↓
┌─────────────────────────────────────────────────────┐
│ PLUGGABLE BACKENDS (choose one of each)            │
├─────────────────────────────────────────────────────┤
│                                                     │
│ LLM: OpenAI | Azure | Anthropic | Cohere           │
│ Embeddings: ONNX (local) | OpenAI | REST API       │
│ Vector DB: Lucene | Pinecone | Qdrant | Weaviate   │
│                                                     │
└─────────────────────────────────────────────────────┘
```

**Key insight:** This isn't a library you call—it's Spring Boot auto-configuration that intercepts your entities and wires up AI capabilities declaratively.

---

## Core design principles

### 1. Annotation-driven, not imperative

**Don't write:** `aiService.embed(product); vectorDb.store(product);`

**Instead:**
```java
@Entity
@AICapable(entityType = "product")
public class Product { }
```
→ Framework auto-embeds and indexes via AOP aspect (`AICapableAspect`)

### 2. Configuration over code

**Define AI behavior in YAML:**
```yaml
ai-entities:
  product:
    searchable-fields:
      - name: description
        weight: 1.0
    embeddable-fields:
      - name: description
        auto-generate: true
```
→ No hardcoded logic in your entities

### 3. Hook-based business logic

**Framework provides infrastructure:**
```java
public interface EntityAccessPolicy {
    boolean canUserAccessEntity(String userId, Map<String, Object> entity);
}
```

**You provide business rules:**
```java
@Bean
EntityAccessPolicy policy() {
    return (userId, entity) -> {
        // Your RBAC/ABAC/tenant isolation logic here
        return userService.canAccess(userId, entity.get("tenantId"));
    };
}
```
→ Framework never implements RBAC—you do. It just calls your hook and logs the result.

### 4. Provider abstraction

**One config property changes the entire backend:**
```yaml
ai:
  providers:
    llm-provider: openai        # or: azure, anthropic, cohere
    embedding-provider: onnx    # or: openai, rest
  vector-db:
    type: lucene                # or: pinecone, qdrant, weaviate, milvus
```
→ Same application code, different infrastructure

### 5. Async-first indexing

**Synchronous would block your HTTP request:**
```java
@PostMapping
Product save(@RequestBody Product p) {
    return repo.save(p);  // ← This would block if embedding was sync
}
```

**Framework uses async queue by default:**
1. `@AIProcess` aspect intercepts save
2. `IndexingCoordinator` enqueues work
3. `AsyncIndexingWorker` processes in background
4. HTTP request returns immediately

Configure strategy per operation:
```java
@AICapable(
    indexingStrategy = ASYNC,    // default for create/update
    onDeleteStrategy = SYNC      // cleanup immediately
)
```

---

## What you get

| Module | What it does | Key classes |
|--------|--------------|-------------|
| **ai-infrastructure-core** | Embeddings, RAG, search, indexing, security | `AICoreService`, `RAGService`, `RAGOrchestrator`, `PIIDetectionService`, `AIAccessControlService` |
| **ai-infrastructure-web** | REST API (59 endpoints) | Controllers for monitoring, audit, compliance, security, profiles |
| **ai-infrastructure-onnx-starter** | Local embeddings (no API calls) | `ONNXEmbeddingProvider` |
| **ai-infrastructure-provider-\*** | LLM provider adapters | `OpenAIProvider`, `AzureProvider`, `AnthropicProvider`, `CohereProvider` |
| **ai-infrastructure-vector-\*** | Vector DB adapters | `LuceneVectorDatabase`, `PineconeVectorDatabase`, `QdrantVectorDatabase` |
| **ai-infrastructure-relationship-query** | JPA-aware relationship traversal + semantic search | `RelationshipQueryService` |

---

## Quick start: Index your entities

### Step 1: Add dependencies

```xml
<dependency>
  <groupId>com.ai.infrastructure</groupId>
  <artifactId>ai-infrastructure-core</artifactId>
  <version>1.0.0</version>
</dependency>

<!-- Choose ONE provider -->
<dependency>
  <groupId>com.ai.infrastructure</groupId>
  <artifactId>ai-infrastructure-provider-openai</artifactId>
  <version>1.0.0</version>
</dependency>

<!-- Choose ONE vector DB -->
<dependency>
  <groupId>com.ai.infrastructure</groupId>
  <artifactId>ai-infrastructure-vector-lucene</artifactId>
  <version>1.0.0</version>
</dependency>
```

### Step 2: Configure providers

```yaml
ai:
  providers:
    llm-provider: openai
    embedding-provider: onnx  # local, no API cost
    openai:
      api-key: ${OPENAI_API_KEY}
      model: gpt-4o-mini

  vector-db:
    type: lucene
    lucene:
      index-path: ./data/vectors
```

### Step 3: Annotate your entity

```java
@Entity
@AICapable(entityType = "product")
public class Product {
    @Id 
    private UUID id;
    private String name;
    private String description;
    private BigDecimal price;
}
```

### Step 4: Define searchable fields

Create `src/main/resources/ai-entity-config.yml`:

```yaml
ai-entities:
  product:
    entity-type: product
    auto-process: true
    indexable: true
    
    searchable-fields:
      - name: name
        weight: 1.0
      - name: description
        weight: 0.8
    
    embeddable-fields:
      - name: description
        auto-generate: true
    
    crud-operations:
      create:
        generate-embedding: true
        index-for-search: true
      update:
        generate-embedding: true
        index-for-search: true
      delete:
        remove-from-search: true
        cleanup-embeddings: true
```

### Step 5: Save entities (auto-indexed)

```java
@Service
class ProductService {
    @Autowired ProductRepository repo;
    
    Product create(Product p) {
        return repo.save(p);  // ← Framework auto-embeds + indexes
    }
}
```

### Step 6: Query with RAG

```java
@Autowired RAGService ragService;

RAGResponse answer = ragService.performRAGQuery(
    RAGRequest.builder()
        .query("What laptops are under $1000?")
        .entityType("product")
        .limit(5)
        .threshold(0.7)
        .build()
);
```

---

## Configuration reference

All settings are in `@ConfigurationProperties` classes (code is source of truth):

| Property prefix | Class | What it controls |
|-----------------|-------|------------------|
| `ai.providers.*` | `AIProviderConfig` | LLM/embedding provider selection, API keys, models |
| `ai.vector-db.*` | `VectorDatabaseConfig` | Vector database backend (type, connection, index settings) |
| `ai.pii-detection.*` | `PIIDetectionProperties` | PII patterns, mode (PASS_THROUGH/DETECT/REDACT), direction |
| `ai.web.*` | `AIWebProperties` | REST endpoint toggles (enable/disable controller groups) |
| `ai.service.*` | `AIServiceConfig` | Timeouts, retries, rate limits, circuit breakers |
| `ai.indexing.*` | `AIIndexingProperties` | Async queue config, batch size, worker threads |
| `ai.cleanup.*` | `AICleanupProperties` | Retention policies, scheduled cleanup jobs |
| `ai.storage.*` | `AIStorageProperties` | Searchable entity storage strategy (single-table vs per-type) |

Entry point: `AIInfrastructureAutoConfiguration` (auto-wires all beans)

---

## Extension points (implement these)

The framework is **infrastructure-only**. You provide business logic via hooks:

| Interface | Purpose | Mandatory? | Default |
|-----------|---------|------------|---------|
| `EntityAccessPolicy` | Access control decisions | No | Fail-closed (denies if missing) |
| `ComplianceCheckProvider` | Domain-specific compliance rules | No | No-op |
| `UserDataDeletionProvider` | DSAR/GDPR deletion for your domain data | No | Deletes only framework data |
| `RetentionPolicyProvider` | Custom retention rules | No | Uses `AICleanupProperties` |
| `ActionHandler` | Intent-triggered actions (e.g., "create order") | No | Registry-based, register as beans |

---

## How async indexing works

```
┌─────────────────────┐
│ HTTP Request        │
│ repo.save(product)  │
└──────────┬──────────┘
           │
           ↓
┌─────────────────────────────────────────┐
│ AICapableAspect (Spring AOP)            │
│ @Around("@annotation(AIProcess)")       │
└──────────┬──────────────────────────────┘
           │
           ↓
┌─────────────────────────────────────────┐
│ IndexingCoordinator                     │
│ • Reads @AICapable.indexingStrategy     │
│ • SYNC → process now                    │
│ • ASYNC → enqueue for worker            │
└──────────┬──────────────────────────────┘
           │
           ↓ (if ASYNC)
┌─────────────────────────────────────────┐
│ IndexingQueueService                    │
│ • Serializes entity to JSON             │
│ • Stores in ai_indexing_queue table     │
│ • Returns immediately (HTTP completes)  │
└──────────────────────────────────────────┘
           │
           ↓ (background)
┌─────────────────────────────────────────┐
│ AsyncIndexingWorker                     │
│ • @Scheduled(fixedDelay = 5000)         │
│ • Fetches queued items                  │
│ • Calls AICapabilityService             │
│   → generateEmbeddings()                │
│   → indexForSearch()                    │
│ • Marks processed, deletes from queue   │
└──────────────────────────────────────────┘
```

---

## Build from source

```bash
cd ai-infrastructure-module
mvn clean install -DskipTests

# Run tests
mvn test

# Run integration tests
cd integration-tests
mvn verify
```

---

## Documentation

| Topic | Location |
|-------|----------|
| **User guide** (entity indexing, search) | `ai-infrastructure-module/docs/USER_GUIDE.md` |
| **Vector DB abstraction** (swap backends) | `ai-infrastructure-module/docs/VECTOR_DATABASE_ABSTRACTION.md` |
| **Provider architecture** (LLM/embedding modules) | `ai-infrastructure-module/docs/MODULE_AI_PROVIDERS/` |
| **Relationship queries** (JPA-aware semantic search) | `ai-infrastructure-module/docs/semantic-relational-implementation/` |
| **Security/privacy roadmap** | `docs/privacy/implementation/README.md` |
| **AI enablement guide** (integration examples) | `docs/AI_Enablment_Guide/README.md` |
| **Behavior analytics** (design, not yet implemented) | `ai-infrastructure-module/docs/Fixing_Arch/Behaviour_Module/` |

---

**Version:** 1.0.0  
**Status:** Production Ready  
**Last Updated:** December 2024

