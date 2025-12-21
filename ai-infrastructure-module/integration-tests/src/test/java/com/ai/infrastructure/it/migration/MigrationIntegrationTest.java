package com.ai.infrastructure.it.migration;

import com.ai.infrastructure.migration.domain.MigrationJob;
import com.ai.infrastructure.migration.domain.MigrationRequest;
import com.ai.infrastructure.migration.service.DataMigrationService;
import com.ai.infrastructure.migration.repository.MigrationJobRepository;
import com.ai.infrastructure.indexing.queue.IndexingQueueService;
import com.ai.infrastructure.indexing.IndexingRequest;
import com.ai.infrastructure.storage.strategy.AISearchableEntityStorageStrategy;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import static org.awaitility.Awaitility.await;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = TestMigrationApplication.class)
@ActiveProfiles("migration-test")
class MigrationIntegrationTest {

    @Autowired
    private DataMigrationService migrationService;

    @Autowired
    private TestMigrationRepository repository;

    @Autowired
    private MigrationJobRepository jobRepository;

    @MockBean
    private IndexingQueueService queueService;

    @MockBean
    private AISearchableEntityStorageStrategy storageStrategy;

    @BeforeEach
    void cleanState() {
        repository.deleteAll();
        Mockito.reset(queueService, storageStrategy);
    }

    private void stubQueueLeaseNoop() {
        Mockito.lenient().when(queueService.lease(Mockito.any(), Mockito.anyInt())).thenReturn(java.util.List.of());
    }

    @Test
    void migration_enqueues_entities_and_completes() throws Exception {
        repository.save(new TestMigrationEntity("id-1", LocalDateTime.now().minusDays(1)));
        repository.save(new TestMigrationEntity("id-2", LocalDateTime.now().minusDays(2)));

        when(storageStrategy.findByEntityTypeAndEntityId("mig-test", "id-1")).thenReturn(Optional.empty());
        when(storageStrategy.findByEntityTypeAndEntityId("mig-test", "id-2")).thenReturn(Optional.empty());
        stubQueueLeaseNoop();

        MigrationRequest request = MigrationRequest.builder()
            .entityType("mig-test")
            .batchSize(10)
            .reindexExisting(false)
            .build();

        MigrationJob job = migrationService.startMigration(request);

        // Wait for the async executor to finish migration
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            MigrationJob refreshed = jobRepository.findById(job.getId()).orElseThrow();
            assertThat(refreshed.getStatus()).isEqualTo(com.ai.infrastructure.migration.domain.MigrationStatus.COMPLETED);
        });

        ArgumentCaptor<IndexingRequest> captor = ArgumentCaptor.forClass(IndexingRequest.class);
        verify(queueService, atLeast(2)).enqueue(captor.capture());
        assertThat(captor.getAllValues())
            .extracting(IndexingRequest::entityId)
            .contains("id-1", "id-2");

    }

    @Test
    void reindexExistingSkipsThenEnqueuesWhenAllowed() {
        repository.deleteAll();
        repository.save(new TestMigrationEntity("id-3", LocalDateTime.now().minusHours(3)));

        // already indexed
        when(storageStrategy.findByEntityTypeAndEntityId("mig-test", "id-3"))
            .thenReturn(Optional.of(new com.ai.infrastructure.entity.AISearchableEntity()));

        MigrationRequest skip = MigrationRequest.builder()
            .entityType("mig-test")
            .batchSize(10)
            .reindexExisting(false)
            .build();

        MigrationJob jobSkip = migrationService.startMigration(skip);
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            MigrationJob refreshed = jobRepository.findById(jobSkip.getId()).orElseThrow();
            assertThat(refreshed.getProcessedEntities()).isZero();
        });
        verify(queueService, times(0)).enqueue(Mockito.any());

        // allow reindex
        Mockito.reset(queueService);
        stubQueueLeaseNoop();
        MigrationRequest allow = MigrationRequest.builder()
            .entityType("mig-test")
            .batchSize(10)
            .reindexExisting(true)
            .build();
        MigrationJob jobAllow = migrationService.startMigration(allow);
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            MigrationJob refreshed = jobRepository.findById(jobAllow.getId()).orElseThrow();
            assertThat(refreshed.getProcessedEntities()).isEqualTo(1);
        });
        verify(queueService, times(1)).enqueue(Mockito.any(IndexingRequest.class));
    }

    @Disabled("Flaky in CI; covered at unit level")
    @Test
    void filtersRespectCreatedAfterAndEntityIds() {
        repository.deleteAll();
        TestMigrationEntity oldEntity = new TestMigrationEntity("old-1", LocalDateTime.now().minusDays(5));
        TestMigrationEntity newEntity = new TestMigrationEntity("new-1", LocalDateTime.now().minusHours(6));
        repository.save(oldEntity);
        repository.save(newEntity);

        when(storageStrategy.findByEntityTypeAndEntityId("mig-test", "old-1")).thenReturn(Optional.empty());
        when(storageStrategy.findByEntityTypeAndEntityId("mig-test", "new-1")).thenReturn(Optional.empty());
        Mockito.lenient().when(storageStrategy.findByEntityTypeAndEntityId(Mockito.eq("mig-test"), Mockito.anyString()))
            .thenReturn(Optional.empty());

        MigrationRequest request = MigrationRequest.builder()
            .entityType("mig-test")
            .batchSize(10)
            .filters(com.ai.infrastructure.migration.domain.MigrationFilters.builder()
                .createdAfter(LocalDateTime.now().minusDays(1).toLocalDate())
                .entityIds(java.util.List.of("new-1"))
                .build())
            .build();

        migrationService.startMigration(request);

        ArgumentCaptor<IndexingRequest> captor = ArgumentCaptor.forClass(IndexingRequest.class);
        verify(queueService, timeout(5000).atLeast(1)).enqueue(captor.capture());
        assertThat(captor.getAllValues())
            .extracting(IndexingRequest::entityId)
            .contains("new-1");
    }

    @Test
    void cancelMidRunStopsEnqueue() throws Exception {
        repository.save(new TestMigrationEntity("c1", LocalDateTime.now().minusDays(1)));
        repository.save(new TestMigrationEntity("c2", LocalDateTime.now().minusDays(1)));
        when(storageStrategy.findByEntityTypeAndEntityId(Mockito.eq("mig-test"), Mockito.anyString()))
            .thenReturn(Optional.empty());
        stubQueueLeaseNoop();

        CountDownLatch firstEnqueue = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Mockito.doAnswer(invocation -> {
            firstEnqueue.countDown();
            release.await(2, TimeUnit.SECONDS);
            return null;
        }).when(queueService).enqueue(Mockito.any(IndexingRequest.class));

        MigrationRequest req = MigrationRequest.builder()
            .entityType("mig-test")
            .batchSize(1)
            .build();
        MigrationJob job = migrationService.startMigration(req);

        assertThat(firstEnqueue.await(3, TimeUnit.SECONDS)).isTrue();

        // cancel after first enqueue observed
        job.setStatus(com.ai.infrastructure.migration.domain.MigrationStatus.CANCELLED);
        jobRepository.save(job);
        release.countDown();

        await().atMost(Duration.ofSeconds(4)).untilAsserted(() -> {
            MigrationJob refreshed = jobRepository.findById(job.getId()).orElseThrow();
            assertThat(refreshed.getProcessedEntities()).isLessThanOrEqualTo(1);
        });
        verify(queueService, atMost(1)).enqueue(Mockito.any(IndexingRequest.class));
    }

    @Test
    void pauseAndResumeProcessesRemaining() {
        repository.save(new TestMigrationEntity("p1", LocalDateTime.now().minusDays(1)));
        repository.save(new TestMigrationEntity("p2", LocalDateTime.now().minusDays(1)));
        when(storageStrategy.findByEntityTypeAndEntityId(Mockito.eq("mig-test"), Mockito.anyString()))
            .thenReturn(Optional.empty());
        stubQueueLeaseNoop();

        MigrationRequest req = MigrationRequest.builder()
            .entityType("mig-test")
            .batchSize(1)
            .build();
        MigrationJob job = migrationService.startMigration(req);

        // pause after first page
        job.setStatus(com.ai.infrastructure.migration.domain.MigrationStatus.PAUSED);
        jobRepository.save(job);

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            MigrationJob refreshed = jobRepository.findById(job.getId()).orElseThrow();
            assertThat(refreshed.getProcessedEntities()).isLessThanOrEqualTo(1);
        });

        // resume
        job.setStatus(com.ai.infrastructure.migration.domain.MigrationStatus.RUNNING);
        jobRepository.save(job);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            MigrationJob refreshed = jobRepository.findById(job.getId()).orElseThrow();
            assertThat(refreshed.getStatus()).isEqualTo(com.ai.infrastructure.migration.domain.MigrationStatus.COMPLETED);
            assertThat(refreshed.getProcessedEntities()).isEqualTo(2);
        });

        verify(queueService, atLeast(2)).enqueue(Mockito.any(IndexingRequest.class));
    }
}
