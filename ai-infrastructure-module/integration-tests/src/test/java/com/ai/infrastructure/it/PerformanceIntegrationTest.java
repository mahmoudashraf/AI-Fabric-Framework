package com.ai.infrastructure.it;

import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.it.entity.TestProduct;
import com.ai.infrastructure.it.repository.TestProductRepository;
import com.ai.infrastructure.it.support.OnnxBackedEmbeddingService;
import com.ai.infrastructure.it.support.OnnxTestConfiguration;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.ai.infrastructure.service.AICapabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance Integration Test exercising AI pipelines with ONNX-backed embeddings and
 * real OpenAI analysis. Dataset sizes are calibrated to keep runtime predictable while
 * still validating batching, concurrency, search latency, memory usage, and large dataset flows.
 */
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("dev")
@Import(OnnxTestConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class PerformanceIntegrationTest {

    private static final int BATCH_SIZE = 3;
    private static final int CONCURRENT_BATCHES = 2;
    private static final int CONCURRENT_BATCH_SIZE = 2;
    private static final int SEARCH_DATASET_SIZE = 8;
    private static final int MEMORY_TEST_SIZE = 4;
    private static final int LARGE_DATASET_SIZE = 8;

    private static final String ENTITY_TYPE_PRODUCT = "test-product";

    @Autowired
    private AICapabilityService capabilityService;

    @Autowired
    private AISearchableEntityRepository searchRepository;

    @Autowired
    private TestProductRepository productRepository;

    @Autowired
    private AIEmbeddingService embeddingService;

    @BeforeEach
    public void setUp() {
        assertTrue(embeddingService instanceof OnnxBackedEmbeddingService, "ONNX embedding service should be active");
        searchRepository.deleteAll();
        productRepository.deleteAll();
    }

    @DynamicPropertySource
    static void overrideLuceneIndexPath(DynamicPropertyRegistry registry) {
        registry.add("ai.vector.lucene.index-path", () -> "./data/test-lucene-index/performance");
    }

    @Test
    public void testBatchProcessingPerformance() {
        System.out.println("⚡ Testing Batch Processing Performance...");
        
        // Given - Create a large batch of products
        List<TestProduct> products = new ArrayList<>();

        for (int i = 0; i < BATCH_SIZE; i++) {
            products.add(TestProduct.builder()
                .name("AI Product " + i)
                .description("High-performance AI product with advanced machine learning capabilities for batch processing test " + i)
                .category("AI")
                .brand("TechCorp")
                .price(new BigDecimal(100.00 + i))
                .sku("AI-" + String.format("%03d", i))
                .stockQuantity(10 + i)
                .active(true)
                .build());
        }

        // When - Process batch and measure time
        Instant start = Instant.now();
        products = productRepository.saveAll(products);
        
        for (TestProduct product : products) {
            capabilityService.processEntityForAI(product, ENTITY_TYPE_PRODUCT);
        }
        Instant end = Instant.now();

        // Then - Verify performance
        Duration duration = Duration.between(start, end);
        long totalTimeMs = duration.toMillis();
        double avgTimePerEntity = (double) totalTimeMs / BATCH_SIZE;

        System.out.println("✅ Batch Processing Performance Test Passed");
        System.out.println("   - Batch size: " + BATCH_SIZE);
        System.out.println("   - Total time: " + totalTimeMs + "ms");
        System.out.println("   - Average time per entity: " + String.format("%.2f", avgTimePerEntity) + "ms");
        System.out.println("   - Entities per second: " + String.format("%.2f", 1000.0 / avgTimePerEntity));

        // Verify all entities were processed
        List<AISearchableEntity> allEntities = searchRepository.findByEntityType(ENTITY_TYPE_PRODUCT);
        assertEquals(BATCH_SIZE, allEntities.size(), "Should process all products in batch");

        // Performance assertions
        long maxAllowedTotal = BATCH_SIZE * 12_000L;
        assertTrue(totalTimeMs < maxAllowedTotal, "Batch processing should complete within an allowance of 12s per entity");
        assertTrue(avgTimePerEntity < 12_000, "Average processing time per entity should be under 12 seconds");
    }

    @Test
    public void testConcurrentProcessingPerformance() {
        System.out.println("⚡ Testing Concurrent Processing Performance...");
        
        // Given - Create products for concurrent processing
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_BATCHES);

        try {
            // When - Process batches concurrently
            Instant start = Instant.now();
            
            List<CompletableFuture<Void>> futures = IntStream.range(0, CONCURRENT_BATCHES)
                .mapToObj(batchIndex -> CompletableFuture.runAsync(() -> {
                    List<TestProduct> products = new ArrayList<>();
                    for (int i = 0; i < CONCURRENT_BATCH_SIZE; i++) {
                        int productId = batchIndex * CONCURRENT_BATCH_SIZE + i;
                        products.add(TestProduct.builder()
                            .name("Concurrent AI Product " + productId)
                            .description("AI product processed concurrently in batch " + batchIndex + " with ID " + productId)
                            .category("AI")
                            .brand("ConcurrentCorp")
                            .price(new BigDecimal(50.00 + productId))
                            .sku("CONC-" + String.format("%03d", productId))
                            .stockQuantity(5)
                            .active(true)
                            .build());
                    }
                    
                    products = productRepository.saveAll(products);
                    for (TestProduct product : products) {
                        capabilityService.processEntityForAI(product, ENTITY_TYPE_PRODUCT);
                    }
                }, executor))
                .toList();

            // Wait for all futures to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            
            Instant end = Instant.now();

            // Then - Verify performance
            Duration duration = Duration.between(start, end);
            long totalTimeMs = duration.toMillis();
            int totalEntities = CONCURRENT_BATCHES * CONCURRENT_BATCH_SIZE;
            double avgTimePerEntity = (double) totalTimeMs / totalEntities;

            System.out.println("✅ Concurrent Processing Performance Test Passed");
            System.out.println("   - Concurrent batches: " + CONCURRENT_BATCHES);
            System.out.println("   - Batch size: " + CONCURRENT_BATCH_SIZE);
            System.out.println("   - Total entities: " + totalEntities);
            System.out.println("   - Total time: " + totalTimeMs + "ms");
            System.out.println("   - Average time per entity: " + String.format("%.2f", avgTimePerEntity) + "ms");
            System.out.println("   - Entities per second: " + String.format("%.2f", 1000.0 / avgTimePerEntity));

            // Verify all entities were processed
            List<AISearchableEntity> allEntities = searchRepository.findByEntityType(ENTITY_TYPE_PRODUCT);
            assertEquals(totalEntities, allEntities.size(), "Should process all products concurrently");

            // Performance assertions
            long maxAllowedTotal = totalEntities * 12_000L;
            assertTrue(totalTimeMs < maxAllowedTotal, "Concurrent processing should complete within an allowance of 12s per entity");
            assertTrue(avgTimePerEntity < 12_000, "Average processing time per entity should be under 12 seconds");

        } finally {
            executor.shutdown();
        }
    }

    @Test
    public void testSearchPerformance() {
        System.out.println("⚡ Testing Search Performance...");
        
        // Given - Create a large dataset for search testing
        int datasetSize = SEARCH_DATASET_SIZE;
        List<TestProduct> products = new ArrayList<>();
        
        for (int i = 0; i < datasetSize; i++) {
            String category = i % 4 == 0 ? "AI" : (i % 4 == 1 ? "Electronics" : (i % 4 == 2 ? "Software" : "Hardware"));
            products.add(TestProduct.builder()
                .name("Search Test Product " + i)
                .description("Product " + i + " in category " + category + " for search performance testing")
                .category(category)
                .brand("SearchCorp")
                .price(new BigDecimal(10.00 + i))
                .sku("SEARCH-" + String.format("%03d", i))
                .stockQuantity(1)
                .active(true)
                .build());
        }

        // Process all products
        products = productRepository.saveAll(products);
        for (TestProduct product : products) {
            capabilityService.processEntityForAI(product, ENTITY_TYPE_PRODUCT);
        }

        // When - Perform various search operations and measure time
        Instant start = Instant.now();
        
        // Test different search patterns
        List<AISearchableEntity> aiResults = filterByEntityType(
            searchRepository.findBySearchableContentContainingIgnoreCase("AI"), ENTITY_TYPE_PRODUCT);
        List<AISearchableEntity> electronicsResults = filterByEntityType(
            searchRepository.findBySearchableContentContainingIgnoreCase("Electronics"), ENTITY_TYPE_PRODUCT);
        List<AISearchableEntity> softwareResults = filterByEntityType(
            searchRepository.findBySearchableContentContainingIgnoreCase("Software"), ENTITY_TYPE_PRODUCT);
        List<AISearchableEntity> hardwareResults = filterByEntityType(
            searchRepository.findBySearchableContentContainingIgnoreCase("Hardware"), ENTITY_TYPE_PRODUCT);
        List<AISearchableEntity> allResults = searchRepository.findByEntityType(ENTITY_TYPE_PRODUCT);
        
        Instant end = Instant.now();

        // Then - Verify performance
        Duration duration = Duration.between(start, end);
        long totalTimeMs = duration.toMillis();

        System.out.println("✅ Search Performance Test Passed");
        System.out.println("   - Dataset size: " + datasetSize);
        System.out.println("   - Search operations: 5");
        System.out.println("   - Total search time: " + totalTimeMs + "ms");
        System.out.println("   - Average time per search: " + String.format("%.2f", (double) totalTimeMs / 5) + "ms");
        System.out.println("   - AI results: " + aiResults.size());
        System.out.println("   - Electronics results: " + electronicsResults.size());
        System.out.println("   - Software results: " + softwareResults.size());
        System.out.println("   - Hardware results: " + hardwareResults.size());
        System.out.println("   - Total results: " + allResults.size());

        // Verify search results
        assertTrue(aiResults.size() > 0, "Should find AI products");
        assertTrue(electronicsResults.size() > 0, "Should find Electronics products");
        assertTrue(softwareResults.size() > 0, "Should find Software products");
        assertTrue(hardwareResults.size() > 0, "Should find Hardware products");
        assertEquals(datasetSize, allResults.size(), "Should find all products");

        // Performance assertions
        assertTrue(totalTimeMs < 5000, "Search operations should complete within 5 seconds");
    }

    @Test
    public void testMemoryUsagePerformance() {
        System.out.println("⚡ Testing Memory Usage Performance...");
        
        // Given - Create products to test memory usage
        int memoryTestSize = MEMORY_TEST_SIZE;
        List<TestProduct> products = new ArrayList<>();
        
        for (int i = 0; i < memoryTestSize; i++) {
            products.add(TestProduct.builder()
                .name("Memory Test Product " + i)
                .description("This is a detailed description for memory testing. " +
                    "It contains multiple sentences to simulate realistic content. " +
                    "The description includes technical terms and specifications. " +
                    "This helps test memory usage during embedding generation and storage. " +
                    "Product " + i + " is designed for comprehensive memory testing scenarios.")
                .category("MemoryTest")
                .brand("MemoryCorp")
                .price(new BigDecimal(100.00 + i))
                .sku("MEM-" + String.format("%03d", i))
                .stockQuantity(10)
                .active(true)
                .build());
        }

        // When - Process products and measure memory impact
        Runtime runtime = Runtime.getRuntime();
        runtime.gc(); // Force garbage collection before measurement
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        products = productRepository.saveAll(products);
        for (TestProduct product : products) {
            capabilityService.processEntityForAI(product, ENTITY_TYPE_PRODUCT);
        }

        runtime.gc(); // Force garbage collection after processing
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = memoryAfter - memoryBefore;

        // Then - Verify memory usage
        List<AISearchableEntity> allEntities = searchRepository.findByEntityType(ENTITY_TYPE_PRODUCT);
        assertEquals(memoryTestSize, allEntities.size(), "Should process all products");

        double memoryPerEntity = (double) memoryUsed / memoryTestSize;
        double memoryPerEntityKB = memoryPerEntity / 1024;

        System.out.println("✅ Memory Usage Performance Test Passed");
        System.out.println("   - Products processed: " + memoryTestSize);
        System.out.println("   - Memory used: " + (memoryUsed / 1024) + " KB");
        System.out.println("   - Memory per entity: " + String.format("%.2f", memoryPerEntityKB) + " KB");
        System.out.println("   - Total entities in database: " + allEntities.size());

        // Memory usage assertions
        assertTrue(memoryUsed < 25 * 1024 * 1024, "Memory usage should be under 25MB for the scenario");
        assertTrue(memoryPerEntityKB < 1024, "Memory per entity should be under 1MB");
    }

    @Test
    public void testLargeDatasetPerformance() {
        System.out.println("⚡ Testing Large Dataset Performance...");
        
        // Given - Create a large dataset
        int largeDatasetSize = LARGE_DATASET_SIZE;
        List<TestProduct> products = new ArrayList<>();
        
        for (int i = 0; i < largeDatasetSize; i++) {
            products.add(TestProduct.builder()
                .name("Large Dataset Product " + i)
                .description("Product " + i + " in large dataset for performance testing with comprehensive AI capabilities")
                .category("LargeDataset")
                .brand("LargeCorp")
                .price(new BigDecimal(1.00 + i))
                .sku("LARGE-" + String.format("%04d", i))
                .stockQuantity(1)
                .active(true)
                .build());
        }

        // When - Process large dataset
        Instant start = Instant.now();
        products = productRepository.saveAll(products);
        
        for (TestProduct product : products) {
            capabilityService.processEntityForAI(product, ENTITY_TYPE_PRODUCT);
        }
        Instant end = Instant.now();

        // Then - Verify performance
        Duration duration = Duration.between(start, end);
        long totalTimeMs = duration.toMillis();
        double avgTimePerEntity = (double) totalTimeMs / largeDatasetSize;

        System.out.println("✅ Large Dataset Performance Test Passed");
        System.out.println("   - Dataset size: " + largeDatasetSize);
        System.out.println("   - Total time: " + totalTimeMs + "ms");
        System.out.println("   - Average time per entity: " + String.format("%.2f", avgTimePerEntity) + "ms");
        System.out.println("   - Entities per second: " + String.format("%.2f", 1000.0 / avgTimePerEntity));

        // Verify all entities were processed
        List<AISearchableEntity> allEntities = searchRepository.findByEntityType(ENTITY_TYPE_PRODUCT);
        assertEquals(largeDatasetSize, allEntities.size(), "Should process all products in large dataset");

        // Performance assertions for large dataset
        long maxAllowedTotal = largeDatasetSize * 12_000L;
        assertTrue(totalTimeMs < maxAllowedTotal, "Large dataset processing should complete within an allowance of 12s per entity");
        assertTrue(avgTimePerEntity < 12_000, "Average processing time per entity should be under 12 seconds");
    }

    private List<AISearchableEntity> filterByEntityType(List<AISearchableEntity> entities, String entityType) {
        return entities.stream()
            .filter(entity -> entityType.equals(entity.getEntityType()))
            .collect(Collectors.toList());
    }
}
