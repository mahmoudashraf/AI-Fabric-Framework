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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance Integration Tests for AI Infrastructure Module
 * 
 * These tests measure performance characteristics and ensure the system
 * can handle realistic loads and concurrent operations.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@Transactional
public class PerformanceIntegrationTest {

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
    public void testSingleEntityProcessingPerformance() {
        // Test: Measure time to process a single entity
        
        // Given
        TestProduct product = TestProduct.builder()
            .name("Performance Test Product")
            .description("This is a test product for performance measurement")
            .category("Performance")
            .price(new BigDecimal("99.99"))
            .active(true)
            .build();

        // When
        long startTime = System.currentTimeMillis();
        product = productRepository.save(product);
        capabilityService.processEntityForAI(product, "test-product");
        long endTime = System.currentTimeMillis();

        // Then
        long processingTime = endTime - startTime;
        assertTrue(processingTime < 5000, "Single entity processing should complete within 5 seconds");
        
        List<AISearchableEntity> entities = searchRepository.findByEntityTypeAndEntityId("test-product", product.getId().toString());
        assertEquals(1, entities.size(), "Should create one searchable entity");

        System.out.println("✅ Single entity processing time: " + processingTime + "ms");
    }

    @Test
    public void testBatchProcessingPerformance() {
        // Test: Measure time to process multiple entities in batch
        
        // Given
        List<TestProduct> products = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            products.add(TestProduct.builder()
                .name("Batch Product " + i)
                .description("Description for batch product " + i)
                .category("Batch")
                .price(new BigDecimal("10.00").multiply(new BigDecimal(i + 1)))
                .active(true)
                .build());
        }

        // When
        long startTime = System.currentTimeMillis();
        products = productRepository.saveAll(products);
        for (TestProduct product : products) {
            capabilityService.processEntityForAI(product, "test-product");
        }
        long endTime = System.currentTimeMillis();

        // Then
        long processingTime = endTime - startTime;
        assertTrue(processingTime < 10000, "Batch processing should complete within 10 seconds");
        
        List<AISearchableEntity> entities = searchRepository.findByEntityType("test-product");
        assertEquals(10, entities.size(), "Should process all products in batch");

        System.out.println("✅ Batch processing time for 10 entities: " + processingTime + "ms");
        System.out.println("Average time per entity: " + (processingTime / 10) + "ms");
    }

    @Test
    public void testConcurrentProcessingPerformance() {
        // Test: Measure performance under concurrent load
        
        // Given
        int numberOfThreads = 5;
        int entitiesPerThread = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // When
        long startTime = System.currentTimeMillis();
        
        for (int thread = 0; thread < numberOfThreads; thread++) {
            final int threadId = thread;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                for (int i = 0; i < entitiesPerThread; i++) {
                    TestProduct product = TestProduct.builder()
                        .name("Concurrent Product " + threadId + "-" + i)
                        .description("Description for concurrent product " + threadId + "-" + i)
                        .category("Concurrent")
                        .price(new BigDecimal("50.00"))
                        .active(true)
                        .build();
                    
                    product = productRepository.save(product);
                    capabilityService.processEntityForAI(product, "test-product");
                }
            }, executor);
            futures.add(future);
        }

        // Wait for all threads to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        long endTime = System.currentTimeMillis();

        // Then
        long processingTime = endTime - startTime;
        int totalEntities = numberOfThreads * entitiesPerThread;
        assertTrue(processingTime < 15000, "Concurrent processing should complete within 15 seconds");
        
        List<AISearchableEntity> entities = searchRepository.findByEntityType("test-product");
        assertEquals(totalEntities, entities.size(), "Should process all entities concurrently");

        System.out.println("✅ Concurrent processing time for " + totalEntities + " entities: " + processingTime + "ms");
        System.out.println("Average time per entity: " + (processingTime / totalEntities) + "ms");
        
        executor.shutdown();
    }

    @Test
    public void testSearchPerformance() {
        // Test: Measure search performance with large dataset
        
        // Given - Create a large dataset
        List<TestProduct> products = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            products.add(TestProduct.builder()
                .name("Search Product " + i)
                .description("This is product " + i + " for search performance testing")
                .category("Search")
                .price(new BigDecimal("100.00"))
                .active(true)
                .build());
        }

        // Process all products
        products = productRepository.saveAll(products);
        for (TestProduct product : products) {
            capabilityService.processEntityForAI(product, "test-product");
        }

        // When - Perform multiple searches
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < 10; i++) {
            List<AISearchableEntity> results = searchRepository.findBySearchableContentContainingIgnoreCase("product");
            assertFalse(results.isEmpty(), "Should find results for each search");
        }
        
        long endTime = System.currentTimeMillis();

        // Then
        long searchTime = endTime - startTime;
        assertTrue(searchTime < 5000, "Search operations should complete within 5 seconds");
        
        System.out.println("✅ Search performance for 10 queries: " + searchTime + "ms");
        System.out.println("Average time per search: " + (searchTime / 10) + "ms");
    }

    @Test
    public void testMemoryUsage() {
        // Test: Monitor memory usage during processing
        
        // Given
        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        
        List<TestProduct> products = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            products.add(TestProduct.builder()
                .name("Memory Test Product " + i)
                .description("This is product " + i + " for memory usage testing with a longer description to simulate real-world data")
                .category("Memory")
                .price(new BigDecimal("200.00"))
                .active(true)
                .build());
        }

        // When
        products = productRepository.saveAll(products);
        for (TestProduct product : products) {
            capabilityService.processEntityForAI(product, "test-product");
        }

        // Then
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = finalMemory - initialMemory;
        
        // Memory usage should be reasonable (less than 100MB for 20 entities)
        assertTrue(memoryUsed < 100 * 1024 * 1024, "Memory usage should be reasonable");
        
        System.out.println("✅ Memory usage for 20 entities: " + (memoryUsed / 1024 / 1024) + "MB");
    }

    @Test
    public void testCleanupPerformance() {
        // Test: Measure cleanup performance
        
        // Given - Create and process entities
        List<TestProduct> products = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            products.add(TestProduct.builder()
                .name("Cleanup Product " + i)
                .description("Description for cleanup product " + i)
                .category("Cleanup")
                .price(new BigDecimal("75.00"))
                .active(true)
                .build());
        }

        products = productRepository.saveAll(products);
        for (TestProduct product : products) {
            capabilityService.processEntityForAI(product, "test-product");
        }

        // Verify entities exist
        List<AISearchableEntity> entities = searchRepository.findByEntityType("test-product");
        assertEquals(10, entities.size(), "Should have 10 entities before cleanup");

        // When - Clean up entities
        long startTime = System.currentTimeMillis();
        
        for (TestProduct product : products) {
            productRepository.delete(product);
            capabilityService.removeEntityFromIndex(product.getId().toString(), "test-product");
        }
        
        long endTime = System.currentTimeMillis();

        // Then
        long cleanupTime = endTime - startTime;
        assertTrue(cleanupTime < 3000, "Cleanup should complete within 3 seconds");
        
        List<AISearchableEntity> remainingEntities = searchRepository.findByEntityType("test-product");
        assertTrue(remainingEntities.isEmpty(), "Should remove all entities");

        System.out.println("✅ Cleanup performance for 10 entities: " + cleanupTime + "ms");
    }
}