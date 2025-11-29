package com.ai.infrastructure.it;

import com.ai.infrastructure.it.entity.TestProduct;
import com.ai.infrastructure.it.service.TestProductService;
import com.ai.infrastructure.service.VectorManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "test.searchable-vector-db.enabled=false", // Disable wrapper to test core behavior
    "ai.indexing.async-worker.enabled=true",   // Ensure async worker is enabled
    "ai.indexing.batch-worker.enabled=false"
})
class AIProcessIndexingIntegrationTest {

    @Autowired
    private TestProductService productService;

    @Autowired
    private VectorManagementService vectorManagementService;

    @BeforeEach
    void setUp() {
        try {
            vectorManagementService.clearVectorsByEntityType("product");
        } catch (Exception ignored) {
            // Ignore if index doesn't exist
        }
    }

    @Test
    @DisplayName("Verify @AIProcess handles vector lifecycle: Create -> Update -> Delete")
    void verifyAIProcessLifecycle() {
        // 1. CREATE
        TestProduct product = TestProduct.builder()
            .name("Lifecycle Test Product")
            .description("Testing full vector lifecycle")
            .category("lifecycle")
            .brand("LifecycleBrand")
            .price(new BigDecimal("100.00"))
            .stockQuantity(10)
            .active(true)
            .build();

        TestProduct created = productService.createProduct(product);
        String entityId = created.getId().toString();

        // Wait for async indexing (embedding generation can take time)
        await().atMost(Duration.ofSeconds(30))
            .pollInterval(Duration.ofMillis(500))
            .until(() -> vectorManagementService.vectorExists("product", entityId));

        assertTrue(vectorManagementService.vectorExists("product", entityId), "Vector should exist after creation");

        // 2. UPDATE
        productService.updateProduct(created.getId(), "Updated Lifecycle Product", "Updated description", null);

        // Wait for vector update - check content (vector generation + update)
        await().atMost(Duration.ofSeconds(30))
            .pollInterval(Duration.ofMillis(500))
            .until(() -> {
                 var vector = vectorManagementService.getVector("product", entityId);
                 return vector.isPresent() && vector.get().getContent().contains("Updated description");
            });

        var updatedVector = vectorManagementService.getVector("product", entityId).orElseThrow();
        assertTrue(updatedVector.getContent().contains("Updated description"), "Vector content should be updated");

        // 3. DELETE
        productService.deleteProduct(created.getId());

        // Wait for async deletion
        await().atMost(Duration.ofSeconds(30))
            .pollInterval(Duration.ofMillis(500))
            .until(() -> !vectorManagementService.vectorExists("product", entityId));

        assertFalse(vectorManagementService.vectorExists("product", entityId), "Vector should be removed after deletion");
    }
}

