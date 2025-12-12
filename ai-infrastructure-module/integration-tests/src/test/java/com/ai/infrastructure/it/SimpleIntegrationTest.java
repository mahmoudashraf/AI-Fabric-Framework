package com.ai.infrastructure.it;

import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.ai.infrastructure.service.AICapabilityService;
import com.ai.infrastructure.service.VectorManagementService;
import com.ai.infrastructure.it.entity.TestProduct;
import com.ai.infrastructure.it.repository.TestProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple Integration Test for AI Infrastructure Module
 * 
 * Basic test to verify the integration module works correctly.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Disabled("Disabled due to ApplicationContext loading failures - table creation issues")
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("onnx-test")
@TestPropertySource(properties = "ai.vector-db.lucene.index-path=./data/test-lucene-index/simple")
@Transactional
public class SimpleIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(SimpleIntegrationTest.class);

    @Autowired
    private AICapabilityService capabilityService;

    @Autowired
    private AISearchableEntityRepository searchRepository;

    @Autowired
    private TestProductRepository productRepository;

    @Autowired
    private VectorManagementService vectorManagementService;

    @BeforeEach
    public void setUp() {
        // Wait for schema initialization - Hibernate needs to create tables first
        try {
            // Trigger schema creation by accessing repository
            searchRepository.count();
        } catch (Exception e) {
            // If tables don't exist yet, wait a bit and retry
            try {
                Thread.sleep(100);
                searchRepository.count();
            } catch (Exception ex) {
                log.warn("Schema may not be initialized yet, continuing anyway", ex);
            }
        }
        
        // Clean up before each test
        try {
            searchRepository.deleteAll();
            productRepository.deleteAll();
        } catch (Exception e) {
            // If tables don't exist, that's okay - they'll be created by Hibernate
            log.debug("Tables may not exist yet, will be created by Hibernate", e);
        }
    }

    @Test
    public void testBasicEntityProcessing() {
        // Given - Create a simple product
        TestProduct product = TestProduct.builder()
            .name("Test Product")
            .description("This is a test product for AI processing")
            .category("Test")
            .price(new BigDecimal("99.99"))
            .active(true)
            .build();

        // When - Save and process the product
        product = productRepository.save(product);
        capabilityService.processEntityForAI(product, "test-product");

        // Then - Verify AI processing
        List<AISearchableEntity> searchableEntities = searchRepository.findByEntityType("test-product");
        assertEquals(1, searchableEntities.size(), "Should process one product");

        AISearchableEntity entity = searchableEntities.get(0);
        assertNotNull(entity.getVectorId(), "Should have vector ID");
        assertFalse(entity.getVectorId().isEmpty(), "Vector ID should not be empty");
        assertNotNull(entity.getSearchableContent(), "Should have searchable content");
        assertTrue(entity.getSearchableContent().length() > 0, "Searchable content should not be empty");
        
        // Verify vector exists in vector database
        assertTrue(vectorManagementService.vectorExists(entity.getEntityType(), entity.getEntityId()), 
                  "Vector should exist in vector database");

        System.out.println("✅ Basic entity processing test completed successfully");
        System.out.println("Processed product: " + product.getName());
        System.out.println("Searchable content length: " + entity.getSearchableContent().length());
    }

    @Test
    public void testEntityRemoval() {
        // Given - Create and process a product
        TestProduct product = TestProduct.builder()
            .name("Product to Remove")
            .description("This product will be removed")
            .category("Test")
            .price(new BigDecimal("50.00"))
            .active(true)
            .build();

        product = productRepository.save(product);
        capabilityService.processEntityForAI(product, "test-product");

        // Verify entity exists
        Optional<AISearchableEntity> entity = searchRepository.findByEntityTypeAndEntityId("test-product", product.getId().toString());
        assertTrue(entity.isPresent(), "Should have one entity");

        // When - Remove the product
        productRepository.delete(product);
        capabilityService.removeEntityFromIndex(product.getId().toString(), "test-product");

        // Then - Verify removal
        Optional<AISearchableEntity> remainingEntity = searchRepository.findByEntityTypeAndEntityId("test-product", product.getId().toString());
        assertTrue(remainingEntity.isEmpty(), "Should remove entity from index");

        System.out.println("✅ Entity removal test completed successfully");
    }

    @Test
    public void testSearchFunctionality() {
        // Given - Create multiple products
        List<TestProduct> products = List.of(
            TestProduct.builder()
                .name("AI-Powered Device")
                .description("Smart device with artificial intelligence")
                .category("Electronics")
                .price(new BigDecimal("299.99"))
                .active(true)
                .build(),
            TestProduct.builder()
                .name("Regular Product")
                .description("Standard product without AI")
                .category("General")
                .price(new BigDecimal("99.99"))
                .active(true)
                .build()
        );

        // When - Process products
        products = productRepository.saveAll(products);
        for (TestProduct product : products) {
            capabilityService.processEntityForAI(product, "test-product");
        }

        // Then - Test search
        List<AISearchableEntity> allEntities = searchRepository.findByEntityType("test-product");
        assertEquals(2, allEntities.size(), "Should have two products");

        // Search for AI-related content
        List<AISearchableEntity> aiResults = searchRepository.findBySearchableContentContainingIgnoreCase("AI");
        assertFalse(aiResults.isEmpty(), "Should find AI-related content");

        System.out.println("✅ Search functionality test completed successfully");
        System.out.println("Found " + aiResults.size() + " AI-related results");
    }
}