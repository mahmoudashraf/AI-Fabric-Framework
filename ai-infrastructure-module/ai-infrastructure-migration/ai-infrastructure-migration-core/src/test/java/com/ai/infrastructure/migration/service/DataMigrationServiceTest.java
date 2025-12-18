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
import com.ai.infrastructure.repository.AISearchableEntityRepository;
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
    private AISearchableEntityRepository searchableEntityRepository;
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
            searchableEntityRepository,
            progressTracker,
            migrationProperties,
            indexingProperties,
            objectMapper,
            executorService,
            capabilityService,
            clock
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
        when(searchableEntityRepository.findByEntityTypeAndEntityId(eq("product"), any()))
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
        when(searchableEntityRepository.findByEntityTypeAndEntityId("product", "1"))
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
