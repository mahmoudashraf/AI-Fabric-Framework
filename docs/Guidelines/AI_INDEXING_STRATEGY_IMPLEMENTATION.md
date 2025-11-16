# AI Indexing Strategy & Cleanup Implementation Guide

**Document Version:** 1.0  
**Date:** 2025-11-14  
**Status:** Agreed Design - Ready for Implementation

---

## Executive Summary

This document defines the **complete indexing and cleanup architecture** for AI-capable entities, incorporating:
- ✅ **3-tier indexing strategy** (SYNC, ASYNC, BATCH)
- ✅ **Flexible priority system** (entity-level defaults + operation overrides + method overrides)
- ✅ **Database-backed queue** with pluggable providers
- ✅ **Comprehensive cleanup processes** with multiple strategies
- ✅ **No synchronous blocking** by default (performance-first approach)

**Key Principles:**
1. **Performance First:** Application never blocks on indexing (except explicit SYNC)
2. **Simple Choices:** Clear 3-strategy model (SYNC/ASYNC/BATCH)
3. **Flexible Configuration:** Defaults with override capability
4. **Durable Queuing:** Database-backed with retry and dead-letter handling
5. **Comprehensive Cleanup:** Automated cleanup of stale data

---

## Table of Contents

1. [Indexing Strategy Design](#1-indexing-strategy-design)
2. [Annotation Architecture](#2-annotation-architecture)
3. [Queue Infrastructure](#3-queue-infrastructure)
4. [Processing Workers](#4-processing-workers)
5. [Cleanup Processes](#5-cleanup-processes)
6. [Implementation Guide](#6-implementation-guide)
7. [Configuration](#7-configuration)
8. [Monitoring & Metrics](#8-monitoring--metrics)
9. [Migration Plan](#9-migration-plan)

---

## 1. Indexing Strategy Design

### 1.1 Three-Tier Strategy

```java
package com.ai.infrastructure.indexing;

/**
 * Indexing strategy determines HOW and WHEN entities are indexed.
 * 
 * Strategy Selection Guide:
 * - SYNC: Critical operations requiring immediate consistency (use sparingly)
 * - ASYNC: Standard content requiring quick indexing (most common)
 * - BATCH: High-volume data where delay is acceptable (analytics, logs)
 */
public enum IndexingStrategy {
    
    /**
     * AUTO: Inherit from parent configuration
     * Used in: @AIProcess to inherit from @AICapable
     * Used in: @AICapable.onXxxStrategy to inherit from indexingStrategy
     */
    AUTO,
    
    /**
     * SYNC: Synchronous indexing - blocks until complete
     * 
     * Behavior:
     * - Indexes in same transaction as main operation
     * - Request blocks until indexing completes (~50-500ms overhead)
     * - Guaranteed indexed before method returns
     * 
     * Use Cases:
     * - Legal/compliance deletions (GDPR right to be forgotten)
     * - Fraud detection (order blocking)
     * - Critical visibility requirements
     * 
     * Trade-offs:
     * ✓ Immediate consistency
     * ✓ Simple error handling (fails if indexing fails)
     * ✗ Slower response times
     * ✗ Indexing failures block requests
     * ✗ Doesn't scale well under load
     * 
     * Performance Impact: +50-500ms per request
     */
    SYNC,
    
    /**
     * ASYNC: Asynchronous indexing - high priority queue
     * 
     * Behavior:
     * - Queues for immediate async processing
     * - Request returns immediately
     * - Processed by dedicated workers within 1-5 seconds
     * - Retries on failure with exponential backoff
     * 
     * Use Cases:
     * - Products, users, articles (user-facing content)
     * - Order status updates
     * - Standard CRUD operations
     * 
     * Trade-offs:
     * ✓ Fast response times
     * ✓ Indexing failures don't block requests
     * ✓ Retry capability
     * ✗ Small delay before searchable (1-5s)
     * ✗ Eventual consistency
     * 
     * Performance Impact: ~5-10ms per request (queue overhead)
     * Indexing SLA: 1-5 seconds
     */
    ASYNC,
    
    /**
     * BATCH: Batch processing - scheduled bulk indexing
     * 
     * Behavior:
     * - Accumulates in queue
     * - Processed in bulk on schedule (every 30s-5min)
     * - High throughput, efficient resource usage
     * - Retries entire batch on failure
     * 
     * Use Cases:
     * - Behaviors, analytics, logs (high-volume data)
     * - View counts, statistics
     * - Non-critical metadata updates
     * 
     * Trade-offs:
     * ✓ Extremely high throughput
     * ✓ Efficient resource usage
     * ✓ Low impact on application
     * ✗ Longer delay before searchable (30s-5min)
     * ✗ Eventual consistency with higher latency
     * 
     * Performance Impact: ~2-5ms per request (queue overhead)
     * Indexing SLA: 30 seconds to 5 minutes
     */
    BATCH
}
```

### 1.2 Strategy Selection Decision Tree

```
┌─────────────────────────────────────────────────────────────────┐
│ Q1: Does the user need to see this in search IMMEDIATELY?      │
│     (within 100ms of the operation completing)                  │
├─────────────────────────────────────────────────────────────────┤
│ YES → Is this a legal/compliance requirement?                  │
│       ├─ YES → SYNC (only option for guaranteed compliance)     │
│       └─ NO  → Use visibility cache + ASYNC                     │
│                (immediate visibility without blocking)           │
├─────────────────────────────────────────────────────────────────┤
│ NO → Is this user-facing content that should be searchable     │
│      within a few seconds?                                      │
│      ├─ YES → ASYNC                                             │
│      └─ NO  → Is this high-volume data (>100 ops/min)?        │
│              ├─ YES → BATCH                                     │
│              └─ NO  → ASYNC (safe default)                      │
└─────────────────────────────────────────────────────────────────┘
```

### 1.3 Common Patterns by Entity Type

| Entity Type | Strategy | Reasoning |
|-------------|----------|-----------|
| **Order** | ASYNC (default)<br>SYNC (delete) | Quick indexing, immediate delete visibility |
| **Product** | ASYNC (all ops) | User-facing content needs quick updates |
| **User** | ASYNC (all ops) | Profile changes should reflect quickly |
| **Behavior** | BATCH (all ops) | High volume, delay acceptable |
| **Analytics** | BATCH (all ops) | High volume, not time-sensitive |
| **Article/Post** | ASYNC (all ops) | Content should be searchable quickly |
| **Comment** | ASYNC or BATCH | Depends on volume and importance |
| **Log** | BATCH (all ops) | High volume, eventual consistency fine |

---

## 2. Annotation Architecture

### 2.1 Enhanced @AICapable Annotation

```java
package com.ai.infrastructure.annotation;

import com.ai.infrastructure.indexing.IndexingStrategy;
import java.lang.annotation.*;

/**
 * Marks an entity as AI-capable with automatic indexing support.
 * 
 * Configuration Hierarchy:
 * 1. Set default strategy via indexingStrategy
 * 2. Override per operation via onXxxStrategy (optional)
 * 3. Override per method via @AIProcess.indexingStrategy (optional)
 * 
 * Example:
 * <pre>
 * @AICapable(
 *     entityType = "product",
 *     indexingStrategy = IndexingStrategy.ASYNC,     // Default for all
 *     onDeleteStrategy = IndexingStrategy.SYNC       // Override for deletes
 * )
 * public class Product { }
 * </pre>
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AICapable {
    
    // ════════════════════════════════════════════════════════
    // Core Configuration
    // ════════════════════════════════════════════════════════
    
    /**
     * Unique identifier for this entity type
     * Used for indexing, search, and configuration
     */
    String entityType();
    
    /**
     * AI features to enable
     */
    String[] features() default {"embedding", "search"};
    
    /**
     * Enable search indexing
     */
    boolean enableSearch() default true;
    
    /**
     * Enable AI recommendations
     */
    boolean enableRecommendations() default false;
    
    /**
     * Auto-generate embeddings
     */
    boolean autoEmbedding() default true;
    
    /**
     * Entity is indexable
     */
    boolean indexable() default true;
    
    // ════════════════════════════════════════════════════════
    // Indexing Strategy Configuration
    // ════════════════════════════════════════════════════════
    
    /**
     * Default indexing strategy for all operations
     * 
     * This is the base strategy used unless overridden by:
     * - onCreateStrategy (for CREATE operations)
     * - onUpdateStrategy (for UPDATE operations)  
     * - onDeleteStrategy (for DELETE operations)
     * - @AIProcess.indexingStrategy (for specific methods)
     * 
     * Recommended: ASYNC for most entities
     */
    IndexingStrategy indexingStrategy() default IndexingStrategy.ASYNC;
    
    /**
     * Indexing strategy for CREATE operations
     * 
     * Override this when:
     * - New entities need immediate visibility (SYNC)
     * - High creation volume needs batching (BATCH)
     * 
     * Set to AUTO to use indexingStrategy
     */
    IndexingStrategy onCreateStrategy() default IndexingStrategy.AUTO;
    
    /**
     * Indexing strategy for UPDATE operations
     * 
     * Override this when:
     * - Updates are high-volume and can be batched (BATCH)
     * - Critical updates need immediate visibility (SYNC)
     * 
     * Set to AUTO to use indexingStrategy
     */
    IndexingStrategy onUpdateStrategy() default IndexingStrategy.AUTO;
    
    /**
     * Indexing strategy for DELETE operations
     * 
     * Override this when:
     * - Deletions must be immediate for visibility (SYNC)
     * - Soft deletes can be batched (BATCH)
     * 
     * Common pattern: SYNC for hard deletes
     * Reason: Users expect deleted items to disappear immediately from search
     * 
     * Set to AUTO to use indexingStrategy
     */
    IndexingStrategy onDeleteStrategy() default IndexingStrategy.AUTO;
    
    // ════════════════════════════════════════════════════════
    // Advanced Configuration
    // ════════════════════════════════════════════════════════
    
    /**
     * Update visibility cache immediately for instant search visibility
     * 
     * When true:
     * - Updates Redis/cache with critical fields immediately (<10ms)
     * - Full indexing still happens async in background
     * - Search results reflect changes instantly
     * 
     * Use for:
     * - Deletions (hide from search immediately)
     * - Status changes (order status, product availability)
     * - Price updates (prevent stale pricing in search)
     * 
     * Performance: Adds ~5-10ms cache write overhead
     */
    boolean updateVisibilityCache() default false;
    
    /**
     * Critical fields to cache for immediate visibility
     * Only used when updateVisibilityCache = true
     * 
     * Example: {"status", "price", "availability", "deleted"}
     */
    String[] cachedFields() default {};
    
    /**
     * Maximum indexing delay allowed before alerting (in seconds)
     * 0 = no monitoring
     */
    int maxIndexingDelaySec() default 0;
}
```

### 2.2 Enhanced @AIProcess Annotation

```java
package com.ai.infrastructure.annotation;

import com.ai.infrastructure.indexing.IndexingStrategy;
import java.lang.annotation.*;

/**
 * Marks a method as processing an AI-capable entity.
 * 
 * Method-level override for indexing strategy (highest priority).
 * If not specified, inherits from @AICapable configuration.
 * 
 * Example:
 * <pre>
 * @AIProcess(
 *     entityType = "order",
 *     processType = "create",
 *     indexingStrategy = IndexingStrategy.SYNC  // Override default
 * )
 * public Order createUrgentOrder(Order order) {
 *     // This specific method needs synchronous indexing
 * }
 * </pre>
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AIProcess {
    
    /**
     * Entity type being processed
     * Must match @AICapable.entityType
     */
    String entityType();
    
    /**
     * Type of processing operation
     * Standard values: "create", "update", "delete"
     * Custom values allowed for special operations
     */
    String processType();
    
    /**
     * Override indexing strategy for this specific method
     * 
     * Priority: Method > Operation > Default
     * - If set to non-AUTO: Uses this strategy
     * - If set to AUTO: Uses @AICapable operation strategy
     * - If operation is AUTO: Uses @AICapable default strategy
     * 
     * Use cases for override:
     * - Critical operation in otherwise normal entity
     * - Bulk import method needs BATCH instead of ASYNC
     * - Special urgent flow needs SYNC
     */
    IndexingStrategy indexingStrategy() default IndexingStrategy.AUTO;
    
    /**
     * Override visibility cache update for this method
     * -1 = inherit from @AICapable
     *  0 = disable
     *  1 = enable
     */
    int updateVisibilityCache() default -1;
}
```

### 2.3 Strategy Resolution Logic

```java
package com.ai.infrastructure.indexing;

import com.ai.infrastructure.annotation.AICapable;
import com.ai.infrastructure.annotation.AIProcess;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;

/**
 * Resolves the effective indexing strategy based on annotation hierarchy.
 * 
 * Resolution Priority (highest to lowest):
 * 1. @AIProcess.indexingStrategy (if not AUTO)
 * 2. @AICapable.onXxxStrategy (if not AUTO)
 * 3. @AICapable.indexingStrategy (default)
 * 4. Global fallback (ASYNC)
 */
@Service
@Slf4j
public class IndexingStrategyResolver {
    
    /**
     * Resolve effective indexing strategy for a method invocation
     */
    public IndexingStrategy resolveStrategy(
            Method method,
            Class<?> entityClass,
            String processType) {
        
        // Priority 1: Check method-level @AIProcess annotation
        AIProcess processAnnotation = method.getAnnotation(AIProcess.class);
        if (processAnnotation != null && 
            processAnnotation.indexingStrategy() != IndexingStrategy.AUTO) {
            
            log.debug("Using method-level strategy: {} for {}.{}",
                processAnnotation.indexingStrategy(),
                entityClass.getSimpleName(),
                method.getName());
            
            return processAnnotation.indexingStrategy();
        }
        
        // Priority 2 & 3: Check @AICapable annotation
        AICapable capableAnnotation = entityClass.getAnnotation(AICapable.class);
        if (capableAnnotation == null) {
            log.warn("Entity {} not annotated with @AICapable, using default ASYNC",
                entityClass.getSimpleName());
            return IndexingStrategy.ASYNC;
        }
        
        // Priority 2: Check operation-specific strategy
        IndexingStrategy operationStrategy = resolveOperationStrategy(
            capableAnnotation, 
            processType
        );
        
        if (operationStrategy != IndexingStrategy.AUTO) {
            log.debug("Using operation-level strategy: {} for {} {}",
                operationStrategy,
                entityClass.getSimpleName(),
                processType);
            
            return operationStrategy;
        }
        
        // Priority 3: Use default indexingStrategy
        log.debug("Using default strategy: {} for {} {}",
            capableAnnotation.indexingStrategy(),
            entityClass.getSimpleName(),
            processType);
        
        return capableAnnotation.indexingStrategy();
    }
    
    /**
     * Resolve operation-specific strategy from @AICapable
     */
    private IndexingStrategy resolveOperationStrategy(
            AICapable annotation,
            String processType) {
        
        return switch (processType.toLowerCase()) {
            case "create" -> annotation.onCreateStrategy();
            case "update" -> annotation.onUpdateStrategy();
            case "delete" -> annotation.onDeleteStrategy();
            default -> {
                log.debug("Unknown process type: {}, using AUTO", processType);
                yield IndexingStrategy.AUTO;
            }
        };
    }
    
    /**
     * Check if visibility cache should be updated
     */
    public boolean shouldUpdateVisibilityCache(
            Method method,
            Class<?> entityClass) {
        
        // Check method override
        AIProcess processAnnotation = method.getAnnotation(AIProcess.class);
        if (processAnnotation != null && processAnnotation.updateVisibilityCache() >= 0) {
            return processAnnotation.updateVisibilityCache() == 1;
        }
        
        // Check entity-level setting
        AICapable capableAnnotation = entityClass.getAnnotation(AICapable.class);
        if (capableAnnotation != null) {
            return capableAnnotation.updateVisibilityCache();
        }
        
        return false;
    }
}
```

---

## 3. Queue Infrastructure

### 3.1 Indexing Queue Entity

```java
package com.ai.infrastructure.entity;

import com.ai.infrastructure.indexing.IndexingStrategy;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Database-backed queue for durable indexing operations.
 * 
 * Features:
 * - Survives application restarts
 * - Supports retry with exponential backoff
 * - Dead letter handling for failed items
 * - Distributed processing support
 * - Full audit trail
 */
@Entity
@Table(
    name = "indexing_queue",
    indexes = {
        // Primary query index: fetch pending items by priority
        @Index(
            name = "idx_indexing_queue_status_priority_scheduled",
            columnList = "status, priority, scheduled_for"
        ),
        
        // Lookup by entity
        @Index(
            name = "idx_indexing_queue_entity",
            columnList = "entity_type, entity_id"
        ),
        
        // Cleanup old completed items
        @Index(
            name = "idx_indexing_queue_completed_processed",
            columnList = "status, processed_at"
        ),
        
        // Reset stuck items
        @Index(
            name = "idx_indexing_queue_processing_updated",
            columnList = "status, updated_at"
        ),
        
        // Monitoring and metrics
        @Index(
            name = "idx_indexing_queue_requested",
            columnList = "requested_at"
        )
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexingQueueEntry {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    // ════════════════════════════════════════════════════════
    // Operation Details
    // ════════════════════════════════════════════════════════
    
    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false, length = 20)
    private IndexOperation operation;
    
    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;
    
    @Column(name = "entity_id", nullable = false, length = 255)
    private String entityId;
    
    @Column(name = "vector_id", length = 255)
    private String vectorId;
    
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;
    
    @Column(name = "metadata", columnDefinition = "JSONB")
    private String metadata;
    
    // ════════════════════════════════════════════════════════
    // Strategy & Priority
    // ════════════════════════════════════════════════════════
    
    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private IndexingStrategy priority;
    
    // ════════════════════════════════════════════════════════
    // Status & Lifecycle
    // ════════════════════════════════════════════════════════
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private IndexingStatus status;
    
    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;
    
    @Column(name = "scheduled_for", nullable = false)
    private LocalDateTime scheduledFor;
    
    @Column(name = "started_at")
    private LocalDateTime startedAt;
    
    @Column(name = "processed_at")
    private LocalDateTime processedAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    // ════════════════════════════════════════════════════════
    // Retry & Error Handling
    // ════════════════════════════════════════════════════════
    
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;
    
    @Column(name = "max_retries", nullable = false)
    private Integer maxRetries = 3;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(name = "last_error_at")
    private LocalDateTime lastErrorAt;
    
    // ════════════════════════════════════════════════════════
    // Distributed Processing
    // ════════════════════════════════════════════════════════
    
    @Column(name = "processing_node", length = 255)
    private String processingNode;
    
    @Column(name = "lock_acquired_at")
    private LocalDateTime lockAcquiredAt;
    
    @Column(name = "lock_expires_at")
    private LocalDateTime lockExpiresAt;
    
    // ════════════════════════════════════════════════════════
    // Lifecycle Hooks
    // ════════════════════════════════════════════════════════
    
    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.requestedAt == null) {
            this.requestedAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.status == null) {
            this.status = IndexingStatus.PENDING;
        }
        if (this.retryCount == null) {
            this.retryCount = 0;
        }
        if (this.maxRetries == null) {
            this.maxRetries = 3;
        }
    }
    
    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

/**
 * Indexing operation type
 */
enum IndexOperation {
    CREATE,
    UPDATE,
    DELETE
}

/**
 * Queue entry status
 */
enum IndexingStatus {
    PENDING,      // Waiting to be processed
    PROCESSING,   // Currently being processed
    COMPLETED,    // Successfully completed
    FAILED,       // Failed after retries
    DEAD_LETTER   // Moved to dead letter queue (requires manual intervention)
}
```

### 3.2 Indexing Queue Repository

```java
package com.ai.infrastructure.repository;

import com.ai.infrastructure.entity.IndexingQueueEntry;
import com.ai.infrastructure.entity.IndexingStatus;
import com.ai.infrastructure.indexing.IndexingStrategy;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface IndexingQueueRepository extends JpaRepository<IndexingQueueEntry, String> {
    
    /**
     * Find pending entries ready for processing
     */
    @Query("""
        SELECT q FROM IndexingQueueEntry q
        WHERE q.status = 'PENDING'
        AND q.scheduledFor <= :now
        AND q.priority = :priority
        ORDER BY q.requestedAt ASC
        """)
    List<IndexingQueueEntry> findPendingEntries(
        @Param("priority") IndexingStrategy priority,
        @Param("now") LocalDateTime now,
        Pageable pageable
    );
    
    /**
     * Find all pending entries across priorities
     */
    @Query("""
        SELECT q FROM IndexingQueueEntry q
        WHERE q.status = 'PENDING'
        AND q.scheduledFor <= :now
        ORDER BY 
            CASE q.priority 
                WHEN 'SYNC' THEN 1
                WHEN 'ASYNC' THEN 2
                WHEN 'BATCH' THEN 3
            END,
            q.requestedAt ASC
        """)
    List<IndexingQueueEntry> findPendingEntriesAllPriorities(
        @Param("now") LocalDateTime now,
        Pageable pageable
    );
    
    /**
     * Find by entity (for deduplication)
     */
    Optional<IndexingQueueEntry> findByEntityTypeAndEntityIdAndStatus(
        String entityType,
        String entityId,
        IndexingStatus status
    );
    
    /**
     * Find stuck entries in PROCESSING status
     */
    @Query("""
        SELECT q FROM IndexingQueueEntry q
        WHERE q.status = 'PROCESSING'
        AND q.updatedAt < :threshold
        """)
    List<IndexingQueueEntry> findStuckEntries(@Param("threshold") LocalDateTime threshold);
    
    /**
     * Count pending entries by priority
     */
    long countByStatusAndPriority(IndexingStatus status, IndexingStrategy priority);
    
    /**
     * Delete completed entries older than cutoff
     */
    @Modifying
    @Query("""
        DELETE FROM IndexingQueueEntry q
        WHERE q.status = 'COMPLETED'
        AND q.processedAt < :cutoff
        """)
    int deleteCompletedEntriesBefore(@Param("cutoff") LocalDateTime cutoff);
    
    /**
     * Find failed entries for dead letter processing
     */
    @Query("""
        SELECT q FROM IndexingQueueEntry q
        WHERE q.status = 'FAILED'
        AND q.requestedAt < :cutoff
        ORDER BY q.requestedAt ASC
        """)
    List<IndexingQueueEntry> findFailedEntriesBefore(@Param("cutoff") LocalDateTime cutoff);
    
    /**
     * Count entries by status
     */
    long countByStatus(IndexingStatus status);
    
    /**
     * Find oldest pending entry (for monitoring)
     */
    Optional<IndexingQueueEntry> findFirstByStatusOrderByRequestedAtAsc(IndexingStatus status);
}
```

### 3.3 Queue Service

```java
package com.ai.infrastructure.indexing.queue;

import com.ai.infrastructure.entity.IndexingQueueEntry;
import com.ai.infrastructure.entity.IndexingStatus;
import com.ai.infrastructure.indexing.IndexingStrategy;
import com.ai.infrastructure.repository.IndexingQueueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Core queue service for indexing operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IndexingQueueService {
    
    private final IndexingQueueRepository repository;
    
    /**
     * Enqueue an indexing request
     */
    @Transactional
    public String enqueue(IndexingRequest request) {
        // Check for duplicate pending request
        Optional<IndexingQueueEntry> existing = repository
            .findByEntityTypeAndEntityIdAndStatus(
                request.getEntityType(),
                request.getEntityId(),
                IndexingStatus.PENDING
            );
        
        if (existing.isPresent()) {
            log.debug("Duplicate indexing request for {}:{}, updating existing entry",
                request.getEntityType(), request.getEntityId());
            
            IndexingQueueEntry entry = existing.get();
            entry.setOperation(request.getOperation());
            entry.setContent(request.getContent());
            entry.setMetadata(request.getMetadata());
            entry.setPriority(request.getPriority());
            entry.setScheduledFor(calculateScheduledTime(request.getPriority()));
            repository.save(entry);
            
            return entry.getId();
        }
        
        // Create new entry
        IndexingQueueEntry entry = IndexingQueueEntry.builder()
            .operation(request.getOperation())
            .entityType(request.getEntityType())
            .entityId(request.getEntityId())
            .vectorId(request.getVectorId())
            .content(request.getContent())
            .metadata(request.getMetadata())
            .priority(request.getPriority())
            .status(IndexingStatus.PENDING)
            .requestedAt(LocalDateTime.now())
            .scheduledFor(calculateScheduledTime(request.getPriority()))
            .retryCount(0)
            .maxRetries(getMaxRetries(request.getPriority()))
            .build();
        
        entry = repository.save(entry);
        
        log.debug("Enqueued indexing request: id={}, type={}, entity={}, priority={}",
            entry.getId(),
            entry.getOperation(),
            entry.getEntityType(),
            entry.getPriority());
        
        return entry.getId();
    }
    
    /**
     * Dequeue entries for processing
     */
    @Transactional
    public List<IndexingQueueEntry> dequeue(IndexingStrategy priority, int batchSize) {
        LocalDateTime now = LocalDateTime.now();
        List<IndexingQueueEntry> entries = repository.findPendingEntries(
            priority,
            now,
            PageRequest.of(0, batchSize)
        );
        
        if (entries.isEmpty()) {
            return entries;
        }
        
        // Mark as processing
        String nodeId = getNodeId();
        LocalDateTime lockExpiry = now.plusMinutes(5);
        
        for (IndexingQueueEntry entry : entries) {
            entry.setStatus(IndexingStatus.PROCESSING);
            entry.setStartedAt(now);
            entry.setProcessingNode(nodeId);
            entry.setLockAcquiredAt(now);
            entry.setLockExpiresAt(lockExpiry);
        }
        
        repository.saveAll(entries);
        
        log.info("Dequeued {} entries for processing (priority: {})",
            entries.size(), priority);
        
        return entries;
    }
    
    /**
     * Mark entry as completed
     */
    @Transactional
    public void markCompleted(String entryId) {
        repository.findById(entryId).ifPresent(entry -> {
            entry.setStatus(IndexingStatus.COMPLETED);
            entry.setProcessedAt(LocalDateTime.now());
            entry.setProcessingNode(null);
            repository.save(entry);
            
            log.debug("Marked entry {} as completed", entryId);
        });
    }
    
    /**
     * Mark entry as failed
     */
    @Transactional
    public void markFailed(String entryId, String errorMessage) {
        repository.findById(entryId).ifPresent(entry -> {
            entry.setRetryCount(entry.getRetryCount() + 1);
            entry.setErrorMessage(errorMessage);
            entry.setLastErrorAt(LocalDateTime.now());
            
            if (entry.getRetryCount() >= entry.getMaxRetries()) {
                // Move to dead letter
                entry.setStatus(IndexingStatus.DEAD_LETTER);
                log.warn("Entry {} moved to dead letter after {} retries: {}",
                    entryId, entry.getRetryCount(), errorMessage);
            } else {
                // Retry with exponential backoff
                entry.setStatus(IndexingStatus.PENDING);
                entry.setScheduledFor(calculateRetryTime(entry.getRetryCount()));
                entry.setProcessingNode(null);
                
                log.info("Entry {} will retry (attempt {}/{})",
                    entryId, entry.getRetryCount(), entry.getMaxRetries());
            }
            
            repository.save(entry);
        });
    }
    
    /**
     * Calculate when to schedule based on priority
     */
    private LocalDateTime calculateScheduledTime(IndexingStrategy priority) {
        LocalDateTime now = LocalDateTime.now();
        return switch (priority) {
            case SYNC -> now; // Process immediately (shouldn't normally queue)
            case ASYNC -> now; // Process ASAP
            case BATCH -> now.plusSeconds(30); // Delay for batch
            default -> now;
        };
    }
    
    /**
     * Calculate retry time with exponential backoff
     */
    private LocalDateTime calculateRetryTime(int retryCount) {
        long delaySeconds = (long) Math.pow(2, retryCount); // 2, 4, 8, 16...
        return LocalDateTime.now().plusSeconds(delaySeconds);
    }
    
    /**
     * Get max retries based on priority
     */
    private int getMaxRetries(IndexingStrategy priority) {
        return switch (priority) {
            case SYNC -> 1; // Don't retry sync operations
            case ASYNC -> 3; // Retry async up to 3 times
            case BATCH -> 5; // Retry batch up to 5 times
            default -> 3;
        };
    }
    
    /**
     * Get current node identifier
     */
    private String getNodeId() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown-node";
        }
    }
}
```

---

## 4. Processing Workers

### 4.1 Async Worker (High Priority)

```java
package com.ai.infrastructure.indexing.worker;

import com.ai.infrastructure.entity.IndexingQueueEntry;
import com.ai.infrastructure.indexing.IndexingStrategy;
import com.ai.infrastructure.indexing.queue.IndexingQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Worker for ASYNC priority queue
 * Processes entries quickly with small batches
 */
@Component
@ConditionalOnProperty(
    name = "ai.indexing.async.enabled",
    havingValue = "true",
    matchIfMissing = true
)
@RequiredArgsConstructor
@Slf4j
public class AsyncIndexingWorker {
    
    private final IndexingQueueService queueService;
    private final IndexingExecutor indexingExecutor;
    
    /**
     * Process ASYNC queue every second
     */
    @Scheduled(fixedDelayString = "${ai.indexing.async.interval:1000}")
    public void processAsyncQueue() {
        try {
            int batchSize = 100; // Small batches for quick processing
            List<IndexingQueueEntry> entries = queueService.dequeue(
                IndexingStrategy.ASYNC,
                batchSize
            );
            
            if (entries.isEmpty()) {
                return;
            }
            
            log.debug("Processing {} ASYNC entries", entries.size());
            
            for (IndexingQueueEntry entry : entries) {
                try {
                    indexingExecutor.execute(entry);
                    queueService.markCompleted(entry.getId());
                } catch (Exception e) {
                    log.error("Failed to process ASYNC entry {}: {}",
                        entry.getId(), e.getMessage(), e);
                    queueService.markFailed(entry.getId(), e.getMessage());
                }
            }
            
            log.info("Processed {} ASYNC entries successfully", entries.size());
            
        } catch (Exception e) {
            log.error("Error in ASYNC worker", e);
        }
    }
}
```

### 4.2 Batch Worker (Low Priority)

```java
package com.ai.infrastructure.indexing.worker;

import com.ai.infrastructure.entity.IndexingQueueEntry;
import com.ai.infrastructure.indexing.IndexingStrategy;
import com.ai.infrastructure.indexing.queue.IndexingQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Worker for BATCH priority queue
 * Processes entries in large batches for efficiency
 */
@Component
@ConditionalOnProperty(
    name = "ai.indexing.batch.enabled",
    havingValue = "true",
    matchIfMissing = true
)
@RequiredArgsConstructor
@Slf4j
public class BatchIndexingWorker {
    
    private final IndexingQueueService queueService;
    private final IndexingExecutor indexingExecutor;
    
    /**
     * Process BATCH queue every 30 seconds
     */
    @Scheduled(fixedDelayString = "${ai.indexing.batch.interval:30000}")
    public void processBatchQueue() {
        try {
            int batchSize = 1000; // Large batches for efficiency
            List<IndexingQueueEntry> entries = queueService.dequeue(
                IndexingStrategy.BATCH,
                batchSize
            );
            
            if (entries.isEmpty()) {
                return;
            }
            
            log.info("Processing {} BATCH entries", entries.size());
            
            // Process entire batch
            int succeeded = 0;
            int failed = 0;
            
            for (IndexingQueueEntry entry : entries) {
                try {
                    indexingExecutor.execute(entry);
                    queueService.markCompleted(entry.getId());
                    succeeded++;
                } catch (Exception e) {
                    log.error("Failed to process BATCH entry {}: {}",
                        entry.getId(), e.getMessage());
                    queueService.markFailed(entry.getId(), e.getMessage());
                    failed++;
                }
            }
            
            log.info("Processed BATCH entries: {} succeeded, {} failed",
                succeeded, failed);
            
        } catch (Exception e) {
            log.error("Error in BATCH worker", e);
        }
    }
}
```

---

## 5. Cleanup Processes

### 5.1 Queue Cleanup Service

```java
package com.ai.infrastructure.indexing.cleanup;

import com.ai.infrastructure.entity.IndexingQueueEntry;
import com.ai.infrastructure.entity.IndexingStatus;
import com.ai.infrastructure.indexing.queue.IndexingQueueService;
import com.ai.infrastructure.repository.IndexingQueueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Cleanup service for indexing queue maintenance
 */
@Service
@ConditionalOnProperty(
    name = "ai.indexing.cleanup.enabled",
    havingValue = "true",
    matchIfMissing = true
)
@RequiredArgsConstructor
@Slf4j
public class IndexingQueueCleanupService {
    
    private final IndexingQueueRepository repository;
    private final IndexingQueueService queueService;
    
    /**
     * Cleanup completed entries (daily at 2 AM)
     */
    @Scheduled(cron = "${ai.indexing.cleanup.completed-cron:0 0 2 * * *}")
    @Transactional
    public void cleanupCompletedEntries() {
        int retentionDays = 7; // Keep completed entries for 7 days
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        
        int deleted = repository.deleteCompletedEntriesBefore(cutoff);
        
        log.info("Cleaned up {} completed indexing queue entries older than {} days",
            deleted, retentionDays);
    }
    
    /**
     * Process dead letter queue (daily at 3 AM)
     */
    @Scheduled(cron = "${ai.indexing.cleanup.dead-letter-cron:0 0 3 * * *}")
    @Transactional
    public void processDeadLetterQueue() {
        int retentionDays = 30; // Keep failed entries for 30 days for investigation
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        
        List<IndexingQueueEntry> failedEntries = repository
            .findFailedEntriesBefore(cutoff);
        
        if (!failedEntries.isEmpty()) {
            log.warn("Found {} dead letter entries older than {} days, archiving...",
                failedEntries.size(), retentionDays);
            
            // Archive to separate table or log
            failedEntries.forEach(entry -> {
                log.warn("Dead letter entry: id={}, entity={}:{}, error={}",
                    entry.getId(),
                    entry.getEntityType(),
                    entry.getEntityId(),
                    entry.getErrorMessage());
            });
            
            // Delete after archiving
            repository.deleteAll(failedEntries);
            
            log.info("Archived and deleted {} dead letter entries", failedEntries.size());
        }
    }
    
    /**
     * Reset stuck entries (every 5 minutes)
     */
    @Scheduled(fixedDelay = 300000) // 5 minutes
    @Transactional
    public void resetStuckEntries() {
        int stuckThresholdMinutes = 10;
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(stuckThresholdMinutes);
        
        List<IndexingQueueEntry> stuckEntries = repository.findStuckEntries(threshold);
        
        if (!stuckEntries.isEmpty()) {
            log.warn("Found {} stuck entries (processing > {} minutes), resetting...",
                stuckEntries.size(), stuckThresholdMinutes);
            
            for (IndexingQueueEntry entry : stuckEntries) {
                log.warn("Resetting stuck entry: id={}, entity={}:{}, node={}",
                    entry.getId(),
                    entry.getEntityType(),
                    entry.getEntityId(),
                    entry.getProcessingNode());
                
                // Reset to pending for retry
                entry.setStatus(IndexingStatus.PENDING);
                entry.setRetryCount(entry.getRetryCount() + 1);
                entry.setProcessingNode(null);
                entry.setLockAcquiredAt(null);
                entry.setLockExpiresAt(null);
                entry.setScheduledFor(LocalDateTime.now());
                entry.setErrorMessage("Reset from stuck PROCESSING state");
                
                if (entry.getRetryCount() >= entry.getMaxRetries()) {
                    entry.setStatus(IndexingStatus.DEAD_LETTER);
                    log.error("Entry {} exceeded max retries, moved to dead letter",
                        entry.getId());
                }
            }
            
            repository.saveAll(stuckEntries);
            
            log.info("Reset {} stuck entries", stuckEntries.size());
        }
    }
    
    /**
     * Cleanup orphaned searchable entities (weekly on Sunday at 4 AM)
     */
    @Scheduled(cron = "${ai.indexing.cleanup.orphan-cron:0 0 4 * * SUN}")
    @Transactional
    public void cleanupOrphanedEntities() {
        log.info("Starting orphaned entity cleanup check...");
        
        // This will be implemented in AISearchableEntity cleanup
        // See section 5.2
        
        log.info("Orphaned entity cleanup complete");
    }
}
```

### 5.2 AISearchableEntity Cleanup Service

```java
package com.ai.infrastructure.cleanup;

import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Cleanup service for AISearchableEntity orphaned records
 */
@Service
@ConditionalOnProperty(
    name = "ai.cleanup.orphaned-entities.enabled",
    havingValue = "true",
    matchIfMissing = true
)
@RequiredArgsConstructor
@Slf4j
public class SearchableEntityCleanupService {
    
    private final AISearchableEntityRepository repository;
    private final VectorDatabaseService vectorService;
    
    /**
     * Cleanup orphaned entities (weekly on Sunday at 4 AM)
     * Entities with vector_id but no actual vector in vector DB
     */
    @Scheduled(cron = "${ai.cleanup.orphaned-entities.cron:0 0 4 * * SUN}")
    @Transactional
    public void cleanupOrphanedEntities() {
        log.info("Starting orphaned entity cleanup...");
        
        List<AISearchableEntity> entities = repository.findByVectorIdIsNotNull();
        
        int orphaned = 0;
        int checked = 0;
        
        for (AISearchableEntity entity : entities) {
            checked++;
            
            // Check if vector still exists
            boolean vectorExists = vectorService.vectorExists(
                entity.getEntityType(),
                entity.getEntityId()
            );
            
            if (!vectorExists) {
                log.warn("Found orphaned entity: type={}, id={}, vectorId={}",
                    entity.getEntityType(),
                    entity.getEntityId(),
                    entity.getVectorId());
                
                repository.delete(entity);
                orphaned++;
            }
            
            if (checked % 1000 == 0) {
                log.info("Checked {} entities, found {} orphaned so far",
                    checked, orphaned);
            }
        }
        
        log.info("Orphaned entity cleanup complete: checked={}, orphaned={}",
            checked, orphaned);
    }
    
    /**
     * Cleanup entities without vectors older than threshold
     * These are likely failed indexing attempts
     */
    @Scheduled(cron = "${ai.cleanup.no-vector-entities.cron:0 0 5 * * SUN}")
    @Transactional
    public void cleanupEntitiesWithoutVectors() {
        int retentionHours = 24; // Clean up if no vector after 24 hours
        LocalDateTime cutoff = LocalDateTime.now().minusHours(retentionHours);
        
        log.info("Cleaning up entities without vectors older than {} hours...",
            retentionHours);
        
        List<AISearchableEntity> entities = repository.findByVectorIdIsNull();
        
        int cleaned = 0;
        for (AISearchableEntity entity : entities) {
            if (entity.getCreatedAt().isBefore(cutoff)) {
                log.warn("Cleaning up entity without vector: type={}, id={}, age={}h",
                    entity.getEntityType(),
                    entity.getEntityId(),
                    java.time.Duration.between(entity.getCreatedAt(), LocalDateTime.now()).toHours());
                
                repository.delete(entity);
                cleaned++;
            }
        }
        
        log.info("Cleaned up {} entities without vectors", cleaned);
    }
}
```

### 5.3 Cleanup Strategy Pattern

```java
package com.ai.infrastructure.cleanup;

/**
 * Cleanup strategy determines HOW old data is cleaned up
 */
public enum CleanupStrategy {
    
    /**
     * SOFT_DELETE: Mark as deleted, keep for audit trail
     */
    SOFT_DELETE,
    
    /**
     * ARCHIVE: Move to archive storage (S3, cold storage)
     */
    ARCHIVE,
    
    /**
     * HARD_DELETE: Permanent deletion
     */
    HARD_DELETE,
    
    /**
     * CASCADE: Delete entity and all related data
     */
    CASCADE
}

/**
 * Cleanup configuration for entity types
 */
@Configuration
public class CleanupConfiguration {
    
    @Bean
    public CleanupPolicyProvider cleanupPolicyProvider() {
        return new CleanupPolicyProvider() {
            
            @Override
            public CleanupStrategy getStrategy(String entityType) {
                return switch (entityType) {
                    case "order", "user" -> CleanupStrategy.ARCHIVE;
                    case "behavior", "analytics" -> CleanupStrategy.HARD_DELETE;
                    default -> CleanupStrategy.SOFT_DELETE;
                };
            }
            
            @Override
            public int getRetentionDays(String entityType) {
                return switch (entityType) {
                    case "order" -> 2555; // 7 years for compliance
                    case "user" -> 365; // 1 year
                    case "behavior" -> 90; // 3 months
                    case "analytics" -> 30; // 1 month
                    default -> 180; // 6 months default
                };
            }
        };
    }
}
```

---

## 6. Implementation Guide

### Step 1: Database Migration

```yaml
# backend/src/main/resources/db/changelog/V003__ai_indexing_queue.yaml
databaseChangeLog:
  - changeSet:
      id: 9
      author: ai-infrastructure
      changes:
        - createTable:
            tableName: ai_indexing_queue
            columns:
              - column: { name: id, type: UUID, constraints: { primaryKey: true, nullable: false } }
              - column: { name: entity_type, type: VARCHAR(128), constraints: { nullable: false } }
              - column: { name: entity_id, type: VARCHAR(128) }
              - column: { name: entity_class, type: VARCHAR(256), constraints: { nullable: false } }
              - column: { name: operation, type: VARCHAR(32), constraints: { nullable: false } }
              - column: { name: strategy, type: VARCHAR(32), constraints: { nullable: false } }
              - column: { name: status, type: VARCHAR(32), defaultValue: PENDING, constraints: { nullable: false } }
              - column: { name: priority, type: VARCHAR(32), constraints: { nullable: false } }
              - column: { name: priority_weight, type: INT, defaultValueNumeric: 0, constraints: { nullable: false } }
              - column: { name: generate_embedding, type: BOOLEAN, defaultValueBoolean: false, constraints: { nullable: false } }
              - column: { name: index_for_search, type: BOOLEAN, defaultValueBoolean: false, constraints: { nullable: false } }
              - column: { name: enable_analysis, type: BOOLEAN, defaultValueBoolean: false, constraints: { nullable: false } }
              - column: { name: remove_from_search, type: BOOLEAN, defaultValueBoolean: false, constraints: { nullable: false } }
              - column: { name: cleanup_embeddings, type: BOOLEAN, defaultValueBoolean: false, constraints: { nullable: false } }
              - column: { name: payload, type: TEXT, constraints: { nullable: false } }
              - column: { name: max_retries, type: INT, defaultValueNumeric: 5, constraints: { nullable: false } }
              - column: { name: retry_count, type: INT, defaultValueNumeric: 0, constraints: { nullable: false } }
              - column: { name: requested_at, type: TIMESTAMP, defaultValueComputed: CURRENT_TIMESTAMP, constraints: { nullable: false } }
              - column: { name: scheduled_for, type: TIMESTAMP, defaultValueComputed: CURRENT_TIMESTAMP, constraints: { nullable: false } }
              - column: { name: created_at, type: TIMESTAMP, defaultValueComputed: CURRENT_TIMESTAMP, constraints: { nullable: false } }
              - column: { name: updated_at, type: TIMESTAMP, defaultValueComputed: CURRENT_TIMESTAMP, constraints: { nullable: false } }

  - changeSet:
      id: 10
      author: ai-infrastructure
      changes:
        - createIndex:
            tableName: ai_indexing_queue
            indexName: idx_ai_queue_status_strategy
            columns:
              - column: { name: status }
              - column: { name: strategy }
        - createIndex:
            tableName: ai_indexing_queue
            indexName: idx_ai_queue_scheduled
            columns:
              - column: { name: scheduled_for }
        - createIndex:
            tableName: ai_indexing_queue
            indexName: idx_ai_queue_entity
            columns:
              - column: { name: entity_type }
              - column: { name: entity_id }
```

### Step 2: Update Existing Entities

```java
// Example: Update Product entity
@Entity
@Table(name = "products")
@AICapable(
    entityType = "product",
    features = {"embedding", "search", "recommendations"},
    indexingStrategy = IndexingStrategy.ASYNC,
    onCreateStrategy = IndexingStrategy.ASYNC,
    onUpdateStrategy = IndexingStrategy.ASYNC,
    onDeleteStrategy = IndexingStrategy.SYNC,
    updateVisibilityCache = true,
    cachedFields = {"id", "name", "price", "status", "deleted"}
)
public class Product {
    // ... existing fields
}

// Example: Update Behavior entity
@Entity
@Table(name = "behaviors")
@AICapable(
    entityType = "behavior",
    indexingStrategy = IndexingStrategy.BATCH,
    onCreateStrategy = IndexingStrategy.BATCH,
    onUpdateStrategy = IndexingStrategy.BATCH,
    onDeleteStrategy = IndexingStrategy.BATCH
)
public class Behavior {
    // ... existing fields
}

// Example: Update Order entity
@Entity
@Table(name = "orders")
@AICapable(
    entityType = "order",
    indexingStrategy = IndexingStrategy.ASYNC,
    onDeleteStrategy = IndexingStrategy.SYNC,
    updateVisibilityCache = true,
    cachedFields = {"id", "status", "deleted"}
)
public class Order {
    // ... existing fields
}
```

### Step 3: Update Service Methods

```java
@Service
public class ProductService {
    
    // Standard create - uses ASYNC from annotation
    @AIProcess(entityType = "product", processType = "create")
    @Transactional
    public Product createProduct(Product product) {
        return productRepository.save(product);
    }
    
    // Bulk import - override to use BATCH
    @AIProcess(
        entityType = "product",
        processType = "create",
        indexingStrategy = IndexingStrategy.BATCH
    )
    @Transactional
    public List<Product> bulkImport(List<Product> products) {
        return productRepository.saveAll(products);
    }
    
    // Critical update - override to use SYNC
    @AIProcess(
        entityType = "product",
        processType = "update",
        indexingStrategy = IndexingStrategy.SYNC
    )
    @Transactional
    public Product criticalPriceUpdate(String productId, BigDecimal newPrice) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new NotFoundException("Product not found"));
        product.setPrice(newPrice);
        return productRepository.save(product);
    }
    
    // Delete - uses SYNC from annotation
    @AIProcess(entityType = "product", processType = "delete")
    @Transactional
    public void deleteProduct(String id) {
        productRepository.deleteById(id);
    }
}
```

---

## 7. Configuration

### application.yml (base defaults)

```yaml
ai:
  indexing:
    enabled: true

    queue:
      max-retries: ${AI_INDEXING_QUEUE_MAX_RETRIES:5}
      visibility-timeout: ${AI_INDEXING_QUEUE_VISIBILITY_TIMEOUT:PT2M}

    async-worker:
      enabled: true
      fixed-delay: ${AI_INDEXING_ASYNC_DELAY:PT0.5S}
      batch-size: ${AI_INDEXING_ASYNC_BATCH:100}
      strategy: ASYNC

    batch-worker:
      enabled: true
      fixed-delay: ${AI_INDEXING_BATCH_DELAY:PT30S}
      batch-size: ${AI_INDEXING_BATCH_SIZE:1000}
      strategy: BATCH

    cleanup:
      enabled: true
      stuck-threshold: ${AI_INDEXING_STUCK_THRESHOLD:PT10M}
      sweep-interval: ${AI_INDEXING_SWEEP_INTERVAL:PT5M}
      completed-retention: ${AI_INDEXING_COMPLETED_RETENTION:P7D}
      dead-letter-retention: ${AI_INDEXING_DEAD_LETTER_RETENTION:P30D}

  cleanup:
    orphaned-entities:
      enabled: true
      cron: "0 0 4 * * SUN"
    no-vector-entities:
      enabled: true
      cron: "0 0 5 * * SUN"
      retention-hours: 24
    strategies:
      order: ARCHIVE
      user: ARCHIVE
      product: SOFT_DELETE
      behavior: HARD_DELETE
      analytics: HARD_DELETE
    retention-days:
      order: 2555
      user: 365
      product: 180
      behavior: 90
      analytics: 30
      default: 180

management:
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: ${spring.application.name}
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
```

### Environment overrides

| Profile | Async delay / batch size | Batch delay / batch size | Cleanup retention | Notes |
|---------|-------------------------|--------------------------|-------------------|-------|
| `application-dev.yml` | `PT2S` / `10` | disabled (fixed delay `PT1M`, `enabled: false`) | Completed `P1D`, dead-letter `P7D` | Lightweight queueing for laptops |
| `application-test.yml` | disabled | disabled | Cleanup disabled | Forces SYNC for deterministic tests |
| `application-prod.yml` | `PT0.5S` / `200` | `PT20S` / `2000` | Completed `P7D`, dead-letter `P30D` | Throughput-focused defaults |

The tuned blocks live next to the other Spring profile settings in `backend/src/main/resources`.

---

## 8. Monitoring & Metrics

> **Operations Runbook:** Surface `indexing.queue.pending`, `indexing.queue.dead_letter`, `indexing.queue.oldest_pending_age_seconds`, and `indexing.queue.failed` on shared dashboards so SRE can spot backlog, dead-letter drift, and worker stalls in real time.

### Key Metrics to Track

```java
@Service
@RequiredArgsConstructor
public class IndexingMetricsService {
    
    private final MeterRegistry meterRegistry;
    private final IndexingQueueRepository repository;
    
    @Scheduled(fixedDelay = 30000) // Every 30 seconds
    public void recordQueueMetrics() {
        // Queue depth by status
        meterRegistry.gauge("indexing.queue.pending",
            repository.countByStatus(IndexingStatus.PENDING));
        
        meterRegistry.gauge("indexing.queue.processing",
            repository.countByStatus(IndexingStatus.PROCESSING));
        
        meterRegistry.gauge("indexing.queue.failed",
            repository.countByStatus(IndexingStatus.FAILED));
        
        meterRegistry.gauge("indexing.queue.dead_letter",
            repository.countByStatus(IndexingStatus.DEAD_LETTER));
        
        // Queue depth by priority
        meterRegistry.gauge("indexing.queue.sync",
            repository.countByStatusAndPriority(IndexingStatus.PENDING, IndexingStrategy.SYNC));
        
        meterRegistry.gauge("indexing.queue.async",
            repository.countByStatusAndPriority(IndexingStatus.PENDING, IndexingStrategy.ASYNC));
        
        meterRegistry.gauge("indexing.queue.batch",
            repository.countByStatusAndPriority(IndexingStatus.PENDING, IndexingStrategy.BATCH));
        
        // Oldest pending entry age
        repository.findFirstByStatusOrderByRequestedAtAsc(IndexingStatus.PENDING)
            .ifPresent(entry -> {
                long ageSeconds = java.time.Duration.between(
                    entry.getRequestedAt(),
                    LocalDateTime.now()
                ).getSeconds();
                
                meterRegistry.gauge("indexing.queue.oldest_pending_age_seconds", ageSeconds);
            });
    }
    
    public void recordIndexingAttempt(String entityType, IndexingStrategy strategy, boolean success) {
        Counter.builder("indexing.attempts")
            .tag("entity_type", entityType)
            .tag("strategy", strategy.name())
            .tag("success", String.valueOf(success))
            .register(meterRegistry)
            .increment();
    }
    
    public void recordIndexingDuration(String entityType, IndexingStrategy strategy, long durationMs) {
        Timer.builder("indexing.duration")
            .tag("entity_type", entityType)
            .tag("strategy", strategy.name())
            .register(meterRegistry)
            .record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
}
```

### Alerts Configuration

```yaml
# alerts.yml
alerts:
  indexing:
    # Queue depth alerts
    - name: indexing_queue_depth_high
      condition: indexing.queue.pending > 10000
      severity: warning
      message: "Indexing queue depth exceeds 10K entries"
      
    - name: indexing_queue_depth_critical
      condition: indexing.queue.pending > 50000
      severity: critical
      message: "Indexing queue depth exceeds 50K entries - system falling behind"
      
    # Processing delays
    - name: indexing_delay_high
      condition: indexing.queue.oldest_pending_age_seconds > 300
      severity: warning
      message: "Oldest pending entry is older than 5 minutes"
      
    - name: indexing_delay_critical
      condition: indexing.queue.oldest_pending_age_seconds > 3600
      severity: critical
      message: "Oldest pending entry is older than 1 hour"
      
    # Dead letter alerts
    - name: dead_letter_entries
      condition: indexing.queue.dead_letter > 0
      severity: warning
      message: "Dead letter queue has entries requiring manual intervention"
      
    # Worker health
    - name: async_worker_stalled
      condition: rate(indexing.attempts{strategy='ASYNC'}[5m]) == 0
      severity: critical
      message: "ASYNC worker has not processed any entries in 5 minutes"
```

---

## 9. Migration Plan

### Phase 1: Foundation (Week 1)

**Objectives:**
- Deploy queue infrastructure
- Add indexes to existing tables
- Deploy workers

**Tasks:**
1. Run database migrations
2. Deploy IndexingQueueService
3. Deploy AsyncIndexingWorker
4. Deploy BatchIndexingWorker
5. Enable monitoring

**Validation:**
- Queue table exists with correct indexes
- Workers are processing entries
- Metrics are being collected

---

### Phase 2: Gradual Rollout (Week 2)

**Objectives:**
- Update entity annotations
- Route traffic to new system

**Tasks:**
1. Update Behavior entities → BATCH (low risk)
2. Update Analytics entities → BATCH
3. Monitor for 24 hours
4. Update Product entities → ASYNC
5. Update User entities → ASYNC
6. Monitor for 48 hours
7. Update Order entities → ASYNC (delete=SYNC)

**Validation:**
- All strategies functioning correctly
- SLAs being met
- No errors in dead letter queue

---

### Phase 3: Cleanup & Optimization (Week 3)

**Objectives:**
- Enable cleanup processes
- Optimize performance

**Tasks:**
1. Enable queue cleanup
2. Enable orphaned entity cleanup
3. Tune worker intervals based on metrics
4. Optimize batch sizes
5. Add visibility cache for critical entities

**Validation:**
- Cleanup processes running successfully
- Queue depth stable
- Performance SLAs met

---

### Phase 4: Production Hardening (Week 4)

**Objectives:**
- Full production deployment
- Monitoring and alerting

**Tasks:**
1. Enable all alerts
2. Create runbooks for common issues
3. Train team on new system
4. Document troubleshooting procedures
5. Perform load testing

**Validation:**
- System handles peak load
- Alerts triggering correctly
- Team trained and comfortable

---

## Appendix A: Troubleshooting Guide

### Issue: Queue Depth Growing

**Symptoms:**
- `indexing.queue.pending` metric increasing
- Oldest pending entry age increasing

**Diagnosis:**
```sql
-- Check queue depth by priority
SELECT priority, status, COUNT(*)
FROM indexing_queue
GROUP BY priority, status;

-- Check oldest entries
SELECT entity_type, priority, requested_at, retry_count
FROM indexing_queue
WHERE status = 'PENDING'
ORDER BY requested_at ASC
LIMIT 20;
```

**Solutions:**
1. Increase worker count
2. Decrease processing interval
3. Increase batch size
4. Check for stuck workers

---

### Issue: High Failure Rate

**Symptoms:**
- Many entries in FAILED or DEAD_LETTER status
- `indexing.attempts{success='false'}` high

**Diagnosis:**
```sql
-- Check error patterns
SELECT error_message, COUNT(*)
FROM indexing_queue
WHERE status IN ('FAILED', 'DEAD_LETTER')
GROUP BY error_message
ORDER BY COUNT(*) DESC;
```

**Solutions:**
1. Fix underlying indexing issue
2. Increase max retries
3. Check vector database health
4. Review dead letter entries manually

---

### Issue: Stuck Entries

**Symptoms:**
- Entries stuck in PROCESSING state
- Processing node not responding

**Diagnosis:**
```sql
-- Find stuck entries
SELECT id, entity_type, processing_node, started_at
FROM indexing_queue
WHERE status = 'PROCESSING'
AND updated_at < NOW() - INTERVAL '10 minutes';
```

**Solutions:**
1. Stuck entry reset runs automatically every 5 minutes
2. Manually reset if needed:
```sql
UPDATE indexing_queue
SET status = 'PENDING',
    processing_node = NULL,
    scheduled_for = NOW()
WHERE status = 'PROCESSING'
AND updated_at < NOW() - INTERVAL '10 minutes';
```

---

## Appendix B: Example Configurations

### High-Volume E-commerce Platform

```yaml
ai:
  indexing:
    async:
      interval: 500        # Every 500ms
      batch-size: 200
      workers: 10
    batch:
      interval: 15000      # Every 15 seconds
      batch-size: 2000
      workers: 5
```

### Low-Volume SaaS Application

```yaml
ai:
  indexing:
    async:
      interval: 5000       # Every 5 seconds
      batch-size: 50
      workers: 2
    batch:
      interval: 60000      # Every minute
      batch-size: 500
      workers: 1
```

---

**Document Status:** ✅ Complete & Ready for Implementation  
**Version:** 1.0  
**Last Updated:** 2025-11-14  
**Next Review:** After Phase 1 Implementation
