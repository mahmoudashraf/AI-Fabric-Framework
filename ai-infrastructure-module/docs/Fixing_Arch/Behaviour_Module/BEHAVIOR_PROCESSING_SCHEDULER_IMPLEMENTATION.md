# Behavior Analysis Processing & Scheduling Implementation Guide

**Version:** 1.0.0  
**Status:** Implementation Ready  
**Scope:** Worker, API Controller, and Flexible Processing Configuration

---

## 📋 TABLE OF CONTENTS

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Configuration Properties](#configuration-properties)
4. [Scheduled Worker Implementation](#scheduled-worker-implementation)
5. [API Controller Implementation](#api-controller-implementation)
6. [Job Management & Cancellation](#job-management--cancellation)
7. [Configuration Examples](#configuration-examples)
8. [Testing Strategy](#testing-strategy)
9. [Usage Examples](#usage-examples)
10. [Implementation Checklist](#implementation-checklist)

---

## 🎯 OVERVIEW

### What We're Building

A **flexible behavior analysis processing system** that supports 3 execution modes:

1. ⏰ **Scheduled Processing** - Background worker runs on cron schedule
2. 🎯 **API-Triggered Batch** - On-demand processing with configurable parameters
3. 🔄 **Continuous Processing** - Long-running background jobs for migrations

### Key Features

- ✅ **YAML-driven configuration** for all processing parameters
- ✅ **Flexible API** - Control user count, duration, throttling
- ✅ **Multiple modes** - Scheduled, on-demand, or continuous
- ✅ **Graceful limits** - Max duration, max users, rate limiting
- ✅ **Job management** - Cancel continuous jobs, pause/resume scheduled processing
- ✅ **Status tracking** - Monitor running jobs in real-time
- ✅ **Production-ready** - Error handling, logging, metrics

### Design Principles

1. **Configuration over code** - All behavior controlled via YAML
2. **Flexibility** - Support scheduled, triggered, and continuous modes
3. **Safety** - Max duration and batch size limits prevent runaway jobs
4. **Observability** - Detailed logging and metrics

---

## 🏗️ ARCHITECTURE

### Component Diagram

```
┌─────────────────────────────────────────────────────────────┐
│ PROCESSING CONFIGURATION (YAML)                             │
├─────────────────────────────────────────────────────────────┤
│ BehaviorProcessingProperties                                │
│ ├─ scheduled-enabled: true/false                           │
│ ├─ schedule-cron: "0 */15 * * * *"                         │
│ ├─ scheduled-batch-size: 100                               │
│ ├─ api-enabled: true/false                                 │
│ ├─ api-max-batch-size: 1000                                │
│ └─ processing-delay: 100ms                                 │
└─────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────┐
│ SCHEDULED WORKER (Optional)                                 │
├─────────────────────────────────────────────────────────────┤
│ BehaviorAnalysisWorker                                      │
│ ├─ @Scheduled(cron = "${...}")                             │
│ ├─ Runs every X minutes (configurable)                     │
│ ├─ Processes N users per batch                             │
│ ├─ Respects max duration limit                             │
│ └─ Publishes metrics                                        │
└─────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────┐
│ API CONTROLLER (Optional)                                   │
├─────────────────────────────────────────────────────────────┤
│ BehaviorProcessingController                                │
│ ├─ POST /users/{userId}          → Single user            │
│ ├─ POST /batch                   → Flexible batch         │
│ └─ POST /continuous              → Background job         │
└─────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────┐
│ ANALYSIS SERVICE (Core)                                     │
├─────────────────────────────────────────────────────────────┤
│ BehaviorAnalysisService                                     │
│ ├─ analyzeUser(userId)           → Targeted analysis      │
│ └─ processNextUser()             → Next in queue          │
└─────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────┐
│ EVENT PROVIDER (SPI - User Implemented)                     │
├─────────────────────────────────────────────────────────────┤
│ ExternalEventProvider                                       │
│ ├─ getEventsForUser(userId)      → Targeted events        │
│ └─ getNextUserEvents()           → Discovery/batch        │
└─────────────────────────────────────────────────────────────┘
```

---

## ⚙️ CONFIGURATION PROPERTIES

### File 1: BehaviorProcessingProperties.java

**Location:** `ai-infrastructure-module/ai-infrastructure-behavior/src/main/java/com/ai/infrastructure/behavior/config/BehaviorProcessingProperties.java`

```java
package com.ai.infrastructure.behavior.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Configuration properties for behavior analysis processing.
 * 
 * Controls scheduled workers and API-triggered processing.
 * All parameters configurable via application.yml.
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.behavior.processing")
public class BehaviorProcessingProperties {
    
    // ========================================
    // SCHEDULED PROCESSING
    // ========================================
    
    /**
     * Enable/disable scheduled background processing.
     * Default: false (opt-in)
     */
    private boolean scheduledEnabled = false;
    
    /**
     * Cron expression for scheduled processing.
     * Default: Every 15 minutes
     * 
     * Examples:
     * - "0 *\/5 * * * *"  = Every 5 minutes
     * - "0 *\/15 * * * *" = Every 15 minutes (default)
     * - "0 0 * * * *"     = Every hour
     * - "0 0 2 * * *"     = Daily at 2 AM
     * - "0 0 0 * * 0"     = Weekly on Sunday
     */
    private String scheduleCron = "0 */15 * * * *";
    
    /**
     * Number of users to process per scheduled batch.
     * Default: 100
     */
    private int scheduledBatchSize = 100;
    
    /**
     * Maximum processing time for scheduled job.
     * After this duration, the job stops gracefully.
     * Default: 10 minutes
     */
    private Duration scheduledMaxDuration = Duration.ofMinutes(10);
    
    // ========================================
    // API-TRIGGERED PROCESSING
    // ========================================
    
    /**
     * Enable/disable manual processing API endpoints.
     * Default: true (allows on-demand triggering)
     */
    private boolean apiEnabled = true;
    
    /**
     * Maximum users allowed in single API request.
     * Prevents abuse/overload.
     * Default: 1000
     */
    private int apiMaxBatchSize = 1000;
    
    /**
     * Maximum processing duration for API requests.
     * Default: 30 minutes
     */
    private Duration apiMaxDuration = Duration.ofMinutes(30);
    
    // ========================================
    // THROTTLING & PERFORMANCE
    // ========================================
    
    /**
     * Delay between processing each user (throttling).
     * Prevents overwhelming the LLM API.
     * Default: 100ms
     * 
     * Adjust based on LLM provider rate limits:
     * - OpenAI GPT-4: 50-100ms
     * - Anthropic Claude: 100-200ms
     * - Local model: 0-50ms
     */
    private Duration processingDelay = Duration.ofMillis(100);
    
    /**
     * Thread pool size for concurrent processing.
     * Default: 1 (sequential processing)
     * 
     * Set to >1 for parallel processing (requires thread-safe ExternalEventProvider).
     */
    private int threadPoolSize = 1;
}
```

---

## 🔄 SCHEDULED WORKER

### File 2: BehaviorAnalysisWorker.java

**Location:** `ai-infrastructure-module/ai-infrastructure-behavior/src/main/java/com/ai/infrastructure/behavior/worker/BehaviorAnalysisWorker.java`

```java
package com.ai.infrastructure.behavior.worker;

import com.ai.infrastructure.behavior.config.BehaviorProcessingProperties;
import com.ai.infrastructure.behavior.entity.BehaviorInsights;
import com.ai.infrastructure.behavior.service.BehaviorAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Scheduled worker for background behavior analysis processing.
 * 
 * Processes users in batches based on YAML configuration.
 * Enabled via: ai.behavior.processing.scheduled-enabled=true
 * 
 * Features:
 * - Configurable cron schedule
 * - Configurable batch size
 * - Max duration limit (graceful stop)
 * - Throttling between users
 * - Error handling per user (continues on failure)
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "ai.behavior.processing",
    name = "scheduled-enabled",
    havingValue = "true"
)
public class BehaviorAnalysisWorker {
    
    private final BehaviorAnalysisService analysisService;
    private final BehaviorProcessingProperties properties;
    
    /**
     * Scheduled batch processing of user behavior.
     * 
     * Cron expression is read from application.yml.
     * Processes up to configured batch size with max duration limit.
     */
    @Scheduled(cron = "${ai.behavior.processing.schedule-cron:0 */15 * * * *}")
    public void processUserBehaviors() {
        log.info("Starting scheduled behavior analysis batch");
        
        int batchSize = properties.getScheduledBatchSize();
        Duration maxDuration = properties.getScheduledMaxDuration();
        Duration processingDelay = properties.getProcessingDelay();
        
        Instant startTime = Instant.now();
        int processedCount = 0;
        int successCount = 0;
        int errorCount = 0;
        
        try {
            for (int i = 0; i < batchSize; i++) {
                // Check if max duration exceeded
                if (Duration.between(startTime, Instant.now()).compareTo(maxDuration) > 0) {
                    log.warn("Scheduled processing exceeded max duration ({}), stopping gracefully at {} users",
                        maxDuration, processedCount);
                    break;
                }
                
                // Process next user
                try {
                    BehaviorInsights result = analysisService.processNextUser();
                    
                    if (result == null) {
                        log.debug("No more users pending analysis, stopping batch at {} users", processedCount);
                        break;
                    }
                    
                    processedCount++;
                    successCount++;
                    
                    log.debug("Processed user {}: trend={}, sentiment={}, churn={:.2f}",
                        result.getUserId(),
                        result.getTrend(),
                        result.getSentimentLabel(),
                        result.getChurnRisk()
                    );
                    
                    // Throttle to avoid overwhelming LLM API
                    if (processingDelay.toMillis() > 0 && i < batchSize - 1) {
                        Thread.sleep(processingDelay.toMillis());
                    }
                    
                } catch (InterruptedException e) {
                    log.warn("Scheduled processing interrupted");
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    errorCount++;
                    log.error("Error processing user in scheduled batch (continuing with next)", e);
                    // Continue with next user - don't fail entire batch
                }
            }
            
            Duration totalDuration = Duration.between(startTime, Instant.now());
            log.info("Scheduled batch completed: processed={}, success={}, errors={}, duration={}ms",
                processedCount, successCount, errorCount, totalDuration.toMillis());
                
        } catch (Exception e) {
            log.error("Fatal error in scheduled batch processing", e);
        }
    }
}
```

---

## 🌐 API CONTROLLER

### File 3: BehaviorProcessingController.java

**Location:** `ai-infrastructure-module/ai-infrastructure-behavior/src/main/java/com/ai/infrastructure/behavior/api/BehaviorProcessingController.java`

```java
package com.ai.infrastructure.behavior.api;

import com.ai.infrastructure.behavior.config.BehaviorProcessingProperties;
import com.ai.infrastructure.behavior.entity.BehaviorInsights;
import com.ai.infrastructure.behavior.service.BehaviorAnalysisService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * REST API for triggering behavior analysis processing.
 * 
 * Provides flexible on-demand processing with configurable parameters:
 * - Single user analysis
 * - Flexible batch processing (control user count, duration, throttling)
 * - Continuous background jobs
 * 
 * Enabled via: ai.behavior.processing.api-enabled=true (default)
 */
@Slf4j
@RestController
@RequestMapping("/api/behavior/processing")
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "ai.behavior.processing",
    name = "api-enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class BehaviorProcessingController {
    
    private final BehaviorAnalysisService analysisService;
    private final BehaviorProcessingProperties properties;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    
    // ========================================
    // SINGLE USER ANALYSIS
    // ========================================
    
    /**
     * Analyze a specific user (targeted analysis).
     * 
     * Use for:
     * - Support tickets (analyze user before responding)
     * - VIP users (immediate analysis)
     * - Testing specific users
     * 
     * @param userId The user to analyze
     * @return The generated behavior insights
     */
    @PostMapping("/users/{userId}")
    public ResponseEntity<BehaviorInsights> analyzeUser(@PathVariable UUID userId) {
        log.info("API: Triggered analysis for user: {}", userId);
        
        try {
            BehaviorInsights result = analysisService.analyzeUser(userId);
            
            if (result == null) {
                return ResponseEntity.noContent().build();
            }
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error analyzing user: {}", userId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    // ========================================
    // FLEXIBLE BATCH PROCESSING
    // ========================================
    
    /**
     * Process multiple users in batch with flexible configuration.
     * 
     * Request controls:
     * - maxUsers: How many users to process (default: config value)
     * - maxDurationMinutes: How long to run (default: config value)
     * - delayBetweenUsersMs: Throttling delay (default: config value)
     * 
     * Examples:
     * - Process next 50 users: {"maxUsers": 50}
     * - Process for 5 minutes: {"maxDurationMinutes": 5}
     * - Fast processing: {"maxUsers": 100, "delayBetweenUsersMs": 10}
     * 
     * @param request Processing configuration
     * @return Processing results summary
     */
    @PostMapping("/batch")
    public ResponseEntity<BatchProcessingResult> processBatch(
        @RequestBody(required = false) BatchProcessingRequest request
    ) {
        if (request == null) {
            request = new BatchProcessingRequest();
        }
        
        log.info("API: Triggered batch processing with request: {}", request);
        
        // Apply limits from configuration
        int maxUsers = Math.min(
            request.getMaxUsers() != null ? request.getMaxUsers() : properties.getScheduledBatchSize(),
            properties.getApiMaxBatchSize()
        );
        
        Duration maxDuration = request.getMaxDurationMinutes() != null
            ? Duration.ofMinutes(request.getMaxDurationMinutes())
            : properties.getApiMaxDuration();
        
        if (maxDuration.compareTo(properties.getApiMaxDuration()) > 0) {
            maxDuration = properties.getApiMaxDuration();
            log.warn("Requested duration {} exceeds max {}, capped", 
                request.getMaxDurationMinutes(), properties.getApiMaxDuration().toMinutes());
        }
        
        Duration processingDelay = request.getDelayBetweenUsersMs() != null
            ? Duration.ofMillis(request.getDelayBetweenUsersMs())
            : properties.getProcessingDelay();
        
        // Execute batch processing
        try {
            BatchProcessingResult result = executeBatchProcessing(
                maxUsers,
                maxDuration,
                processingDelay,
                request.isContinuous()
            );
            
            log.info("Batch processing completed: {}", result);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error in batch processing", e);
            return ResponseEntity.internalServerError()
                .body(BatchProcessingResult.builder()
                    .status("FAILED")
                    .error(e.getMessage())
                    .build());
        }
    }
    
    // ========================================
    // CONTINUOUS PROCESSING (Background Job)
    // ========================================
    
    /**
     * Start continuous processing in background.
     * 
     * Runs indefinitely or for specified iterations:
     * - Process N users per batch
     * - Wait M minutes between batches
     * - Stop after X iterations (or run forever)
     * 
     * Use for:
     * - Initial data migration
     * - Bulk catch-up processing
     * - Continuous monitoring in dev/staging
     * 
     * @param request Continuous processing configuration
     * @return Processing job ID (job runs in background)
     */
    @PostMapping("/continuous")
    public ResponseEntity<ContinuousProcessingResponse> startContinuousProcessing(
        @RequestBody ContinuousProcessingRequest request
    ) {
        log.info("API: Starting continuous processing: {}", request);
        
        String jobId = UUID.randomUUID().toString();
        
        // Submit as async job
        executorService.submit(() -> {
            log.info("Continuous processing job {} started", jobId);
            
            int batchSize = request.getUsersPerBatch() != null
                ? Math.min(request.getUsersPerBatch(), properties.getApiMaxBatchSize())
                : 100;
            
            long intervalMs = request.getIntervalMinutes() != null
                ? Duration.ofMinutes(request.getIntervalMinutes()).toMillis()
                : Duration.ofMinutes(5).toMillis();
            
            int maxIterations = request.getMaxIterations() != null
                ? request.getMaxIterations()
                : Integer.MAX_VALUE;
            
            int totalProcessed = 0;
            int totalSuccess = 0;
            int totalErrors = 0;
            
            for (int iteration = 0; iteration < maxIterations; iteration++) {
                try {
                    log.info("Continuous job {}: iteration {}/{}", jobId, iteration + 1, 
                        maxIterations == Integer.MAX_VALUE ? "∞" : maxIterations);
                    
                    BatchProcessingResult iterationResult = executeBatchProcessing(
                        batchSize,
                        properties.getApiMaxDuration(),
                        properties.getProcessingDelay(),
                        true // continuous mode
                    );
                    
                    totalProcessed += iterationResult.getProcessedCount();
                    totalSuccess += iterationResult.getSuccessCount();
                    totalErrors += iterationResult.getErrorCount();
                    
                    log.info("Continuous job {} iteration {} completed: processed={}, total={}",
                        jobId, iteration + 1, iterationResult.getProcessedCount(), totalProcessed);
                    
                    // Wait before next iteration (unless it's the last one)
                    if (iteration < maxIterations - 1) {
                        Thread.sleep(intervalMs);
                    }
                    
                } catch (InterruptedException e) {
                    log.warn("Continuous job {} interrupted at iteration {}", jobId, iteration);
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Error in continuous job {} iteration {}", jobId, iteration, e);
                }
            }
            
            log.info("Continuous processing job {} completed: total processed={}, success={}, errors={}",
                jobId, totalProcessed, totalSuccess, totalErrors);
        });
        
        return ResponseEntity.accepted()
            .body(ContinuousProcessingResponse.builder()
                .jobId(jobId)
                .status("STARTED")
                .message("Continuous processing job submitted in background")
                .estimatedDuration(calculateEstimatedDuration(request))
                .build());
    }
    
    // ========================================
    // CORE BATCH PROCESSING LOGIC
    // ========================================
    
    /**
     * Execute batch processing with given parameters.
     * Shared by both API endpoint and worker.
     */
    private BatchProcessingResult executeBatchProcessing(
        int maxUsers,
        Duration maxDuration,
        Duration processingDelay,
        boolean continuous
    ) {
        Instant startTime = Instant.now();
        int processedCount = 0;
        int successCount = 0;
        int errorCount = 0;
        int skippedCount = 0;
        
        try {
            for (int i = 0; i < maxUsers; i++) {
                // Check duration limit
                if (Duration.between(startTime, Instant.now()).compareTo(maxDuration) > 0) {
                    log.info("Max duration {} reached after {} users, stopping gracefully",
                        maxDuration, processedCount);
                    break;
                }
                
                try {
                    BehaviorInsights result = analysisService.processNextUser();
                    
                    if (result == null) {
                        if (continuous) {
                            log.debug("No users pending in continuous mode, marking as skipped");
                            skippedCount++;
                        } else {
                            log.info("No more users to process, stopping batch at {} users", processedCount);
                            break;
                        }
                    } else {
                        processedCount++;
                        successCount++;
                        
                        // Log on interval for long batches
                        if (processedCount % 10 == 0) {
                            log.debug("Batch progress: {}/{} users processed", processedCount, maxUsers);
                        }
                    }
                    
                    // Throttle to avoid overwhelming LLM API
                    if (processingDelay.toMillis() > 0 && i < maxUsers - 1) {
                        Thread.sleep(processingDelay.toMillis());
                    }
                    
                } catch (InterruptedException e) {
                    log.warn("Batch processing interrupted");
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    errorCount++;
                    log.error("Error processing user {} in batch (continuing)", i, e);
                    // Continue with next user
                }
            }
            
        } catch (Exception e) {
            log.error("Fatal error in batch processing", e);
        }
        
        Duration totalDuration = Duration.between(startTime, Instant.now());
        
        return BatchProcessingResult.builder()
            .status("COMPLETED")
            .processedCount(processedCount)
            .successCount(successCount)
            .errorCount(errorCount)
            .skippedCount(skippedCount)
            .durationMs(totalDuration.toMillis())
            .averageTimePerUserMs(processedCount > 0 ? totalDuration.toMillis() / processedCount : 0)
            .startedAt(LocalDateTime.now().minus(totalDuration))
            .completedAt(LocalDateTime.now())
            .build();
    }
    
    private String calculateEstimatedDuration(ContinuousProcessingRequest request) {
        if (request.getMaxIterations() == null) {
            return "Indefinite (runs until stopped)";
        }
        
        int iterations = request.getMaxIterations();
        int intervalMins = request.getIntervalMinutes() != null ? request.getIntervalMinutes() : 5;
        int estimatedMinutes = iterations * intervalMins;
        
        return String.format("%d iterations × %d min = ~%d minutes", 
            iterations, intervalMins, estimatedMinutes);
    }
    
    // ========================================
    // REQUEST/RESPONSE DTOs
    // ========================================
    
    @Data
    public static class BatchProcessingRequest {
        /**
         * Maximum number of users to process.
         * Default: configured scheduled-batch-size
         * Max: configured api-max-batch-size
         */
        private Integer maxUsers;
        
        /**
         * Maximum processing duration in minutes.
         * Job stops gracefully after this time.
         * Default: configured api-max-duration
         */
        private Integer maxDurationMinutes;
        
        /**
         * Delay between processing each user (milliseconds).
         * Used for throttling LLM API calls.
         * Default: configured processing-delay
         */
        private Long delayBetweenUsersMs;
        
        /**
         * If true, continues even when no users are pending.
         * Useful for continuous monitoring.
         * Default: false
         */
        private boolean continuous = false;
    }
    
    @Data
    @Builder
    public static class BatchProcessingResult {
        /**
         * Processing status: COMPLETED, FAILED
         */
        private String status;
        
        /**
         * Total users processed (including successes and errors).
         */
        private Integer processedCount;
        
        /**
         * Number of successfully analyzed users.
         */
        private Integer successCount;
        
        /**
         * Number of users that failed analysis.
         */
        private Integer errorCount;
        
        /**
         * Number of iterations that found no pending users.
         */
        private Integer skippedCount;
        
        /**
         * Total processing duration in milliseconds.
         */
        private Long durationMs;
        
        /**
         * Average time per user in milliseconds.
         */
        private Long averageTimePerUserMs;
        
        /**
         * When processing started.
         */
        private LocalDateTime startedAt;
        
        /**
         * When processing completed.
         */
        private LocalDateTime completedAt;
        
        /**
         * Error message (if status = FAILED).
         */
        private String error;
    }
    
    @Data
    public static class ContinuousProcessingRequest {
        /**
         * Number of users to process per batch/iteration.
         * Default: 100
         * Max: configured api-max-batch-size
         */
        private Integer usersPerBatch;
        
        /**
         * Interval between batches in minutes.
         * Default: 5 minutes
         */
        private Integer intervalMinutes;
        
        /**
         * Maximum number of iterations to run.
         * Set to null or 0 for infinite processing.
         * Default: null (infinite)
         */
        private Integer maxIterations;
    }
    
    @Data
    @Builder
    public static class ContinuousProcessingResponse {
        /**
         * Unique job identifier for tracking.
         */
        private String jobId;
        
        /**
         * Job status: STARTED, FAILED
         */
        private String status;
        
        /**
         * Human-readable message.
         */
        private String message;
        
        /**
         * Estimated total duration (if iterations are limited).
         */
        private String estimatedDuration;
    }
}
```

---

## 🎛️ JOB MANAGEMENT & CANCELLATION

### Overview

The processing system supports **job lifecycle management**:

1. **Continuous Jobs** - Start, cancel, check status
2. **Scheduled Processing** - Pause, resume, check status

### Job Tracking Architecture

```
┌─────────────────────────────────────────────────────────┐
│ JOB REGISTRY                                            │
├─────────────────────────────────────────────────────────┤
│ ConcurrentHashMap<jobId, Future<?>>                    │
│ ├─ Tracks running continuous jobs                      │
│ └─ Enables cancellation via Future.cancel()            │
│                                                         │
│ ConcurrentHashMap<jobId, ContinuousJobStatus>         │
│ ├─ Current iteration                                   │
│ ├─ Total processed                                     │
│ ├─ Status (RUNNING, COMPLETED, CANCELLED, FAILED)     │
│ └─ Start/end timestamps                                │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│ SCHEDULED PROCESSING CONTROL                            │
├─────────────────────────────────────────────────────────┤
│ volatile boolean scheduledProcessingPaused              │
│ ├─ true: Worker skips processing                       │
│ └─ false: Worker processes normally                    │
└─────────────────────────────────────────────────────────┘
```

---

### Enhanced Controller with Job Management

**Updated `BehaviorProcessingController.java`:**

```java
@Slf4j
@RestController
@RequestMapping("/api/behavior/processing")
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "ai.behavior.processing",
    name = "api-enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class BehaviorProcessingController {
    
    private final BehaviorAnalysisService analysisService;
    private final BehaviorProcessingProperties properties;
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    
    // Track running continuous jobs for cancellation
    private final ConcurrentHashMap<String, Future<?>> runningJobs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ContinuousJobStatus> jobStatuses = new ConcurrentHashMap<>();
    
    // Flag to pause/resume scheduled processing
    private volatile boolean scheduledProcessingPaused = false;
    
    // ... (existing endpoints: /users/{userId}, /batch)
    
    // ========================================
    // CONTINUOUS PROCESSING (Enhanced)
    // ========================================
    
    /**
     * Start continuous processing in background.
     * Returns jobId for tracking and cancellation.
     */
    @PostMapping("/continuous")
    public ResponseEntity<ContinuousProcessingResponse> startContinuousProcessing(
        @RequestBody ContinuousProcessingRequest request
    ) {
        log.info("API: Starting continuous processing: {}", request);
        
        String jobId = UUID.randomUUID().toString();
        
        // Initialize job status
        ContinuousJobStatus status = new ContinuousJobStatus();
        status.setJobId(jobId);
        status.setStatus("RUNNING");
        status.setStartedAt(LocalDateTime.now());
        status.setMaxIterations(request.getMaxIterations());
        jobStatuses.put(jobId, status);
        
        // Submit as async job and track Future for cancellation
        Future<?> future = executorService.submit(() -> {
            log.info("Continuous job {} started", jobId);
            
            int batchSize = request.getUsersPerBatch() != null
                ? Math.min(request.getUsersPerBatch(), properties.getApiMaxBatchSize())
                : 100;
            
            long intervalMs = request.getIntervalMinutes() != null
                ? Duration.ofMinutes(request.getIntervalMinutes()).toMillis()
                : Duration.ofMinutes(5).toMillis();
            
            int maxIterations = request.getMaxIterations() != null
                ? request.getMaxIterations()
                : Integer.MAX_VALUE;
            
            int totalProcessed = 0;
            
            try {
                for (int iteration = 0; iteration < maxIterations; iteration++) {
                    // Check if job was cancelled
                    if (Thread.currentThread().isInterrupted()) {
                        log.info("Continuous job {} cancelled at iteration {}", jobId, iteration);
                        status.setStatus("CANCELLED");
                        status.setCompletedAt(LocalDateTime.now());
                        break;
                    }
                    
                    // Update job status
                    status.setCurrentIteration(iteration + 1);
                    status.setTotalProcessed(totalProcessed);
                    
                    log.info("Continuous job {}: iteration {}/{}", jobId, iteration + 1, 
                        maxIterations == Integer.MAX_VALUE ? "∞" : maxIterations);
                    
                    // Process batch
                    BatchProcessingResult batchResult = executeBatchProcessing(
                        batchSize,
                        properties.getApiMaxDuration(),
                        properties.getProcessingDelay(),
                        true
                    );
                    
                    totalProcessed += batchResult.getProcessedCount();
                    status.setTotalProcessed(totalProcessed);
                    
                    // Wait before next iteration
                    if (iteration < maxIterations - 1) {
                        Thread.sleep(intervalMs);
                    }
                }
                
                // Job completed normally
                if (!status.getStatus().equals("CANCELLED")) {
                    status.setStatus("COMPLETED");
                    status.setCompletedAt(LocalDateTime.now());
                }
                
            } catch (InterruptedException e) {
                log.warn("Continuous job {} interrupted", jobId);
                status.setStatus("CANCELLED");
                status.setCompletedAt(LocalDateTime.now());
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Continuous job {} failed", jobId, e);
                status.setStatus("FAILED");
                status.setError(e.getMessage());
                status.setCompletedAt(LocalDateTime.now());
            } finally {
                runningJobs.remove(jobId);
            }
            
            log.info("Continuous job {} finished: status={}, total processed={}",
                jobId, status.getStatus(), totalProcessed);
        });
        
        // Track the Future for cancellation
        runningJobs.put(jobId, future);
        
        return ResponseEntity.accepted()
            .body(ContinuousProcessingResponse.builder()
                .jobId(jobId)
                .status("STARTED")
                .message("Continuous processing job submitted in background")
                .build());
    }
    
    // ========================================
    // JOB CANCELLATION
    // ========================================
    
    /**
     * Cancel a running continuous processing job.
     * 
     * @param jobId The job ID to cancel
     * @return Cancellation status
     */
    @DeleteMapping("/continuous/{jobId}")
    public ResponseEntity<JobCancellationResponse> cancelContinuousJob(@PathVariable String jobId) {
        log.info("API: Cancelling continuous job: {}", jobId);
        
        Future<?> future = runningJobs.get(jobId);
        ContinuousJobStatus status = jobStatuses.get(jobId);
        
        if (future == null && status == null) {
            return ResponseEntity.notFound().build();
        }
        
        if (status != null && !status.getStatus().equals("RUNNING")) {
            return ResponseEntity.ok(JobCancellationResponse.builder()
                .jobId(jobId)
                .success(false)
                .message("Job already " + status.getStatus())
                .currentStatus(status.getStatus())
                .build());
        }
        
        // Cancel the job
        boolean cancelled = false;
        if (future != null) {
            cancelled = future.cancel(true); // Interrupt if running
        }
        
        if (status != null) {
            status.setStatus("CANCELLED");
            status.setCompletedAt(LocalDateTime.now());
        }
        
        return ResponseEntity.ok(JobCancellationResponse.builder()
            .jobId(jobId)
            .success(cancelled)
            .message(cancelled ? "Job cancelled successfully" : "Job cancellation requested")
            .currentStatus("CANCELLED")
            .processedBeforeCancellation(status != null ? status.getTotalProcessed() : 0)
            .build());
    }
    
    /**
     * Get status of a continuous processing job.
     * 
     * @param jobId The job ID to check
     * @return Current job status
     */
    @GetMapping("/continuous/{jobId}/status")
    public ResponseEntity<ContinuousJobStatus> getContinuousJobStatus(@PathVariable String jobId) {
        ContinuousJobStatus status = jobStatuses.get(jobId);
        
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(status);
    }
    
    /**
     * List all continuous jobs (active and recent).
     * 
     * @return List of all tracked jobs
     */
    @GetMapping("/continuous/jobs")
    public ResponseEntity<List<ContinuousJobStatus>> listContinuousJobs() {
        return ResponseEntity.ok(
            jobStatuses.values().stream()
                .sorted((a, b) -> b.getStartedAt().compareTo(a.getStartedAt()))
                .toList()
        );
    }
    
    // ========================================
    // SCHEDULED PROCESSING CONTROL
    // ========================================
    
    /**
     * Pause scheduled background processing.
     * 
     * The worker will continue to run on schedule, but will skip processing.
     * Useful for maintenance windows or when you need to process manually.
     * 
     * @return Pause status
     */
    @PostMapping("/scheduled/pause")
    public ResponseEntity<ScheduledControlResponse> pauseScheduledProcessing() {
        log.info("API: Pausing scheduled processing");
        
        scheduledProcessingPaused = true;
        
        return ResponseEntity.ok(ScheduledControlResponse.builder()
            .action("PAUSED")
            .message("Scheduled processing paused. Worker will skip processing until resumed.")
            .paused(true)
            .timestamp(LocalDateTime.now())
            .build());
    }
    
    /**
     * Resume scheduled background processing.
     * 
     * Re-enables the worker to process users on schedule.
     * 
     * @return Resume status
     */
    @PostMapping("/scheduled/resume")
    public ResponseEntity<ScheduledControlResponse> resumeScheduledProcessing() {
        log.info("API: Resuming scheduled processing");
        
        scheduledProcessingPaused = false;
        
        return ResponseEntity.ok(ScheduledControlResponse.builder()
            .action("RESUMED")
            .message("Scheduled processing resumed. Worker will process on next schedule.")
            .paused(false)
            .timestamp(LocalDateTime.now())
            .build());
    }
    
    /**
     * Get scheduled processing status.
     * 
     * @return Current pause/resume state
     */
    @GetMapping("/scheduled/status")
    public ResponseEntity<ScheduledStatusResponse> getScheduledStatus() {
        return ResponseEntity.ok(ScheduledStatusResponse.builder()
            .enabled(properties.isScheduledEnabled())
            .paused(scheduledProcessingPaused)
            .scheduleCron(properties.getScheduleCron())
            .batchSize(properties.getScheduledBatchSize())
            .message(scheduledProcessingPaused 
                ? "Scheduled processing is PAUSED" 
                : "Scheduled processing is ACTIVE")
            .build());
    }
    
    /**
     * Check if scheduled processing is currently paused.
     * Used by BehaviorAnalysisWorker to skip processing.
     */
    public boolean isScheduledProcessingPaused() {
        return scheduledProcessingPaused;
    }
    
    // ... (existing DTOs and helper methods)
    
    // ========================================
    // NEW DTOs FOR JOB MANAGEMENT
    // ========================================
    
    @Data
    public static class ContinuousJobStatus {
        private String jobId;
        private String status;              // RUNNING, COMPLETED, CANCELLED, FAILED
        private Integer currentIteration;
        private Integer maxIterations;
        private Integer totalProcessed;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private String error;
    }
    
    @Data
    @Builder
    public static class JobCancellationResponse {
        private String jobId;
        private boolean success;
        private String message;
        private String currentStatus;
        private Integer processedBeforeCancellation;
    }
    
    @Data
    @Builder
    public static class ScheduledControlResponse {
        private String action;              // PAUSED, RESUMED
        private String message;
        private boolean paused;
        private LocalDateTime timestamp;
    }
    
    @Data
    @Builder
    public static class ScheduledStatusResponse {
        private boolean enabled;            // Is scheduled processing enabled in config?
        private boolean paused;             // Is it currently paused via API?
        private String scheduleCron;
        private Integer batchSize;
        private String message;
    }
}
```

---

### Enhanced Worker with Pause Check

**Updated `BehaviorAnalysisWorker.java`:**

```java
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "ai.behavior.processing",
    name = "scheduled-enabled",
    havingValue = "true"
)
public class BehaviorAnalysisWorker {
    
    private final BehaviorAnalysisService analysisService;
    private final BehaviorProcessingProperties properties;
    private final BehaviorProcessingController controller; // For pause check
    
    @Scheduled(cron = "${ai.behavior.processing.schedule-cron:0 */15 * * * *}")
    public void processUserBehaviors() {
        // Check if paused via API
        if (controller.isScheduledProcessingPaused()) {
            log.info("Scheduled processing is PAUSED, skipping this run");
            return;
        }
        
        log.info("Starting scheduled behavior analysis batch");
        
        // ... rest of processing logic
    }
}
```

---

## 📊 JOB MANAGEMENT API ENDPOINTS

### 1. Cancel Continuous Job

```bash
DELETE /api/behavior/processing/continuous/{jobId}
```

**Example:**
```bash
# Start a continuous job
RESPONSE=$(curl -X POST http://localhost:8080/api/behavior/processing/continuous \
  -H "Content-Type: application/json" \
  -d '{"usersPerBatch": 100, "intervalMinutes": 5}')

JOB_ID=$(echo $RESPONSE | jq -r '.jobId')

# Cancel it later
curl -X DELETE http://localhost:8080/api/behavior/processing/continuous/$JOB_ID
```

**Response:**
```json
{
  "jobId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "success": true,
  "message": "Job cancelled successfully",
  "currentStatus": "CANCELLED",
  "processedBeforeCancellation": 347
}
```

---

### 2. Check Continuous Job Status

```bash
GET /api/behavior/processing/continuous/{jobId}/status
```

**Response:**
```json
{
  "jobId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "status": "RUNNING",
  "currentIteration": 12,
  "maxIterations": 20,
  "totalProcessed": 1200,
  "startedAt": "2025-12-27T10:00:00",
  "completedAt": null,
  "error": null
}
```

---

### 3. List All Continuous Jobs

```bash
GET /api/behavior/processing/continuous/jobs
```

**Response:**
```json
[
  {
    "jobId": "job-1",
    "status": "RUNNING",
    "currentIteration": 5,
    "totalProcessed": 500,
    "startedAt": "2025-12-27T10:00:00"
  },
  {
    "jobId": "job-2",
    "status": "COMPLETED",
    "totalProcessed": 2000,
    "startedAt": "2025-12-27T09:00:00",
    "completedAt": "2025-12-27T09:45:00"
  }
]
```

---

### 4. Pause Scheduled Processing

```bash
POST /api/behavior/processing/scheduled/pause
```

**Response:**
```json
{
  "action": "PAUSED",
  "message": "Scheduled processing paused. Worker will skip processing until resumed.",
  "paused": true,
  "timestamp": "2025-12-27T10:30:00"
}
```

**Use cases:**
- Maintenance window
- Manual processing mode temporarily
- LLM provider issues/downtime
- Testing without background interference

---

### 5. Resume Scheduled Processing

```bash
POST /api/behavior/processing/scheduled/resume
```

**Response:**
```json
{
  "action": "RESUMED",
  "message": "Scheduled processing resumed. Worker will process on next schedule.",
  "paused": false,
  "timestamp": "2025-12-27T11:00:00"
}
```

---

### 6. Check Scheduled Processing Status

```bash
GET /api/behavior/processing/scheduled/status
```

**Response:**
```json
{
  "enabled": true,
  "paused": false,
  "scheduleCron": "0 */15 * * * *",
  "batchSize": 100,
  "message": "Scheduled processing is ACTIVE"
}
```

---

## 🔄 WORKFLOW EXAMPLES

### Scenario 1: Pause for Maintenance

```bash
# 1. Pause scheduled processing
curl -X POST http://localhost:8080/api/behavior/processing/scheduled/pause

# 2. Perform maintenance (upgrade, config changes, etc.)
# ...

# 3. Resume scheduled processing
curl -X POST http://localhost:8080/api/behavior/processing/scheduled/resume
```

---

### Scenario 2: Cancel Long-Running Migration

```bash
# 1. Start migration job
JOB_RESPONSE=$(curl -X POST http://localhost:8080/api/behavior/processing/continuous \
  -H "Content-Type: application/json" \
  -d '{
    "usersPerBatch": 500,
    "intervalMinutes": 1,
    "maxIterations": 200
  }')

JOB_ID=$(echo $JOB_RESPONSE | jq -r '.jobId')
echo "Started job: $JOB_ID"

# 2. Monitor progress
while true; do
  STATUS=$(curl -s http://localhost:8080/api/behavior/processing/continuous/$JOB_ID/status)
  PROCESSED=$(echo $STATUS | jq -r '.totalProcessed')
  echo "Processed: $PROCESSED users"
  sleep 30
  
  # Cancel if needed (e.g., error detected)
  if [ $PROCESSED -gt 10000 ]; then
    echo "Cancelling job..."
    curl -X DELETE http://localhost:8080/api/behavior/processing/continuous/$JOB_ID
    break
  fi
done
```

---

### Scenario 3: Switch from Scheduled to Manual

```bash
# 1. Pause scheduled processing
curl -X POST http://localhost:8080/api/behavior/processing/scheduled/pause

# 2. Process manually with custom config
curl -X POST http://localhost:8080/api/behavior/processing/batch \
  -H "Content-Type: application/json" \
  -d '{
    "maxUsers": 500,
    "delayBetweenUsersMs": 50
  }'

# 3. Resume scheduled when done
curl -X POST http://localhost:8080/api/behavior/processing/scheduled/resume
```

---

## 🧪 TESTING JOB MANAGEMENT

### Test: Cancel Continuous Job

```java
@Test
void cancelContinuousJob_stopsRunningJob() throws Exception {
    // Start a long-running job
    when(properties.getApiMaxBatchSize()).thenReturn(1000);
    
    String requestJson = """
        {
          "usersPerBatch": 100,
          "intervalMinutes": 1,
          "maxIterations": 100
        }
        """;
    
    MvcResult startResult = mockMvc.perform(post("/api/behavior/processing/continuous")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
        .andExpect(status().isAccepted())
        .andReturn();
    
    String responseBody = startResult.getResponse().getContentAsString();
    String jobId = objectMapper.readTree(responseBody).get("jobId").asText();
    
    // Wait a bit for job to start
    Thread.sleep(100);
    
    // Cancel the job
    mockMvc.perform(delete("/api/behavior/processing/continuous/" + jobId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.currentStatus").value("CANCELLED"));
    
    // Verify job status updated
    mockMvc.perform(get("/api/behavior/processing/continuous/" + jobId + "/status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
}
```

---

### Test: Pause/Resume Scheduled Processing

```java
@Test
void pauseScheduledProcessing_preventsWorkerExecution() throws Exception {
    // Pause scheduled processing
    mockMvc.perform(post("/api/behavior/processing/scheduled/pause"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paused").value(true));
    
    // Verify status
    mockMvc.perform(get("/api/behavior/processing/scheduled/status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paused").value(true));
    
    // Resume
    mockMvc.perform(post("/api/behavior/processing/scheduled/resume"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paused").value(false));
}
```

---

## 📝 CONFIGURATION FILE

### File 4: application-behavior-processing-example.yml

**Location:** `ai-infrastructure-module/ai-infrastructure-behavior/src/main/resources/application-behavior-processing-example.yml`

```yaml
# ============================================================
# Behavior Analysis Processing Configuration
# ============================================================
# Copy this to your application.yml and customize

ai:
  behavior:
    processing:
      
      # ==========================================
      # SCHEDULED PROCESSING (Background Worker)
      # ==========================================
      
      # Enable scheduled background processing
      # Set to true for production steady-state
      scheduled-enabled: true
      
      # Cron expression for scheduling
      # Examples:
      #   "0 */5 * * * *"   = Every 5 minutes
      #   "0 */15 * * * *"  = Every 15 minutes (default)
      #   "0 0 * * * *"     = Every hour
      #   "0 0 2 * * *"     = Daily at 2 AM
      #   "0 0 0 * * 0"     = Weekly on Sunday midnight
      schedule-cron: "0 */15 * * * *"
      
      # Number of users to process per scheduled batch
      # Higher = more users per run, but longer execution
      scheduled-batch-size: 100
      
      # Maximum processing time for scheduled job
      # Job stops gracefully after this duration
      scheduled-max-duration: 10m
      
      # ==========================================
      # API-TRIGGERED PROCESSING
      # ==========================================
      
      # Enable manual processing API endpoints
      # Set to true for dev/testing or hybrid mode
      api-enabled: true
      
      # Maximum users allowed in a single API request
      # Prevents abuse and resource exhaustion
      api-max-batch-size: 1000
      
      # Maximum duration for API-triggered processing
      # Prevents long-running requests from timing out
      api-max-duration: 30m
      
      # ==========================================
      # THROTTLING & PERFORMANCE
      # ==========================================
      
      # Delay between processing each user
      # Prevents overwhelming LLM API rate limits
      # 
      # Recommended values by provider:
      # - OpenAI GPT-4: 50-100ms (500-1000 users/min)
      # - Anthropic Claude: 100-200ms (300-600 users/min)
      # - Azure OpenAI: 100-150ms (400-600 users/min)
      # - Local ONNX: 10-50ms (1200-6000 users/min)
      processing-delay: 100ms
      
      # Thread pool size for concurrent processing
      # WARNING: Only increase if ExternalEventProvider is thread-safe
      # Default: 1 (sequential processing)
      thread-pool-size: 1

---

# ============================================================
# PROFILE: Aggressive Processing (High Volume)
# ============================================================

spring:
  config:
    activate:
      on-profile: behavior-aggressive

ai:
  behavior:
    processing:
      scheduled-enabled: true
      schedule-cron: "0 */5 * * * *"  # Every 5 minutes
      scheduled-batch-size: 200       # Large batches
      scheduled-max-duration: 15m
      processing-delay: 50ms          # Fast processing
      api-max-batch-size: 5000

---

# ============================================================
# PROFILE: Conservative Processing (Low Volume / Rate Limited)
# ============================================================

spring:
  config:
    activate:
      on-profile: behavior-conservative

ai:
  behavior:
    processing:
      scheduled-enabled: true
      schedule-cron: "0 0 * * * *"    # Every hour
      scheduled-batch-size: 50        # Small batches
      scheduled-max-duration: 10m
      processing-delay: 500ms         # Slow processing
      api-max-batch-size: 100

---

# ============================================================
# PROFILE: API-Only (No Background Processing)
# ============================================================

spring:
  config:
    activate:
      on-profile: behavior-api-only

ai:
  behavior:
    processing:
      scheduled-enabled: false        # Disable background worker
      api-enabled: true               # Only manual triggering
      api-max-batch-size: 1000

---

# ============================================================
# PROFILE: Migration Mode (Bulk Processing)
# ============================================================

spring:
  config:
    activate:
      on-profile: behavior-migration

ai:
  behavior:
    processing:
      scheduled-enabled: false        # Disable scheduled
      api-enabled: true
      api-max-batch-size: 10000       # Allow large batches
      api-max-duration: 120m          # 2 hours max
      processing-delay: 10ms          # Fast processing

---

# ============================================================
# PROFILE: Development (Manual Only)
# ============================================================

spring:
  config:
    activate:
      on-profile: behavior-dev

ai:
  behavior:
    processing:
      scheduled-enabled: false
      api-enabled: true
      processing-delay: 0ms           # No throttling in dev
```

---

## 🔧 AUTO-CONFIGURATION UPDATE

### File 5: BehaviorAIAutoConfiguration.java (Update)

**Location:** `ai-infrastructure-module/ai-infrastructure-behavior/src/main/java/com/ai/infrastructure/behavior/config/BehaviorAIAutoConfiguration.java`

**Add `@EnableScheduling` to enable scheduled processing:**

```java
package com.ai.infrastructure.behavior.config;

import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.DependsOn;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.scheduling.annotation.EnableScheduling;  // ADD THIS

@Slf4j
@AutoConfiguration
@ConditionalOnProperty(prefix = "ai.behavior", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@DependsOn("AIEntityConfigurationLoader")
@ComponentScan(basePackages = "com.ai.infrastructure.behavior")
@EntityScan(basePackages = "com.ai.infrastructure.behavior.entity")
@EnableScheduling  // ADD THIS LINE
public class BehaviorAIAutoConfiguration {
    
    private final AIEntityConfigurationLoader frameworkConfigLoader;
    
    @Value("${ai.behavior.mode:LIGHT}")
    private String mode;
    
    @PostConstruct
    public void registerBehaviorConfig() {
        String presetFile = "classpath:behavior-presets/behavior-ai-" + mode.toLowerCase() + ".yml";
        
        log.info("Registering Behavior Module preset configuration (mode: {})", mode);
        
        frameworkConfigLoader.loadConfigurationFromFile(presetFile, false);
        
        log.info("Behavior AI Addon ready (mode: {})", mode);
    }
}
```

---

## 🧪 TESTING STRATEGY

### Unit Tests for Worker

**File:** `BehaviorAnalysisWorkerTest.java`

```java
package com.ai.infrastructure.behavior.worker;

import com.ai.infrastructure.behavior.config.BehaviorProcessingProperties;
import com.ai.infrastructure.behavior.entity.BehaviorInsights;
import com.ai.infrastructure.behavior.service.BehaviorAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BehaviorAnalysisWorkerTest {
    
    @Mock
    private BehaviorAnalysisService analysisService;
    
    @Mock
    private BehaviorProcessingProperties properties;
    
    @InjectMocks
    private BehaviorAnalysisWorker worker;
    
    @BeforeEach
    void setup() {
        when(properties.getScheduledBatchSize()).thenReturn(10);
        when(properties.getScheduledMaxDuration()).thenReturn(Duration.ofMinutes(10));
        when(properties.getProcessingDelay()).thenReturn(Duration.ofMillis(10));
    }
    
    @Test
    void processUserBehaviors_processesConfiguredBatchSize() {
        // Mock successful processing
        when(analysisService.processNextUser()).thenReturn(
            BehaviorInsights.builder().userId(UUID.randomUUID()).build()
        );
        
        worker.processUserBehaviors();
        
        // Should call processNextUser 10 times (batch size)
        verify(analysisService, times(10)).processNextUser();
    }
    
    @Test
    void processUserBehaviors_stopsWhenNoMoreUsers() {
        // First 5 calls succeed, then no more users
        when(analysisService.processNextUser())
            .thenReturn(BehaviorInsights.builder().userId(UUID.randomUUID()).build())
            .thenReturn(BehaviorInsights.builder().userId(UUID.randomUUID()).build())
            .thenReturn(BehaviorInsights.builder().userId(UUID.randomUUID()).build())
            .thenReturn(BehaviorInsights.builder().userId(UUID.randomUUID()).build())
            .thenReturn(BehaviorInsights.builder().userId(UUID.randomUUID()).build())
            .thenReturn(null); // No more users
        
        worker.processUserBehaviors();
        
        // Should stop after 6 calls (5 successes + 1 null)
        verify(analysisService, times(6)).processNextUser();
    }
    
    @Test
    void processUserBehaviors_continuesOnError() {
        // Some users succeed, one fails
        when(analysisService.processNextUser())
            .thenReturn(BehaviorInsights.builder().userId(UUID.randomUUID()).build())
            .thenThrow(new RuntimeException("LLM timeout"))
            .thenReturn(BehaviorInsights.builder().userId(UUID.randomUUID()).build());
        
        worker.processUserBehaviors();
        
        // Should continue despite error
        verify(analysisService, times(10)).processNextUser();
    }
}
```

---

### Integration Tests for API Controller

**File:** `BehaviorProcessingControllerTest.java`

```java
package com.ai.infrastructure.behavior.api;

import com.ai.infrastructure.behavior.config.BehaviorProcessingProperties;
import com.ai.infrastructure.behavior.entity.BehaviorInsights;
import com.ai.infrastructure.behavior.model.BehaviorTrend;
import com.ai.infrastructure.behavior.model.SentimentLabel;
import com.ai.infrastructure.behavior.service.BehaviorAnalysisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BehaviorProcessingController.class)
class BehaviorProcessingControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private BehaviorAnalysisService analysisService;
    
    @MockBean
    private BehaviorProcessingProperties properties;
    
    @Test
    void analyzeUser_returnsBehaviorInsights() throws Exception {
        UUID userId = UUID.randomUUID();
        
        BehaviorInsights mockResult = BehaviorInsights.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .segment("Power User")
            .sentimentLabel(SentimentLabel.SATISFIED)
            .sentimentScore(0.7)
            .churnRisk(0.15)
            .trend(BehaviorTrend.STABLE)
            .analyzedAt(LocalDateTime.now())
            .build();
        
        when(analysisService.analyzeUser(userId)).thenReturn(mockResult);
        
        mockMvc.perform(post("/api/behavior/processing/users/" + userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(userId.toString()))
            .andExpect(jsonPath("$.segment").value("Power User"))
            .andExpect(jsonPath("$.sentimentLabel").value("SATISFIED"));
    }
    
    @Test
    void processBatch_withCustomConfig_returnsResult() throws Exception {
        when(properties.getApiMaxBatchSize()).thenReturn(1000);
        when(properties.getApiMaxDuration()).thenReturn(java.time.Duration.ofMinutes(30));
        when(properties.getProcessingDelay()).thenReturn(java.time.Duration.ofMillis(100));
        
        // Mock 5 successful processings
        when(analysisService.processNextUser())
            .thenReturn(BehaviorInsights.builder().userId(UUID.randomUUID()).build())
            .thenReturn(BehaviorInsights.builder().userId(UUID.randomUUID()).build())
            .thenReturn(BehaviorInsights.builder().userId(UUID.randomUUID()).build())
            .thenReturn(BehaviorInsights.builder().userId(UUID.randomUUID()).build())
            .thenReturn(BehaviorInsights.builder().userId(UUID.randomUUID()).build())
            .thenReturn(null); // No more users
        
        String requestJson = """
            {
              "maxUsers": 10,
              "maxDurationMinutes": 5
            }
            """;
        
        mockMvc.perform(post("/api/behavior/processing/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.processedCount").value(5))
            .andExpect(jsonPath("$.successCount").value(5));
    }
    
    @Test
    void startContinuousProcessing_returnsJobId() throws Exception {
        String requestJson = """
            {
              "usersPerBatch": 100,
              "intervalMinutes": 5,
              "maxIterations": 10
            }
            """;
        
        mockMvc.perform(post("/api/behavior/processing/continuous")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.jobId").exists())
            .andExpect(jsonPath("$.status").value("STARTED"));
    }
}
```

---

## 📚 USAGE EXAMPLES

### Example 1: Production Setup (Scheduled + API)

```yaml
# application.yml
ai:
  behavior:
    enabled: true
    processing:
      scheduled-enabled: true
      schedule-cron: "0 */15 * * * *"
      scheduled-batch-size: 100
      api-enabled: true
```

**Result:**
- ✅ Automatic processing every 15 minutes (100 users/batch)
- ✅ Manual API available for VIP users or emergencies

---

### Example 2: Development Setup (API Only)

```yaml
# application.yml
ai:
  behavior:
    enabled: true
    processing:
      scheduled-enabled: false
      api-enabled: true
```

**Usage:**
```bash
# Process specific test user
curl -X POST http://localhost:8080/api/behavior/processing/users/test-user-id

# Process next 20 users for testing
curl -X POST http://localhost:8080/api/behavior/processing/batch \
  -H "Content-Type: application/json" \
  -d '{"maxUsers": 20}'
```

---

### Example 3: Initial Migration (100K Users)

```yaml
# application.yml
ai:
  behavior:
    processing:
      scheduled-enabled: false
      api-enabled: true
      api-max-batch-size: 10000
      api-max-duration: 120m
      processing-delay: 10ms
```

**Option A: Large Batches**
```bash
# Process in 10 batches of 10K users each
for i in {1..10}; do
  echo "Processing batch $i/10..."
  curl -X POST http://localhost:8080/api/behavior/processing/batch \
    -H "Content-Type: application/json" \
    -d '{
      "maxUsers": 10000,
      "maxDurationMinutes": 60,
      "delayBetweenUsersMs": 10
    }'
  sleep 60
done
```

**Option B: Continuous Job**
```bash
# Single API call, processes in background
curl -X POST http://localhost:8080/api/behavior/processing/continuous \
  -H "Content-Type: application/json" \
  -d '{
    "usersPerBatch": 500,
    "intervalMinutes": 1,
    "maxIterations": 200
  }'

# Returns immediately with jobId
# Job processes 500 users/minute for 200 minutes (~100K users)
```

---

### Example 4: Catch-Up After Downtime

```bash
# Process users from last 24 hours
curl -X POST http://localhost:8080/api/behavior/processing/batch \
  -H "Content-Type: application/json" \
  -d '{
    "maxUsers": 5000,
    "maxDurationMinutes": 30,
    "delayBetweenUsersMs": 50
  }'
```

---

### Example 5: Rate-Limited LLM Provider

```yaml
# application.yml - Slow and steady
ai:
  behavior:
    processing:
      scheduled-enabled: true
      schedule-cron: "0 */30 * * * *"  # Every 30 minutes
      scheduled-batch-size: 50
      processing-delay: 1000ms          # 1 second between users
```

**Result:** ~50 users/30min = ~100 users/hour (gentle on API limits)

---

### Example 6: Job Management - Cancel Long-Running Job

```bash
# Start a continuous job for migration
JOB_RESPONSE=$(curl -X POST http://localhost:8080/api/behavior/processing/continuous \
  -H "Content-Type: application/json" \
  -d '{
    "usersPerBatch": 500,
    "intervalMinutes": 1,
    "maxIterations": 200
  }')

# Extract job ID
JOB_ID=$(echo $JOB_RESPONSE | jq -r '.jobId')
echo "Migration job started: $JOB_ID"

# Monitor progress
while true; do
  STATUS=$(curl -s http://localhost:8080/api/behavior/processing/continuous/$JOB_ID/status)
  CURRENT=$(echo $STATUS | jq -r '.currentIteration')
  TOTAL=$(echo $STATUS | jq -r '.totalProcessed')
  echo "Iteration: $CURRENT, Total processed: $TOTAL users"
  
  # Check if something went wrong
  if [ "$CURRENT" -gt 50 ] && [ "$TOTAL" -lt 1000 ]; then
    echo "ERROR: Too many iterations with too few results. Cancelling..."
    curl -X DELETE http://localhost:8080/api/behavior/processing/continuous/$JOB_ID
    break
  fi
  
  sleep 30
done

# Verify cancellation
curl http://localhost:8080/api/behavior/processing/continuous/$JOB_ID/status
# Should show: "status": "CANCELLED"
```

---

### Example 7: Pause Scheduled Processing for Maintenance

```bash
# Before maintenance window
echo "Pausing scheduled processing..."
curl -X POST http://localhost:8080/api/behavior/processing/scheduled/pause

# Check status
curl http://localhost:8080/api/behavior/processing/scheduled/status
# Shows: "paused": true

# Perform maintenance
echo "Performing database maintenance..."
# ... upgrade, backup, etc.

# Resume after maintenance
echo "Resuming scheduled processing..."
curl -X POST http://localhost:8080/api/behavior/processing/scheduled/resume

# Verify resumed
curl http://localhost:8080/api/behavior/processing/scheduled/status
# Shows: "paused": false
```

---

### Example 8: List and Monitor All Continuous Jobs

```bash
# List all running and completed jobs
curl http://localhost:8080/api/behavior/processing/continuous/jobs

# Example response:
# [
#   {
#     "jobId": "job-1",
#     "status": "RUNNING",
#     "currentIteration": 15,
#     "maxIterations": 100,
#     "totalProcessed": 1500,
#     "startedAt": "2025-12-27T10:00:00"
#   },
#   {
#     "jobId": "job-2",
#     "status": "COMPLETED",
#     "totalProcessed": 5000,
#     "startedAt": "2025-12-27T08:00:00",
#     "completedAt": "2025-12-27T08:45:00"
#   }
# ]

# Cancel all running jobs (if needed)
curl http://localhost:8080/api/behavior/processing/continuous/jobs | \
  jq -r '.[] | select(.status=="RUNNING") | .jobId' | \
  while read jobId; do
    echo "Cancelling job: $jobId"
    curl -X DELETE http://localhost:8080/api/behavior/processing/continuous/$jobId
  done
```

---

### Example 9: Temporary Switch to Manual Mode

```bash
# Production is running scheduled processing
# But you need to do manual batch for urgent users

# 1. Pause scheduled
curl -X POST http://localhost:8080/api/behavior/processing/scheduled/pause

# 2. Process urgent users manually with aggressive config
curl -X POST http://localhost:8080/api/behavior/processing/batch \
  -H "Content-Type: application/json" \
  -d '{
    "maxUsers": 1000,
    "maxDurationMinutes": 15,
    "delayBetweenUsersMs": 50
  }'

# 3. Resume scheduled processing
curl -X POST http://localhost:8080/api/behavior/processing/scheduled/resume
```

---

## 📊 MONITORING & OBSERVABILITY

### Logging Examples

```
# Scheduled worker starts
INFO  Starting scheduled behavior analysis batch

# Progress logging (every 10 users)
DEBUG Batch progress: 50/100 users processed

# Individual user processed
DEBUG Processed user 550e8400-...: trend=STABLE, sentiment=SATISFIED, churn=0.15

# Batch completed
INFO  Scheduled batch completed: processed=100, success=98, errors=2, duration=12543ms
```

### Metrics to Track

```java
// Add to BehaviorMetricsPublisher or similar
meterRegistry.counter("behavior.processing.scheduled.runs").increment();
meterRegistry.counter("behavior.processing.api.requests").increment();
meterRegistry.gauge("behavior.processing.batch.duration_ms", durationMs);
meterRegistry.counter("behavior.processing.users.processed", processedCount);
meterRegistry.counter("behavior.processing.users.errors", errorCount);
```

---

## ✅ IMPLEMENTATION CHECKLIST

### Phase 1: Configuration (30 minutes)
- [ ] Create `BehaviorProcessingProperties.java`
- [ ] Add `@ConfigurationProperties` binding
- [ ] Create `application-behavior-processing-example.yml`
- [ ] Update `BehaviorAIAutoConfiguration` with `@EnableScheduling`

### Phase 2: Scheduled Worker (45 minutes)
- [ ] Create `BehaviorAnalysisWorker.java`
- [ ] Add `@Scheduled` method
- [ ] Add pause check (via controller.isScheduledProcessingPaused())
- [ ] Implement batch processing loop
- [ ] Add max duration check
- [ ] Add throttling/delay
- [ ] Add error handling per user

### Phase 3: API Controller (90 minutes)
- [ ] Create `BehaviorProcessingController.java`
- [ ] Add job tracking infrastructure (ConcurrentHashMap)
- [ ] Implement `POST /users/{userId}` (single user)
- [ ] Implement `POST /batch` (flexible batch)
- [ ] Implement `POST /continuous` (background job with tracking)
- [ ] Implement `DELETE /continuous/{jobId}` (cancel job)
- [ ] Implement `GET /continuous/{jobId}/status` (job status)
- [ ] Implement `GET /continuous/jobs` (list all jobs)
- [ ] Implement `POST /scheduled/pause` (pause scheduled processing)
- [ ] Implement `POST /scheduled/resume` (resume scheduled processing)
- [ ] Implement `GET /scheduled/status` (check scheduled status)
- [ ] Create all request/response DTOs
- [ ] Add validation for request limits

### Phase 4: Testing (60 minutes)
- [ ] Unit tests for worker (batch size, error handling, stop conditions, pause check)
- [ ] Unit tests for controller (single user, batch, continuous)
- [ ] Unit tests for job cancellation
- [ ] Unit tests for pause/resume scheduled processing
- [ ] Integration test (scheduled processing)
- [ ] Integration test (API endpoints)
- [ ] Integration test (job lifecycle: start → status → cancel)

### Phase 5: Documentation (15 minutes)
- [ ] Add JavaDoc to all classes
- [ ] Create usage examples in README
- [ ] Document configuration properties

**Total Estimated Time: 3.5 hours**

---

## 🎯 SUCCESS CRITERIA

### Functional Requirements
- ✅ Scheduled worker processes users automatically (when enabled)
- ✅ API endpoints trigger processing on-demand
- ✅ Flexible batch parameters (maxUsers, maxDuration, delay)
- ✅ Continuous mode supports long-running jobs
- ✅ Graceful stopping on max duration
- ✅ Error handling doesn't fail entire batch

### Configuration Requirements
- ✅ All processing behavior controlled via YAML
- ✅ Can disable scheduled worker
- ✅ Can disable API endpoints
- ✅ Supports multiple profiles (dev, prod, migration)

### Performance Requirements
- ✅ Throttling prevents LLM API overload
- ✅ Max duration prevents runaway jobs
- ✅ Batch size limits prevent resource exhaustion

---

## 🚀 DEPLOYMENT CHECKLIST

### Pre-Deployment
- [ ] Choose processing mode (scheduled, API, or both)
- [ ] Configure cron schedule for your use case
- [ ] Set appropriate batch sizes
- [ ] Configure throttling based on LLM provider limits
- [ ] Test in staging with production-like data volume

### Deployment
- [ ] Add configuration to `application.yml`
- [ ] Deploy updated artifacts
- [ ] Verify worker starts (if enabled)
- [ ] Test API endpoints (if enabled)
- [ ] Monitor logs for processing activity

### Post-Deployment
- [ ] Verify users are being analyzed
- [ ] Check processing metrics
- [ ] Monitor error rates
- [ ] Adjust throttling if needed
- [ ] Tune batch size based on performance

---

## 🎓 APPENDIX

### A. Cron Expression Quick Reference

```
┌───────────── minute (0 - 59)
│ ┌───────────── hour (0 - 23)
│ │ ┌───────────── day of month (1 - 31)
│ │ │ ┌───────────── month (1 - 12)
│ │ │ │ ┌───────────── day of week (0 - 6) (Sunday=0)
│ │ │ │ │
│ │ │ │ │
* * * * * *

Examples:
"0 */5 * * * *"    = Every 5 minutes
"0 */15 * * * *"   = Every 15 minutes
"0 0 * * * *"      = Every hour at :00
"0 0 2 * * *"      = Daily at 2:00 AM
"0 0 0 * * 0"      = Weekly on Sunday at midnight
"0 0 0 1 * *"      = Monthly on the 1st
```

### B. Throttling Calculation

```
LLM Provider Rate Limits:
├─ OpenAI GPT-4: 10,000 tokens/min ≈ 60 requests/min → 1000ms delay
├─ Anthropic Claude: 5,000 tokens/min ≈ 40 requests/min → 1500ms delay
├─ Azure OpenAI: Variable (check your quota)
└─ Local ONNX: Unlimited → 0-10ms delay

Formula:
delay_ms = (60,000 / max_requests_per_minute)

Examples:
- 60 req/min → 1000ms delay
- 120 req/min → 500ms delay
- 600 req/min → 100ms delay (default)
```

### C. Batch Size vs. Duration

```
Scenario: Process 1000 users

Option 1: Single large batch
├─ maxUsers: 1000
├─ delay: 100ms
└─ Time: 1000 × 100ms = 100 seconds + processing time ≈ 2-3 minutes

Option 2: Multiple small batches
├─ 10 batches of 100 users
├─ delay: 100ms
└─ Time: 10 × (100 × 100ms) = 16 minutes + processing ≈ 20 minutes

Recommendation: Use continuous mode for large volumes
├─ usersPerBatch: 100
├─ intervalMinutes: 1
└─ Processes in background without blocking
```

### D. Job Cancellation & Control

```
CONTINUOUS JOBS:
├─ Start → Returns jobId
├─ Monitor → GET /continuous/{jobId}/status
├─ Cancel → DELETE /continuous/{jobId}
└─ Status persists until job removed from memory

SCHEDULED PROCESSING:
├─ Pause → POST /scheduled/pause (worker skips processing)
├─ Resume → POST /scheduled/resume (worker continues)
├─ Status → GET /scheduled/status
└─ Pause is immediate, Resume takes effect on next schedule

JOB LIFECYCLE:
Continuous Job States:
├─ RUNNING → Job is actively processing
├─ COMPLETED → All iterations finished successfully
├─ CANCELLED → User cancelled via API
└─ FAILED → Job encountered fatal error

Scheduled Processing States:
├─ ACTIVE → enabled=true, paused=false (processing normally)
├─ PAUSED → enabled=true, paused=true (skipping processing)
└─ DISABLED → enabled=false (worker not running)
```

### E. Error Recovery

```
Scheduled Worker:
├─ Error in one user → Log error, continue with next
├─ Fatal error → Log and exit, retry in next scheduled run
├─ Paused via API → Skip processing, wait for resume
└─ Max duration exceeded → Stop gracefully, resume in next run

API Batch:
├─ Error in one user → Log error, continue with next
├─ Fatal error → Return error response with partial results
└─ Max duration exceeded → Return success with actual count

Continuous Job:
├─ Error in iteration → Log error, continue to next iteration
├─ Cancelled via API → Stop gracefully, update status to CANCELLED
├─ Interrupted (Thread.interrupt()) → Stop gracefully, log total processed
└─ Job completes all iterations → Status COMPLETED
```

---

## 📂 FILE STRUCTURE

```
ai-infrastructure-behavior/
└── src/
    └── main/
        ├── java/com/ai/infrastructure/behavior/
        │   ├── config/
        │   │   ├── BehaviorProcessingProperties.java     ← NEW
        │   │   └── BehaviorAIAutoConfiguration.java      ← UPDATE (add @EnableScheduling)
        │   │
        │   ├── worker/
        │   │   └── BehaviorAnalysisWorker.java           ← NEW
        │   │
        │   └── api/
        │       └── BehaviorProcessingController.java     ← NEW
        │
        └── resources/
            └── application-behavior-processing-example.yml ← NEW
```

---

## 📡 COMPLETE API REFERENCE

### Processing Endpoints

| Endpoint | Method | Purpose | Request Body | Response |
|----------|--------|---------|--------------|----------|
| `/users/{userId}` | POST | Analyze specific user | None | `BehaviorInsights` |
| `/batch` | POST | Flexible batch processing | `BatchProcessingRequest` | `BatchProcessingResult` |
| `/continuous` | POST | Start background job | `ContinuousProcessingRequest` | `ContinuousProcessingResponse` |

### Job Management Endpoints (NEW)

| Endpoint | Method | Purpose | Response |
|----------|--------|---------|----------|
| `/continuous/{jobId}` | DELETE | Cancel running job | `JobCancellationResponse` |
| `/continuous/{jobId}/status` | GET | Get job status | `ContinuousJobStatus` |
| `/continuous/jobs` | GET | List all jobs | `List<ContinuousJobStatus>` |
| `/scheduled/pause` | POST | Pause scheduled processing | `ScheduledControlResponse` |
| `/scheduled/resume` | POST | Resume scheduled processing | `ScheduledControlResponse` |
| `/scheduled/status` | GET | Get scheduled status | `ScheduledStatusResponse` |

### Request DTOs

```java
// Batch processing
{
  "maxUsers": 200,                   // Max users to process
  "maxDurationMinutes": 10,          // Max time to run
  "delayBetweenUsersMs": 100,        // Throttling delay
  "continuous": false                // Stop when no users pending
}

// Continuous processing
{
  "usersPerBatch": 100,              // Users per iteration
  "intervalMinutes": 5,              // Time between iterations
  "maxIterations": 20                // Stop after N iterations (null = infinite)
}
```

### Response DTOs

```java
// Job cancellation
{
  "jobId": "uuid",
  "success": true,
  "message": "Job cancelled successfully",
  "currentStatus": "CANCELLED",
  "processedBeforeCancellation": 347
}

// Job status
{
  "jobId": "uuid",
  "status": "RUNNING",               // RUNNING, COMPLETED, CANCELLED, FAILED
  "currentIteration": 12,
  "maxIterations": 20,
  "totalProcessed": 1200,
  "startedAt": "2025-12-27T10:00:00",
  "completedAt": null
}

// Scheduled control
{
  "action": "PAUSED",                // PAUSED, RESUMED
  "message": "Scheduled processing paused",
  "paused": true,
  "timestamp": "2025-12-27T10:30:00"
}

// Scheduled status
{
  "enabled": true,                   // Config: scheduled-enabled
  "paused": false,                   // Runtime: API pause state
  "scheduleCron": "0 */15 * * * *",
  "batchSize": 100,
  "message": "Scheduled processing is ACTIVE"
}
```

---

## 🎯 QUICK START (For Implementation Session)

### Step 1: Create Configuration Properties
```bash
# Copy code from this document:
# Section: "CONFIGURATION PROPERTIES" → BehaviorProcessingProperties.java
```

### Step 2: Create Scheduled Worker
```bash
# Copy code from this document:
# Section: "SCHEDULED WORKER IMPLEMENTATION" → BehaviorAnalysisWorker.java
```

### Step 3: Create API Controller
```bash
# Copy code from this document:
# Section: "API CONTROLLER IMPLEMENTATION" → BehaviorProcessingController.java
```

### Step 4: Update Auto-Configuration
```bash
# Add @EnableScheduling to BehaviorAIAutoConfiguration.java
```

### Step 5: Add Configuration Example
```bash
# Copy configuration from this document to application.yml
```

### Step 6: Test
```bash
# Enable scheduled processing
ai.behavior.processing.scheduled-enabled: true

# Or test via API
curl -X POST http://localhost:8080/api/behavior/processing/batch \
  -d '{"maxUsers": 10}'
```

---

**Document Version:** 2.0.0 (Enhanced with Job Management)  
**Last Updated:** 2025-12-27  
**Status:** ✅ Ready for Implementation  
**Estimated Time:** 3.5 hours  
**Dependencies:** BehaviorAnalysisService (must exist)  
**Author:** AI Infrastructure Team

---

## 🎯 NEW IN v2.0.0

### Job Management & Cancellation Support

1. **Continuous Job Cancellation**
   - ✅ Cancel running background jobs via API
   - ✅ Track job status in real-time
   - ✅ List all active and completed jobs

2. **Scheduled Processing Control**
   - ✅ Pause scheduled processing (for maintenance)
   - ✅ Resume scheduled processing
   - ✅ Check pause/resume status

3. **Enhanced Monitoring**
   - ✅ Job tracking with ConcurrentHashMap
   - ✅ Status updates during execution
   - ✅ Graceful cancellation (no data loss)

### Complete API Surface

**Processing:**
- `POST /users/{userId}` - Single user analysis
- `POST /batch` - Flexible batch processing
- `POST /continuous` - Start background job

**Job Management:**
- `DELETE /continuous/{jobId}` - Cancel job
- `GET /continuous/{jobId}/status` - Job status
- `GET /continuous/jobs` - List all jobs

**Scheduled Control:**
- `POST /scheduled/pause` - Pause worker
- `POST /scheduled/resume` - Resume worker
- `GET /scheduled/status` - Check status

---

**NOTE FOR IMPLEMENTATION SESSION:**
This document contains COMPLETE, COPY-PASTE-READY code for all components.
All code includes job cancellation and scheduled pause/resume capabilities.
Start with Phase 1 and work through the checklist sequentially.

