# Indexing Failure Tracking Analysis

**Question**: If indexing fails, how do we track this without AISearchableEntity?

**Answer**: Your system ALREADY has robust failure tracking through `IndexingQueueEntry` - it's independent of AISearchableEntity!

---

## Current Failure Tracking Architecture

### Two Separate Systems Track Different Things

#### 1. IndexingQueueEntry (Transient Operational Tracking)

**Table**: `ai_indexing_queue`

**Purpose**: Track **in-flight** indexing operations

**Lifecycle States** (`IndexingStatus.java`):
```java
PENDING      → Waiting to be processed
PROCESSING   → Currently being indexed
COMPLETED    → Successfully indexed (purged after retention period)
FAILED       → Temporary failure (unused in current code)
DEAD_LETTER  → Permanent failure after max retries
```

**Key Fields** (`IndexingQueueEntry.java`):
```java
- entityType, entityId          // What to index
- status                        // Current state
- retryCount                    // How many times tried
- maxRetries                    // When to give up (default: 5)
- errorMessage                  // Last error
- deadLetterReason              // Why it failed permanently
- requestedAt, startedAt        // Timing
- completedAt, lastErrorAt      // More timing
- scheduledFor                  // When to retry next
- visibilityTimeoutUntil        // Prevent duplicate processing
```

**Flow** (`IndexingQueueService.java`):
```java
// Enqueue for indexing
IndexingQueueEntry entry = queueService.enqueue(request);
// Status: PENDING

// Worker picks up entry
List<IndexingQueueEntry> batch = queueService.lease(strategy, batchSize);
// Status: PROCESSING

// If success
queueService.markCompleted(entry);
// Status: COMPLETED → purged after retention period

// If failure
queueService.markFailure(entry, errorMessage);
// Retry with exponential backoff (2s, 4s, 8s, 16s...)
// After 5 failures → Status: DEAD_LETTER (permanent)
```

**Automatic Retry Logic** (Line 88-112):
```java
public void markFailure(IndexingQueueEntry entry, String errorMessage) {
    entry.setErrorMessage(errorMessage);
    entry.setLastErrorAt(now);

    int attempts = entry.getRetryCount() + 1;
    entry.setRetryCount(attempts);

    if (attempts >= entry.getMaxRetries()) {
        // Give up - move to dead letter queue
        entry.setStatus(IndexingStatus.DEAD_LETTER);
        entry.setDeadLetterReason(errorMessage);
        log.error("Moved to dead letter after {} attempts: {}", attempts, errorMessage);
    } else {
        // Retry with exponential backoff
        entry.setStatus(IndexingStatus.PENDING);
        long delaySeconds = Math.min(300, (long) Math.pow(2, attempts));
        entry.setScheduledFor(now.plusSeconds(delaySeconds));
        log.warn("Will retry in {} seconds (attempt {}/{})", delaySeconds, attempts, maxRetries);
    }
}
```

**Cleanup Jobs** (`IndexingCleanupScheduler.java`):
```java
@Scheduled(fixedDelay = 5 minutes)
public void reclaimStuckEntries() {
    // Reset entries that got stuck in PROCESSING state
    // (worker crashed, etc.)
    queueService.resetStuckEntries();
}

@Scheduled(cron = "0 0 * * * *")  // Hourly
public void purgeOldEntries() {
    // Purge COMPLETED entries after retention period (default: 7 days)
    queueService.purgeCompletedOlderThan(threshold);

    // Purge DEAD_LETTER entries after retention period (default: 30 days)
    queueService.purgeDeadLettersOlderThan(threshold);
}
```

#### 2. AISearchableEntity (Persistent State Tracking)

**Table**: `ai_searchable_entity`

**Purpose**: Track **successfully indexed** entities (duplicate of vector DB)

**States** (implicit):
```java
vectorId != null  → Successfully indexed
vectorId == null  → Indexing failed or incomplete
```

**Usage**:
- Check if entity is already indexed (deduplication)
- Find entities without vectors (failed indexing)
- Sync with vector database

**Problems**:
- Duplicate storage with vector DB
- Can get out of sync
- Requires cleanup job for "no vector" entities

---

## The Key Insight

**IndexingQueueEntry and AISearchableEntity track DIFFERENT things**:

| System | Tracks | Duration | Purpose |
|--------|--------|----------|---------|
| **IndexingQueueEntry** | Operations in-flight | Transient (hours to days) | Operational tracking, retries, failures |
| **AISearchableEntity** | Successfully indexed entities | Persistent (forever) | Deduplication, sync verification |

**They're independent!** Removing AISearchableEntity doesn't affect failure tracking.

---

## After Removing AISearchableEntity

### Failure Tracking: UNCHANGED ✅

**IndexingQueueEntry continues to track**:
- ✅ In-flight operations
- ✅ Retry attempts
- ✅ Error messages
- ✅ Dead letter queue (permanent failures)
- ✅ Exponential backoff
- ✅ Stuck entry recovery

**Nothing changes in failure tracking!**

### What Changes: Deduplication Check

**Current** (with AISearchableEntity):
```java
// Check if already indexed
boolean alreadyIndexed = storageStrategy
    .findByEntityTypeAndEntityId("product", "123")
    .isPresent();
```

**After** (without AISearchableEntity):
```java
// Check vector DB directly
boolean alreadyIndexed = vectorDatabaseService
    .vectorExists("product", "123");
```

**Same functionality, single source of truth.**

---

## Monitoring Failed Indexing

### Current Monitoring Options

#### Option 1: Query Dead Letter Queue
```java
@RestController
public class IndexingMonitoringController {

    @GetMapping("/admin/indexing/failures")
    public List<FailedIndexingDTO> getFailures() {
        // Query dead letter entries
        List<IndexingQueueEntry> deadLetters =
            indexingQueueRepository.findByStatus(IndexingStatus.DEAD_LETTER);

        return deadLetters.stream()
            .map(entry -> new FailedIndexingDTO(
                entry.getEntityType(),
                entry.getEntityId(),
                entry.getRetryCount(),
                entry.getErrorMessage(),
                entry.getDeadLetterReason(),
                entry.getLastErrorAt()
            ))
            .collect(Collectors.toList());
    }

    @GetMapping("/admin/indexing/stats")
    public IndexingStats getStats() {
        return IndexingStats.builder()
            .pending(indexingQueueRepository.countByStatus(IndexingStatus.PENDING))
            .processing(indexingQueueRepository.countByStatus(IndexingStatus.PROCESSING))
            .deadLetter(indexingQueueRepository.countByStatus(IndexingStatus.DEAD_LETTER))
            .build();
    }
}
```

#### Option 2: Scheduled Report
```java
@Scheduled(cron = "0 0 8 * * MON")  // Monday mornings
public void sendWeeklyFailureReport() {
    List<IndexingQueueEntry> failures =
        indexingQueueRepository.findByStatusAndLastErrorAtAfter(
            IndexingStatus.DEAD_LETTER,
            LocalDateTime.now().minusDays(7)
        );

    if (!failures.isEmpty()) {
        String report = buildFailureReport(failures);
        emailService.send("admin@company.com", "Indexing Failures Report", report);
    }
}
```

#### Option 3: Metrics & Alerts
```java
@Component
public class IndexingMetrics {

    private final MeterRegistry meterRegistry;

    @Scheduled(fixedDelay = 60000)  // Every minute
    public void updateMetrics() {
        meterRegistry.gauge("indexing.queue.pending",
            indexingQueueRepository.countByStatus(IndexingStatus.PENDING));

        meterRegistry.gauge("indexing.queue.processing",
            indexingQueueRepository.countByStatus(IndexingStatus.PROCESSING));

        meterRegistry.gauge("indexing.queue.dead_letter",
            indexingQueueRepository.countByStatus(IndexingStatus.DEAD_LETTER));
    }
}
```

**Alert on**: `indexing.queue.dead_letter > 100`

#### Option 4: Logs
```java
// Already logged in IndexingQueueService.java:101
log.error("Indexing entry {} moved to dead letter after {} attempts: {}",
    entry.getId(), attempts, errorMessage);
```

**Use log aggregation** (Datadog, Splunk, ELK) to track:
- Error rates
- Common failure reasons
- Entities that consistently fail

---

## Enhanced Failure Tracking (Optional)

### Add Failure Analytics Table

If you want **historical analysis** of failures beyond the queue retention period:

```java
@Entity
@Table(name = "indexing_failure_log")
public class IndexingFailureLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private String entityId;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "error_category")
    @Enumerated(EnumType.STRING)
    private ErrorCategory errorCategory;  // NETWORK, VALIDATION, EMBEDDING, VECTOR_DB

    @Column(name = "retry_count")
    private int retryCount;

    @Column(name = "failed_at", nullable = false)
    private LocalDateTime failedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolution_method")
    private String resolutionMethod;  // MANUAL_REINDEX, AUTO_RETRY, IGNORED
}
```

**Usage**:
```java
// When entry moves to DEAD_LETTER
public void markFailure(IndexingQueueEntry entry, String errorMessage) {
    // ... existing retry logic

    if (attempts >= entry.getMaxRetries()) {
        // Log to failure analytics
        IndexingFailureLog log = new IndexingFailureLog();
        log.setEntityType(entry.getEntityType());
        log.setEntityId(entry.getEntityId());
        log.setErrorMessage(errorMessage);
        log.setErrorCategory(categorizeError(errorMessage));
        log.setRetryCount(attempts);
        log.setFailedAt(now);
        failureLogRepository.save(log);
    }
}

// Admin can analyze
@GetMapping("/admin/indexing/failure-trends")
public FailureTrends getFailureTrends(
    @RequestParam LocalDate from,
    @RequestParam LocalDate to
) {
    List<IndexingFailureLog> logs = failureLogRepository
        .findByFailedAtBetween(from.atStartOfDay(), to.atTime(23, 59, 59));

    // Group by error category, entity type, etc.
    return analyzeTrends(logs);
}
```

**Benefits**:
- Historical failure analysis
- Identify problematic entity types
- Track resolution effectiveness
- Compliance reporting

**Downside**: Extra table to maintain

---

## Comparison: Failure Tracking With vs Without AISearchableEntity

| Feature | With AISearchableEntity | Without AISearchableEntity |
|---------|------------------------|---------------------------|
| **In-flight tracking** | IndexingQueueEntry | IndexingQueueEntry (unchanged) |
| **Retry logic** | IndexingQueueEntry | IndexingQueueEntry (unchanged) |
| **Dead letter queue** | IndexingQueueEntry | IndexingQueueEntry (unchanged) |
| **Error messages** | IndexingQueueEntry | IndexingQueueEntry (unchanged) |
| **Failed entity tracking** | AISearchableEntity with vectorId=null | Query DEAD_LETTER entries |
| **Cleanup job** | Weekly job for "no vector" entities | Hourly purge of old DEAD_LETTER |
| **Query failed entities** | SQL on AISearchableEntity | SQL on IndexingQueueEntry |
| **Monitoring** | Check both systems | Check IndexingQueueEntry only |

---

## Practical Examples

### Example 1: Entity Fails to Index

**Without AISearchableEntity**:
```
1. Product 123 enqueued → IndexingQueueEntry (PENDING)
2. Worker processes → Status: PROCESSING
3. Embedding fails → markFailure() called
4. Status: PENDING, scheduledFor: now + 2s (retry 1/5)
5. Retry fails again → scheduledFor: now + 4s (retry 2/5)
6. ... continues up to 5 retries
7. Final failure → Status: DEAD_LETTER
8. Stays in DEAD_LETTER for 30 days (configurable)
9. Purged after retention period

Query dead letters:
SELECT * FROM ai_indexing_queue WHERE status = 'DEAD_LETTER'
```

**With AISearchableEntity** (current):
```
Same as above, PLUS:
- AISearchableEntity created with vectorId = null
- Weekly cleanup job finds and removes AISearchableEntity

Extra complexity for no benefit!
```

### Example 2: Check If Indexing Failed

**Without AISearchableEntity**:
```java
// Check if entity is in dead letter queue
Optional<IndexingQueueEntry> failure = indexingQueueRepository
    .findByEntityTypeAndEntityIdAndStatus("product", "123", IndexingStatus.DEAD_LETTER);

if (failure.isPresent()) {
    System.out.println("Failed: " + failure.get().getErrorMessage());
} else {
    // Either succeeded or still processing
    boolean exists = vectorDb.vectorExists("product", "123");
    System.out.println(exists ? "Indexed successfully" : "Still processing or never queued");
}
```

**With AISearchableEntity** (current):
```java
// Check AISearchableEntity
Optional<AISearchableEntity> entity = storageStrategy
    .findByEntityTypeAndEntityId("product", "123");

if (entity.isPresent()) {
    if (entity.get().getVectorId() == null) {
        System.out.println("Failed indexing");
    } else {
        System.out.println("Indexed successfully");
    }
} else {
    // Could be in queue, or never indexed
    Optional<IndexingQueueEntry> queueEntry = indexingQueueRepository
        .findByEntityTypeAndEntityId("product", "123");
    // ... check queue status
}

// Two systems to check! Complex!
```

### Example 3: Retry Failed Indexing

**Without AISearchableEntity**:
```java
@PostMapping("/admin/indexing/retry/{entryId}")
public void retryFailedIndexing(@PathVariable String entryId) {
    IndexingQueueEntry entry = indexingQueueRepository.findById(entryId)
        .orElseThrow(() -> new NotFoundException("Entry not found"));

    if (entry.getStatus() == IndexingStatus.DEAD_LETTER) {
        // Reset for retry
        entry.setStatus(IndexingStatus.PENDING);
        entry.setScheduledFor(LocalDateTime.now());
        entry.setRetryCount(0);
        entry.setErrorMessage(null);
        entry.setDeadLetterReason(null);
        indexingQueueRepository.save(entry);

        log.info("Manually retrying indexing for {}:{}",
            entry.getEntityType(), entry.getEntityId());
    }
}
```

**With AISearchableEntity** (current):
```java
// Same as above, but also need to handle AISearchableEntity
// More complex, potential sync issues
```

---

## Configuration

```yaml
ai:
  indexing:
    enabled: true

    queue:
      max-retries: 5                  # Default retry attempts
      visibility-timeout: PT10M       # 10 minutes before reclaiming stuck entries

    cleanup:
      enabled: true
      sweep-interval: PT5M            # Check for stuck entries every 5 minutes
      completed-retention: P7D        # Keep COMPLETED entries for 7 days
      dead-letter-retention: P30D     # Keep DEAD_LETTER entries for 30 days
```

---

## Migration Impact

### What Stays the Same ✅

1. **IndexingQueueEntry** - No changes
2. **Retry logic** - No changes
3. **Dead letter queue** - No changes
4. **Cleanup scheduler** - No changes
5. **Failure tracking** - No changes
6. **Monitoring APIs** - No changes (just query IndexingQueueEntry)

### What Changes 🔄

1. **Deduplication check** - Query vector DB instead of AISearchableEntity
2. **No-vector cleanup job** - DELETE (no longer needed)
3. **Monitoring** - Single system instead of two

### What Improves ⬆️

1. **Simpler** - One system to check instead of two
2. **More accurate** - Vector DB is source of truth
3. **Less cleanup** - No "orphaned entities" or "no vector" jobs
4. **Faster** - No AISearchableEntity sync overhead

---

## Recommendations

### For Production Monitoring

1. **Enable dead letter alerts**:
```yaml
monitoring:
  alerts:
    - metric: indexing.queue.dead_letter
      threshold: 100
      severity: warning
    - metric: indexing.queue.dead_letter
      threshold: 1000
      severity: critical
```

2. **Weekly failure reports**:
```java
@Scheduled(cron = "0 0 8 * * MON")
public void sendFailureReport() {
    // Email summary of failures to ops team
}
```

3. **Dashboard metrics**:
- Pending count (should be near 0 most of the time)
- Processing count (workers actively processing)
- Dead letter count (permanent failures)
- Retry rate (failures / total)

### For Historical Analysis (Optional)

Consider adding `IndexingFailureLog` table if you need:
- Long-term failure trends
- Compliance reporting
- Root cause analysis beyond 30 days

---

## Summary

**Q: How do we track indexing failures without AISearchableEntity?**

**A: The same way we do now - through `IndexingQueueEntry`!**

**Key Points**:
1. ✅ IndexingQueueEntry already tracks all failures (independent of AISearchableEntity)
2. ✅ Retry logic, dead letter queue, error messages - all preserved
3. ✅ Monitoring continues via IndexingQueueEntry queries
4. ✅ Actually simpler - single system instead of two
5. ✅ More accurate - vector DB is source of truth

**AISearchableEntity with vectorId=null was a poor way to track failures**:
- Required separate cleanup job
- Could get out of sync
- Duplicate of what IndexingQueueEntry already does better

**After removal**: Failure tracking works the same, just cleaner.
