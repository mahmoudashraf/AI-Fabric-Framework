package com.ai.infrastructure.it;

import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.it.entity.TestProduct;
import com.ai.infrastructure.it.repository.TestProductRepository;
import com.ai.infrastructure.it.service.TestProductService;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.ai.infrastructure.service.VectorManagementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;

import org.mockito.ArgumentCaptor;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("dev")
class AIProcessAnnotationIntegrationTest {

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
    private com.ai.infrastructure.config.AIEntityConfigurationLoader configurationLoader;

    @BeforeEach
    void setUp() {
        searchableEntityRepository.deleteAll();
        productRepository.deleteAll();
        clearProductVectors();
        reset(searchableEntityRepository);
    }

    @AfterEach
    void cleanUp() {
        searchableEntityRepository.deleteAll();
        productRepository.deleteAll();
        clearProductVectors();
    }

    @Test
    @DisplayName("@AIProcess(create) indexes product content with embeddings and metadata")
    void aiProcessCreateIndexesEntity() {
        TestProduct saved = productService.createProduct(TestProduct.builder()
            .name("Aurora Smart Projector")
            .description("4K ultra-short throw projector with adaptive brightness")
            .category("home-theater")
            .brand("Aurora")
            .price(new BigDecimal("2499.99"))
            .stockQuantity(7)
            .active(true)
            .build());

        var crudConfig = configurationLoader.getEntityConfig("product");
        assertNotNull(crudConfig, "AI entity config for 'product' should exist");
        assertNotNull(crudConfig.getCrudOperations(), "CRUD operations should be present");
        System.out.println("CRUD keys: " + crudConfig.getCrudOperations().keySet());

        String entityId = saved.getId().toString();

        await().atMost(WAIT_TIMEOUT)
            .until(() -> mockingDetails(searchableEntityRepository).getInvocations().stream()
                .anyMatch(invocation -> invocation.getMethod().getName().equals("save")));

        ArgumentCaptor<AISearchableEntity> entityCaptor = ArgumentCaptor.forClass(AISearchableEntity.class);
        verify(searchableEntityRepository, atLeastOnce()).save(entityCaptor.capture());

        AISearchableEntity searchable = entityCaptor.getValue();
        assertEquals("product", searchable.getEntityType());
        assertTrue(searchable.getSearchableContent().toLowerCase().contains("aurora smart projector"));
        assertNotNull(searchable.getVectorId());
        assertNotNull(searchable.getMetadata());
    }

    @Test
    @DisplayName("@AIProcess(update) regenerates embedding and searchable payload")
    void aiProcessUpdateRebuildsEmbedding() {
        TestProduct saved = productService.createProduct(TestProduct.builder()
            .name("Nimbus Drone")
            .description("Compact photography drone with 8K capture")
            .category("drones")
            .brand("Nimbus")
            .price(new BigDecimal("1899.00"))
            .stockQuantity(3)
            .active(true)
            .build());

        String entityId = saved.getId().toString();

        await().atMost(WAIT_TIMEOUT)
            .until(() -> mockingDetails(searchableEntityRepository).getInvocations().stream()
                .anyMatch(invocation -> invocation.getMethod().getName().equals("save")));

        ArgumentCaptor<AISearchableEntity> initialCaptor = ArgumentCaptor.forClass(AISearchableEntity.class);
        verify(searchableEntityRepository, atLeastOnce()).save(initialCaptor.capture());
        String originalVectorId = initialCaptor.getValue().getVectorId();

        reset(searchableEntityRepository);

        productService.updateProduct(saved.getId(), "Nimbus Drone Pro",
            "Upgraded photography drone with obstacle avoidance and 10x optical zoom",
            new BigDecimal("2149.00"));

        await().atMost(WAIT_TIMEOUT)
            .until(() -> mockingDetails(searchableEntityRepository).getInvocations().stream()
                .anyMatch(invocation -> invocation.getMethod().getName().equals("save")));

        ArgumentCaptor<AISearchableEntity> updateCaptor = ArgumentCaptor.forClass(AISearchableEntity.class);
        verify(searchableEntityRepository, atLeastOnce()).save(updateCaptor.capture());

        AISearchableEntity updated = updateCaptor.getValue();

        assertNotEquals(originalVectorId, updated.getVectorId());
        assertTrue(updated.getSearchableContent().contains("Pro"));
    }

    @Test
    @DisplayName("@AIProcess(delete) removes vectors and searchable entities")
    void aiProcessDeleteRemovesIndex() {
        TestProduct saved = productService.createProduct(TestProduct.builder()
            .name("Lumen Studio Light")
            .description("Studio lighting kit with adaptive temperature control")
            .category("lighting")
            .brand("Lumen")
            .price(new BigDecimal("799.00"))
            .stockQuantity(9)
            .active(true)
            .build());

        String entityId = saved.getId().toString();

        await().atMost(WAIT_TIMEOUT)
            .until(() -> mockingDetails(searchableEntityRepository).getInvocations().stream()
                .anyMatch(invocation -> invocation.getMethod().getName().equals("save")));

        reset(searchableEntityRepository);

        productService.deleteProduct(saved.getId());

        await().atMost(WAIT_TIMEOUT)
            .untilAsserted(() -> verify(searchableEntityRepository, atLeastOnce())
                .deleteByEntityTypeAndEntityId("product", entityId));

        await().atMost(WAIT_TIMEOUT)
            .untilAsserted(() -> assertFalse(vectorManagementService.vectorExists("product", entityId)));

        assertFalse(vectorManagementService.vectorExists("product", entityId));
        assertFalse(productRepository.findById(saved.getId()).isPresent());
    }

    private void clearProductVectors() {
        try {
            vectorManagementService.clearVectorsByEntityType("product");
        } catch (Exception ignored) {
            // Vector provider may not have been initialised yet; ignore for cleanup.
        }
    }
}

