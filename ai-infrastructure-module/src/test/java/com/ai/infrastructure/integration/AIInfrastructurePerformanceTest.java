package com.ai.infrastructure.integration;

import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import com.ai.infrastructure.dto.AIEntityConfig;
import com.ai.infrastructure.dto.AISearchableField;
import com.ai.infrastructure.dto.AIEmbeddableField;
import com.ai.infrastructure.service.AICapabilityService;
import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Performance and Load Tests for AI Infrastructure Module
 * Tests system performance under various load conditions
 */
@ExtendWith(MockitoExtension.class)
public class AIInfrastructurePerformanceTest {

    @Mock
    private AIEntityConfigurationLoader configLoader;

    @Mock
    private AISearchableEntityRepository searchRepository;

    private AICapabilityService capabilityService;

    @BeforeEach
    public void setUp() {
        capabilityService = new AICapabilityService(configLoader, searchRepository);
        
        // Setup mock configuration
        AIEntityConfig config = createTestConfig();
        when(configLoader.getEntityConfig("product")).thenReturn(config);
    }

    @Test
    public void testConcurrentEntityProcessing() throws InterruptedException {
        // Given
        int numberOfThreads = 10;
        int entitiesPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        List<Future<Void>> futures = new ArrayList<>();

        // When
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < numberOfThreads; i++) {
            final int threadId = i;
            Future<Void> future = executor.submit(() -> {
                try {
                    for (int j = 0; j < entitiesPerThread; j++) {
                        TestProduct product = new TestProduct();
                        product.setId((long) (threadId * entitiesPerThread + j));
                        product.setName("Product " + (threadId * entitiesPerThread + j));
                        product.setDescription("Description for product " + (threadId * entitiesPerThread + j));
                        
                        capabilityService.processEntity(product, "create");
                    }
                } finally {
                    latch.countDown();
                }
                return null;
            });
            futures.add(future);
        }

        // Wait for all threads to complete
        assertTrue(latch.await(30, TimeUnit.SECONDS), "All threads should complete within 30 seconds");
        
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        int totalEntities = numberOfThreads * entitiesPerThread;

        // Then
        verify(searchRepository, times(totalEntities)).save(any(AISearchableEntity.class));
        
        System.out.println("Processed " + totalEntities + " entities in " + totalTime + "ms");
        System.out.println("Average time per entity: " + (totalTime / (double) totalEntities) + "ms");
        
        // Performance assertions
        assertTrue(totalTime < 10000, "Processing should complete within 10 seconds");
        assertTrue((totalTime / (double) totalEntities) < 10, "Average processing time per entity should be less than 10ms");
    }

    @Test
    public void testBulkEntityProcessing() {
        // Given
        List<TestProduct> products = IntStream.range(0, 1000)
            .mapToObj(i -> {
                TestProduct product = new TestProduct();
                product.setId((long) i);
                product.setName("Bulk Product " + i);
                product.setDescription("Bulk description " + i);
                return product;
            })
            .toList();

        // When
        long startTime = System.currentTimeMillis();
        
        for (TestProduct product : products) {
            capabilityService.processEntity(product, "create");
        }
        
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;

        // Then
        verify(searchRepository, times(1000)).save(any(AISearchableEntity.class));
        
        System.out.println("Processed 1000 entities in bulk in " + totalTime + "ms");
        System.out.println("Average time per entity: " + (totalTime / 1000.0) + "ms");
        
        // Performance assertions
        assertTrue(totalTime < 5000, "Bulk processing should complete within 5 seconds");
    }

    @Test
    public void testSearchPerformance() {
        // Given
        List<AISearchableEntity> mockResults = IntStream.range(0, 1000)
            .mapToObj(i -> {
                AISearchableEntity entity = new AISearchableEntity();
                entity.setEntityId((long) i);
                entity.setEntityType("product");
                entity.setContent("Product " + i + " description");
                entity.setEmbeddings(Arrays.asList(0.1, 0.2, 0.3));
                return entity;
            })
            .toList();

        when(searchRepository.findByEntityType("product")).thenReturn(mockResults);

        // When
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < 100; i++) {
            capabilityService.searchEntities("test query " + i, "product");
        }
        
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;

        // Then
        verify(searchRepository, times(100)).findByEntityType("product");
        
        System.out.println("Performed 100 searches in " + totalTime + "ms");
        System.out.println("Average time per search: " + (totalTime / 100.0) + "ms");
        
        // Performance assertions
        assertTrue(totalTime < 2000, "Search operations should complete within 2 seconds");
    }

    @Test
    public void testMemoryUsage() {
        // Given
        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();

        // When
        List<TestProduct> products = IntStream.range(0, 10000)
            .mapToObj(i -> {
                TestProduct product = new TestProduct();
                product.setId((long) i);
                product.setName("Memory Test Product " + i);
                product.setDescription("Memory test description " + i);
                return product;
            })
            .toList();

        for (TestProduct product : products) {
            capabilityService.processEntity(product, "create");
        }

        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = finalMemory - initialMemory;

        // Then
        System.out.println("Memory used for 10000 entities: " + (memoryUsed / 1024 / 1024) + " MB");
        
        // Memory usage should be reasonable (less than 100MB for 10000 entities)
        assertTrue(memoryUsed < 100 * 1024 * 1024, "Memory usage should be less than 100MB for 10000 entities");
    }

    @Test
    public void testStressTest() throws InterruptedException {
        // Given
        int numberOfThreads = 20;
        int operationsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        List<Future<Void>> futures = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        // When
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < numberOfThreads; i++) {
            final int threadId = i;
            Future<Void> future = executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        try {
                            TestProduct product = new TestProduct();
                            product.setId((long) (threadId * operationsPerThread + j));
                            product.setName("Stress Test Product " + (threadId * operationsPerThread + j));
                            product.setDescription("Stress test description " + (threadId * operationsPerThread + j));
                            
                            capabilityService.processEntity(product, "create");
                            successCount.incrementAndGet();
                        } catch (Exception e) {
                            errorCount.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
                return null;
            });
            futures.add(future);
        }

        // Wait for all threads to complete
        assertTrue(latch.await(60, TimeUnit.SECONDS), "All threads should complete within 60 seconds");
        
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;

        // Then
        int totalOperations = numberOfThreads * operationsPerThread;
        System.out.println("Stress test completed:");
        System.out.println("Total operations: " + totalOperations);
        System.out.println("Successful operations: " + successCount.get());
        System.out.println("Failed operations: " + errorCount.get());
        System.out.println("Total time: " + totalTime + "ms");
        System.out.println("Operations per second: " + (totalOperations * 1000.0 / totalTime));

        // Performance and reliability assertions
        assertTrue(successCount.get() > totalOperations * 0.95, "Success rate should be at least 95%");
        assertTrue(errorCount.get() < totalOperations * 0.05, "Error rate should be less than 5%");
        assertTrue(totalTime < 30000, "Stress test should complete within 30 seconds");
    }

    private AIEntityConfig createTestConfig() {
        AIEntityConfig config = new AIEntityConfig();
        config.setEntityType("product");
        config.setSearchableFields(Arrays.asList(
            createSearchableField("name", 1.0),
            createSearchableField("description", 0.8)
        ));
        config.setEmbeddableFields(Arrays.asList(
            createEmbeddableField("name", "text"),
            createEmbeddableField("description", "text")
        ));
        return config;
    }

    private AISearchableField createSearchableField(String fieldName, Double weight) {
        AISearchableField field = new AISearchableField();
        field.setFieldName(fieldName);
        field.setWeight(weight);
        return field;
    }

    private AIEmbeddableField createEmbeddableField(String fieldName, String fieldType) {
        AIEmbeddableField field = new AIEmbeddableField();
        field.setFieldName(fieldName);
        field.setFieldType(fieldType);
        return field;
    }

    // Test entity class
    public static class TestProduct {
        private Long id;
        private String name;
        private String description;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
}