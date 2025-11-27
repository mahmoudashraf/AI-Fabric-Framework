package com.ai.infrastructure.it;

import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.it.entity.TestProduct;
import com.ai.infrastructure.it.repository.TestProductRepository;
import com.ai.infrastructure.it.service.TestProductService;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.ai.infrastructure.service.VectorManagementService;
import com.ai.infrastructure.service.AICapabilityService;
import com.ai.infrastructure.it.support.IndexingQueueTestSupport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("dev")
class AISearchableEntityLifecycleIntegrationTest {

    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private TestProductService productService;

    @Autowired
    private TestProductRepository productRepository;

    @SpyBean
    private AISearchableEntityRepository searchableEntityRepository;

    @Autowired
    private VectorManagementService vectorManagementService;

    @Autowired
    private AICapabilityService aiCapabilityService;

    @Autowired
    private IndexingQueueTestSupport indexingQueueTestSupport;

    @BeforeEach
    void setUp() {
        searchableEntityRepository.deleteAll();
        productRepository.deleteAll();
        clearProductVectors();
        reset(searchableEntityRepository);
    }

    @AfterEach
    void tearDown() {
        searchableEntityRepository.deleteAll();
        productRepository.deleteAll();
        clearProductVectors();
    }

    @Test
    @DisplayName("Creating a product indexes an AISearchableEntity with vector metadata")
    void searchableEntityCreatedAfterProductCreation() {
        TestProduct request = TestProduct.builder()
            .name("Orion Carbon Bike")
            .description("Premium endurance-focused carbon bike with ergonomic fit")
            .category("cycling")
            .brand("Orion")
            .price(new BigDecimal("8999.00"))
            .stockQuantity(5)
            .active(true)
            .build();

        TestProduct saved = productService.createProduct(request);
        indexingQueueTestSupport.drainQueue();
        String entityId = saved.getId().toString();

        await().atMost(WAIT_TIMEOUT)
            .until(() -> mockingDetails(searchableEntityRepository).getInvocations().stream()
                .anyMatch(invocation -> invocation.getMethod().getName().equals("save")));

        ArgumentCaptor<AISearchableEntity> entityCaptor = ArgumentCaptor.forClass(AISearchableEntity.class);
        verify(searchableEntityRepository, atLeastOnce()).save(entityCaptor.capture());
        AISearchableEntity searchable = entityCaptor.getAllValues().get(entityCaptor.getAllValues().size() - 1);

        assertEquals("product", searchable.getEntityType());
        assertEquals(entityId, searchable.getEntityId());
        assertNotNull(searchable.getVectorId());
        assertTrue(searchable.getSearchableContent().contains("Orion Carbon Bike"));

        String metadataJson = searchable.getMetadata();
        assertNotNull(metadataJson, "Metadata JSON should be populated");

        ObjectMapper objectMapper = new ObjectMapper();
        LinkedHashMap<String, String> metadata = assertDoesNotThrow(() ->
            objectMapper.readValue(metadataJson, new TypeReference<LinkedHashMap<String, String>>() {})
        );

        List<String> keysInOrder = new ArrayList<>(metadata.keySet());
        List<String> expectedOrder = List.of("category", "price", "brand");
        assertEquals(expectedOrder, keysInOrder,
            () -> "Metadata keys should retain deterministic order. expected=" + expectedOrder
                + ", actual=" + keysInOrder + ", metadataJson=" + metadataJson);
        assertEquals("{\"category\":\"cycling\",\"price\":\"8999.00\",\"brand\":\"Orion\"}", metadataJson);
        assertEquals("cycling", metadata.get("category"));
        assertEquals("8999.00", metadata.get("price"));
        assertEquals("Orion", metadata.get("brand"));

        vectorManagementService.getVector("product", entityId)
            .ifPresent(vectorRecord -> assertEquals(metadataJson, vectorRecord.getMetadata().get("raw")));

    }

    @Test
    @DisplayName("Updating a product regenerates the searchable vector payload")
    void searchableEntityUpdatedAfterProductUpdate() {
        TestProduct saved = productService.createProduct(TestProduct.builder()
            .name("Helios Travel Pack")
            .description("Modular travel backpack with smart compartments")
            .category("travel")
            .brand("Helios")
            .price(new BigDecimal("320.00"))
            .stockQuantity(12)
            .active(true)
            .build());

        String entityId = saved.getId().toString();

        await().atMost(WAIT_TIMEOUT)
            .until(() -> mockingDetails(searchableEntityRepository).getInvocations().stream()
                .anyMatch(invocation -> invocation.getMethod().getName().equals("save")));

        ArgumentCaptor<AISearchableEntity> initialCaptor = ArgumentCaptor.forClass(AISearchableEntity.class);
        verify(searchableEntityRepository, atLeastOnce()).save(initialCaptor.capture());
        String originalVectorId = initialCaptor.getAllValues().get(initialCaptor.getAllValues().size() - 1).getVectorId();

        reset(searchableEntityRepository);

        productService.updateProduct(saved.getId(), "Helios Travel Pack 2.0",
            "Updated travel backpack with compression straps and RFID shielding",
            new BigDecimal("349.00"));
        indexingQueueTestSupport.drainQueue();

        await().atMost(WAIT_TIMEOUT)
            .until(() -> mockingDetails(searchableEntityRepository).getInvocations().stream()
                .anyMatch(invocation -> invocation.getMethod().getName().equals("save")));

        ArgumentCaptor<AISearchableEntity> updatedCaptor = ArgumentCaptor.forClass(AISearchableEntity.class);
        verify(searchableEntityRepository, atLeastOnce()).save(updatedCaptor.capture());
        AISearchableEntity updated = updatedCaptor.getAllValues().get(updatedCaptor.getAllValues().size() - 1);

        assertNotEquals(originalVectorId, updated.getVectorId(), "Vector id should change after reindexing");
        assertTrue(updated.getSearchableContent().contains("2.0"));
    }

    @Test
    @DisplayName("Deleting a product removes AISearchableEntity and vector")
    void searchableEntityDeletedAfterProductDeletion() {
        TestProduct saved = productService.createProduct(TestProduct.builder()
            .name("Nebula Studio Mic")
            .description("Professional studio microphone with adaptive noise gating")
            .category("audio")
            .brand("Nebula")
            .price(new BigDecimal("499.00"))
            .stockQuantity(4)
            .active(true)
            .build());

        String entityId = saved.getId().toString();

        await().atMost(WAIT_TIMEOUT)
            .until(() -> mockingDetails(searchableEntityRepository).getInvocations().stream()
                .anyMatch(invocation -> invocation.getMethod().getName().equals("save")));

        reset(searchableEntityRepository);

        productService.deleteProduct(saved.getId());
        indexingQueueTestSupport.drainQueue();

        await().atMost(WAIT_TIMEOUT)
            .untilAsserted(() -> verify(searchableEntityRepository, atLeastOnce())
                .deleteByEntityTypeAndEntityId("product", entityId));

        await().atMost(WAIT_TIMEOUT)
            .untilAsserted(() -> assertFalse(vectorManagementService.vectorExists("product", entityId)));

        assertFalse(productRepository.findById(saved.getId()).isPresent());
    }

    private void clearProductVectors() {
        try {
            vectorManagementService.clearVectorsByEntityType("product");
        } catch (Exception ignored) {
            // Vector provider may not have been initialised yet; ignore for cleanup.
        }
    }

    @Test
    @DisplayName("Repair job restores missing vectors for AISearchableEntity")
    void searchableEntityVectorRepairJobRehydratesMissingVector() {
        TestProduct saved = productService.createProduct(TestProduct.builder()
            .name("Stale Vector Headphones")
            .description("Noise-cancelling headphones used for vector repair scenario")
            .category("audio")
            .brand("Resync")
            .price(new BigDecimal("329.00"))
            .stockQuantity(6)
            .active(true)
            .build());

        String entityId = saved.getId().toString();

        await().atMost(WAIT_TIMEOUT)
            .until(() -> mockingDetails(searchableEntityRepository).getInvocations().stream()
                .anyMatch(invocation -> invocation.getMethod().getName().equals("save")));

        ArgumentCaptor<AISearchableEntity> initialCaptor = ArgumentCaptor.forClass(AISearchableEntity.class);
        verify(searchableEntityRepository, atLeastOnce()).save(initialCaptor.capture());
        String originalVectorId = initialCaptor.getAllValues().get(initialCaptor.getAllValues().size() - 1).getVectorId();

        assertTrue(vectorManagementService.vectorExists("product", entityId));

        vectorManagementService.removeVector("product", entityId);
        assertFalse(vectorManagementService.vectorExists("product", entityId));

        TestProduct persisted = productRepository.findById(saved.getId()).orElseThrow();

        reset(searchableEntityRepository);

        aiCapabilityService.processEntityForAI(persisted, "product");
        indexingQueueTestSupport.drainQueue();

        await().atMost(WAIT_TIMEOUT)
            .until(() -> mockingDetails(searchableEntityRepository).getInvocations().stream()
                .anyMatch(invocation -> invocation.getMethod().getName().equals("save")));

        ArgumentCaptor<AISearchableEntity> repairCaptor = ArgumentCaptor.forClass(AISearchableEntity.class);
        verify(searchableEntityRepository, atLeastOnce()).save(repairCaptor.capture());

        AISearchableEntity repairedEntity = repairCaptor.getAllValues().get(repairCaptor.getAllValues().size() - 1);
        assertTrue(vectorManagementService.vectorExists("product", entityId));
        assertNotEquals(originalVectorId, repairedEntity.getVectorId(), "Repair job should generate a fresh vector id");
        assertEquals(entityId, repairedEntity.getEntityId());
        assertTrue(repairedEntity.getMetadata().contains("brand"));
    }
}

