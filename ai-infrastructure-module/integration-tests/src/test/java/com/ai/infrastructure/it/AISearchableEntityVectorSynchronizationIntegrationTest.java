package com.ai.infrastructure.it;

import com.ai.infrastructure.dto.VectorRecord;
import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.rag.RAGService;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.ai.infrastructure.service.VectorManagementService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("dev")
class AISearchableEntityVectorSynchronizationIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private VectorManagementService vectorManagementService;

    @Autowired
    private AISearchableEntityRepository searchableEntityRepository;

    @Autowired
    private RAGService ragService;

    @Autowired
    private VectorDatabaseService vectorDatabaseService;

    @BeforeEach
    void setUp() {
        searchableEntityRepository.deleteAll();
        vectorManagementService.clearAllVectors();
    }

    @AfterEach
    void tearDown() {
        searchableEntityRepository.deleteAll();
        vectorManagementService.clearAllVectors();
    }

    @Test
    @DisplayName("Storing a vector creates a synchronized AISearchableEntity entry")
    void creationWhenVectorStored() throws Exception {
        String entityType = "plan-product";
        String entityId = "product-123";
        String content = "Luxury Swiss watch with automatic movement";
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("category", "watch");
        metadata.put("brand", "Rolex");
        metadata.put("price", "5000");

        assertTrue(vectorDatabaseService.getClass().getSimpleName().contains("SearchableEntityVectorDatabaseService"),
            () -> "Expected decorated VectorDatabaseService but got " + vectorDatabaseService.getClass());

        String vectorId = vectorManagementService.storeVector(entityType, entityId, content, vector(32, 0.1), metadata);

        Optional<AISearchableEntity> entityOptional = searchableEntityRepository.findByEntityTypeAndEntityId(entityType, entityId);
        assertTrue(entityOptional.isPresent(), "AISearchableEntity should be created after storing vector");

        AISearchableEntity entity = entityOptional.get();
        assertEquals(vectorId, entity.getVectorId());
        assertEquals(content, entity.getSearchableContent());
        Map<String, String> storedMetadata = parseMetadataJson(entity.getMetadata());
        assertEquals("watch", storedMetadata.get("category"));
        assertEquals("Rolex", storedMetadata.get("brand"));
        assertEquals("5000", storedMetadata.get("price"));
        assertNotNull(entity.getCreatedAt());
        assertNotNull(entity.getVectorUpdatedAt());

        Optional<com.ai.infrastructure.dto.VectorRecord> vectorRecord = vectorManagementService.getVector(entityType, entityId);
        assertTrue(vectorRecord.isPresent(), "Vector record should exist in the vector database");
        assertEquals(vectorId, vectorRecord.get().getVectorId());
    }

    @Test
    @DisplayName("Vector IDs remain linked between repository and vector store")
    void vectorIdLinkingIntegrity() {
        String entityType = "plan-product-link";
        String entityId = "product-456";
        String content = "Premium chronograph with ceramic bezel";

        String vectorId = vectorManagementService.storeVector(entityType, entityId, content, vector(32, 0.2), Map.of("category", "watch"));

        Optional<AISearchableEntity> entity = searchableEntityRepository.findByVectorId(vectorId);
        assertTrue(entity.isPresent());
        assertEquals(entityId, entity.get().getEntityId());

        Optional<com.ai.infrastructure.dto.VectorRecord> vectorRecord = vectorManagementService.getVectorById(vectorId);
        assertTrue(vectorRecord.isPresent());
        assertEquals(content, vectorRecord.get().getContent());
    }

    @Test
    @DisplayName("Updating a vector keeps AISearchableEntity content and timestamps in sync")
    void updateKeepsSearchableEntityInSync() throws Exception {
        String entityType = "plan-product-update";
        String entityId = "product-789";

        vectorManagementService.storeVector(entityType, entityId, "First description", vector(32, 0.3), Map.of("version", "1"));
        AISearchableEntity initial = searchableEntityRepository.findByEntityTypeAndEntityId(entityType, entityId).orElseThrow();
        LocalDateTime originalVectorUpdatedAt = initial.getVectorUpdatedAt();

        sleep(20);

        vectorManagementService.storeVector(entityType, entityId, "Second description with more details", vector(32, 0.4), Map.of("version", "2"));
        AISearchableEntity updated = searchableEntityRepository.findByEntityTypeAndEntityId(entityType, entityId).orElseThrow();

        assertTrue(updated.getVectorUpdatedAt().isAfter(originalVectorUpdatedAt));
        assertEquals("Second description with more details", updated.getSearchableContent());
        Map<String, String> updatedMetadata = parseMetadataJson(updated.getMetadata());
        assertEquals("2", updatedMetadata.get("version"));
    }

    @Test
    @DisplayName("Removing a vector clears the corresponding AISearchableEntity")
    void removalClearsRepository() {
        String entityType = "plan-product-remove";
        String entityId = "product-321";

        vectorManagementService.storeVector(entityType, entityId, "Disposable description", vector(16, 0.5), Map.of());
        assertTrue(vectorManagementService.removeVector(entityType, entityId));

        assertTrue(searchableEntityRepository.findByEntityTypeAndEntityId(entityType, entityId).isEmpty());
        assertFalse(vectorManagementService.vectorExists(entityType, entityId));
    }

    @Test
    @DisplayName("RAG indexing creates searchable entities")
    void ragIndexingCreatesSearchableEntity() {
        String entityType = "rag-article";
        String entityId = "article-001";
        Map<String, Object> metadata = Map.of(
            "title", "AI in Modern Software",
            "category", "technology"
        );

        ragService.indexContent(entityType, entityId, "Introduction to AI and Machine Learning", metadata);

        Optional<AISearchableEntity> entity = searchableEntityRepository.findByEntityTypeAndEntityId(entityType, entityId);
        assertTrue(entity.isPresent());
        assertTrue(entity.get().getMetadata().contains("AI in Modern Software"));
        assertFalse(entity.get().getVectorId().isEmpty());
    }

    @Test
    @DisplayName("Metadata JSON persists structured values")
    void metadataPersistenceAndRetrieval() throws Exception {
        String entityType = "plan-product-metadata";
        String entityId = "product-654";
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("category", "audio");
        metadata.put("brand", "Nebula");
        metadata.put("price", "899.00");

        vectorManagementService.storeVector(entityType, entityId, "Studio monitor with reference response", vector(24, 0.6), metadata);

        AISearchableEntity entity = searchableEntityRepository.findByEntityTypeAndEntityId(entityType, entityId).orElseThrow();
        Map<String, String> parsed = parseMetadataJson(entity.getMetadata());

        assertEquals("audio", parsed.get("category"));
        assertEquals("Nebula", parsed.get("brand"));
        assertEquals("899.00", parsed.get("price"));
    }

    @Test
    @DisplayName("Repository search operations return expected entities")
    void repositoryQueryOperations() {
        String entityType = "plan-query";
        vectorManagementService.storeVector(entityType, "entity-1", "Portable espresso maker for travelers", vector(18, 0.7), Map.of());
        vectorManagementService.storeVector(entityType, "entity-2", "Compact travel adapter with dual USB ports", vector(18, 0.8), Map.of());
        vectorManagementService.storeVector(entityType, "entity-3", "Luxury wool travel blanket", vector(18, 0.9), Map.of());

        List<AISearchableEntity> results = searchableEntityRepository.findBySearchableContentContainingIgnoreCase("travel");
        Set<String> ids = results.stream().map(AISearchableEntity::getEntityId).collect(Collectors.toSet());

        assertThat(ids).containsExactlyInAnyOrder("entity-1", "entity-2", "entity-3");
    }

    @Test
    @DisplayName("Clearing vectors by entity type removes only targeted entities")
    void clearVectorsByEntityTypeRemovesEntities() {
        vectorManagementService.storeVector("plan-clear", "entity-1", "First", vector(10, 1.0), Map.of());
        vectorManagementService.storeVector("plan-clear", "entity-2", "Second", vector(10, 1.1), Map.of());
        vectorManagementService.storeVector("plan-other", "entity-3", "Third", vector(10, 1.2), Map.of());

        vectorManagementService.clearVectorsByEntityType("plan-clear");

        assertTrue(searchableEntityRepository.findByEntityTypeAndEntityId("plan-clear", "entity-1").isEmpty());
        assertTrue(searchableEntityRepository.findByEntityTypeAndEntityId("plan-clear", "entity-2").isEmpty());
        assertTrue(searchableEntityRepository.findByEntityTypeAndEntityId("plan-other", "entity-3").isPresent());
    }

    @Test
    @DisplayName("Concurrent vector upserts maintain a single searchable entity")
    void concurrentStoreOperationsMaintainSingleEntity() throws InterruptedException {
        String entityType = "plan-concurrent";
        String entityId = "entity-cc";

        ExecutorService executor = Executors.newFixedThreadPool(6);
        try {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                final int idx = i;
                futures.add(CompletableFuture.runAsync(() ->
                    vectorManagementService.storeVector(entityType, entityId,
                        "Content iteration " + idx, vector(12, idx * 0.05), Map.of("iteration", idx)), executor));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        List<AISearchableEntity> entities = searchableEntityRepository.findByEntityType(entityType);
        assertEquals(1, entities.size());
        assertTrue(entities.get(0).getSearchableContent().startsWith("Content iteration"));
    }

    @Test
    @DisplayName("Batch store and remove operations synchronize repository state")
    void batchStoreAndRemoveSynchronizesRepository() {
        List<VectorRecord> records = List.of(
            VectorRecord.builder()
                .entityType("plan-batch")
                .entityId("batch-1")
                .content("Batch record one")
                .embedding(vector(14, 2.1))
                .metadata(Map.of("order", 1))
                .build(),
            VectorRecord.builder()
                .entityType("plan-batch")
                .entityId("batch-2")
                .content("Batch record two")
                .embedding(vector(14, 2.2))
                .metadata(Map.of("order", 2))
                .build()
        );

        List<String> vectorIds = vectorManagementService.batchStoreVectors(records);
        assertEquals(2, vectorIds.size());
        assertEquals(2, searchableEntityRepository.findByEntityType("plan-batch").size());

        vectorManagementService.batchRemoveVectors(vectorIds);
        assertTrue(searchableEntityRepository.findByEntityType("plan-batch").isEmpty());
    }

    private List<Double> vector(int dimension, double seed) {
        List<Double> embedding = new ArrayList<>(dimension);
        for (int i = 0; i < dimension; i++) {
            embedding.add(seed + (i * 0.0001));
        }
        return embedding;
    }

    private Map<String, String> parseMetadataJson(String raw) throws Exception {
        String cleaned = raw;
        if (cleaned != null && cleaned.length() >= 2 && cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).replace("\\\"", "\"");
        }
        return OBJECT_MAPPER.readValue(cleaned, new TypeReference<>() {});
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
