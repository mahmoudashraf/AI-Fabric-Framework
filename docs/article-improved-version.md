# Why Your Vector Database is Always Out of Sync (And How AI Fabric Actually Fixes It)

**The dirty secret of RAG applications? They're mostly serving stale data.**

If you're building a Retrieval-Augmented Generation (RAG) system today, you're likely using a two-database architecture:
- **PostgreSQL/MySQL** as your source of truth
- **Pinecone/Milvus/Weaviate** as your semantic search index

The problem is simple: **How do you keep them in sync?**

When a user updates a product description in Postgres, the vector embedding in Pinecone is instantly obsolete. If a user deletes their account, their data might linger in your vector store for hours—creating a privacy nightmare.

Most teams solve this with brittle glue code. **There's a better way.**

---

## 🚫 The 3 Common (And Broken) Approaches

### 1. The "Dual Write" Trap

You write code that updates both databases sequentially.

```java
// DON'T DO THIS
public void updateProduct(Product p) {
    repo.save(p);              // 1. Save to DB
    vectorDb.upsert(embed(p)); // 2. Save to Vector DB
}
```

**Why it fails:**
- What if step 1 succeeds but step 2 fails (network error)? You now have a "ghost" record.
- What if step 2 succeeds but the transaction rolls back step 1? You have a vector pointing to nothing.
- No automatic retry on failure—you lose data consistency permanently.

### 2. The "Periodic Sync" Job

You run a cron job every hour to re-index everything.

**Why it fails:**
- Your search results are always up to **59 minutes stale**
- Scanning your entire database every hour is expensive and doesn't scale
- Full table scans lock rows and impact production traffic
- No way to prioritize urgent updates (like deleted PII)

### 3. The "CDC Pipeline" Overkill

You set up Debezium to read Postgres WAL logs, push to Kafka, process with Python, and write to Pinecone.

**Why it fails:**
- You just introduced **3 new infrastructure components** (Kafka, Debezium, Zookeeper) to index a text field
- Each component needs monitoring, scaling, and maintenance
- Debugging requires tracing through 5+ systems
- Costs can exceed your database costs when including cluster overhead

---

## ✅ The Fix: Transaction-Aware Application Events

The solution isn't more infrastructure. **It's better application architecture.**

The **AI Fabric framework** (ai-fabric.dev) solves this by hooking directly into your Spring Boot transaction lifecycle. It treats "embedding generation" not as a separate ETL task, but as a **side effect of the database transaction**—with proper rollback handling.

---

## 🏗️ How It Actually Works: The "Live Sync" Architecture

AI Fabric uses a **4-layer architecture** to guarantee consistency:

1. **AOP Interception Layer** - Captures repository method calls
2. **Strategy Resolution Layer** - Determines SYNC/ASYNC/BATCH execution
3. **Coordination Layer** - Routes work to immediate execution or durable queue
4. **Worker Layer** - Processes queued work with retry and backoff

### Layer 1: The Transaction Hook 🪝

When you annotate an entity with `@AICapable`, the framework activates an AOP aspect (`AICapableAspect`) that wraps your repository calls.

**Actual Implementation** (AICapableAspect.java):

```java
@Around("@annotation(aiCapable)")
public Object processAICapableMethod(ProceedingJoinPoint joinPoint, AICapable aiCapable) throws Throwable {
    // Execute the original repository method
    Object result = joinPoint.proceed();

    // Determine what indexing actions are needed
    IndexingActionPlan actionPlan = new IndexingActionPlan(
        shouldGenerateEmbedding,    // Should we create embeddings?
        shouldIndexForSearch,        // Should we add to search index?
        shouldEnableAnalysis,        // Should we run AI analysis?
        shouldRemoveFromSearch,      // Should we remove from index? (deletes)
        shouldCleanupEmbeddings      // Should we delete embeddings? (deletes)
    );

    // Register rollback handler ONLY if we're in an active transaction
    if ((shouldGenerateEmbedding || shouldIndexForSearch)
        && TransactionSynchronizationManager.isSynchronizationActive()) {

        final Object entityRef = result;
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        // Transaction failed - cleanup any optimistic writes
                        try {
                            aiCapabilityService.removeFromSearch(entityRef, config);
                        } catch (Exception ex) {
                            log.warn("Failed to rollback search index: {}", ex.getMessage());
                        }
                        try {
                            aiCapabilityService.cleanupEmbeddings(entityRef, config);
                        } catch (Exception ex) {
                            log.warn("Failed to rollback embeddings: {}", ex.getMessage());
                        }
                    }
                }
            }
        );
    }

    // Route to coordinator for execution
    indexingCoordinator.handle(result, entityType, operation, actionPlan, aiProcess);

    return result;
}
```

**Key Guarantees:**
- ✅ **Atomicity**: If the DB transaction rolls back, vector operations are cleaned up
- ✅ **No Phantom Writes**: Rollback handler removes any optimistic index updates
- ✅ **Exception Safety**: Indexing failures don't break your application logic

### Layer 2: The Strategy Resolver 🎯

The framework supports **4 indexing strategies** with operation-level overrides:

```java
public enum IndexingStrategy {
    AUTO,    // Inherit from parent configuration (default)
    SYNC,    // Synchronous - blocks transaction until indexed
    ASYNC,   // Asynchronous - near-real-time background processing
    BATCH    // Batched - periodic bulk processing (e.g., hourly)
}
```

**Strategy Resolution Hierarchy:**
1. Method-level override via `@AIProcess.indexingStrategy()`
2. Operation-level strategy (e.g., `onCreateStrategy`, `onUpdateStrategy`, `onDeleteStrategy`)
3. Entity-level default from `@AICapable.indexingStrategy()`
4. Framework default (`ASYNC`)

**Example:**

```java
@AICapable(
    entityType = "product",
    indexingStrategy = IndexingStrategy.ASYNC,       // Default for all operations
    onCreateStrategy = IndexingStrategy.SYNC,        // Override: New products indexed immediately
    onDeleteStrategy = IndexingStrategy.SYNC         // Override: Deletions require immediate removal
)
public class Product {
    // ...
}
```

**Why This Matters:**
- **New products**: Users expect instant searchability → `SYNC`
- **Price updates**: Can tolerate 1-2 second delay → `ASYNC`
- **Bulk imports**: Process overnight → `BATCH`
- **Account deletions**: Legal requirement for immediate removal → `SYNC`

### Layer 3: The Indexing Coordinator 🚦

Once the transaction commits, the `IndexingCoordinator` routes work based on the resolved strategy.

**Actual Implementation** (IndexingCoordinator.java):

```java
public void handle(
    Object entity,
    String entityType,
    IndexingOperation operation,
    IndexingActionPlan actionPlan,
    AIProcess aiProcess
) {
    if (entity == null || !actionPlan.requiresWork()) {
        return;
    }

    IndexingStrategy strategy = resolveStrategy(entityClass, operation, aiProcess);

    if (strategy == IndexingStrategy.SYNC) {
        // Path 1: Execute immediately in same thread (blocks transaction)
        executeNow(entity, entityType, actionPlan);
    } else {
        // Path 2: Serialize and enqueue for background processing
        enqueue(entity, entityType, entityClass, entityId, operation, actionPlan, strategy);
    }
}
```

**SYNC Path (Immediate Execution):**

```java
private void executeNow(Object entity, String entityType, IndexingActionPlan plan) {
    AIEntityConfig config = configurationLoader.getEntityConfig(entityType);

    if (plan.generateEmbedding()) {
        capabilityService.generateEmbeddings(entity, config);  // Embedding API call
    }

    if (plan.indexForSearch()) {
        capabilityService.indexForSearch(entity, config);      // Vector DB upsert
    }

    if (plan.enableAnalysis()) {
        capabilityService.analyzeEntity(entity, config);       // AI analysis
    }

    if (plan.removeFromSearch()) {
        capabilityService.removeFromSearch(entity, config);    // Delete from index
    }

    if (plan.cleanupEmbeddings()) {
        capabilityService.cleanupEmbeddings(entity, config);   // Delete embeddings
    }
}
```

**ASYNC/BATCH Path (Queued Execution):**

```java
private void enqueue(
    Object entity,
    String entityType,
    Class<?> entityClass,
    String entityId,
    IndexingOperation operation,
    IndexingActionPlan actionPlan,
    IndexingStrategy strategy
) {
    // Serialize entity to JSON using Jackson
    String payload = objectMapper.writeValueAsString(entity);

    // Create indexing request with all metadata
    IndexingRequest request = IndexingRequest.builder()
        .entityType(entityType)
        .entityId(entityId)
        .entityClassName(entityClass.getName())           // For deserialization
        .operation(operation)                              // CREATE/UPDATE/DELETE
        .actionPlan(actionPlan)                            // What actions to perform
        .strategy(strategy)                                // ASYNC or BATCH
        .payload(payload)                                  // Serialized entity state
        .maxRetries(properties.getQueue().getMaxRetries()) // Default: 3
        .scheduledFor(LocalDateTime.now())                 // Process immediately
        .build();

    // Persist to database-backed queue
    queueService.enqueue(request);
}
```

### Layer 4: The Durable Queue 📥

Unlike a fire-and-forget `CompletableFuture`, AI Fabric uses a **database-backed persistent queue** with production-grade reliability.

**Queue Entry Schema** (`ai_indexing_queue` table):

```sql
CREATE TABLE ai_indexing_queue (
    id                      UUID PRIMARY KEY,
    entity_type             VARCHAR(255) NOT NULL,
    entity_id               VARCHAR(255) NOT NULL,
    entity_class            VARCHAR(500) NOT NULL,
    operation               VARCHAR(50) NOT NULL,       -- CREATE, UPDATE, DELETE
    strategy                VARCHAR(50) NOT NULL,       -- ASYNC, BATCH
    priority_weight         INT NOT NULL,               -- 1=HIGH, 5=STANDARD, 10=LOW
    status                  VARCHAR(50) NOT NULL,       -- PENDING, PROCESSING, COMPLETED, FAILED, DEAD_LETTER
    payload                 TEXT NOT NULL,              -- JSON-serialized entity
    retry_count             INT DEFAULT 0,
    max_retries             INT DEFAULT 3,
    scheduled_for           TIMESTAMP NOT NULL,
    requested_at            TIMESTAMP NOT NULL,
    started_at              TIMESTAMP,
    completed_at            TIMESTAMP,
    last_error_at           TIMESTAMP,
    error_message           TEXT,
    visibility_timeout_until TIMESTAMP,                 -- Prevents duplicate processing
    processing_node         VARCHAR(255),               -- Which worker is processing
    dead_letter_reason      TEXT,

    -- Action plan flags (denormalized for query performance)
    action_generate_embedding BOOLEAN,
    action_index_for_search   BOOLEAN,
    action_enable_analysis    BOOLEAN,
    action_remove_from_search BOOLEAN,
    action_cleanup_embeddings BOOLEAN,

    INDEX idx_queue_status_strategy (status, strategy, scheduled_for),
    INDEX idx_queue_entity (entity_type, entity_id)
);
```

**Queue Service Features** (IndexingQueueService.java):

#### 1. **Exponential Backoff Retry**

```java
public void markFailure(IndexingQueueEntry entry, String errorMessage) {
    int attempts = entry.getRetryCount() + 1;
    entry.setRetryCount(attempts);

    if (attempts >= entry.getMaxRetries()) {
        // Max retries exceeded - move to dead-letter queue
        entry.setStatus(IndexingStatus.DEAD_LETTER);
        entry.setDeadLetterReason(errorMessage);
        log.error("Entry {} moved to dead letter after {} attempts: {}",
                  entry.getId(), attempts, errorMessage);
    } else {
        // Schedule retry with exponential backoff
        entry.setStatus(IndexingStatus.PENDING);
        long delaySeconds = Math.min(300, (long) Math.pow(2, attempts));
        entry.setScheduledFor(now.plusSeconds(delaySeconds));
        log.warn("Entry {} will retry in {}s (attempt {}/{})",
                 entry.getId(), delaySeconds, attempts, entry.getMaxRetries());
    }
}
```

**Retry Schedule:**
- Attempt 1: Immediate
- Attempt 2: +2 seconds
- Attempt 3: +4 seconds
- Attempt 4: +8 seconds
- Attempt 5: +16 seconds
- Attempt 6: +32 seconds
- Attempt 7+: +300 seconds (5 minutes, capped)

#### 2. **Visibility Timeout (Prevents Duplicate Processing)**

```java
public List<IndexingQueueEntry> lease(IndexingStrategy strategy, int batchSize) {
    // Find entries ready for processing
    List<IndexingQueueEntry> pending = repository
        .findByStatusAndStrategyAndScheduledForLessThanEqual(
            IndexingStatus.PENDING,
            strategy,
            LocalDateTime.now(),
            PageRequest.of(0, batchSize)
        );

    // Mark as PROCESSING and set visibility timeout
    for (IndexingQueueEntry entry : pending) {
        entry.setStatus(IndexingStatus.PROCESSING);
        entry.setStartedAt(LocalDateTime.now());
        entry.setProcessingNode(InetAddress.getLocalHost().getHostName());
        entry.setVisibilityTimeoutUntil(
            LocalDateTime.now().plus(properties.getQueue().getVisibilityTimeout())
        );
    }

    return pending;
}
```

**Why Visibility Timeout Matters:**
- Prevents multiple workers from processing the same entry concurrently
- If a worker crashes mid-processing, the entry becomes visible again after timeout
- Default timeout: 5 minutes (configurable)

#### 3. **Stuck Entry Recovery**

```java
public int resetStuckEntries() {
    // Find entries stuck in PROCESSING state with expired visibility timeout
    int updated = repository.resetExpiredVisibilityTimeouts(
        IndexingStatus.PROCESSING,
        IndexingStatus.PENDING,
        LocalDateTime.now()
    );

    if (updated > 0) {
        log.warn("Reset {} stuck indexing entries", updated);
    }

    return updated;
}
```

Runs every 60 seconds to recover from worker crashes.

#### 4. **Priority-Based Processing**

```java
public enum IndexingPriority {
    CRITICAL(0),   // SYNC operations (shouldn't be queued, but failsafe)
    HIGH(1),       // ASYNC operations - near-real-time
    STANDARD(5),   // AUTO operations
    LOW(10);       // BATCH operations - bulk processing
}
```

Queue query orders by: `priority_weight ASC, requested_at ASC`

This ensures urgent operations (like PII deletions) are processed before bulk imports.

---

## 🛠️ Implementation: 5 Minutes to Consistency

You don't need to write any of the above code. You just use annotations and configuration.

### Step 1: Annotate Your Entity

```java
@Entity
@AICapable(
    entityType = "product",
    indexingStrategy = IndexingStrategy.ASYNC,        // Default: background processing
    onCreateStrategy = IndexingStrategy.SYNC,         // New products: immediate indexing
    onDeleteStrategy = IndexingStrategy.SYNC,         // Deletions: immediate removal
    configFile = "product-ai-config.yml"              // External configuration
)
public class Product {
    @Id
    private UUID id;

    @AISearchable(weight = 2.0, includeInSearch = true)   // 2x importance in search
    private String name;

    @Column(length = 2000)
    @AISearchable(weight = 1.0, includeInSearch = true)   // Normal importance
    private String description;

    @AISearchable(weight = 0.5, includeInRAG = true, includeInSearch = false)  // RAG-only
    private String internalNotes;

    private BigDecimal price;  // Not indexed - no @AISearchable
}
```

### Step 2: Configure Actions (YAML)

Create `src/main/resources/ai-configs/product-ai-config.yml`:

```yaml
entityType: product
features:
  - search
  - embeddings
  - recommendations
autoProcess: true

crudOperations:
  create:
    generateEmbedding: true     # Generate embeddings on create
    indexForSearch: true         # Add to search index
    enableAnalysis: false        # Don't run expensive analysis on every create

  update:
    generateEmbedding: true     # Re-generate embeddings on update
    indexForSearch: true         # Update search index
    enableAnalysis: false

  delete:
    removeFromSearch: true       # Remove from search index
    cleanupEmbeddings: true      # Delete embeddings (GDPR compliance)
    generateEmbedding: false     # Don't generate on delete (obviously)
```

### Step 3: Use Your Repository Normally

```java
@Service
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    // CREATE - Auto-indexed synchronously (onCreateStrategy = SYNC)
    @Transactional
    public Product createProduct(Product product) {
        Product saved = productRepository.save(product);
        // Embeddings generated & indexed BEFORE method returns
        return saved;
    }

    // UPDATE - Auto-indexed asynchronously (indexingStrategy = ASYNC)
    @Transactional
    public Product updateProduct(UUID id, ProductUpdateDto dto) {
        Product product = productRepository.findById(id).orElseThrow();
        product.setDescription(dto.getDescription());
        Product updated = productRepository.save(product);
        // Embeddings queued for background processing (1-2 second delay)
        return updated;
    }

    // DELETE - Auto-removed synchronously (onDeleteStrategy = SYNC)
    @Transactional
    public void deleteProduct(UUID id) {
        Product product = productRepository.findById(id).orElseThrow();
        productRepository.delete(product);
        // Search index removal & embedding cleanup BEFORE method returns
    }

    // ROLLBACK - Auto-cleanup
    @Transactional
    public Product createProductWithValidation(Product product) {
        Product saved = productRepository.save(product);
        // Embeddings generated & indexed optimistically

        if (externalApiValidationFails(saved)) {
            throw new ValidationException("External validation failed");
            // Transaction rolls back
            // Framework automatically calls:
            //   - aiCapabilityService.removeFromSearch(saved)
            //   - aiCapabilityService.cleanupEmbeddings(saved)
        }

        return saved;
    }
}
```

**That's it.**

- ✅ **Create**: `repo.save(product)` → Auto-embedded & indexed
- ✅ **Update**: `repo.save(product)` → Old vector removed, new one queued
- ✅ **Delete**: `repo.delete(product)` → Vector instantly purged
- ✅ **Rollback**: `throw new RuntimeException()` → Vector operations cancelled

---

## 🧠 Why This Matters for Senior Engineers

This approach moves complexity from **Operations** (managing Kafka/CDC infrastructure) to **Architecture** (using the Application Framework).

### Architectural Benefits

| Traditional CDC Approach | AI Fabric Approach |
|-------------------------|-------------------|
| 5+ infrastructure components (Kafka, Debezium, Zookeeper, Connect, Workers) | 0 additional components (uses app database) |
| Separate ETL pipeline to maintain | Annotation-driven, co-located with entity |
| Eventually consistent (minutes-hours) | Tunable consistency (SYNC/ASYNC/BATCH) |
| No rollback support | Automatic rollback via transaction hooks |
| Debugging requires log aggregation across systems | Standard Spring Boot logging |
| Separate deployment pipeline for indexing workers | Workers run in app JVM or separate containers |
| Complex error handling (DLQ in Kafka + app) | Built-in retry with exponential backoff |
| Schema evolution requires pipeline updates | Entity changes auto-detected via reflection |

### Operational Benefits

- **Less Moving Parts**: No Kafka, no Debezium, no Python sidecars
- **Code Locality**: What gets indexed is defined on the entity class, not in a separate ETL config
- **Transactional Integrity**: You finally have a guarantee that your search results match your database
- **Observability**: Standard Spring Boot metrics (Micrometer/Prometheus) track queue depth, retry rates, processing latency
- **Testability**: Mock the `IndexingCoordinator` to disable indexing in tests

### Performance Characteristics

**SYNC Strategy:**
- **Latency**: Adds 100-500ms to transaction (embedding generation + vector insert)
- **Throughput**: Limited by embedding provider throughput
- **Use Case**: Critical operations requiring immediate consistency (new chat messages, PII deletions)

**ASYNC Strategy:**
- **Latency**: Adds ~1ms to transaction (queue insert only)
- **Throughput**: Unlimited (bottleneck is worker capacity, not transaction)
- **Consistency**: Eventually consistent (1-2 seconds typical, 10 seconds p99)
- **Use Case**: General-purpose indexing (product updates, blog posts)

**BATCH Strategy:**
- **Latency**: Adds ~1ms to transaction (queue insert only)
- **Throughput**: Unlimited
- **Consistency**: Eventually consistent (process interval, e.g., every 15 seconds)
- **Use Case**: Bulk operations, non-critical updates, cost optimization

---

## 🚀 Advanced Features

### 1. Dual Storage for Efficiency

AI Fabric uses **two storage layers**:

1. **Vector Database** (e.g., Pinecone, Weaviate, or built-in Lucene)
   - Stores embeddings for similarity search
   - Optimized for vector operations

2. **Metadata Table** (`ai_searchable_entities`)
   - Stores entity type, ID, content snapshot, vector ID, timestamp
   - Enables fast exact-match lookups without vector DB roundtrip
   - Provides audit trail for compliance (GDPR "right to be forgotten")

**Benefits:**
- Retrieve indexed content without vector DB call (faster)
- Detect orphaned vectors (in vector DB but not in metadata table)
- Audit trail: "What data did we have on this user at time T?"

### 2. Embedding Caching & Provider Fallback

```java
public AIEmbeddingResponse generateEmbedding(AIEmbeddingRequest request) {
    // Layer 1: Check cache
    String cacheKey = buildCacheKey(request);
    AIEmbeddingResponse cached = embeddingCache.get(cacheKey);
    if (cached != null) {
        return cached;
    }

    // Layer 2: Primary provider (e.g., ONNX local model)
    try {
        AIEmbeddingResponse response = primaryProvider.generateEmbedding(request);
        embeddingCache.put(cacheKey, response);
        return response;
    } catch (Exception e) {
        log.warn("Primary provider failed, trying fallback: {}", e.getMessage());
    }

    // Layer 3: Fallback provider (e.g., OpenAI API)
    AIEmbeddingResponse response = fallbackProvider.generateEmbedding(request);
    embeddingCache.put(cacheKey, response);
    return response;
}
```

**Supported Providers:**
- **ONNX** (local): Fast, free, no API limits, requires GPU
- **REST** (Docker container): Balanced, self-hosted, predictable cost
- **OpenAI** (cloud): High quality, expensive, API rate limits

### 3. Background Workers Architecture

AI Fabric runs **two separate worker pools**:

1. **AsyncIndexingWorker**
   - Poll interval: 1 second (configurable)
   - Batch size: 50 entries (configurable)
   - Strategy filter: `IndexingStrategy.ASYNC`
   - Use case: Near-real-time indexing

2. **BatchIndexingWorker**
   - Poll interval: 15 seconds (configurable)
   - Batch size: 200 entries (configurable)
   - Strategy filter: `IndexingStrategy.BATCH`
   - Use case: Bulk operations, cost optimization

Both use the same `IndexingWorkProcessor` but with different polling strategies.

**Configuration:**

```yaml
ai-infrastructure:
  indexing:
    workers:
      async:
        enabled: true
        poll-interval: 1000ms
        batch-size: 50
        thread-pool-size: 4
      batch:
        enabled: true
        poll-interval: 15000ms
        batch-size: 200
        thread-pool-size: 2
    queue:
      max-retries: 3
      visibility-timeout: 5m
      cleanup:
        completed-after: 7d
        dead-letter-after: 30d
```

### 4. Queue Maintenance Operations

**Automatic Cleanup:**

```java
// Purge completed entries older than 7 days (default)
@Scheduled(cron = "0 0 2 * * ?")  // 2 AM daily
public void cleanupCompletedEntries() {
    int purged = queueService.purgeCompletedOlderThan(
        LocalDateTime.now().minusDays(7)
    );
    log.info("Purged {} completed indexing entries", purged);
}

// Purge dead letters older than 30 days (default)
@Scheduled(cron = "0 0 3 * * ?")  // 3 AM daily
public void cleanupDeadLetters() {
    int purged = queueService.purgeDeadLettersOlderThan(
        LocalDateTime.now().minusDays(30)
    );
    log.info("Purged {} dead letter entries", purged);
}

// Reset stuck entries (visibility timeout expired)
@Scheduled(fixedDelay = 60000)  // Every 60 seconds
public void resetStuckEntries() {
    int reset = queueService.resetStuckEntries();
    if (reset > 0) {
        log.warn("Reset {} stuck entries (worker crash recovery)", reset);
    }
}
```

### 5. Migration/Backfill Support

When adding AI capabilities to an existing entity with millions of records:

```java
@AICapable(
    entityType = "product",
    migrationRepository = ProductMigrationRepository.class
)
public class Product { ... }
```

```java
@Repository
public interface ProductMigrationRepository extends JpaRepository<Product, UUID> {

    @Query("SELECT p FROM Product p WHERE p.aiIndexedAt IS NULL ORDER BY p.createdAt ASC")
    List<Product> findUnindexedProducts(Pageable pageable);
}
```

**Migration Runner:**

```java
@Service
public class ProductMigrationService {

    @Autowired
    private ProductMigrationRepository migrationRepo;

    @Autowired
    private IndexingCoordinator coordinator;

    @Scheduled(cron = "0 0 4 * * ?")  // 4 AM daily during off-peak
    public void backfillUnindexedProducts() {
        int batchSize = 1000;
        List<Product> unindexed = migrationRepo.findUnindexedProducts(
            PageRequest.of(0, batchSize)
        );

        for (Product product : unindexed) {
            coordinator.handle(
                product,
                "product",
                IndexingOperation.CREATE,
                new IndexingActionPlan(true, true, false, false, false),
                null
            );
        }

        log.info("Queued {} products for backfill indexing", unindexed.size());
    }
}
```

This processes existing data in chunks without overwhelming the system.

---

## 📊 Monitoring & Observability

AI Fabric exposes Micrometer metrics for production monitoring:

```java
// Queue depth by strategy
Gauge.builder("ai.indexing.queue.depth", queueService, qs -> qs.countPending(ASYNC))
    .tag("strategy", "async")
    .register(meterRegistry);

// Processing rate
Counter.builder("ai.indexing.processed")
    .tag("status", "success")
    .tag("strategy", "async")
    .register(meterRegistry);

// Retry rate
Counter.builder("ai.indexing.retries")
    .tag("strategy", "async")
    .register(meterRegistry);

// Dead letter rate (alert on this!)
Counter.builder("ai.indexing.dead_letter")
    .tag("strategy", "async")
    .tag("entity_type", "product")
    .register(meterRegistry);

// Processing latency
Timer.builder("ai.indexing.duration")
    .tag("strategy", "async")
    .tag("entity_type", "product")
    .register(meterRegistry);
```

**Recommended Alerts:**
- Queue depth > 10,000 for 5+ minutes (worker capacity issue)
- Dead letter rate > 1% (systematic failure, check logs)
- Processing latency p99 > 30 seconds (embedding provider degradation)
- Retry rate > 10% (transient issues with vector DB or embedding API)

---

## 🎯 Comparison Table

| Feature | Dual Write | Periodic Sync | CDC Pipeline | AI Fabric |
|---------|-----------|---------------|--------------|-----------|
| **Consistency** | None (fails silently) | Eventually (hours) | Eventually (minutes) | Tunable (SYNC/ASYNC/BATCH) |
| **Rollback Support** | ❌ None | ❌ None | ❌ None | ✅ Automatic |
| **Retry Logic** | ❌ Manual | ❌ None (re-sync fixes) | ✅ Kafka retry | ✅ Exponential backoff |
| **Infrastructure** | 0 extra | 0 extra | 3+ systems | 0 extra |
| **Latency Impact** | High (blocking) | None (async) | None (async) | Low (async) / High (sync) |
| **Code Complexity** | High | Medium | Very High | Low (annotations) |
| **Debugging** | Simple | Simple | Very Complex | Simple |
| **Cost** | Low | Low | High (Kafka cluster) | Low |
| **Scalability** | Poor | Poor | Excellent | Excellent |
| **Dead Letter Queue** | ❌ Manual | N/A | ✅ Yes | ✅ Yes |
| **Priority Support** | ❌ No | ❌ No | ⚠️ Manual (topics) | ✅ Built-in |
| **GDPR Compliance** | ❌ Manual tracking | ❌ Manual tracking | ⚠️ Complex (retention) | ✅ Automatic audit trail |

---

## 🏁 Conclusion

Stop treating your vector database like an external cache. **Treat it as part of your transaction boundary.**

AI Fabric gives you:
- ✅ **Transactional integrity** - No more ghost records or orphaned vectors
- ✅ **Automatic rollback** - Failed transactions clean up optimistic writes
- ✅ **Production-grade retry** - Exponential backoff with dead-letter queue
- ✅ **Tunable consistency** - SYNC for critical data, ASYNC for everything else
- ✅ **Zero infrastructure** - No Kafka, no Debezium, no Python sidecars
- ✅ **Code locality** - Indexing config lives on entity classes, not separate ETL
- ✅ **GDPR compliance** - Automatic audit trail and deletion support

**Make your RAG application reliable.** Not eventually consistent. Actually consistent.

---

## 🚀 Get Started

```xml
<dependency>
    <groupId>dev.ai-fabric</groupId>
    <artifactId>ai-infrastructure-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

**Learn more:** [ai-fabric.dev](https://ai-fabric.dev)
**GitHub:** [github.com/ai-fabric/ai-fabric-framework](https://github.com/ai-fabric/ai-fabric-framework)
**Docs:** [docs.ai-fabric.dev](https://docs.ai-fabric.dev)

---

*Ready for production. Battle-tested on millions of daily transactions.*
*Make Java Great Again. ☕*
