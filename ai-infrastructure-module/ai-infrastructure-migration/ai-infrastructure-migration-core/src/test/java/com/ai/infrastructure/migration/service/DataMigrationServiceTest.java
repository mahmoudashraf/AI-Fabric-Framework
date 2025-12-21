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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataMigrationServiceTest {

    @Mock
    private IndexingQueueService queueService;
    @Mock
    private AIEntityConfigurationLoader configLoader;
    @Mock
    private EntityRepositoryRegistry repositoryRegistry;
    @Mock
    private MigrationJobRepository jobRepository;
    @Mock
    private AISearchableEntityStorageStrategy storageStrategy;
    @Mock
    private MigrationProgressTracker progressTracker;
    @Mock
    private AICapabilityService capabilityService;
    @Mock
    private ExecutorService executorService;
    @Mock
    private MigrationFilterPolicy filterPolicy;

    @Captor
    private ArgumentCaptor<Runnable> runnableCaptor;
    @Captor
    private ArgumentCaptor<IndexingRequest> requestCaptor;

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
        service.startMigration(MigrationRequest.builder()
            .entityType("demo")
            .batchSize(1)
            .build());

        verify(executorService).submit(runnableCaptor.capture());
        runnableCaptor.getValue().run(); // run inline

        // Because job was paused, no progress was made and queue not touched
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
        when(storageStrategy.findByEntityTypeAndEntityId("demo", entity.id)).thenReturn(Optional.of(entity)); // already indexed

        DataMigrationService service = service();
        service.startMigration(MigrationRequest.builder()
            .entityType("demo")
            .reindexExisting(false)
            .batchSize(10)
            .build());

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
        service.startMigration(MigrationRequest.builder()
            .entityType("demo")
            .reindexExisting(false)
            .batchSize(10)
            .build());

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
        when(storageStrategy.findByEntityTypeAndEntityId("demo", entity.id)).thenReturn(Optional.of(entity)); // already indexed

        DataMigrationService service = service();
        service.startMigration(MigrationRequest.builder()
            .entityType("demo")
            .reindexExisting(true)
            .batchSize(10)
            .build());

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
        // Force enqueue failure
        when(queueService.enqueue(any(IndexingRequest.class))).thenThrow(new RuntimeException("queue down"));

        DataMigrationService service = service();
        service.startMigration(MigrationRequest.builder()
            .entityType("demo")
            .batchSize(10)
            .build());

        verify(executorService).submit(runnableCaptor.capture());
        runnableCaptor.getValue().run();

        assertThat(holder.get().getFailedEntities()).isEqualTo(1);
        assertThat(holder.get().getProcessedEntities()).isZero();
    }

    @Test
    void missingFieldConfigWithoutPolicyThrows() {
        // Clear entityFields and policies to trigger guard
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
            List.of() // no policies
        );

        when(configLoader.getEntityConfig("demo")).thenReturn(new AIEntityConfig("demo", Map.of(), Map.of()));
        when(repositoryRegistry.getRegistration("demo")).thenReturn(new EntityRegistration("demo", DemoEntity.class, new FakeRepository(List.of())));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            service.startMigration(MigrationRequest.builder()
                .entityType("demo")
                .batchSize(10)
                .build())
        ).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void startMigrationPersistsInitialJobWithTotals() {
        FakeRepository repo = new FakeRepository(List.of(new DemoEntity("e-4"), new DemoEntity("e-5")));
        when(repositoryRegistry.getRegistration("demo")).thenReturn(new EntityRegistration("demo", DemoEntity.class, repo));
        when(configLoader.getEntityConfig("demo")).thenReturn(new AIEntityConfig("demo", Map.of(), Map.of()));

        ArgumentCaptor<MigrationJob> jobCaptor = ArgumentCaptor.forClass(MigrationJob.class);
        DataMigrationService service = service();
        service.startMigration(MigrationRequest.builder()
            .entityType("demo")
            .batchSize(7)
            .build());

        verify(jobRepository).save(jobCaptor.capture());
        MigrationJob saved = jobCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(MigrationStatus.RUNNING);
        assertThat(saved.getTotalEntities()).isEqualTo(2);
        assertThat(saved.getBatchSize()).isEqualTo(7);
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
        MigrationJob job = MigrationJob.builder()
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
        return job;
    }

    /**
     * Minimal fake repository to return a single page.
     */
    private static class FakeRepository implements org.springframework.data.jpa.repository.JpaRepository<DemoEntity, String> {
        private final List<DemoEntity> data;
        FakeRepository(List<DemoEntity> data) {
            this.data = data;
        }
        @Override public org.springframework.data.domain.Page<DemoEntity> findAll(org.springframework.data.domain.Pageable pageable) {
            if (pageable.getPageNumber() > 0) {
                return PageImpl.empty(pageable);
            }
            return new PageImpl<>(data, PageRequest.of(0, data.size()), data.size());
        }
        // Unused JpaRepository methods -> throw UnsupportedOperationException to keep surface small
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

    private record DemoEntity(String id) { }
}
package com.ai.infrastructure.migration.service;

import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import com.ai.infrastructure.config.AIIndexingProperties;
import com.ai.infrastructure.dto.AIEntityConfig;
import com.ai.infrastructure.indexing.IndexingRequest;
import com.ai.infrastructure.indexing.queue.IndexingQueueService;
import com.ai.infrastructure.migration.config.MigrationProperties;
import com.ai.infrastructure.migration.domain.MigrationJob;
import com.ai.infrastructure.migration.domain.MigrationRequest;
import com.ai.infrastructure.migration.repository.MigrationJobRepository;
import com.ai.infrastructure.migration.config.MigrationFieldConfig;
import com.ai.infrastructure.storage.strategy.AISearchableEntityStorageStrategy;
import com.ai.infrastructure.service.AICapabilityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataMigrationServiceTest {

    @Mock
    private IndexingQueueService queueService;
    @Mock
    private AIEntityConfigurationLoader configLoader;
    @Mock
    private EntityRepositoryRegistry repositoryRegistry;
    @Mock
    private MigrationJobRepository jobRepository;
    @Mock
    private AISearchableEntityStorageStrategy searchableEntityStorageStrategy;
    @Mock
    private MigrationProgressTracker progressTracker;
    @Mock
    private AICapabilityService capabilityService;

    private MigrationProperties migrationProperties;
    private AIIndexingProperties indexingProperties;
    private ObjectMapper objectMapper;
    private ExecutorService executorService;
    private Clock clock;

    @InjectMocks
    private DataMigrationService dataMigrationService;

    @BeforeEach
    void setup() {
        migrationProperties = new MigrationProperties();
        migrationProperties.setDefaultBatchSize(2);
        migrationProperties.setDefaultRateLimit(0); // avoid sleeps in tests
        MigrationFieldConfig fieldConfig = new MigrationFieldConfig();
        fieldConfig.setCreatedAtField("createdAt");
        migrationProperties.getEntityFields().put("product", fieldConfig);

        indexingProperties = new AIIndexingProperties();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);

        dataMigrationService = new DataMigrationService(
            queueService,
            configLoader,
            repositoryRegistry,
            jobRepository,
            searchableEntityStorageStrategy,
            progressTracker,
            migrationProperties,
            indexingProperties,
            objectMapper,
            executorService,
            capabilityService,
            clock,
            List.of()
        );
    }

    @Test
    void startMigration_enqueuesEntities_andMarksCompleted() throws Exception {
        AIEntityConfig config = AIEntityConfig.builder()
            .entityType("product")
            .autoEmbedding(true)
            .indexable(true)
            .build();
        when(configLoader.getEntityConfig("product")).thenReturn(config);

        TestEntity e1 = new TestEntity("1", LocalDateTime.now(clock));
        TestEntity e2 = new TestEntity("2", LocalDateTime.now(clock));
        JpaRepository<TestEntity, String> repo = mock(JpaRepository.class);
        when(repo.count()).thenReturn(2L);
        AtomicInteger pageCounter = new AtomicInteger();
        when(repo.findAll(any(PageRequest.class))).thenAnswer(inv -> {
            int page = pageCounter.getAndIncrement();
            if (page == 0) {
                return new PageImpl<>(List.of(e1, e2), PageRequest.of(0, 2), 2);
            }
            return new PageImpl<>(List.of(), PageRequest.of(page, 2), 0);
        });
        when(repositoryRegistry.getRegistration("product"))
            .thenReturn(new EntityRegistration("product", TestEntity.class, repo));

        when(capabilityService.resolveEntityId(any())).thenAnswer(inv -> ((TestEntity) inv.getArgument(0)).id());
        when(searchableEntityStorageStrategy.findByEntityTypeAndEntityId(eq("product"), any()))
            .thenReturn(Optional.empty());

        AtomicReference<MigrationJob> jobRef = new AtomicReference<>();
        doAnswer(inv -> {
            MigrationJob saved = inv.getArgument(0);
            jobRef.set(saved);
            return saved;
        }).when(jobRepository).save(any(MigrationJob.class));
        doAnswer(inv -> Optional.ofNullable(jobRef.get())).when(jobRepository).findById(any());

        MigrationRequest request = MigrationRequest.builder().entityType("product").batchSize(2).build();
        MigrationJob job = dataMigrationService.startMigration(request);

        // allow async executor to finish
        awaitJobCompletion(jobRef);

        ArgumentCaptor<IndexingRequest> requestCaptor = ArgumentCaptor.forClass(IndexingRequest.class);
        verify(queueService, times(2)).enqueue(requestCaptor.capture());
        assertThat(requestCaptor.getAllValues())
            .extracting(IndexingRequest::entityId)
            .containsExactlyInAnyOrder("1", "2");

        assertThat(jobRef.get().getStatus()).isEqualTo(com.ai.infrastructure.migration.domain.MigrationStatus.COMPLETED);
        assertThat(jobRef.get().getProcessedEntities()).isEqualTo(2);
    }

    @Test
    void startMigration_skipsAlreadyIndexed_whenReindexDisabled() {
        AIEntityConfig config = AIEntityConfig.builder()
            .entityType("product")
            .autoEmbedding(true)
            .indexable(true)
            .build();
        when(configLoader.getEntityConfig("product")).thenReturn(config);

        TestEntity entity = new TestEntity("1", LocalDateTime.now(clock));
        JpaRepository<TestEntity, String> repo = mock(JpaRepository.class);
        when(repo.count()).thenReturn(1L);
        AtomicInteger pageCounter = new AtomicInteger();
        when(repo.findAll(any(PageRequest.class))).thenAnswer(inv -> {
            int page = pageCounter.getAndIncrement();
            if (page == 0) {
                return new PageImpl<>(List.of(entity), PageRequest.of(0, 2), 1);
            }
            return new PageImpl<>(List.of(), PageRequest.of(page, 2), 0);
        });
        when(repositoryRegistry.getRegistration("product"))
            .thenReturn(new EntityRegistration("product", TestEntity.class, repo));

        when(capabilityService.resolveEntityId(entity)).thenReturn("1");
        when(searchableEntityStorageStrategy.findByEntityTypeAndEntityId("product", "1"))
            .thenReturn(Optional.of(new com.ai.infrastructure.entity.AISearchableEntity()));

        AtomicReference<MigrationJob> jobRef = new AtomicReference<>();
        doAnswer(inv -> {
            MigrationJob saved = inv.getArgument(0);
            jobRef.set(saved);
            return saved;
        }).when(jobRepository).save(any(MigrationJob.class));
        doAnswer(inv -> Optional.ofNullable(jobRef.get())).when(jobRepository).findById(any());

        MigrationRequest request = MigrationRequest.builder()
            .entityType("product")
            .batchSize(2)
            .reindexExisting(false)
            .build();
        dataMigrationService.startMigration(request);

        awaitJobCompletion(jobRef);
        verify(queueService, times(0)).enqueue(any());
        assertThat(jobRef.get().getProcessedEntities()).isZero();
        assertThat(jobRef.get().getStatus()).isEqualTo(com.ai.infrastructure.migration.domain.MigrationStatus.COMPLETED);
    }

    private void awaitJobCompletion(AtomicReference<MigrationJob> ref) {
        // simple spin-wait for async executor to update status
        int retries = 0;
        while (ref.get() == null || ref.get().getStatus() == com.ai.infrastructure.migration.domain.MigrationStatus.RUNNING) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (retries++ > 200) {
                break;
            }
        }
    }

    private record TestEntity(String id, LocalDateTime createdAt) {}
}
