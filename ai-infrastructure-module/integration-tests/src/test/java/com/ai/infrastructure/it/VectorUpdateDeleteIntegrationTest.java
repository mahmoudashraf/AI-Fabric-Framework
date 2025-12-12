package com.ai.infrastructure.it;

import com.ai.infrastructure.dto.VectorRecord;
import com.ai.infrastructure.service.VectorManagementService;
import com.ai.infrastructure.storage.strategy.AISearchableEntityStorageStrategy;
import com.ai.infrastructure.it.support.IndexingQueueTestSupport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = "ai-infrastructure.storage.strategy=SINGLE_TABLE")
class VectorUpdateDeleteIntegrationTest {

    private static final int VECTOR_DIMENSION = 24;
    private static final String INDEX_PATH = "./data/test-lucene-index/vector-update-" + UUID.randomUUID();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private VectorManagementService vectorManagementService;

    @Autowired
    private AISearchableEntityStorageStrategy storageStrategy;

    @Autowired
    private IndexingQueueTestSupport indexingQueueTestSupport;

    @DynamicPropertySource
    static void overrideIndexPath(DynamicPropertyRegistry registry) {
        registry.add("ai.vector-db.lucene.index-path", () -> INDEX_PATH);
    }

    @AfterEach
    void cleanupEntityState() {
        // Each test uses its own entity type, so removing everything is safe
        vectorManagementService.clearAllVectors();
        storageStrategy.deleteAll();
    }

    @AfterAll
    void deleteIndexDirectory() throws IOException {
        Path path = Path.of(INDEX_PATH).toAbsolutePath().normalize();
        if (Files.exists(path)) {
            Files.walk(path)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(candidate -> {
                    try {
                        Files.deleteIfExists(candidate);
                    } catch (IOException ignored) {
                        // best-effort cleanup
                    }
                });
        }
    }

    @Test
    @DisplayName("Vector updates replace identifier while propagating metadata updates, and deletions remove all artifacts")
    void vectorUpdateAndDeleteStayConsistent() {
        String entityType = "vector-update-" + UUID.randomUUID();
        String entityId = "entity-" + UUID.randomUUID();

        String initialVectorId = vectorManagementService.storeVector(
            entityType,
            entityId,
            "Original content",
            syntheticEmbedding(VECTOR_DIMENSION, 1),
            Map.of("version", 1, "origin", "initial")
        );
        indexingQueueTestSupport.drainQueue();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Optional<com.ai.infrastructure.entity.AISearchableEntity> initialEntity = storageStrategy.findByEntityTypeAndEntityId(entityType, entityId);
            assertTrue(initialEntity.isPresent(), "Searchable entity should exist after initial storage");
            assertEquals(initialVectorId, initialEntity.get().getVectorId(), "Searchable entity should link to stored vector");
        });

        vectorManagementService.storeVector(
            entityType,
            entityId,
            "Updated content",
            syntheticEmbedding(VECTOR_DIMENSION, 2),
            Map.of("version", 2, "origin", "update")
        );
        indexingQueueTestSupport.drainQueue();

        VectorRecord updatedVector = vectorManagementService.getVector(entityType, entityId)
            .orElseThrow(() -> new AssertionError("Updated vector should be retrievable"));

        assertFalse(initialVectorId.equals(updatedVector.getVectorId()), "Vector ID should change after update to reference fresh embedding");
        assertEquals("Updated content", updatedVector.getContent(), "Content should reflect updated value");
        assertEquals("2", String.valueOf(updatedVector.getMetadata().get("version")), "Metadata version should be updated");
        assertEquals("update", String.valueOf(updatedVector.getMetadata().get("origin")), "Metadata origin should reflect update");

        com.ai.infrastructure.entity.AISearchableEntity searchableEntity = storageStrategy.findByEntityTypeAndEntityId(entityType, entityId)
            .orElseThrow(() -> new AssertionError("Searchable entity should exist after update"));

        assertEquals(updatedVector.getVectorId(), searchableEntity.getVectorId(), "Searchable entity should point to latest vector ID");
        String metadataJson = searchableEntity.getMetadata();
        assertNotNull(metadataJson, "Persisted metadata JSON should not be null");
        Map<String, Object> metadataMap;
        try {
            metadataMap = OBJECT_MAPPER.readValue(metadataJson, new TypeReference<Map<String, Object>>() {});
        } catch (MismatchedInputException mismatchedInput) {
            try {
                String nestedJson = OBJECT_MAPPER.readValue(metadataJson, String.class);
                metadataMap = OBJECT_MAPPER.readValue(nestedJson, new TypeReference<Map<String, Object>>() {});
            } catch (Exception nestedParsing) {
                throw new AssertionError("Failed to parse searchable entity metadata JSON", nestedParsing);
            }
        } catch (Exception parsingException) {
            throw new AssertionError("Failed to parse searchable entity metadata JSON", parsingException);
        }
        assertEquals("2", String.valueOf(metadataMap.get("version")), "Persisted metadata should contain updated version");
        assertEquals("update", String.valueOf(metadataMap.get("origin")), "Persisted metadata should contain updated origin");

        vectorManagementService.removeVector(entityType, entityId);
        indexingQueueTestSupport.drainQueue();

        assertFalse(vectorManagementService.vectorExists(entityType, entityId), "Vector should be removed from vector store");
        assertTrue(storageStrategy.findByEntityTypeAndEntityId(entityType, entityId).isEmpty(),
            "Searchable entity should be removed after vector deletion");
    }

    private List<Double> syntheticEmbedding(int dimension, int seed) {
        return java.util.stream.IntStream.range(0, dimension)
            .mapToDouble(index -> (seed + index + 1) * 0.001d)
            .boxed()
            .toList();
    }
}

