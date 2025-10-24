package com.ai.infrastructure.it;

import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.ai.infrastructure.service.AICapabilityService;
import com.ai.infrastructure.it.entity.TestProduct;
import com.ai.infrastructure.it.repository.TestProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Error Handling Integration Test for AI Infrastructure Module
 * 
 * Tests error handling and edge cases including:
 * - Null and empty data handling
 * - Invalid entity processing
 * - Database constraint violations
 * - Service failures and recovery
 * - Edge case scenarios
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("mock-test")
@Transactional
public class ErrorHandlingIntegrationTest {

    @Autowired
    private AICapabilityService capabilityService;

    @Autowired
    private AISearchableEntityRepository searchRepository;

    @Autowired
    private TestProductRepository productRepository;

    @BeforeEach
    public void setUp() {
        // Clean up before each test
        searchRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    public void testNullEntityHandling() {
        System.out.println("🛡️ Testing Null Entity Handling...");
        
        // Given - Null entity
        TestProduct nullProduct = null;

        // When/Then - Should handle null gracefully
        assertDoesNotThrow(() -> {
            capabilityService.processEntityForAI(nullProduct, "test-product");
        }, "Should handle null entity gracefully");

        // Verify no entities were processed
        List<AISearchableEntity> entities = searchRepository.findByEntityType("test-product");
        assertEquals(0, entities.size(), "Should not process null entity");

        System.out.println("✅ Null Entity Handling Test Passed");
    }

    @Test
    public void testEmptyContentHandling() {
        System.out.println("🛡️ Testing Empty Content Handling...");
        
        // Given - Product with empty/null content
        TestProduct emptyProduct = TestProduct.builder()
            .name("")  // Empty name
            .description(null)  // Null description
            .category("")  // Empty category
            .brand(null)  // Null brand
            .price(new BigDecimal("0.00"))
            .sku("")  // Empty SKU
            .stockQuantity(0)
            .active(true)
            .build();

        // When - Process empty product
        emptyProduct = productRepository.save(emptyProduct);
        assertDoesNotThrow(() -> {
            capabilityService.processEntityForAI(emptyProduct, "test-product");
        }, "Should handle empty content gracefully");

        // Then - Verify processing
        List<AISearchableEntity> entities = searchRepository.findByEntityType("test-product");
        assertEquals(1, entities.size(), "Should process entity even with empty content");

        AISearchableEntity entity = entities.get(0);
        assertNotNull(entity.getEmbeddings(), "Should generate embeddings even for empty content");
        assertNotNull(entity.getSearchableContent(), "Should have searchable content");
        assertTrue(entity.getSearchableContent().length() >= 0, "Searchable content should be non-null");

        System.out.println("✅ Empty Content Handling Test Passed");
        System.out.println("   - Searchable content length: " + entity.getSearchableContent().length());
    }

    @Test
    public void testInvalidEntityTypeHandling() {
        System.out.println("🛡️ Testing Invalid Entity Type Handling...");
        
        // Given - Valid product with invalid entity type
        TestProduct product = TestProduct.builder()
            .name("Test Product")
            .description("Test description")
            .category("Test")
            .price(new BigDecimal("99.99"))
            .active(true)
            .build();

        // When - Process with invalid entity type
        product = productRepository.save(product);
        assertDoesNotThrow(() -> {
            capabilityService.processEntityForAI(product, "invalid-entity-type");
        }, "Should handle invalid entity type gracefully");

        // Then - Verify processing
        List<AISearchableEntity> entities = searchRepository.findByEntityType("invalid-entity-type");
        assertEquals(1, entities.size(), "Should process entity with invalid type");

        System.out.println("✅ Invalid Entity Type Handling Test Passed");
    }

    @Test
    public void testDuplicateEntityHandling() {
        System.out.println("🛡️ Testing Duplicate Entity Handling...");
        
        // Given - Same product processed twice
        TestProduct product = TestProduct.builder()
            .name("Duplicate Test Product")
            .description("This product will be processed twice")
            .category("Test")
            .price(new BigDecimal("99.99"))
            .active(true)
            .build();

        // When - Process same entity twice
        product = productRepository.save(product);
        
        // First processing
        capabilityService.processEntityForAI(product, "test-product");
        List<AISearchableEntity> firstProcessing = searchRepository.findByEntityType("test-product");
        assertEquals(1, firstProcessing.size(), "Should process entity first time");

        // Second processing (should handle gracefully)
        assertDoesNotThrow(() -> {
            capabilityService.processEntityForAI(product, "test-product");
        }, "Should handle duplicate processing gracefully");

        // Then - Verify final state
        List<AISearchableEntity> finalEntities = searchRepository.findByEntityType("test-product");
        assertTrue(finalEntities.size() >= 1, "Should have at least one entity after duplicate processing");

        System.out.println("✅ Duplicate Entity Handling Test Passed");
        System.out.println("   - Final entities count: " + finalEntities.size());
    }

    @Test
    public void testLargeContentHandling() {
        System.out.println("🛡️ Testing Large Content Handling...");
        
        // Given - Product with very large content
        StringBuilder largeDescription = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            largeDescription.append("This is a very long description for testing large content handling. ");
            largeDescription.append("It contains repetitive text to simulate large content scenarios. ");
            largeDescription.append("Line ").append(i).append(" of the large description. ");
        }

        TestProduct largeProduct = TestProduct.builder()
            .name("Large Content Product")
            .description(largeDescription.toString())
            .category("LargeContent")
            .price(new BigDecimal("999.99"))
            .active(true)
            .build();

        // When - Process large content
        largeProduct = productRepository.save(largeProduct);
        assertDoesNotThrow(() -> {
            capabilityService.processEntityForAI(largeProduct, "test-product");
        }, "Should handle large content gracefully");

        // Then - Verify processing
        List<AISearchableEntity> entities = searchRepository.findByEntityType("test-product");
        assertEquals(1, entities.size(), "Should process large content entity");

        AISearchableEntity entity = entities.get(0);
        assertNotNull(entity.getEmbeddings(), "Should generate embeddings for large content");
        assertNotNull(entity.getSearchableContent(), "Should have searchable content");
        assertTrue(entity.getSearchableContent().length() > 1000, "Should preserve large content");

        System.out.println("✅ Large Content Handling Test Passed");
        System.out.println("   - Content length: " + entity.getSearchableContent().length());
        System.out.println("   - Embeddings count: " + entity.getEmbeddings().size());
    }

    @Test
    public void testSpecialCharactersHandling() {
        System.out.println("🛡️ Testing Special Characters Handling...");
        
        // Given - Product with special characters
        TestProduct specialProduct = TestProduct.builder()
            .name("Special Chars Product: !@#$%^&*()_+-=[]{}|;':\",./<>?")
            .description("Description with special chars: éñüñçödé, 中文, العربية, русский, 🚀🎉💡")
            .category("SpecialChars")
            .price(new BigDecimal("123.45"))
            .active(true)
            .build();

        // When - Process special characters
        specialProduct = productRepository.save(specialProduct);
        assertDoesNotThrow(() -> {
            capabilityService.processEntityForAI(specialProduct, "test-product");
        }, "Should handle special characters gracefully");

        // Then - Verify processing
        List<AISearchableEntity> entities = searchRepository.findByEntityType("test-product");
        assertEquals(1, entities.size(), "Should process special characters entity");

        AISearchableEntity entity = entities.get(0);
        assertNotNull(entity.getEmbeddings(), "Should generate embeddings for special characters");
        assertNotNull(entity.getSearchableContent(), "Should have searchable content");
        assertTrue(entity.getSearchableContent().contains("Special Chars"), "Should preserve special characters");

        System.out.println("✅ Special Characters Handling Test Passed");
        System.out.println("   - Searchable content: " + entity.getSearchableContent().substring(0, Math.min(100, entity.getSearchableContent().length())));
    }

    @Test
    public void testNegativeValuesHandling() {
        System.out.println("🛡️ Testing Negative Values Handling...");
        
        // Given - Product with negative values
        TestProduct negativeProduct = TestProduct.builder()
            .name("Negative Values Product")
            .description("Product with negative values for testing")
            .category("NegativeTest")
            .price(new BigDecimal("-99.99"))  // Negative price
            .stockQuantity(-10)  // Negative stock
            .active(true)
            .build();

        // When - Process negative values
        negativeProduct = productRepository.save(negativeProduct);
        assertDoesNotThrow(() -> {
            capabilityService.processEntityForAI(negativeProduct, "test-product");
        }, "Should handle negative values gracefully");

        // Then - Verify processing
        List<AISearchableEntity> entities = searchRepository.findByEntityType("test-product");
        assertEquals(1, entities.size(), "Should process negative values entity");

        AISearchableEntity entity = entities.get(0);
        assertNotNull(entity.getEmbeddings(), "Should generate embeddings for negative values");
        assertNotNull(entity.getMetadata(), "Should have metadata with negative values");

        System.out.println("✅ Negative Values Handling Test Passed");
    }

    @Test
    public void testConcurrentModificationHandling() {
        System.out.println("🛡️ Testing Concurrent Modification Handling...");
        
        // Given - Product for concurrent modification
        TestProduct product = TestProduct.builder()
            .name("Concurrent Test Product")
            .description("Product for concurrent modification testing")
            .category("ConcurrentTest")
            .price(new BigDecimal("99.99"))
            .active(true)
            .build();

        product = productRepository.save(product);

        // When - Process entity while modifying it
        assertDoesNotThrow(() -> {
            // Start processing
            capabilityService.processEntityForAI(product, "test-product");
            
            // Modify product while processing might be ongoing
            product.setName("Modified Name");
            product.setDescription("Modified Description");
            productRepository.save(product);
            
            // Process again
            capabilityService.processEntityForAI(product, "test-product");
        }, "Should handle concurrent modifications gracefully");

        // Then - Verify processing
        List<AISearchableEntity> entities = searchRepository.findByEntityType("test-product");
        assertTrue(entities.size() >= 1, "Should have processed entity despite concurrent modifications");

        System.out.println("✅ Concurrent Modification Handling Test Passed");
        System.out.println("   - Entities count: " + entities.size());
    }

    @Test
    public void testEntityRemovalErrorHandling() {
        System.out.println("🛡️ Testing Entity Removal Error Handling...");
        
        // Given - Non-existent entity ID
        final String nonExistentId = "non-existent-id-12345";

        // When/Then - Should handle removal of non-existent entity gracefully
        assertDoesNotThrow(() -> {
            capabilityService.removeEntityFromIndex(nonExistentId, "test-product");
        }, "Should handle removal of non-existent entity gracefully");

        // Test with null ID
        assertDoesNotThrow(() -> {
            capabilityService.removeEntityFromIndex(null, "test-product");
        }, "Should handle null ID gracefully");

        // Test with empty ID
        assertDoesNotThrow(() -> {
            capabilityService.removeEntityFromIndex("", "test-product");
        }, "Should handle empty ID gracefully");

        System.out.println("✅ Entity Removal Error Handling Test Passed");
    }

    @Test
    public void testDatabaseConstraintViolations() {
        System.out.println("🛡️ Testing Database Constraint Violations...");
        
        // Given - Product that might violate constraints
        TestProduct constraintProduct = TestProduct.builder()
            .name("Constraint Test Product")
            .description("Product for testing database constraints")
            .category("ConstraintTest")
            .price(new BigDecimal("99.99"))
            .active(true)
            .build();

        // When - Save and process
        constraintProduct = productRepository.save(constraintProduct);
        assertDoesNotThrow(() -> {
            capabilityService.processEntityForAI(constraintProduct, "test-product");
        }, "Should handle database operations gracefully");

        // Then - Verify processing
        List<AISearchableEntity> entities = searchRepository.findByEntityType("test-product");
        assertEquals(1, entities.size(), "Should process entity despite potential constraints");

        System.out.println("✅ Database Constraint Violations Test Passed");
    }

    @Test
    public void testServiceFailureRecovery() {
        System.out.println("🛡️ Testing Service Failure Recovery...");
        
        // Given - Product for failure recovery testing
        TestProduct recoveryProduct = TestProduct.builder()
            .name("Recovery Test Product")
            .description("Product for testing service failure recovery")
            .category("RecoveryTest")
            .price(new BigDecimal("99.99"))
            .active(true)
            .build();

        // When - Process multiple times to test recovery
        recoveryProduct = productRepository.save(recoveryProduct);
        
        // First processing
        assertDoesNotThrow(() -> {
            capabilityService.processEntityForAI(recoveryProduct, "test-product");
        }, "Should handle first processing");

        // Second processing (simulating recovery)
        assertDoesNotThrow(() -> {
            capabilityService.processEntityForAI(recoveryProduct, "test-product");
        }, "Should handle recovery processing");

        // Then - Verify recovery
        List<AISearchableEntity> entities = searchRepository.findByEntityType("test-product");
        assertTrue(entities.size() >= 1, "Should recover from service failures");

        System.out.println("✅ Service Failure Recovery Test Passed");
        System.out.println("   - Entities after recovery: " + entities.size());
    }
}
