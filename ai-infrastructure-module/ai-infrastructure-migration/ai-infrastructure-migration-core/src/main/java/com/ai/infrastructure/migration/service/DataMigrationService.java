package com.ai.infrastructure.migration.service;

import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import com.ai.infrastructure.config.AIIndexingProperties;
import com.ai.infrastructure.dto.AIEntityConfig;
import com.ai.infrastructure.indexing.IndexingActionPlan;
import com.ai.infrastructure.indexing.IndexingOperation;
import com.ai.infrastructure.indexing.IndexingRequest;
import com.ai.infrastructure.indexing.IndexingStrategy;
import com.ai.infrastructure.indexing.queue.IndexingQueueService;
import com.ai.infrastructure.migration.config.MigrationProperties;
import com.ai.infrastructure.migration.domain.MigrationFilters;
import com.ai.infrastructure.migration.domain.MigrationJob;
import com.ai.infrastructure.migration.domain.MigrationProgress;
import com.ai.infrastructure.migration.domain.MigrationRequest;
import com.ai.infrastructure.migration.domain.MigrationStatus;
import com.ai.infrastructure.migration.repository.MigrationJobRepository;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.ai.infrastructure.service.AICapabilityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

@Slf4j
@Service
public class DataMigrationService {

    private final IndexingQueueService queueService;
    private final AIEntityConfigurationLoader configLoader;
    private final EntityRepositoryRegistry repositoryRegistry;
    private final MigrationJobRepository jobRepository;
    private final AISearchableEntityRepository searchableEntityRepository;
    private final MigrationProgressTracker progressTracker;
    private final MigrationProperties migrationProperties;
    private final AIIndexingProperties indexingProperties;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;
    private final AICapabilityService capabilityService;
    private final Clock clock;

    public DataMigrationService(
        IndexingQueueService queueService,
        AIEntityConfigurationLoader configLoader,
        EntityRepositoryRegistry repositoryRegistry,
        MigrationJobRepository jobRepository,
        AISearchableEntityRepository searchableEntityRepository,
        MigrationProgressTracker progressTracker,
        MigrationProperties migrationProperties,
        AIIndexingProperties indexingProperties,
        ObjectMapper objectMapper,
        ExecutorService executorService,
        AICapabilityService capabilityService,
        Clock clock
    ) {
        this.queueService = queueService;
        this.configLoader = configLoader;
        this.repositoryRegistry = repositoryRegistry;
        this.jobRepository = jobRepository;
        this.searchableEntityRepository = searchableEntityRepository;
        this.progressTracker = progressTracker;
        this.migrationProperties = migrationProperties;
        this.indexingProperties = indexingProperties;
        this.objectMapper = objectMapper;
        this.executorService = executorService;
        this.capabilityService = capabilityService;
        this.clock = clock;
    }

    /**
     * Convenience API for migrating all entities with defaults.
     */
    public MigrationJob indexAllEntities(String entityType) {
        MigrationRequest request = MigrationRequest.builder()
            .entityType(entityType)
            .batchSize(migrationProperties.getDefaultBatchSize())
            .rateLimit(migrationProperties.getDefaultRateLimit())
            .reindexExisting(false)
            .build();
        return startMigration(request);
    }

    /**
     * Starts a migration job asynchronously.
     */
    public MigrationJob startMigration(@Valid MigrationRequest request) {
        AIEntityConfig config = configLoader.getEntityConfig(request.getEntityType());
        if (config == null) {
            throw new IllegalArgumentException("No ai-entity-config entry found for entity type: " + request.getEntityType());
        }

        EntityRegistration registration = repositoryRegistry.getRegistration(request.getEntityType());
        JpaRepository<?, ?> repository = registration.repository();

        long total = repository.count();
        MigrationJob job = createJob(request, total);
        jobRepository.save(job);

        executorService.submit(() -> processJob(job.getId(), request, registration, config));
        return job;
    }

    public MigrationProgress getProgress(String jobId) {
        MigrationJob job = jobRepository.findById(jobId)
            .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        return progressTracker.toProgress(job);
    }

    @Transactional
    public void pauseMigration(String jobId) {
        updateStatus(jobId, MigrationStatus.PAUSED);
    }

    @Transactional
    public void resumeMigration(String jobId) {
        MigrationJob job = updateStatus(jobId, MigrationStatus.RUNNING);
        EntityRegistration registration = repositoryRegistry.getRegistration(job.getEntityType());
        AIEntityConfig config = configLoader.getEntityConfig(job.getEntityType());
        MigrationRequest resumeRequest = MigrationRequest.builder()
            .entityType(job.getEntityType())
            .batchSize(job.getBatchSize())
            .rateLimit(job.getRateLimit())
            .reindexExisting(job.getReindexExisting())
            .filters(job.getFilters())
            .createdBy(job.getCreatedBy())
            .build();
        executorService.submit(() -> processJob(job.getId(), resumeRequest, registration, config));
    }

    @Transactional
    public void cancelMigration(String jobId) {
        MigrationJob job = updateStatus(jobId, MigrationStatus.CANCELLED);
        job.setCompletedAt(LocalDateTime.now(clock));
        jobRepository.save(job);
    }

    public Iterable<MigrationJob> listJobs() {
        return jobRepository.findAll();
    }

    private MigrationJob createJob(MigrationRequest request, long totalCount) {
        LocalDateTime now = LocalDateTime.now(clock);
        return MigrationJob.builder()
            .id("mig-" + UUID.randomUUID())
            .entityType(request.getEntityType())
            .status(MigrationStatus.RUNNING)
            .totalEntities(totalCount)
            .processedEntities(0L)
            .failedEntities(0L)
            .currentPage(0)
            .batchSize(defaultBatchSize(request))
            .rateLimit(request.getRateLimit())
            .reindexExisting(Boolean.TRUE.equals(request.getReindexExisting()))
            .filters(request.getFilters())
            .createdBy(request.getCreatedBy())
            .startedAt(now)
            .lastUpdatedAt(now)
            .build();
    }

    private Integer defaultBatchSize(MigrationRequest request) {
        if (request.getBatchSize() != null && request.getBatchSize() > 0) {
            return request.getBatchSize();
        }
        return migrationProperties.getDefaultBatchSize();
    }

    private void processJob(String jobId, MigrationRequest request, EntityRegistration registration, AIEntityConfig config) {
        try {
            while (true) {
                MigrationJob job = jobRepository.findById(jobId)
                    .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

                if (job.isPaused()) {
                    log.info("Migration job {} paused at page {}", jobId, job.getCurrentPage());
                    return;
                }
                if (job.isCancelled()) {
                    log.info("Migration job {} cancelled", jobId);
                    return;
                }

                Page<?> page = registration.repository()
                    .findAll(PageRequest.of(job.getCurrentPage(), job.getBatchSize()));
                if (page.isEmpty()) {
                    markCompleted(job);
                    return;
                }

                int successes = 0;
                int failures = 0;

                for (Object entity : page.getContent()) {
                    try {
                        if (!matchesFilters(entity, request.getFilters())) {
                            continue;
                        }

                        String entityId = capabilityService.resolveEntityId(entity);
                        if (entityId == null || entityId.isBlank()) {
                            log.debug("Skipping entity without resolvable id: {}", entity.getClass().getSimpleName());
                            continue;
                        }

                        if (!Boolean.TRUE.equals(request.getReindexExisting())
                            && alreadyIndexed(config.getEntityType(), entityId)) {
                            continue;
                        }

                        enqueueForIndexing(entity, config);
                        successes++;
                    } catch (Exception ex) {
                        log.warn("Failed to enqueue entity for migration", ex);
                        failures++;
                    }
                }

                job.setProcessedEntities(job.getProcessedEntities() + successes);
                job.setFailedEntities(job.getFailedEntities() + failures);
                job.setCurrentPage(job.getCurrentPage() + 1);
                job.setLastUpdatedAt(LocalDateTime.now(clock));
                jobRepository.save(job);

                applyRateLimit(job);
            }
        } catch (Exception ex) {
            log.error("Migration job {} failed", jobId, ex);
            MigrationJob job = jobRepository.findById(jobId).orElse(null);
            if (job != null) {
                job.setStatus(MigrationStatus.FAILED);
                job.setErrorMessage(ex.getMessage());
                job.setLastUpdatedAt(LocalDateTime.now(clock));
                jobRepository.save(job);
            }
        }
    }

    private void markCompleted(MigrationJob job) {
        job.setStatus(MigrationStatus.COMPLETED);
        job.setCompletedAt(LocalDateTime.now(clock));
        job.setLastUpdatedAt(job.getCompletedAt());
        jobRepository.save(job);
    }

    private boolean matchesFilters(Object entity, MigrationFilters filters) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }

        String entityId = capabilityService.resolveEntityId(entity);
        if (!filters.safeEntityIds().isEmpty() && !filters.safeEntityIds().contains(entityId)) {
            return false;
        }

        LocalDate createdDate = resolveCreatedDate(entity);
        if (filters.getCreatedBefore() != null && createdDate != null && !createdDate.isBefore(filters.getCreatedBefore())) {
            return false;
        }
        if (filters.getCreatedAfter() != null && createdDate != null && !createdDate.isAfter(filters.getCreatedAfter())) {
            return false;
        }
        return true;
    }

    private LocalDate resolveCreatedDate(Object entity) {
        try {
            Field field = findDateField(entity.getClass(), "createdAt", "createdDate");
            if (field == null) {
                return null;
            }
            field.setAccessible(true);
            Object value = field.get(entity);
            if (value instanceof LocalDateTime dateTime) {
                return dateTime.toLocalDate();
            }
            if (value instanceof LocalDate date) {
                return date;
            }
        } catch (Exception e) {
            log.debug("Unable to resolve created date for {}", entity.getClass().getSimpleName());
        }
        return null;
    }

    private Field findDateField(Class<?> clazz, String... fieldNames) {
        for (String name : fieldNames) {
            try {
                return clazz.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    private boolean alreadyIndexed(String entityType, String entityId) {
        return searchableEntityRepository
            .findByEntityTypeAndEntityId(entityType, entityId)
            .isPresent();
    }

    private void enqueueForIndexing(Object entity, AIEntityConfig config) throws Exception {
        String entityId = capabilityService.resolveEntityId(entity);
        String payload = objectMapper.writeValueAsString(entity);

        IndexingRequest request = IndexingRequest.builder()
            .entityType(config.getEntityType())
            .entityId(entityId)
            .entityClassName(entity.getClass().getName())
            .operation(IndexingOperation.CREATE)
            .strategy(IndexingStrategy.ASYNC)
            .actionPlan(new IndexingActionPlan(true, true, false, false, false))
            .payload(payload)
            .maxRetries(indexingProperties.getQueue().getMaxRetries())
            .build();

        queueService.enqueue(request);
    }

    private void applyRateLimit(MigrationJob job) {
        Integer limit = job.getRateLimit() != null ? job.getRateLimit() : migrationProperties.getDefaultRateLimit();
        if (limit == null || limit <= 0) {
            return;
        }
        long delayMs = Math.max(1, (60_000L / limit));
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private MigrationJob updateStatus(String jobId, MigrationStatus status) {
        MigrationJob job = jobRepository.findById(jobId)
            .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        job.setStatus(status);
        job.setLastUpdatedAt(LocalDateTime.now(clock));
        return jobRepository.save(job);
    }
}
