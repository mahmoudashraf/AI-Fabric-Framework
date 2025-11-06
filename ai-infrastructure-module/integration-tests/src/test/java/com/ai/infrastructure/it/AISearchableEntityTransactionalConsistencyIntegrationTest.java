package com.ai.infrastructure.it;

import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.ai.infrastructure.service.VectorManagementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
class AISearchableEntityTransactionalConsistencyIntegrationTest {

    @Autowired
    private VectorManagementService vectorManagementService;

    @Autowired
    private AISearchableEntityRepository searchableEntityRepository;

    @Autowired
    private AIEmbeddingService embeddingService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    private final Set<String> trackedEntityTypes = new HashSet<>();

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_DEFAULT);
    }

    @AfterEach
    void tearDown() {
        trackedEntityTypes.forEach(entityType -> {
            vectorManagementService.clearVectorsByEntityType(entityType);
            searchableEntityRepository.deleteByEntityType(entityType);
        });
        trackedEntityTypes.clear();
    }

    @Test
    @DisplayName("Rolling back a transaction removes vectors and searchable entities")
    void vectorStorageRollsBackWithTransaction() {
        String entityType = "tx-product-" + UUID.randomUUID();
        String entityId = "rollback-" + UUID.randomUUID();
        trackedEntityTypes.add(entityType);

        assertThrows(RuntimeException.class, () -> transactionTemplate.execute(status -> {
            vectorManagementService.storeVector(
                entityType,
                entityId,
                "Transactional rollback product",
                  embeddingFor("Transactional rollback product"),
                Map.of("scenario", "rollback")
            );
            throw new RuntimeException("Force rollback for transactional consistency test");
        }));

        await().untilAsserted(() -> {
            Optional<AISearchableEntity> entity = searchableEntityRepository.findByEntityTypeAndEntityId(entityType, entityId);
            assertTrue(entity.isEmpty(), "AISearchableEntity should not persist after rollback");
            assertFalse(vectorManagementService.vectorExists(entityType, entityId),
                "Vector should be removed after transaction rollback");
        });
    }

    @Test
    @DisplayName("Successful transaction commits vectors and searchable entities atomically")
    void vectorStorageCommitsWithTransaction() {
        String entityType = "tx-product-" + UUID.randomUUID();
        String entityId = "commit-" + UUID.randomUUID();
        trackedEntityTypes.add(entityType);

        String vectorId = transactionTemplate.execute(status -> vectorManagementService.storeVector(
            entityType,
            entityId,
            "Transactional commit product",
              embeddingFor("Transactional commit product"),
            Map.of("scenario", "commit")
        ));

        assertNotNull(vectorId, "Vector ID should be returned on successful commit");

        await().untilAsserted(() -> {
            Optional<AISearchableEntity> entity = searchableEntityRepository.findByEntityTypeAndEntityId(entityType, entityId);
            assertTrue(entity.isPresent(), "AISearchableEntity should persist after commit");
            assertEquals(vectorId, entity.get().getVectorId(),
                "Searchable entity should reference committed vector ID");
            assertTrue(vectorManagementService.vectorExists(entityType, entityId),
                "Vector should exist after commit");
        });
    }

    private List<Double> embeddingFor(String content) {
        return embeddingService.generateEmbedding(
            AIEmbeddingRequest.builder()
                .text(content)
                .build()
        ).getEmbedding();
    }
}

