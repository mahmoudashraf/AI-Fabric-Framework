package com.ai.infrastructure.behavior.api;

import com.ai.infrastructure.behavior.config.BehaviorProcessingProperties;
import com.ai.infrastructure.behavior.entity.BehaviorInsights;
import com.ai.infrastructure.behavior.service.BehaviorAnalysisService;
import com.ai.infrastructure.behavior.state.BehaviorProcessingState;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;

@Slf4j
@RestController
@RequestMapping("/api/behavior/processing")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai.behavior.processing", name = "api-enabled", havingValue = "true", matchIfMissing = true)
public class BehaviorProcessingController {

    private final BehaviorAnalysisService analysisService;
    private final BehaviorProcessingProperties properties;
    private final BehaviorProcessingState processingState;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ConcurrentHashMap<String, Future<?>> runningJobs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ContinuousJobStatus> jobStatuses = new ConcurrentHashMap<>();

    @PostMapping("/users/{userId}")
    public ResponseEntity<BehaviorInsights> analyzeUser(@PathVariable UUID userId) {
        try {
            BehaviorInsights result = analysisService.analyzeUser(userId);
            if (result == null) {
                return ResponseEntity.noContent().build();
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("API analyze user failed: {}", userId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/batch")
    public ResponseEntity<BatchProcessingResult> processBatch(@RequestBody(required = false) BatchProcessingRequest request) {
        if (request == null) {
            request = new BatchProcessingRequest();
        }
        int maxUsers = request.getMaxUsers() != null ? request.getMaxUsers() : properties.getScheduledBatchSize();
        maxUsers = Math.min(maxUsers, properties.getApiMaxBatchSize());
        Duration maxDuration = request.getMaxDurationMinutes() != null
            ? Duration.ofMinutes(request.getMaxDurationMinutes())
            : properties.getApiMaxDuration();
        Duration delay = request.getDelayBetweenUsersMs() != null
            ? Duration.ofMillis(request.getDelayBetweenUsersMs())
            : properties.getProcessingDelay();

        BatchProcessingResult result = executeBatchProcessing(maxUsers, maxDuration, delay, false);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/continuous")
    public ResponseEntity<ContinuousProcessingResponse> startContinuous(@RequestBody ContinuousProcessingRequest request) {
        String jobId = UUID.randomUUID().toString();
        ContinuousJobStatus status = new ContinuousJobStatus();
        status.setJobId(jobId);
        status.setStatus("RUNNING");
        status.setStartedAt(LocalDateTime.now());
        jobStatuses.put(jobId, status);

        final int usersPerBatch = Math.min(
            request.getUsersPerBatch() != null ? request.getUsersPerBatch() : properties.getContinuousUsersPerBatch(),
            properties.getApiMaxBatchSize()
        );
        final Duration interval = request.getIntervalMinutes() != null
            ? Duration.ofMinutes(request.getIntervalMinutes())
            : properties.getContinuousInterval();
        final int maxIterations = request.getMaxIterations() != null ? request.getMaxIterations() : Integer.MAX_VALUE;

        Future<?> future = executor.submit(() -> {
            int totalProcessed = 0;
            try {
                for (int i = 0; i < maxIterations; i++) {
                    if (Thread.currentThread().isInterrupted()) {
                        status.setStatus("CANCELLED");
                        break;
                    }
                    status.setCurrentIteration(i + 1);
                    BatchProcessingResult res = executeBatchProcessing(
                        usersPerBatch,
                        properties.getApiMaxDuration(),
                        properties.getProcessingDelay(),
                        true
                    );
                    totalProcessed += res.getProcessedCount();
                    status.setTotalProcessed(totalProcessed);
                    if (i < maxIterations - 1) {
                        Thread.sleep(interval.toMillis());
                    }
                }
                if (!"CANCELLED".equals(status.getStatus())) {
                    status.setStatus("COMPLETED");
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                status.setStatus("CANCELLED");
            } catch (Exception e) {
                log.error("Continuous job {} failed", jobId, e);
                status.setStatus("FAILED");
                status.setError(e.getMessage());
            } finally {
                status.setCompletedAt(LocalDateTime.now());
                runningJobs.remove(jobId);
            }
        });

        runningJobs.put(jobId, future);

        return ResponseEntity.ok(ContinuousProcessingResponse.builder()
            .jobId(jobId)
            .status("RUNNING")
            .build());
    }

    @PostMapping("/continuous/{jobId}/cancel")
    public ResponseEntity<ContinuousProcessingResponse> cancelContinuous(@PathVariable String jobId) {
        Future<?> future = runningJobs.remove(jobId);
        ContinuousJobStatus status = jobStatuses.get(jobId);
        if (future == null || status == null) {
            return ResponseEntity.notFound().build();
        }
        future.cancel(true);
        status.setStatus("CANCELLED");
        status.setCompletedAt(LocalDateTime.now());
        return ResponseEntity.ok(ContinuousProcessingResponse.builder()
            .jobId(jobId)
            .status("CANCELLED")
            .build());
    }

    @PostMapping("/scheduled/pause")
    public ResponseEntity<ScheduledControlResponse> pauseScheduledProcessing() {
        processingState.setScheduledPaused(true);
        return ResponseEntity.ok(ScheduledControlResponse.builder()
            .message("Scheduled processing paused. Worker will skip processing until resumed.")
            .paused(true)
            .build());
    }

    @PostMapping("/scheduled/resume")
    public ResponseEntity<ScheduledControlResponse> resumeScheduledProcessing() {
        processingState.setScheduledPaused(false);
        return ResponseEntity.ok(ScheduledControlResponse.builder()
            .message("Scheduled processing resumed.")
            .paused(false)
            .build());
    }

    private BatchProcessingResult executeBatchProcessing(int maxUsers, Duration maxDuration, Duration delay, boolean suppressLogs) {
        Instant start = Instant.now();
        int processed = 0;
        int success = 0;
        int errors = 0;

        for (int i = 0; i < maxUsers; i++) {
            if (Duration.between(start, Instant.now()).compareTo(maxDuration) > 0) {
                break;
            }
            try {
                BehaviorInsights insight = analysisService.processNextUser();
                if (insight == null) {
                    break;
                }
                processed++;
                success++;
                if (delay.toMillis() > 0 && i < maxUsers - 1) {
                    Thread.sleep(delay.toMillis());
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                errors++;
                if (!suppressLogs) {
                    log.error("Error during batch processing", e);
                }
            }
        }

        Duration duration = Duration.between(start, Instant.now());
        return BatchProcessingResult.builder()
            .processedCount(processed)
            .successCount(success)
            .errorCount(errors)
            .durationMs(duration.toMillis())
            .build();
    }

    @Data
    public static class BatchProcessingRequest {
        private Integer maxUsers;
        private Integer maxDurationMinutes;
        private Long delayBetweenUsersMs;
    }

    @Data
    @Builder
    public static class BatchProcessingResult {
        private int processedCount;
        private int successCount;
        private int errorCount;
        private long durationMs;
    }

    @Data
    public static class ContinuousProcessingRequest {
        private Integer usersPerBatch;
        private Integer intervalMinutes;
        private Integer maxIterations;
    }

    @Data
    @Builder
    public static class ContinuousProcessingResponse {
        private String jobId;
        private String status;
    }

    @Data
    @Builder
    public static class ScheduledControlResponse {
        private String message;
        private boolean paused;
    }

    @Data
    public static class ContinuousJobStatus {
        private String jobId;
        private String status;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private Integer currentIteration;
        private Integer maxIterations;
        private Integer totalProcessed;
        private String error;
    }
}
