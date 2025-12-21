package com.ai.infrastructure.migration.service;

import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import com.ai.infrastructure.config.AIIndexingProperties;
import com.ai.infrastructure.dto.AIEntityConfig;
import com.ai.infrastructure.indexing.IndexingOperation;
import com.ai.infrastructure.indexing.IndexingRequest;
import com.ai.infrastructure.indexing.IndexingStrategy;
import com.ai.infrastructure.indexing.queue.IndexingQueueService;
import com.ai.infrastructure.migration.config.MigrationFieldConfig;
import com.ai.infrastructure.migration.config.MigrationProperties;
import com.ai.infrastructure.migration.domain.MigrationJob;
import com.ai.infrastructure.migration.domain.MigrationRequest;
import com.ai.infrastructure.migration.domain.MigrationStatus;
import com.ai.infrastructure.migration.repository.MigrationJobRepository;
import com.ai.infrastructure.service.AICapabilityService;
import com.ai.infrastructure.storage.strategy.AISearchableEntityStorageStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataMigrationServiceTest {

    @Mock private IndexingQueueService queueService;
    @Mock private AIEntityConfigurationLoader configLoader;
    @Mock private EntityRepositoryRegistry repositoryRegistry;
    @Mock private MigrationJobRepository jobRepository;
    @Mock private AISearchableEntityStorageStrategy storageStrategy;
    @Mock private MigrationProgressTracker progressTracker;
    @Mock private AICapabilityService capabilityService;
    @Mock private ExecutorService executorService;
    @Mock private MigrationFilterPolicy filterPolicy;

    @Captor private ArgumentCaptor<Runnable> runnableCaptor;
    @Captor private ArgumentCaptor<IndexingRequest> requestCaptor;

    private MigrationProperties migrationProperties;
    private AIIndexingProperties indexingProperties;
    private Clock clock;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        migrationProperties = new MigrationProperties();
        MigrationFieldConfig fieldConfig = new MigrationFieldConfig();
        fieldConfig.setCreatedAtField("createdAt");
        migrationProperties.getEntityFields().put("demo", fieldConfig);

        indexingProperties = new AIIndexingProperties();
        clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneId.of("UTC"));
    }

    @Test
    void respectsPauseStatusAndStops() {
        MigrationJob job = baseJob("demo");
        job.setStatus(MigrationStatus.PAUSED);
        AtomicReference<MigrationJob> holder = new AtomicReference<>(job);

        when(jobRepository.findById(job.getId())).thenAnswer(inv -> Optional.of(holder.get()));
        when(jobRepository.save(any())).thenAnswer(inv -> {
            holder.set(inv.getArgument(0));
            return inv.getArgument(0);
        });

        DataMigrationService service = service();
        service.startMigration(MigrationRequest.builder().entityType("demo").batchSize(1).build());

        verify(executorService).submit(runnableCaptor.capture());
        runnableCaptor.getValue().run();

        assertThat(holder.get().getProcessedEntities()).isZero();
        verify(queueService, never()).enqueue(any());
    }

    @Test
    void skipsAlreadyIndexedWhenReindexDisabled() {
        MigrationJob job = baseJob("demo");
        AtomicReference<MigrationJob> holder = new AtomicReference<>(job);
        when(jobRepository.findById(job.getId())).thenAnswer(inv -> Optional.of(holder.get()));
        when(jobRepository.save(any())).thenAnswer(inv -> {
            holder.set(inv.getArgument(0));
            return inv.getArgument(0);
        });

        DemoEntity entity = new DemoEntity("e-1");
        FakeRepository repo = new FakeRepository(List.of(entity));
        when(repositoryRegistry.getRegistration("demo")).thenReturn(new EntityRegistration("demo", DemoEntity.class, repo));
        when(configLoader.getEntityConfig("demo")).thenReturn(new AIEntityConfig("demo", Map.of(), Map.of()));
        when(capabilityService.resolveEntityId(entity)).thenReturn(entity.id);
        when(storageStrategy.findByEntityTypeAndEntityId("demo", entity.id)).thenReturn(Optional.of(entity));

        DataMigrationService service = service();
        service.startMigration(MigrationRequest.builder().entityType("demo").reindexExisting(false).batchSize(10).build());

        verify(executorService).submit(runnableCaptor.capture());
        runnableCaptor.getValue().run();

        verify(queueService, never()).enqueue(any());
        assertThat(holder.get().getProcessedEntities()).isZero();
    }

    @Test
    void enqueuesPayloadWithDefaults() {
        MigrationJob job = baseJob("demo");
        AtomicReference<MigrationJob> holder = new AtomicReference<>(job);
        when(jobRepository.findById(job.getId())).thenAnswer(inv -> Optional.of(holder.get()));
        when(jobRepository.save(any())).thenAnswer(inv -> {
            holder.set(inv.getArgument(0));
            return inv.getArgument(0);
        });

        DemoEntity entity = new DemoEntity("e-1");
        FakeRepository repo = new FakeRepository(List.of(entity));
        when(repositoryRegistry.getRegistration("demo")).thenReturn(new EntityRegistration("demo", DemoEntity.class, repo));
        when(configLoader.getEntityConfig("demo")).thenReturn(new AIEntityConfig("demo", Map.of(), Map.of()));
        when(capabilityService.resolveEntityId(entity)).thenReturn(entity.id);
        when(storageStrategy.findByEntityTypeAndEntityId("demo", entity.id)).thenReturn(Optional.empty());

        DataMigrationService service = service();
        service.startMigration(MigrationRequest.builder().entityType("demo").reindexExisting(false).batchSize(10).build());

        verify(executorService).submit(runnableCaptor.capture());
        runnableCaptor.getValue().run();

        verify(queueService).enqueue(requestCaptor.capture());
        IndexingRequest req = requestCaptor.getValue();
        assertThat(req.getEntityType()).isEqualTo("demo");
        assertThat(req.getEntityId()).isEqualTo("e-1");
        assertThat(req.getStrategy()).isEqualTo(IndexingStrategy.ASYNC);
        assertThat(req.getOperation()).isEqualTo(IndexingOperation.CREATE);
        assertThat(req.getActionPlan().isGenerateEmbedding()).isTrue();
        assertThat(req.getActionPlan().isIndexForSearch()).isTrue();
        assertThat(req.getMaxRetries()).isEqualTo(indexingProperties.getQueue().getMaxRetries());
        assertThat(req.getPayload()).contains("e-1");
    }

    @Test
    void enqueuesWhenReindexExistingTrueEvenIfAlreadyIndexed() {
        MigrationJob job = baseJob("demo");
        AtomicReference<MigrationJob> holder = new AtomicReference<>(job);
        when(jobRepository.findById(job.getId())).thenAnswer(inv -> Optional.of(holder.get()));
        when(jobRepository.save(any())).thenAnswer(inv -> {
            holder.set(inv.getArgument(0));
            return inv.getArgument(0);
        });

        DemoEntity entity = new DemoEntity("e-2");
        FakeRepository repo = new FakeRepository(List.of(entity));
        when(repositoryRegistry.getRegistration("demo")).thenReturn(new EntityRegistration("demo", DemoEntity.class, repo));
        when(configLoader.getEntityConfig("demo")).thenReturn(new AIEntityConfig("demo", Map.of(), Map.of()));
        when(capabilityService.resolveEntityId(entity)).thenReturn(entity.id);
        when(storageStrategy.findByEntityTypeAndEntityId("demo", entity.id)).thenReturn(Optional.of(entity));

        DataMigrationService service = service();
        service.startMigration(MigrationRequest.builder().entityType("demo").reindexExisting(true).batchSize(10).build());

        verify(executorService).submit(runnableCaptor.capture());
        runnableCaptor.getValue().run();

        verify(queueService).enqueue(any(IndexingRequest.class));
        assertThat(holder.get().getProcessedEntities()).isEqualTo(1);
    }

    @Test
    void enqueueFailureCountsAsFailed() {
        MigrationJob job = baseJob("demo");
        AtomicReference<MigrationJob> holder = new AtomicReference<>(job);
        when(jobRepository.findById(job.getId())).thenAnswer(inv -> Optional.of(holder.get()));
        when(jobRepository.save(any())).thenAnswer(inv -> {
            holder.set(inv.getArgument(0));
            return inv.getArgument(0);
        });

        DemoEntity entity = new DemoEntity("e-3");
        FakeRepository repo = new FakeRepository(List.of(entity));
        when(repositoryRegistry.getRegistration("demo")).thenReturn(new EntityRegistration("demo", DemoEntity.class, repo));
        when(configLoader.getEntityConfig("demo")).thenReturn(new AIEntityConfig("demo", Map.of(), Map.of()));
        when(capabilityService.resolveEntityId(entity)).thenReturn(entity.id);
        when(storageStrategy.findByEntityTypeAndEntityId("demo", entity.id)).thenReturn(Optional.empty());
        when(queueService.enqueue(any(IndexingRequest.class))).thenThrow(new RuntimeException("queue down"));

        DataMigrationService service = service();
        service.startMigration(MigrationRequest.builder().entityType("demo").batchSize(10).build());

        verify(executorService).submit(runnableCaptor.capture());
        runnableCaptor.getValue().run();

        assertThat(holder.get().getFailedEntities()).isEqualTo(1);
        assertThat(holder.get().getProcessedEntities()).isZero();
    }

    @Test
    void missingFieldConfigWithoutPolicyThrows() {
        migrationProperties.getEntityFields().clear();
        DataMigrationService service = new DataMigrationService(
            queueService,
            configLoader,
            repositoryRegistry,
            jobRepository,
            storageStrategy,
            progressTracker,
            migrationProperties,
            indexingProperties,
            new com.fasterxml.jackson.databind.ObjectMapper(),
            executorService,
            capabilityService,
            clock,
            List.of()
        );

        when(configLoader.getEntityConfig("demo")).thenReturn(new AIEntityConfig("demo", Map.of(), Map.of()));
        when(repositoryRegistry.getRegistration("demo")).thenReturn(new EntityRegistration("demo", DemoEntity.class, new FakeRepository(List.of())));

        assertThatThrownBy(() -> service.startMigration(MigrationRequest.builder().entityType("demo").batchSize(10).build()))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void startMigrationPersistsInitialJobWithTotals() {
        FakeRepository repo = new FakeRepository(List.of(new DemoEntity("e-4"), new DemoEntity("e-5")));
        when(repositoryRegistry.getRegistration("demo")).thenReturn(new EntityRegistration("demo", DemoEntity.class, repo));
        when(configLoader.getEntityConfig("demo")).thenReturn(new AIEntityConfig("demo", Map.of(), Map.of()));

        ArgumentCaptor<MigrationJob> jobCaptor = ArgumentCaptor.forClass(MigrationJob.class);
        DataMigrationService service = service();
        service.startMigration(MigrationRequest.builder().entityType("demo").batchSize(7).build());

        verify(jobRepository).save(jobCaptor.capture());
        MigrationJob saved = jobCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(MigrationStatus.RUNNING);
        assertThat(saved.getTotalEntities()).isEqualTo(2);
        assertThat(saved.getBatchSize()).isEqualTo(7);
    }

    @Test
    void marksJobFailedOnUnhandledException() {
        MigrationJob job = baseJob("demo");
        AtomicReference<MigrationJob> holder = new AtomicReference<>(job);
        when(jobRepository.findById(job.getId())).thenAnswer(inv -> Optional.of(holder.get()));
        when(jobRepository.save(any())).thenAnswer(inv -> {
            holder.set(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(repositoryRegistry.getRegistration("demo")).thenThrow(new RuntimeException("boom"));
        when(configLoader.getEntityConfig("demo")).thenReturn(new AIEntityConfig("demo", Map.of(), Map.of()));

        DataMigrationService service = service();
        service.startMigration(MigrationRequest.builder().entityType("demo").batchSize(10).build());

        verify(executorService).submit(runnableCaptor.capture());
        runnableCaptor.getValue().run();

        assertThat(holder.get().getStatus()).isEqualTo(MigrationStatus.FAILED);
        assertThat(holder.get().getErrorMessage()).contains("boom");
    }

    @Test
    void matchesFiltersRespectCreatedBeforeAfterAndSafeIds() {
        MigrationFieldConfig fieldConfig = new MigrationFieldConfig();
        fieldConfig.setCreatedAtField("createdAt");
        migrationProperties.getEntityFields().put("demo", fieldConfig);

        DataMigrationService service = service();
        MigrationRequest req = MigrationRequest.builder()
            .entityType("demo")
            .filters(new com.ai.infrastructure.migration.domain.MigrationFilters(
                List.of("keep-me"),
                java.time.LocalDate.now(clock).plusDays(1),
                java.time.LocalDate.now(clock).minusDays(1)
            ))
            .build();

        DemoEntity entity = new DemoEntity("keep-me", java.time.LocalDate.now(clock));
        assertThat(invokeMatchesFilters(service, entity, req, fieldConfig)).isTrue();

        DemoEntity other = new DemoEntity("drop-me", java.time.LocalDate.now(clock));
        assertThat(invokeMatchesFilters(service, other, req, fieldConfig)).isFalse();
    }

    @Test
    void rateLimitSkipsWhenZeroOrNull() {
        MigrationJob job = baseJob("demo");
        job.setRateLimit(0);
        DataMigrationService service = service();
        invokeRateLimit(service, job);

        job.setRateLimit(null);
        invokeRateLimit(service, job);
    }

    private DataMigrationService service() {
        return new DataMigrationService(
            queueService,
            configLoader,
            repositoryRegistry,
            jobRepository,
            storageStrategy,
            progressTracker,
            migrationProperties,
            indexingProperties,
            new com.fasterxml.jackson.databind.ObjectMapper(),
            executorService,
            capabilityService,
            clock,
            List.of(filterPolicy)
        );
    }

    private MigrationJob baseJob(String entityType) {
        return MigrationJob.builder()
            .id("mig-" + UUID.randomUUID())
            .entityType(entityType)
            .status(MigrationStatus.RUNNING)
            .totalEntities(1L)
            .processedEntities(0L)
            .failedEntities(0L)
            .currentPage(0)
            .batchSize(10)
            .rateLimit(null)
            .reindexExisting(false)
            .startedAt(clock.instant().atZone(clock.getZone()).toLocalDateTime())
            .lastUpdatedAt(clock.instant().atZone(clock.getZone()).toLocalDateTime())
            .build();
    }

    private static class FakeRepository implements org.springframework.data.jpa.repository.JpaRepository<DemoEntity, String> {
        private final List<DemoEntity> data;
        FakeRepository(List<DemoEntity> data) {
            this.data = data;
        }
        @Override public org.springframework.data.domain.Page<DemoEntity> findAll(org.springframework.data.domain.Pageable pageable) {
            if (pageable.getPageNumber() > 0) {
                return org.springframework.data.domain.Page.empty(pageable);
            }
            return new PageImpl<>(data, PageRequest.of(0, data.size()), data.size());
        }
        @Override public List<DemoEntity> findAll() { throw new UnsupportedOperationException(); }
        @Override public List<DemoEntity> findAllById(Iterable<String> strings) { throw new UnsupportedOperationException(); }
        @Override public <S extends DemoEntity> List<S> saveAll(Iterable<S> entities) { throw new UnsupportedOperationException(); }
        @Override public void flush() { }
        @Override public <S extends DemoEntity> S saveAndFlush(S entity) { throw new UnsupportedOperationException(); }
        @Override public <S extends DemoEntity> List<S> saveAllAndFlush(Iterable<S> entities) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch(Iterable<DemoEntity> entities) { }
        @Override public void deleteAllByIdInBatch(Iterable<String> strings) { }
        @Override public void deleteAllInBatch() { }
        @Override public DemoEntity getOne(String s) { throw new UnsupportedOperationException(); }
        @Override public DemoEntity getById(String s) { throw new UnsupportedOperationException(); }
        @Override public DemoEntity getReferenceById(String s) { throw new UnsupportedOperationException(); }
        @Override public <S extends DemoEntity> S save(S entity) { throw new UnsupportedOperationException(); }
        @Override public Optional<DemoEntity> findById(String s) { return Optional.empty(); }
        @Override public boolean existsById(String s) { return false; }
        @Override public long count() { return data.size(); }
        @Override public void deleteById(String s) { }
        @Override public void delete(DemoEntity entity) { }
        @Override public void deleteAllById(Iterable<? extends String> strings) { }
        @Override public void deleteAll(Iterable<? extends DemoEntity> entities) { }
        @Override public void deleteAll() { }
        @Override public <S extends DemoEntity> Optional<S> findOne(org.springframework.data.domain.Example<S> example) { return Optional.empty(); }
        @Override public <S extends DemoEntity> List<S> findAll(org.springframework.data.domain.Example<S> example) { return List.of(); }
        @Override public <S extends DemoEntity> List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { return List.of(); }
        @Override public <S extends DemoEntity> PageImpl<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) { return new PageImpl<>(List.of()); }
        @Override public <S extends DemoEntity> long count(org.springframework.data.domain.Example<S> example) { return 0; }
        @Override public <S extends DemoEntity> boolean exists(org.springframework.data.domain.Example<S> example) { return false; }
        @Override public List<DemoEntity> findAll(org.springframework.data.domain.Sort sort) { return data; }
    }

    private static class DemoEntity {
        String id;
        java.time.LocalDate createdAt;
        DemoEntity(String id) {
            this(id, java.time.LocalDate.now());
        }
        DemoEntity(String id, java.time.LocalDate createdAt) {
            this.id = id;
            this.createdAt = createdAt;
        }
    }

    private boolean invokeMatchesFilters(DataMigrationService service,
                                         DemoEntity entity,
                                         MigrationRequest req,
                                         MigrationFieldConfig fieldConfig) {
        try {
            var method = DataMigrationService.class.getDeclaredMethod(
                "matchesFilters",
                Object.class,
                com.ai.infrastructure.migration.domain.MigrationRequest.class,
                com.ai.infrastructure.dto.AIEntityConfig.class,
                com.ai.infrastructure.migration.config.MigrationFieldConfig.class,
                com.ai.infrastructure.migration.service.MigrationFilterPolicy.class
            );
            method.setAccessible(true);
            return (boolean) method.invoke(
                service,
                entity,
                req,
                new AIEntityConfig("demo", Map.of(), Map.of()),
                fieldConfig,
                filterPolicy
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void invokeRateLimit(DataMigrationService service, MigrationJob job) {
        try {
            var method = DataMigrationService.class.getDeclaredMethod("applyRateLimit", MigrationJob.class);
            method.setAccessible(true);
            method.invoke(service, job);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
