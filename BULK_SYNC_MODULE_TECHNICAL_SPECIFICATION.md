# Bulk Sync & Indexing Module - Technical Specification

## 📋 Module Overview

**Module Name**: `ai-infrastructure-bulk-sync`  
**Package**: `com.ai.infrastructure.bulksync`  
**Version**: 1.0.0  
**Dependencies**: `ai-infrastructure-core`

---

## 🏗️ Module Structure

```
ai-infrastructure-bulk-sync/
├── src/main/java/com/ai/infrastructure/bulksync/
│   ├── config/
│   │   ├── BulkSyncAutoConfiguration.java
│   │   ├── BulkSyncProperties.java
│   │   └── BulkSyncSchedulerConfig.java
│   ├── service/
│   │   ├── BulkIndexingService.java
│   │   ├── EntityDiscoveryService.java
│   │   ├── SyncValidationService.java
│   │   ├── BatchProcessingEngine.java
│   │   ├── BulkIndexingMonitorService.java
│   │   └── BulkIndexingRecoveryService.java
│   ├── model/
│   │   ├── BulkIndexingJob.java
│   │   ├── BulkIndexingProgress.java
│   │   ├── BulkIndexingResult.java
│   │   ├── BulkIndexingOptions.java
│   │   ├── SyncStatus.java
│   │   ├── DriftReport.java
│   │   └── AICapableEntityInfo.java
│   ├── repository/
│   │   ├── BulkIndexingJobRepository.java
│   │   └── SyncCheckpointRepository.java
│   ├── processor/
│   │   ├── EntityBatchProcessor.java
│   │   ├── EmbeddingBatchProcessor.java
│   │   └── IndexingBatchProcessor.java
│   ├── validator/
│   │   ├── SyncValidator.java
│   │   ├── DriftDetector.java
│   │   └── ConsistencyChecker.java
│   ├── controller/
│   │   ├── BulkSyncController.java
│   │   └── BulkSyncMonitorController.java
│   ├── scheduler/
│   │   ├── ScheduledSyncValidator.java
│   │   └── ScheduledDriftCorrector.java
│   └── exception/
│       ├── BulkSyncException.java
│       ├── EntityDiscoveryException.java
│       └── SyncValidationException.java
├── src/main/resources/
│   ├── META-INF/
│   │   └── spring.factories
│   └── application-bulksync.yml
└── pom.xml
```

---

## 📦 Core Components

### 1. BulkIndexingService

**Purpose**: Main service for bulk indexing operations

```java
package com.ai.infrastructure.bulksync.service;

import com.ai.infrastructure.bulksync.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class BulkIndexingService {
    
    private final EntityDiscoveryService entityDiscoveryService;
    private final BatchProcessingEngine batchProcessingEngine;
    private final BulkIndexingMonitorService monitorService;
    private final BulkSyncProperties properties;
    
    /**
     * Index all entities of a given type
     * 
     * @param entityType the entity type to index
     * @return BulkIndexingResult with job details
     */
    public BulkIndexingResult indexAllEntities(String entityType) {
        return indexAllEntities(entityType, BulkIndexingOptions.defaultOptions());
    }
    
    /**
     * Index all entities with custom options
     * 
     * @param entityType the entity type to index
     * @param options indexing options
     * @return BulkIndexingResult with job details
     */
    public BulkIndexingResult indexAllEntities(
            String entityType, 
            BulkIndexingOptions options) {
        
        log.info("Starting bulk indexing for entity type: {}", entityType);
        
        // Create job
        BulkIndexingJob job = monitorService.createJob(entityType, options);
        
        try {
            // Discover entities
            AICapableEntityInfo entityInfo = 
                entityDiscoveryService.getEntityInfo(entityType);
            
            // Get total count
            long totalCount = entityDiscoveryService.getEntityCount(entityType);
            monitorService.updateTotalCount(job.getId(), totalCount);
            
            // Process in batches
            CompletableFuture<Void> future = processEntitiesInBatches(
                entityType, 
                entityInfo,
                job.getId(), 
                options
            );
            
            // If async, return immediately
            if (options.isAsync()) {
                return BulkIndexingResult.builder()
                    .jobId(job.getId())
                    .status(BulkIndexingStatus.IN_PROGRESS)
                    .message("Bulk indexing started asynchronously")
                    .build();
            }
            
            // Wait for completion
            future.join();
            
            // Get final status
            BulkIndexingJob completedJob = monitorService.getJob(job.getId());
            
            return BulkIndexingResult.builder()
                .jobId(completedJob.getId())
                .status(completedJob.getStatus())
                .totalEntities(completedJob.getTotalEntities())
                .processedEntities(completedJob.getProcessedEntities())
                .successCount(completedJob.getSuccessCount())
                .failureCount(completedJob.getFailureCount())
                .durationMs(completedJob.getDurationMs())
                .message("Bulk indexing completed")
                .build();
                
        } catch (Exception e) {
            log.error("Error during bulk indexing for {}", entityType, e);
            monitorService.markJobFailed(job.getId(), e.getMessage());
            throw new BulkSyncException("Bulk indexing failed", e);
        }
    }
    
    /**
     * Process entities in batches
     */
    private CompletableFuture<Void> processEntitiesInBatches(
            String entityType,
            AICapableEntityInfo entityInfo,
            String jobId,
            BulkIndexingOptions options) {
        
        return CompletableFuture.runAsync(() -> {
            int batchSize = options.getBatchSize();
            int page = 0;
            boolean hasMore = true;
            
            while (hasMore) {
                try {
                    // Fetch batch
                    List<Object> batch = entityDiscoveryService.fetchBatch(
                        entityType, 
                        page, 
                        batchSize
                    );
                    
                    if (batch.isEmpty()) {
                        hasMore = false;
                        break;
                    }
                    
                    // Process batch
                    BatchResult result = batchProcessingEngine.processBatch(
                        batch,
                        entityInfo,
                        options
                    );
                    
                    // Update progress
                    monitorService.updateProgress(
                        jobId,
                        result.getSuccessCount(),
                        result.getFailureCount()
                    );
                    
                    page++;
                    
                    // Check if we should continue
                    if (batch.size() < batchSize) {
                        hasMore = false;
                    }
                    
                    // Respect rate limits
                    if (hasMore && options.getRateLimitDelayMs() > 0) {
                        Thread.sleep(options.getRateLimitDelayMs());
                    }
                    
                } catch (Exception e) {
                    log.error("Error processing batch {} for {}", page, entityType, e);
                    monitorService.recordBatchError(jobId, page, e.getMessage());
                    
                    if (!options.isContinueOnError()) {
                        throw new BulkSyncException("Batch processing failed", e);
                    }
                }
            }
            
            monitorService.markJobCompleted(jobId);
        });
    }
    
    /**
     * Index specific entities by their IDs
     */
    public BulkIndexingResult indexEntities(
            String entityType, 
            List<String> entityIds) {
        
        log.info("Indexing {} entities of type {}", entityIds.size(), entityType);
        
        BulkIndexingJob job = monitorService.createJob(entityType, null);
        monitorService.updateTotalCount(job.getId(), entityIds.size());
        
        try {
            AICapableEntityInfo entityInfo = 
                entityDiscoveryService.getEntityInfo(entityType);
            
            // Fetch entities
            List<Object> entities = entityDiscoveryService.fetchByIds(
                entityType, 
                entityIds
            );
            
            // Process
            BatchResult result = batchProcessingEngine.processBatch(
                entities,
                entityInfo,
                BulkIndexingOptions.defaultOptions()
            );
            
            monitorService.updateProgress(
                job.getId(),
                result.getSuccessCount(),
                result.getFailureCount()
            );
            
            monitorService.markJobCompleted(job.getId());
            
            BulkIndexingJob completedJob = monitorService.getJob(job.getId());
            
            return BulkIndexingResult.builder()
                .jobId(completedJob.getId())
                .status(completedJob.getStatus())
                .totalEntities(completedJob.getTotalEntities())
                .successCount(completedJob.getSuccessCount())
                .failureCount(completedJob.getFailureCount())
                .build();
                
        } catch (Exception e) {
            log.error("Error indexing specific entities", e);
            monitorService.markJobFailed(job.getId(), e.getMessage());
            throw new BulkSyncException("Entity indexing failed", e);
        }
    }
    
    /**
     * Resume a paused or failed bulk indexing job
     */
    public BulkIndexingResult resumeIndexing(String jobId) {
        log.info("Resuming bulk indexing job: {}", jobId);
        
        BulkIndexingJob job = monitorService.getJob(jobId);
        
        if (job.getStatus() == BulkIndexingStatus.COMPLETED) {
            throw new BulkSyncException("Job already completed");
        }
        
        // Calculate where to resume from
        long processedCount = job.getProcessedEntities();
        int resumePage = (int) (processedCount / properties.getDefaultBatchSize());
        
        // Resume processing
        String entityType = job.getEntityType();
        AICapableEntityInfo entityInfo = 
            entityDiscoveryService.getEntityInfo(entityType);
        
        BulkIndexingOptions options = BulkIndexingOptions.builder()
            .batchSize(properties.getDefaultBatchSize())
            .startPage(resumePage)
            .build();
        
        CompletableFuture<Void> future = processEntitiesInBatches(
            entityType,
            entityInfo,
            jobId,
            options
        );
        
        future.join();
        
        BulkIndexingJob completedJob = monitorService.getJob(jobId);
        
        return BulkIndexingResult.builder()
            .jobId(completedJob.getId())
            .status(completedJob.getStatus())
            .totalEntities(completedJob.getTotalEntities())
            .successCount(completedJob.getSuccessCount())
            .failureCount(completedJob.getFailureCount())
            .build();
    }
}
```

---

### 2. EntityDiscoveryService

**Purpose**: Discover and introspect @AICapable entities

```java
package com.ai.infrastructure.bulksync.service;

import com.ai.infrastructure.annotation.AICapable;
import com.ai.infrastructure.bulksync.model.AICapableEntityInfo;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.support.Repositories;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class EntityDiscoveryService {
    
    private final ApplicationContext applicationContext;
    private final EntityManager entityManager;
    private final Map<String, AICapableEntityInfo> entityCache = 
        new ConcurrentHashMap<>();
    
    /**
     * Discover all @AICapable entities in the application
     */
    public List<AICapableEntityInfo> discoverAllAICapableEntities() {
        log.info("Discovering @AICapable entities");
        
        List<AICapableEntityInfo> entities = new ArrayList<>();
        Repositories repositories = new Repositories(applicationContext);
        
        for (Class<?> domainClass : repositories) {
            if (domainClass.isAnnotationPresent(AICapable.class)) {
                AICapable annotation = domainClass.getAnnotation(AICapable.class);
                AICapableEntityInfo info = createEntityInfo(
                    domainClass, 
                    annotation
                );
                entities.add(info);
                entityCache.put(annotation.entityType(), info);
            }
        }
        
        log.info("Discovered {} @AICapable entities", entities.size());
        return entities;
    }
    
    /**
     * Get entity info for a specific entity type
     */
    public AICapableEntityInfo getEntityInfo(String entityType) {
        if (entityCache.isEmpty()) {
            discoverAllAICapableEntities();
        }
        
        AICapableEntityInfo info = entityCache.get(entityType);
        if (info == null) {
            throw new EntityDiscoveryException(
                "Entity type not found: " + entityType
            );
        }
        
        return info;
    }
    
    /**
     * Get repository for an entity type
     */
    @SuppressWarnings("unchecked")
    public <T> JpaRepository<T, ?> getRepository(String entityType) {
        AICapableEntityInfo info = getEntityInfo(entityType);
        Repositories repositories = new Repositories(applicationContext);
        
        return (JpaRepository<T, ?>) repositories
            .getRepositoryFor(info.getEntityClass())
            .orElseThrow(() -> new EntityDiscoveryException(
                "Repository not found for: " + entityType
            ));
    }
    
    /**
     * Get total count of entities
     */
    public long getEntityCount(String entityType) {
        JpaRepository<?, ?> repository = getRepository(entityType);
        return repository.count();
    }
    
    /**
     * Fetch a batch of entities
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> fetchBatch(String entityType, int page, int size) {
        String jpql = String.format(
            "SELECT e FROM %s e",
            getEntityInfo(entityType).getEntityClass().getSimpleName()
        );
        
        return (List<T>) entityManager.createQuery(jpql)
            .setFirstResult(page * size)
            .setMaxResults(size)
            .getResultList();
    }
    
    /**
     * Fetch entities by IDs
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> fetchByIds(String entityType, List<String> ids) {
        AICapableEntityInfo info = getEntityInfo(entityType);
        
        String jpql = String.format(
            "SELECT e FROM %s e WHERE e.id IN :ids",
            info.getEntityClass().getSimpleName()
        );
        
        return (List<T>) entityManager.createQuery(jpql)
            .setParameter("ids", ids)
            .getResultList();
    }
    
    /**
     * Extract indexable content from an entity
     */
    public IndexableContent extractContent(Object entity, AICapableEntityInfo info) {
        try {
            // Call getSearchableText() method if exists
            Method method = info.getEntityClass().getMethod("getSearchableText");
            String content = (String) method.invoke(entity);
            
            // Get metadata if getAIMetadata() exists
            Map<String, Object> metadata = null;
            try {
                Method metadataMethod = info.getEntityClass()
                    .getMethod("getAIMetadata");
                metadata = (Map<String, Object>) metadataMethod.invoke(entity);
            } catch (NoSuchMethodException e) {
                // Optional method
            }
            
            // Get entity ID
            Method idMethod = info.getEntityClass().getMethod("getId");
            Object id = idMethod.invoke(entity);
            
            return IndexableContent.builder()
                .entityType(info.getEntityType())
                .entityId(id.toString())
                .content(content)
                .metadata(metadata)
                .build();
                
        } catch (Exception e) {
            log.error("Error extracting content from entity", e);
            throw new EntityDiscoveryException(
                "Failed to extract content", e
            );
        }
    }
    
    private AICapableEntityInfo createEntityInfo(
            Class<?> entityClass, 
            AICapable annotation) {
        
        return AICapableEntityInfo.builder()
            .entityType(annotation.entityType())
            .entityClass(entityClass)
            .entityClassName(entityClass.getName())
            .tableName(getTableName(entityClass))
            .hasSearchableTextMethod(hasMethod(entityClass, "getSearchableText"))
            .hasMetadataMethod(hasMethod(entityClass, "getAIMetadata"))
            .build();
    }
    
    private String getTableName(Class<?> entityClass) {
        jakarta.persistence.Table tableAnnotation = 
            entityClass.getAnnotation(jakarta.persistence.Table.class);
        
        return tableAnnotation != null 
            ? tableAnnotation.name() 
            : entityClass.getSimpleName().toLowerCase();
    }
    
    private boolean hasMethod(Class<?> clazz, String methodName) {
        try {
            clazz.getMethod(methodName);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
```

---

### 3. SyncValidationService

**Purpose**: Validate synchronization and detect drift

```java
package com.ai.infrastructure.bulksync.service;

import com.ai.infrastructure.bulksync.model.*;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyncValidationService {
    
    private final EntityDiscoveryService entityDiscoveryService;
    private final AISearchableEntityRepository searchableEntityRepository;
    
    /**
     * Validate if database and index are synchronized
     */
    public SyncStatus validateSync(String entityType) {
        log.info("Validating sync for entity type: {}", entityType);
        
        try {
            // Get counts
            long dbCount = entityDiscoveryService.getEntityCount(entityType);
            long indexCount = searchableEntityRepository
                .countByEntityType(entityType);
            
            // Find missing and orphaned
            List<String> missingInIndex = findMissingInIndex(entityType);
            List<String> orphanedInIndex = findOrphanedInIndex(entityType);
            
            // Determine status
            SyncStatusType status;
            if (missingInIndex.isEmpty() && orphanedInIndex.isEmpty()) {
                status = SyncStatusType.IN_SYNC;
            } else if (missingInIndex.size() < dbCount * 0.1) {
                status = SyncStatusType.MINOR_DRIFT;
            } else if (missingInIndex.size() < dbCount * 0.5) {
                status = SyncStatusType.MAJOR_DRIFT;
            } else {
                status = SyncStatusType.OUT_OF_SYNC;
            }
            
            return SyncStatus.builder()
                .entityType(entityType)
                .status(status)
                .databaseCount(dbCount)
                .indexCount(indexCount)
                .missingInIndexCount(missingInIndex.size())
                .orphanedInIndexCount(orphanedInIndex.size())
                .missingInIndex(missingInIndex)
                .orphanedInIndex(orphanedInIndex)
                .syncPercentage(calculateSyncPercentage(
                    dbCount, 
                    missingInIndex.size()
                ))
                .build();
                
        } catch (Exception e) {
            log.error("Error validating sync for {}", entityType, e);
            throw new SyncValidationException("Sync validation failed", e);
        }
    }
    
    /**
     * Find entities present in DB but missing in index
     */
    public List<String> findMissingInIndex(String entityType) {
        log.debug("Finding entities missing in index for: {}", entityType);
        
        // Get all DB IDs
        JpaRepository<?, ?> repository = 
            entityDiscoveryService.getRepository(entityType);
        
        Set<String> dbIds = repository.findAll().stream()
            .map(entity -> {
                try {
                    Method idMethod = entity.getClass().getMethod("getId");
                    return idMethod.invoke(entity).toString();
                } catch (Exception e) {
                    return null;
                }
            })
            .filter(id -> id != null)
            .collect(Collectors.toSet());
        
        // Get all indexed IDs
        Set<String> indexedIds = searchableEntityRepository
            .findAllByEntityType(entityType).stream()
            .map(searchable -> searchable.getEntityId())
            .collect(Collectors.toSet());
        
        // Find missing
        dbIds.removeAll(indexedIds);
        
        log.info("Found {} entities missing in index for {}", 
            dbIds.size(), entityType);
        
        return new ArrayList<>(dbIds);
    }
    
    /**
     * Find entities present in index but missing in DB (orphaned)
     */
    public List<String> findOrphanedInIndex(String entityType) {
        log.debug("Finding orphaned entities in index for: {}", entityType);
        
        // Get all indexed IDs
        Set<String> indexedIds = searchableEntityRepository
            .findAllByEntityType(entityType).stream()
            .map(searchable -> searchable.getEntityId())
            .collect(Collectors.toSet());
        
        // Get all DB IDs
        JpaRepository<?, ?> repository = 
            entityDiscoveryService.getRepository(entityType);
        
        Set<String> dbIds = repository.findAll().stream()
            .map(entity -> {
                try {
                    Method idMethod = entity.getClass().getMethod("getId");
                    return idMethod.invoke(entity).toString();
                } catch (Exception e) {
                    return null;
                }
            })
            .filter(id -> id != null)
            .collect(Collectors.toSet());
        
        // Find orphaned
        indexedIds.removeAll(dbIds);
        
        log.info("Found {} orphaned entities in index for {}", 
            indexedIds.size(), entityType);
        
        return new ArrayList<>(indexedIds);
    }
    
    /**
     * Detect entities with stale indexed data
     */
    public List<String> findStaleEntities(String entityType) {
        // TODO: Compare updatedAt timestamps between DB and index
        // This requires tracking entity update timestamps
        log.warn("Stale entity detection not yet implemented");
        return List.of();
    }
    
    /**
     * Fix detected drift by indexing missing and removing orphaned
     */
    public DriftCorrectionResult fixDrift(String entityType) {
        log.info("Fixing drift for entity type: {}", entityType);
        
        SyncStatus syncStatus = validateSync(entityType);
        
        int indexed = 0;
        int removed = 0;
        
        // Index missing entities
        if (!syncStatus.getMissingInIndex().isEmpty()) {
            // Use bulk indexing service to index missing entities
            // This is handled by BulkIndexingService.indexEntities()
            indexed = syncStatus.getMissingInIndex().size();
        }
        
        // Remove orphaned entities
        if (!syncStatus.getOrphanedInIndex().isEmpty()) {
            for (String orphanedId : syncStatus.getOrphanedInIndex()) {
                searchableEntityRepository.deleteByEntityTypeAndEntityId(
                    entityType, 
                    orphanedId
                );
                removed++;
            }
        }
        
        return DriftCorrectionResult.builder()
            .entityType(entityType)
            .entitiesIndexed(indexed)
            .entitiesRemoved(removed)
            .success(true)
            .build();
    }
    
    private double calculateSyncPercentage(long total, int missing) {
        if (total == 0) return 100.0;
        return ((total - missing) / (double) total) * 100.0;
    }
}
```

---

### 4. BatchProcessingEngine

**Purpose**: Process entities in batches with rate limiting

```java
package com.ai.infrastructure.bulksync.service;

import com.ai.infrastructure.bulksync.model.*;
import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.indexing.queue.IndexingQueueService;
import com.ai.infrastructure.indexing.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchProcessingEngine {
    
    private final EntityDiscoveryService entityDiscoveryService;
    private final AIEmbeddingService embeddingService;
    private final IndexingQueueService indexingQueueService;
    private final BulkSyncProperties properties;
    private final ExecutorService executorService;
    
    /**
     * Process a batch of entities
     */
    public BatchResult processBatch(
            List<Object> entities,
            AICapableEntityInfo entityInfo,
            BulkIndexingOptions options) {
        
        log.debug("Processing batch of {} entities", entities.size());
        
        int successCount = 0;
        int failureCount = 0;
        List<String> errors = new ArrayList<>();
        
        // Decide processing strategy
        if (options.isParallelProcessing() && 
            entities.size() >= properties.getParallelThreshold()) {
            
            return processParallel(entities, entityInfo, options);
        } else {
            return processSequential(entities, entityInfo, options);
        }
    }
    
    /**
     * Process entities sequentially
     */
    private BatchResult processSequential(
            List<Object> entities,
            AICapableEntityInfo entityInfo,
            BulkIndexingOptions options) {
        
        int successCount = 0;
        int failureCount = 0;
        List<String> errors = new ArrayList<>();
        
        for (Object entity : entities) {
            try {
                processEntity(entity, entityInfo, options);
                successCount++;
            } catch (Exception e) {
                log.error("Error processing entity", e);
                failureCount++;
                errors.add(e.getMessage());
                
                if (!options.isContinueOnError()) {
                    break;
                }
            }
        }
        
        return BatchResult.builder()
            .successCount(successCount)
            .failureCount(failureCount)
            .errors(errors)
            .build();
    }
    
    /**
     * Process entities in parallel
     */
    private BatchResult processParallel(
            List<Object> entities,
            AICapableEntityInfo entityInfo,
            BulkIndexingOptions options) {
        
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<String> errors = new CopyOnWriteArrayList<>();
        
        for (Object entity : entities) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    processEntity(entity, entityInfo, options);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    log.error("Error processing entity in parallel", e);
                    failureCount.incrementAndGet();
                    errors.add(e.getMessage());
                }
            }, executorService);
            
            futures.add(future);
        }
        
        // Wait for all to complete
        CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        ).join();
        
        return BatchResult.builder()
            .successCount(successCount.get())
            .failureCount(failureCount.get())
            .errors(errors)
            .build();
    }
    
    /**
     * Process a single entity
     */
    private void processEntity(
            Object entity,
            AICapableEntityInfo entityInfo,
            BulkIndexingOptions options) throws Exception {
        
        // Extract content
        IndexableContent content = entityDiscoveryService.extractContent(
            entity, 
            entityInfo
        );
        
        // Create indexing request
        IndexingRequest request = IndexingRequest.builder()
            .entityType(content.getEntityType())
            .entityId(content.getEntityId())
            .entityClassName(entityInfo.getEntityClassName())
            .operation(IndexingOperation.INDEX)
            .strategy(IndexingStrategy.BULK)
            .actionPlan(createActionPlan(options))
            .payload(content.toJson())
            .scheduledFor(LocalDateTime.now())
            .maxRetries(properties.getMaxRetries())
            .build();
        
        // Enqueue for indexing
        indexingQueueService.enqueue(request);
    }
    
    private IndexingActionPlan createActionPlan(BulkIndexingOptions options) {
        return new IndexingActionPlan(
            options.isGenerateEmbedding(),
            options.isIndexForSearch(),
            options.isEnableAnalysis(),
            false, // removeFromSearch
            false  // cleanupEmbeddings
        );
    }
}
```

---

## 📊 Model Classes

### BulkIndexingJob

```java
package com.ai.infrastructure.bulksync.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "bulk_indexing_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkIndexingJob {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false)
    private String entityType;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BulkIndexingStatus status;
    
    @Column(nullable = false)
    private LocalDateTime startedAt;
    
    @Column
    private LocalDateTime completedAt;
    
    @Column(nullable = false)
    private long totalEntities;
    
    @Column(nullable = false)
    private long processedEntities;
    
    @Column(nullable = false)
    private long successCount;
    
    @Column(nullable = false)
    private long failureCount;
    
    @Column(columnDefinition = "TEXT")
    private String errorDetails;
    
    @Column
    private Long durationMs;
    
    @Column(columnDefinition = "TEXT")
    private String metricsJson;
    
    public void updateProgress(int success, int failure) {
        this.processedEntities += (success + failure);
        this.successCount += success;
        this.failureCount += failure;
    }
    
    public void complete() {
        this.status = BulkIndexingStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        this.durationMs = java.time.Duration.between(
            startedAt, 
            completedAt
        ).toMillis();
    }
    
    public void fail(String error) {
        this.status = BulkIndexingStatus.FAILED;
        this.completedAt = LocalDateTime.now();
        this.errorDetails = error;
    }
}
```

---

## 🌐 REST API

### BulkSyncController

```java
package com.ai.infrastructure.bulksync.controller;

import com.ai.infrastructure.bulksync.model.*;
import com.ai.infrastructure.bulksync.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai/bulk-sync")
@RequiredArgsConstructor
public class BulkSyncController {
    
    private final BulkIndexingService bulkIndexingService;
    private final SyncValidationService syncValidationService;
    private final EntityDiscoveryService entityDiscoveryService;
    private final BulkIndexingMonitorService monitorService;
    
    /**
     * Discover all @AICapable entities
     */
    @GetMapping("/discover")
    public ResponseEntity<List<AICapableEntityInfo>> discoverEntities() {
        List<AICapableEntityInfo> entities = 
            entityDiscoveryService.discoverAllAICapableEntities();
        return ResponseEntity.ok(entities);
    }
    
    /**
     * Start bulk indexing for an entity type
     */
    @PostMapping("/index/{entityType}")
    public ResponseEntity<BulkIndexingResult> indexAll(
            @PathVariable String entityType,
            @RequestBody(required = false) BulkIndexingOptions options) {
        
        if (options == null) {
            options = BulkIndexingOptions.defaultOptions();
        }
        
        BulkIndexingResult result = bulkIndexingService.indexAllEntities(
            entityType, 
            options
        );
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * Index specific entities by ID
     */
    @PostMapping("/index/{entityType}/ids")
    public ResponseEntity<BulkIndexingResult> indexByIds(
            @PathVariable String entityType,
            @RequestBody List<String> entityIds) {
        
        BulkIndexingResult result = bulkIndexingService.indexEntities(
            entityType, 
            entityIds
        );
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * Get job status
     */
    @GetMapping("/status/{jobId}")
    public ResponseEntity<BulkIndexingJob> getStatus(
            @PathVariable String jobId) {
        
        BulkIndexingJob job = monitorService.getJob(jobId);
        return ResponseEntity.ok(job);
    }
    
    /**
     * Resume a paused or failed job
     */
    @PostMapping("/resume/{jobId}")
    public ResponseEntity<BulkIndexingResult> resumeJob(
            @PathVariable String jobId) {
        
        BulkIndexingResult result = bulkIndexingService.resumeIndexing(jobId);
        return ResponseEntity.ok(result);
    }
    
    /**
     * Validate sync status
     */
    @PostMapping("/validate/{entityType}")
    public ResponseEntity<SyncStatus> validateSync(
            @PathVariable String entityType) {
        
        SyncStatus status = syncValidationService.validateSync(entityType);
        return ResponseEntity.ok(status);
    }
    
    /**
     * Fix detected drift
     */
    @PostMapping("/fix-drift/{entityType}")
    public ResponseEntity<DriftCorrectionResult> fixDrift(
            @PathVariable String entityType) {
        
        DriftCorrectionResult result = syncValidationService.fixDrift(
            entityType
        );
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * Get all active jobs
     */
    @GetMapping("/jobs/active")
    public ResponseEntity<List<BulkIndexingJob>> getActiveJobs() {
        List<BulkIndexingJob> jobs = monitorService.getActiveJobs();
        return ResponseEntity.ok(jobs);
    }
    
    /**
     * Cancel a running job
     */
    @PostMapping("/cancel/{jobId}")
    public ResponseEntity<Void> cancelJob(@PathVariable String jobId) {
        monitorService.cancelJob(jobId);
        return ResponseEntity.ok().build();
    }
}
```

---

## ⚙️ Configuration

### BulkSyncProperties

```java
package com.ai.infrastructure.bulksync.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai.bulk-sync")
public class BulkSyncProperties {
    
    /**
     * Default batch size for bulk operations
     */
    private int defaultBatchSize = 100;
    
    /**
     * Maximum parallel threads for processing
     */
    private int maxParallelThreads = 4;
    
    /**
     * Threshold for switching to parallel processing
     */
    private int parallelThreshold = 50;
    
    /**
     * Rate limit delay between batches (ms)
     */
    private long rateLimitDelayMs = 100;
    
    /**
     * Maximum retries for failed entities
     */
    private int maxRetries = 3;
    
    /**
     * Enable scheduled sync validation
     */
    private boolean enableScheduledValidation = true;
    
    /**
     * Cron expression for scheduled validation (default: 2 AM daily)
     */
    private String validationCron = "0 0 2 * * *";
    
    /**
     * Enable auto drift correction
     */
    private boolean enableAutoDriftCorrection = false;
    
    /**
     * Job retention days
     */
    private int jobRetentionDays = 30;
}
```

### application-bulksync.yml

```yaml
ai:
  bulk-sync:
    default-batch-size: 100
    max-parallel-threads: 4
    parallel-threshold: 50
    rate-limit-delay-ms: 100
    max-retries: 3
    enable-scheduled-validation: true
    validation-cron: "0 0 2 * * *"
    enable-auto-drift-correction: false
    job-retention-days: 30

# Performance tuning for large datasets
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 50
          fetch_size: 100
```

---

## 🧪 Testing Strategy

### Unit Tests
- Service-level tests for each component
- Mock external dependencies
- Test edge cases and error handling

### Integration Tests
- End-to-end bulk indexing tests
- Sync validation tests
- Drift detection and correction tests

### Performance Tests
- Large dataset testing (100K+ entities)
- Concurrent job execution
- Memory and CPU profiling
- Rate limiting validation

### Load Tests
- Multiple simultaneous bulk operations
- System behavior under stress
- Recovery from failures

---

## 📈 Metrics and Monitoring

### Key Metrics
- Entities processed per second
- Batch processing time
- Success/failure rates
- Queue depth
- Memory usage
- API call costs

### Health Checks
- Job status monitoring
- Sync drift detection
- Dead letter queue size
- Rate limit compliance

---

## 🚀 Deployment

### Maven Dependency
```xml
<dependency>
    <groupId>com.ai.infrastructure</groupId>
    <artifactId>ai-infrastructure-bulk-sync</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Auto-Configuration
The module will auto-configure when detected on the classpath.

### Database Migration
Liquibase/Flyway scripts for `bulk_indexing_jobs` table.

---

## 📝 Usage Examples

### Basic Bulk Indexing
```java
@Autowired
private BulkIndexingService bulkIndexingService;

// Index all users
BulkIndexingResult result = bulkIndexingService.indexAllEntities("user");
```

### With Options
```java
BulkIndexingOptions options = BulkIndexingOptions.builder()
    .batchSize(200)
    .async(true)
    .parallelProcessing(true)
    .continueOnError(true)
    .build();

BulkIndexingResult result = bulkIndexingService.indexAllEntities(
    "product", 
    options
);
```

### Sync Validation
```java
@Autowired
private SyncValidationService syncValidationService;

SyncStatus status = syncValidationService.validateSync("user");

if (status.getStatus() != SyncStatusType.IN_SYNC) {
    DriftCorrectionResult fixed = syncValidationService.fixDrift("user");
}
```

---

**Document Version**: 1.0.0  
**Date**: November 25, 2025  
**Status**: Technical Specification  
**Next Steps**: Review → Approval → Implementation
