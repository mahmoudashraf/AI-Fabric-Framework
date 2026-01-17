# Data Migration Module - User Guide

## Overview

The Data Migration Module is a production-ready system for bulk indexing and migrating existing entities into the AI Infrastructure's searchable entity store. It enables asynchronous, resumable, and filtered migration of JPA entities into the vector search and relationship query systems.

### What This Module Does

- **Bulk Entity Indexing**: Migrate thousands/millions of existing entities into AI-powered search
- **Async Processing**: Background job execution with pause/resume/cancel support
- **Smart Filtering**: Selective migration by date ranges, entity IDs, or custom policies
- **Progress Tracking**: Real-time monitoring with ETA calculations
- **Rate Limiting**: Control throughput to prevent system overload
- **Resumable Jobs**: Survive failures and restarts with automatic checkpoint recovery
- **Skip-Already-Indexed**: Avoid duplicate work with intelligent deduplication

### Target Audience

Developers migrating legacy data into AI-enabled search systems, performing initial data loads, or implementing backfill operations.

---

## Quick Start

### 1. Enable the Module

Add to your `application.yml`:

```yaml
ai:
  migration:
    enabled: true
    default-batch-size: 500
    default-rate-limit: 100
    max-concurrent-jobs: 3
```

### 2. Configure Entity Field Mappings

For default filtering support, configure field names:

```yaml
ai:
  migration:
    entity-fields:
      user-profile:
        created-at-field: "createdAt"
      product:
        created-at-field: "createdDate"
      order:
        created-at-field: "orderTimestamp"
```

### 3. Start a Migration

```java
@Autowired
private DataMigrationService migrationService;

public void migrateUsers() {
    MigrationRequest request = MigrationRequest.builder()
        .entityType("user-profile")
        .batchSize(500)
        .rateLimit(100)
        .reindexExisting(false)
        .build();
    
    MigrationJob job = migrationService.startMigration(request);
    System.out.println("Started migration job: " + job.getId());
}
```

### 4. Monitor Progress

```java
public void checkProgress(String jobId) {
    MigrationProgress progress = migrationService.getProgress(jobId);
    
    System.out.printf("Status: %s%n", progress.getStatus());
    System.out.printf("Progress: %d/%d (%.2f%%)%n", 
        progress.getProcessed(), 
        progress.getTotal(), 
        progress.getPercentComplete());
    System.out.printf("ETA: %s%n", progress.getEstimatedTimeRemaining());
}
```

---

## Core Concepts

### How Migration Works

1. **Discovery**: Module scans all `@AICapable` entities with JPA repositories
2. **Job Creation**: Creates a `MigrationJob` record with total entity count
3. **Async Processing**: Spawns background thread to process batches
4. **Pagination**: Fetches entities page-by-page from repository
5. **Filtering**: Applies date/ID filters or custom policies
6. **Deduplication**: Skips already-indexed entities (unless `reindexExisting=true`)
7. **Queueing**: Enqueues each entity for indexing via `IndexingQueueService`
8. **Checkpointing**: Updates job progress after each batch
9. **Rate Limiting**: Applies throttling between batches if configured
10. **Completion**: Marks job as COMPLETED when all pages processed

### Job Lifecycle

```
RUNNING → COMPLETED
   ↓
PAUSED → (resume) → RUNNING
   ↓
CANCELLED
   ↓
FAILED
```

### Entity Registration

Entities must be:
- Annotated with `@AICapable(entityType = "your-type")`
- Have a corresponding JPA repository
- Have an `ai-entity-config` entry loaded

Example:

```java
@Entity
@AICapable(
    entityType = "user-profile",
    autoEmbedding = true,
    indexable = true
)
public class UserProfile {
    @Id
    private UUID id;
    
    private String name;
    private LocalDateTime createdAt;
    // ... other fields
}
```

---

## Data Model

### MigrationJob Entity

Tracks job state and progress:

```java
@Entity
@Table(name = "ai_migration_jobs")
public class MigrationJob {
    String id;                    // "mig-{UUID}"
    String entityType;            // Entity type being migrated
    MigrationStatus status;       // RUNNING | PAUSED | COMPLETED | FAILED | CANCELLED
    
    // Progress Tracking
    Long totalEntities;           // Total entities to process
    Long processedEntities;       // Successfully enqueued
    Long failedEntities;          // Failed to enqueue
    Integer currentPage;          // Last completed page
    
    // Configuration
    Integer batchSize;            // Entities per page
    Integer rateLimit;            // Max requests/minute (null = no limit)
    Boolean reindexExisting;      // Reindex already-indexed entities
    MigrationFilters filters;     // Optional filters
    
    // Timestamps
    LocalDateTime startedAt;
    LocalDateTime completedAt;
    LocalDateTime lastUpdatedAt;
    
    // Metadata
    String errorMessage;          // Error details if FAILED
    String createdBy;             // User/system identifier
}
```

### MigrationRequest DTO

Input for starting a migration:

```java
@Value
@Builder
public class MigrationRequest {
    @NotBlank
    String entityType;            // Required: entity type to migrate
    
    @Min(1)
    Integer batchSize;            // Default: 500
    
    Integer rateLimit;            // Requests/min; null = no limit
    
    Boolean reindexExisting;      // Default: false
    
    MigrationFilters filters;     // Optional filtering
    
    String createdBy;             // Optional audit trail
}
```

### MigrationFilters

Optional filters to narrow migration scope:

```java
@Data
@Builder
public class MigrationFilters {
    LocalDate createdBefore;      // Only entities created before this date
    LocalDate createdAfter;       // Only entities created after this date
    List<String> entityIds;       // Specific entity IDs to migrate
}
```

### MigrationProgress DTO

Real-time progress snapshot:

```java
@Value
@Builder
public class MigrationProgress {
    String jobId;
    MigrationStatus status;
    long total;
    long processed;
    long failed;
    double percentComplete;       // 0.0 to 100.0
    Duration estimatedTimeRemaining;
}
```

### MigrationStatus Enum

```java
public enum MigrationStatus {
    PENDING,        // Not yet used
    RUNNING,        // Actively processing
    PAUSED,         // Temporarily stopped
    COMPLETED,      // Successfully finished
    FAILED,         // Fatal error occurred
    CANCELLED       // User-requested stop
}
```

---

## Configuration Reference

### Core Settings

```yaml
ai:
  migration:
    # Master switch
    enabled: true                         # Default: true
    
    # Defaults
    default-batch-size: 500               # Default: 500
    default-rate-limit: 100               # Default: 100 (requests/min)
    
    # Concurrency
    max-concurrent-jobs: 3                # Default: 3
    
    # Cleanup
    cleanup-completed-after-days: 30      # Default: 30
    
    # Entity Field Mappings (for default filtering)
    entity-fields:
      user-profile:
        created-at-field: "createdAt"
      product:
        created-at-field: "createdDate"
      # ... more entities
```

### Batch Size Recommendations

| Total Entities | Recommended Batch Size |
|----------------|------------------------|
| < 1,000        | 100                    |
| 1K - 10K       | 500                    |
| 10K - 100K     | 1,000                  |
| 100K - 1M      | 2,000                  |
| > 1M           | 5,000                  |

### Rate Limiting

Rate limit controls throughput (requests per minute):

```yaml
default-rate-limit: 100  # 100 entities/minute
```

**Delay Calculation**: `delayMs = 60,000 / rateLimit`
- `rateLimit=100` → 600ms delay between batches
- `rateLimit=60` → 1000ms delay
- `rateLimit=null` → No delay

---

## API Reference

### DataMigrationService

#### Start Migration

```java
public MigrationJob startMigration(@Valid MigrationRequest request)
```

Starts an asynchronous migration job.

**Parameters**:
- `request`: Migration configuration

**Returns**: Created `MigrationJob` with initial state

**Throws**:
- `IllegalArgumentException`: If entity type not found or misconfigured
- `IllegalStateException`: If no field config or policy for entity type

**Example**:

```java
MigrationRequest request = MigrationRequest.builder()
    .entityType("user-profile")
    .batchSize(1000)
    .rateLimit(200)
    .reindexExisting(false)
    .filters(MigrationFilters.builder()
        .createdAfter(LocalDate.of(2024, 1, 1))
        .createdBefore(LocalDate.of(2024, 12, 31))
        .build())
    .createdBy("admin-script")
    .build();

MigrationJob job = migrationService.startMigration(request);
```

#### Convenience Method: Index All

```java
public MigrationJob indexAllEntities(String entityType)
```

Migrates all entities with default settings.

**Example**:

```java
MigrationJob job = migrationService.indexAllEntities("product");
```

#### Get Progress

```java
public MigrationProgress getProgress(String jobId)
```

Retrieves current progress for a job.

**Example**:

```java
MigrationProgress progress = migrationService.getProgress("mig-123");
System.out.printf("%.2f%% complete%n", progress.getPercentComplete());
```

#### Pause Migration

```java
public void pauseMigration(String jobId)
```

Pauses a running job. Current batch completes before pausing.

**Example**:

```java
migrationService.pauseMigration("mig-123");
```

#### Resume Migration

```java
public void resumeMigration(String jobId)
```

Resumes a paused job from last checkpoint.

**Example**:

```java
migrationService.resumeMigration("mig-123");
```

#### Cancel Migration

```java
public void cancelMigration(String jobId)
```

Permanently stops a job.

**Example**:

```java
migrationService.cancelMigration("mig-123");
```

#### List Jobs

```java
public Iterable<MigrationJob> listJobs()
```

Returns all migration jobs.

**Example**:

```java
Iterable<MigrationJob> jobs = migrationService.listJobs();
for (MigrationJob job : jobs) {
    System.out.printf("%s: %s (%d/%d)%n", 
        job.getId(), 
        job.getStatus(), 
        job.getProcessedEntities(), 
        job.getTotalEntities());
}
```

---

## Filtering Strategies

### Strategy 1: Date Range Filters (Default)

Requires `entity-fields` configuration.

**Configuration**:

```yaml
ai:
  migration:
    entity-fields:
      user-profile:
        created-at-field: "createdAt"
```

**Usage**:

```java
MigrationRequest request = MigrationRequest.builder()
    .entityType("user-profile")
    .filters(MigrationFilters.builder()
        .createdAfter(LocalDate.of(2024, 1, 1))
        .createdBefore(LocalDate.of(2024, 12, 31))
        .build())
    .build();
```

**Supported Field Types**:
- `LocalDateTime` (converted to `LocalDate`)
- `LocalDate`

### Strategy 2: Specific Entity IDs

Migrate only specific entities:

```java
MigrationRequest request = MigrationRequest.builder()
    .entityType("product")
    .filters(MigrationFilters.builder()
        .entityIds(List.of(
            "prod-001", 
            "prod-002", 
            "prod-003"
        ))
        .build())
    .build();
```

### Strategy 3: Custom Filter Policy

For complex filtering logic, implement `MigrationFilterPolicy`:

```java
@Component
public class ActiveUserFilterPolicy implements MigrationFilterPolicy {
    
    @Override
    public boolean supports(String entityType) {
        return "user-profile".equals(entityType);
    }
    
    @Override
    public boolean shouldMigrate(Object entity, 
                                 MigrationRequest request, 
                                 AIEntityConfig config) {
        if (entity instanceof UserProfile user) {
            // Only migrate active users
            return user.isActive() && user.getLastLoginAt() != null;
        }
        return false;
    }
}
```

**Benefits**:
- Type-safe filtering
- Access to full entity state
- Complex business logic
- No field mapping required

### Strategy 4: Combine Filters

Combine date ranges + entity IDs:

```java
MigrationRequest request = MigrationRequest.builder()
    .entityType("order")
    .filters(MigrationFilters.builder()
        .createdAfter(LocalDate.of(2024, 1, 1))
        .entityIds(List.of("order-vip-001", "order-vip-002"))
        .build())
    .build();
```

---

## Advanced Features

### Reindexing Existing Entities

By default, migration skips entities already present in the vector database (by `entityType` + `entityId`). To force reindexing:

```java
MigrationRequest request = MigrationRequest.builder()
    .entityType("product")
    .reindexExisting(true)  // Force reindex
    .build();
```

**Use Cases**:
- Schema changes in searchable fields
- Embedding model updates
- Data corrections
- Index corruption recovery

### Rate Limiting for Production

Control resource usage during business hours:

```java
// Slow migration during peak hours (60/min = 1/sec)
MigrationRequest dayRequest = MigrationRequest.builder()
    .entityType("user-profile")
    .rateLimit(60)
    .build();

// Fast migration during off-hours (1200/min = 20/sec)
MigrationRequest nightRequest = MigrationRequest.builder()
    .entityType("user-profile")
    .rateLimit(1200)
    .build();
```

**Disable Rate Limiting**:

```java
.rateLimit(null)  // or omit
```

### Concurrent Jobs

Run multiple entity types in parallel:

```yaml
ai:
  migration:
    max-concurrent-jobs: 5
```

```java
// All run concurrently
migrationService.indexAllEntities("user-profile");
migrationService.indexAllEntities("product");
migrationService.indexAllEntities("order");
migrationService.indexAllEntities("review");
```

### Custom Repository Binding

Override default repository discovery:

```java
@Entity
@AICapable(
    entityType = "user-profile",
    migrationRepository = CustomUserRepository.class
)
public class UserProfile {
    // ...
}
```

### Progress Monitoring Dashboard

Build a monitoring UI:

```java
@RestController
@RequestMapping("/api/admin/migrations")
public class MigrationMonitorController {
    
    @Autowired
    private DataMigrationService migrationService;
    
    @GetMapping
    public List<JobStatusDTO> getAllJobs() {
        return StreamSupport.stream(migrationService.listJobs().spliterator(), false)
            .map(this::toDTO)
            .toList();
    }
    
    @GetMapping("/{jobId}/progress")
    public MigrationProgress getProgress(@PathVariable String jobId) {
        return migrationService.getProgress(jobId);
    }
    
    @PostMapping("/{jobId}/pause")
    public void pause(@PathVariable String jobId) {
        migrationService.pauseMigration(jobId);
    }
    
    @PostMapping("/{jobId}/resume")
    public void resume(@PathVariable String jobId) {
        migrationService.resumeMigration(jobId);
    }
    
    @PostMapping("/{jobId}/cancel")
    public void cancel(@PathVariable String jobId) {
        migrationService.cancelMigration(jobId);
    }
    
    private JobStatusDTO toDTO(MigrationJob job) {
        MigrationProgress progress = migrationService.getProgress(job.getId());
        return JobStatusDTO.builder()
            .jobId(job.getId())
            .entityType(job.getEntityType())
            .status(job.getStatus())
            .percentComplete(progress.getPercentComplete())
            .eta(progress.getEstimatedTimeRemaining())
            .startedAt(job.getStartedAt())
            .build();
    }
}
```

---

## Database Schema

### Table: `ai_migration_jobs`

```sql
CREATE TABLE ai_migration_jobs (
    id VARCHAR(255) PRIMARY KEY,
    entity_type VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    
    -- Progress
    total_entities BIGINT NOT NULL,
    processed_entities BIGINT NOT NULL,
    failed_entities BIGINT NOT NULL,
    current_page INT NOT NULL,
    
    -- Configuration
    batch_size INT NOT NULL,
    rate_limit INT,
    reindex_existing BOOLEAN NOT NULL,
    filter_config TEXT,
    
    -- Timestamps
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    last_updated_at TIMESTAMP,
    
    -- Metadata
    error_message TEXT,
    created_by VARCHAR(255)
);

-- Indexes
CREATE INDEX idx_mig_status ON ai_migration_jobs(status);
CREATE INDEX idx_mig_entity_type ON ai_migration_jobs(entity_type);
CREATE INDEX idx_mig_started_at ON ai_migration_jobs(started_at);
```

---

## Best Practices

### Initial Data Load

For first-time setup with large datasets:

```java
// 1. Start with small batch to test
MigrationJob testJob = migrationService.startMigration(
    MigrationRequest.builder()
        .entityType("user-profile")
        .batchSize(10)
        .filters(MigrationFilters.builder()
            .entityIds(List.of("test-user-1", "test-user-2"))
            .build())
        .build()
);

// 2. Verify test job completes successfully
waitForCompletion(testJob.getId());

// 3. Run full migration with optimal batch size
MigrationJob fullJob = migrationService.startMigration(
    MigrationRequest.builder()
        .entityType("user-profile")
        .batchSize(2000)
        .rateLimit(500)
        .build()
);
```

### Incremental Migrations

Migrate recent data only:

```java
// Migrate last 30 days
LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);

MigrationRequest request = MigrationRequest.builder()
    .entityType("order")
    .filters(MigrationFilters.builder()
        .createdAfter(thirtyDaysAgo)
        .build())
    .build();
```

### Zero-Downtime Migration

Migrate during low-traffic periods:

```java
@Scheduled(cron = "0 0 2 * * ?")  // 2 AM daily
public void migrateNewEntities() {
    LocalDate yesterday = LocalDate.now().minusDays(1);
    LocalDate today = LocalDate.now();
    
    migrationService.startMigration(
        MigrationRequest.builder()
            .entityType("user-profile")
            .filters(MigrationFilters.builder()
                .createdAfter(yesterday)
                .createdBefore(today)
                .build())
            .rateLimit(1000)  // Faster during off-hours
            .build()
    );
}
```

### Error Handling

Monitor and retry failed jobs:

```java
public void monitorJobs() {
    for (MigrationJob job : migrationService.listJobs()) {
        if (job.getStatus() == MigrationStatus.FAILED) {
            log.error("Job {} failed: {}", job.getId(), job.getErrorMessage());
            
            // Optionally retry
            if (job.getProcessedEntities() > 0) {
                // Resume from checkpoint
                migrationService.resumeMigration(job.getId());
            }
        }
    }
}
```

### Testing Migrations

```java
@SpringBootTest
class MigrationIntegrationTest {
    
    @Autowired
    private DataMigrationService migrationService;
    
    @Autowired
    private UserRepository userRepository;
    
    @Test
    void shouldMigrateAllUsers() {
        // Given: 100 test users
        List<User> users = createTestUsers(100);
        userRepository.saveAll(users);
        
        // When: Start migration
        MigrationJob job = migrationService.indexAllEntities("user-profile");
        
        // Then: Wait and verify
        waitForCompletion(job.getId(), Duration.ofMinutes(5));
        
        MigrationProgress progress = migrationService.getProgress(job.getId());
        assertThat(progress.getStatus()).isEqualTo(MigrationStatus.COMPLETED);
        assertThat(progress.getProcessed()).isEqualTo(100);
        assertThat(progress.getFailed()).isZero();
    }
}
```

---

## Troubleshooting

### Issue: Job Stuck in RUNNING

**Symptoms**: Job shows RUNNING but no progress for extended period

**Diagnosis**:
1. Check executor service threads: `jstack <pid> | grep migration-worker`
2. Verify repository queries aren't blocking
3. Check indexing queue for backlog

**Solution**:
```java
// Pause and resume to reset
migrationService.pauseMigration(jobId);
Thread.sleep(5000);
migrationService.resumeMigration(jobId);
```

### Issue: High Failure Rate

**Symptoms**: `failedEntities` count increasing rapidly

**Diagnosis**:
1. Check logs for exceptions
2. Verify indexing queue is accepting requests
3. Confirm entity serialization works

**Solution**:
```java
// Reduce batch size and rate limit
MigrationRequest retry = MigrationRequest.builder()
    .entityType(failedJob.getEntityType())
    .batchSize(50)  // Smaller batches
    .rateLimit(20)  // Slower rate
    .build();
```

### Issue: Memory Issues

**Symptoms**: OutOfMemoryError during migration

**Cause**: Batch size too large for available heap

**Solution**:
```yaml
ai:
  migration:
    default-batch-size: 100  # Reduce from 500
```

### Issue: Field Not Found Exception

**Symptoms**: `IllegalStateException: Field 'createdAt' not found`

**Cause**: Incorrect field mapping or missing field

**Solution**:
```yaml
ai:
  migration:
    entity-fields:
      user-profile:
        created-at-field: "createdDate"  # Fix field name
```

### Issue: No Entities Registered

**Symptoms**: `IllegalArgumentException: No repository registration for entity type`

**Diagnosis**:
1. Verify `@AICapable` annotation on entity
2. Check JPA repository exists
3. Confirm `ai-entity-config` entry loaded

**Solution**:
```java
// Ensure entity is properly annotated
@Entity
@AICapable(entityType = "user-profile")
public class UserProfile {
    // ...
}

// Verify config exists
ai-entities:
  user-profile:
    auto-embedding: true
    indexable: true
```

---

## Example Use Cases

### Use Case 1: Initial Index Population

Populate search index after enabling AI features:

```java
@Component
public class InitialIndexPopulator {
    
    @Autowired
    private DataMigrationService migrationService;
    
    @EventListener(ApplicationReadyEvent.class)
    public void populateInitialIndex() {
        if (shouldRunInitialMigration()) {
            List.of("user-profile", "product", "order", "review")
                .forEach(entityType -> {
                    migrationService.startMigration(
                        MigrationRequest.builder()
                            .entityType(entityType)
                            .batchSize(1000)
                            .rateLimit(200)
                            .createdBy("system-init")
                            .build()
                    );
                });
        }
    }
}
```

### Use Case 2: Selective Backfill

Backfill only VIP customers:

```java
@Component
public class VipCustomerMigrationPolicy implements MigrationFilterPolicy {
    
    @Override
    public boolean supports(String entityType) {
        return "customer".equals(entityType);
    }
    
    @Override
    public boolean shouldMigrate(Object entity, 
                                 MigrationRequest request, 
                                 AIEntityConfig config) {
        if (entity instanceof Customer customer) {
            return customer.getTier() == CustomerTier.VIP 
                || customer.getLifetimeValue() > 10000;
        }
        return false;
    }
}
```

### Use Case 3: Scheduled Daily Sync

Keep index in sync with daily incremental updates:

```java
@Component
public class DailyMigrationScheduler {
    
    @Autowired
    private DataMigrationService migrationService;
    
    @Scheduled(cron = "0 0 3 * * ?")  // 3 AM daily
    public void migrateYesterdayData() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate today = LocalDate.now();
        
        List.of("user-profile", "product", "order")
            .forEach(entityType -> {
                migrationService.startMigration(
                    MigrationRequest.builder()
                        .entityType(entityType)
                        .filters(MigrationFilters.builder()
                            .createdAfter(yesterday)
                            .createdBefore(today)
                            .build())
                        .reindexExisting(true)
                        .createdBy("daily-sync")
                        .build()
                );
            });
    }
}
```

### Use Case 4: Multi-Tenant Migration

Migrate one tenant at a time:

```java
public void migrateTenant(String tenantId) {
    MigrationRequest request = MigrationRequest.builder()
        .entityType("user-profile")
        .filters(MigrationFilters.builder()
            .entityIds(getUserIdsForTenant(tenantId))
            .build())
        .createdBy("tenant-migration-" + tenantId)
        .build();
    
    MigrationJob job = migrationService.startMigration(request);
    
    // Track per-tenant progress
    tenantMigrationTracker.put(tenantId, job.getId());
}
```

---

## Performance Tuning

### Batch Size Optimization

Find optimal batch size through testing:

```java
public void findOptimalBatchSize(String entityType) {
    int[] batchSizes = {100, 500, 1000, 2000, 5000};
    
    for (int batchSize : batchSizes) {
        long start = System.currentTimeMillis();
        
        MigrationJob job = migrationService.startMigration(
            MigrationRequest.builder()
                .entityType(entityType)
                .batchSize(batchSize)
                .rateLimit(null)  // Disable rate limiting for test
                .filters(MigrationFilters.builder()
                    .entityIds(getSampleEntityIds(1000))  // Fixed sample
                    .build())
                .build()
        );
        
        waitForCompletion(job.getId());
        long duration = System.currentTimeMillis() - start;
        
        System.out.printf("Batch size %d: %dms (%.2f entities/sec)%n",
            batchSize, duration, 1000.0 / duration * 1000);
    }
}
```

### Thread Pool Sizing

Adjust for CPU/IO-bound workloads:

```yaml
# CPU-bound (lots of embedding generation)
ai:
  migration:
    max-concurrent-jobs: 2  # Conservative

# IO-bound (mostly database/network)
ai:
  migration:
    max-concurrent-jobs: 10  # Aggressive
```

---

## Migration Checklist

Before running production migration:

- [ ] Test with small sample (10-100 entities)
- [ ] Verify entity configuration correct
- [ ] Configure appropriate batch size
- [ ] Set rate limiting for production load
- [ ] Implement monitoring/alerting
- [ ] Plan for resume/retry on failures
- [ ] Schedule during low-traffic period
- [ ] Have rollback plan ready
- [ ] Monitor database connection pool
- [ ] Check indexing queue capacity
- [ ] Verify embedding provider quotas
- [ ] Document migration window in runbook

---

## FAQ

**Q: Can I migrate entities without a createdAt field?**
A: Yes, use a custom `MigrationFilterPolicy` or don't use date filters.

**Q: What happens if migration fails mid-way?**
A: Job checkpoints progress per page. Resume from `currentPage`.

**Q: Can I change batch size for a running job?**
A: No. Pause, cancel, and start new job with new batch size.

**Q: Does migration affect production traffic?**
A: Only if rate limiting is too aggressive. Monitor queue depth and adjust.

**Q: How long do migration jobs persist?**
A: Configure via `cleanup-completed-after-days` (default: 30 days).

**Q: Can I run migrations on read replicas?**
A: Yes, configure datasource for migration repositories.

**Q: What if my entity has millions of records?**
A: Use date range filters to chunk into smaller jobs.

---

## Version Information

- **Module Version**: 1.0.0
- **Minimum Java**: 17
- **Spring Boot**: 3.x
- **Dependencies**: ai-infrastructure-core (indexing, storage, configuration)

---

## Support & Resources

- **Source Code**: `com.ai.infrastructure.migration`
- **Main Service**: `DataMigrationService.java`
- **Configuration**: `MigrationProperties.java`
- **Tests**: `DataMigrationServiceTest.java`

---

*This guide reflects the actual implementation in the codebase. For framework-wide features (indexing, embeddings, search), refer to the main AI Infrastructure documentation.*
