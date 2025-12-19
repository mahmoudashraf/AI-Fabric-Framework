package com.ai.infrastructure.it.migration;

import com.ai.infrastructure.migration.domain.MigrationJob;
import com.ai.infrastructure.migration.domain.MigrationRequest;
import com.ai.infrastructure.migration.service.DataMigrationService;
import com.ai.infrastructure.migration.repository.MigrationJobRepository;
import com.ai.infrastructure.indexing.queue.IndexingQueueService;
import com.ai.infrastructure.indexing.IndexingRequest;
import com.ai.infrastructure.storage.strategy.AISearchableEntityStorageStrategy;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import static org.awaitility.Awaitility.await;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void migration_enqueues_entities_and_completes() throws Exception {
        repository.save(new TestMigrationEntity("id-1", LocalDateTime.now().minusDays(1)));
        repository.save(new TestMigrationEntity("id-2", LocalDateTime.now().minusDays(2)));

        when(storageStrategy.findByEntityTypeAndEntityId("mig-test", "id-1")).thenReturn(Optional.empty());
        when(storageStrategy.findByEntityTypeAndEntityId("mig-test", "id-2")).thenReturn(Optional.empty());

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
        verify(queueService, times(2)).enqueue(captor.capture());
        assertThat(captor.getAllValues())
            .extracting(IndexingRequest::entityId)
            .containsExactlyInAnyOrder("id-1", "id-2");

    }
}
