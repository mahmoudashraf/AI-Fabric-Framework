package com.ai.infrastructure.it;

import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.dto.VectorRecord;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.ai.infrastructure.service.VectorManagementService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SearchPerformanceAtScaleIntegrationTest {

    private static final int VECTOR_DIMENSION = 24;
    private static final int BATCH_SIZE = 1_000;
    private static final int SEARCH_LIMIT = 20;
    private static final int SEARCH_ITERATIONS = 25;
    private static final List<Integer> DATASET_SIZES = List.of(100, 1_000, 10_000, 100_000);
    private static final String INDEX_PATH = "./data/test-lucene-index/search-performance-" + UUID.randomUUID();

    @Autowired
    private VectorManagementService vectorManagementService;

    @Autowired
    private AISearchableEntityRepository searchableEntityRepository;

    @DynamicPropertySource
    static void configureLuceneIndex(DynamicPropertyRegistry registry) {
        registry.add("ai.vector-db.lucene.index-path", () -> INDEX_PATH);
    }

    @Test
    @DisplayName("Semantic search latency stays within SLAs up to 100K indexed vectors")
    void searchPerformanceRemainsWithinTargets() {
        Map<Integer, SearchMetrics> metricsBySize = DATASET_SIZES.stream()
            .collect(Collectors.toMap(size -> size, this::benchmarkDataset, (a, b) -> a, LinkedHashMap::new));

        metricsBySize.forEach((size, metrics) -> {
            assertTrue(metrics.averageLatencyMs() <= targetLatency(size),
                () -> String.format("Average latency %dms exceeds target %dms for %,d vectors", 
                    metrics.averageLatencyMs(), targetLatency(size), size));

            assertTrue(metrics.p95LatencyMs() <= maxLatency(size),
                () -> String.format("p95 latency %dms exceeds maximum %dms for %,d vectors",
                    metrics.p95LatencyMs(), maxLatency(size), size));

            assertTrue(metrics.memoryDeltaMb() <= memoryBudget(size),
                () -> String.format("Memory delta %dMB exceeds budget %dMB for %,d vectors",
                    metrics.memoryDeltaMb(), memoryBudget(size), size));

            assertTrue(metrics.medianResultCount() >= Math.min(SEARCH_LIMIT, size),
                () -> String.format("Expected at least %d results but observed %d for %,d vectors",
                    Math.min(SEARCH_LIMIT, size), metrics.medianResultCount(), size));
        });
    }

    private SearchMetrics benchmarkDataset(int size) {
        String entityType = String.format("search-scale-%d-%s", size, UUID.randomUUID());
        Runtime runtime = Runtime.getRuntime();

        invokeGc(runtime);
        long baselineMemory = usedMemory(runtime);

        Stream.iterate(0, offset -> offset < size, offset -> offset + BATCH_SIZE)
            .forEach(offset -> {
                int batchLength = Math.min(BATCH_SIZE, size - offset);
                List<VectorRecord> batch = IntStream.range(offset, offset + batchLength)
                    .mapToObj(index -> VectorRecord.builder()
                        .entityType(entityType)
                        .entityId(entityType + "-entity-" + index)
                        .content("Search performance content " + index)
                        .embedding(syntheticEmbedding(VECTOR_DIMENSION, index))
                        .metadata(Map.of(
                            "ordinal", index,
                            "datasetSize", size
                        ))
                        .build())
                    .toList();

                List<String> storedIds = vectorManagementService.batchStoreVectors(batch);
                assertEquals(batchLength, storedIds.size(),
                    "Each stored vector should return a corresponding vector ID");
            });

        await().atMost(Duration.ofSeconds(30)).until(() ->
            searchableEntityRepository.countByEntityTypeAndVectorIdIsNotNull(entityType) >= size
        );

        List<Double> queryVector = syntheticEmbedding(VECTOR_DIMENSION, size / 2);
        List<SearchIteration> iterations = IntStream.range(0, SEARCH_ITERATIONS)
            .mapToObj(iteration -> performSearch(entityType, queryVector))
            .toList();

        long[] latenciesNs = iterations.stream()
            .mapToLong(SearchIteration::latencyNs)
            .sorted()
            .toArray();

        long averageMs = Math.round(Arrays.stream(latenciesNs)
            .average()
            .orElse(0.0) / 1_000_000.0);

        long p95Ms = latenciesNs.length == 0 ? 0 :
            Math.round(latenciesNs[(int) Math.ceil(latenciesNs.length * 0.95) - 1] / 1_000_000.0);

        int medianResultCount = iterations.stream()
            .mapToInt(SearchIteration::resultCount)
            .sorted()
            .skip((iterations.size() - 1L) / 2)
            .findFirst()
            .orElse(0);

        invokeGc(runtime);
        long memoryDeltaMb = Math.max(0, (usedMemory(runtime) - baselineMemory) / (1024 * 1024));

        clearDataset(entityType);

        return new SearchMetrics(averageMs, p95Ms, memoryDeltaMb, medianResultCount);
    }

    private SearchIteration performSearch(String entityType, List<Double> queryVector) {
        long start = System.nanoTime();
        AISearchResponse response = vectorManagementService.searchByEntityType(queryVector, entityType, SEARCH_LIMIT, 0.0);
        long latencyNs = System.nanoTime() - start;

        List<Map<String, Object>> results = Optional.ofNullable(response)
            .map(AISearchResponse::getResults)
            .orElseGet(List::of);

        assertNotNull(response, "Search response should never be null");
        assertFalse(results.isEmpty(), "Search should return results for stored entity type");

        return new SearchIteration(latencyNs, results.size());
    }

    private List<Double> syntheticEmbedding(int dimension, int seed) {
        return IntStream.range(0, dimension)
            .mapToDouble(index -> (seed + index + 1) * 0.0001d)
            .boxed()
            .toList();
    }

    private void clearDataset(String entityType) {
        vectorManagementService.clearVectorsByEntityType(entityType);
        searchableEntityRepository.deleteByEntityType(entityType);
    }

    private void invokeGc(Runtime runtime) {
        System.gc();
        System.runFinalization();
        try {
            Thread.sleep(25L);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private long usedMemory(Runtime runtime) {
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private long targetLatency(int size) {
        if (size <= 100) {
            return 20;
        }
        if (size <= 1_000) {
            return 50;
        }
        if (size <= 10_000) {
            return 100;
        }
        return 500;
    }

    private long maxLatency(int size) {
        if (size <= 100) {
            return 50;
        }
        if (size <= 1_000) {
            return 100;
        }
        if (size <= 10_000) {
            return 200;
        }
        return 1_000;
    }

    private long memoryBudget(int size) {
        if (size <= 1_000) {
            return 128;
        }
        if (size <= 10_000) {
            return 256;
        }
        return 768;
    }

    @AfterAll
    void cleanIndexDirectory() throws IOException {
        Path path = Path.of(INDEX_PATH).toAbsolutePath().normalize();
        if (Files.exists(path)) {
            try (Stream<Path> paths = Files.walk(path).sorted(Comparator.reverseOrder())) {
                paths.forEach(candidate -> {
                    try {
                        Files.deleteIfExists(candidate);
                    } catch (IOException ignored) {
                        // best-effort cleanup
                    }
                });
            }
        }
    }

    private record SearchIteration(long latencyNs, int resultCount) {
    }

    private record SearchMetrics(long averageLatencyMs, long p95LatencyMs, long memoryDeltaMb, int medianResultCount) {
    }
}

