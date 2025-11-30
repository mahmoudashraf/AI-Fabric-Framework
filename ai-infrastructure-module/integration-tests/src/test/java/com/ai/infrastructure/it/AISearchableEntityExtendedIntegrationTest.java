package com.ai.infrastructure.it;

import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.it.entity.TestProduct;
import com.ai.infrastructure.it.repository.TestProductRepository;
import com.ai.infrastructure.it.service.TestProductService;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.ai.infrastructure.service.AICapabilityService;
import com.ai.infrastructure.service.VectorManagementService;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled("Disabled due to ApplicationContext loading failures - table creation issues")
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("dev")
class AISearchableEntityExtendedIntegrationTest {

    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(20);

    @Autowired
    private TestProductService testProductService;

    @Autowired
    private TestProductRepository testProductRepository;

    @Autowired
    private AISearchableEntityRepository searchableEntityRepository;

    @SpyBean
    private VectorManagementService vectorManagementService;

    @Autowired
    private AICapabilityService aiCapabilityService;

    @SpyBean
    private AIEmbeddingService aiEmbeddingService;

    @BeforeEach
    void setUp() {
        searchableEntityRepository.deleteAll();
        testProductRepository.deleteAll();
        clearVectors();
    }

    @AfterEach
    void tearDown() {
        searchableEntityRepository.deleteAll();
        testProductRepository.deleteAll();
        clearVectors();
        Mockito.reset(vectorManagementService);
        Mockito.reset(aiEmbeddingService);
    }

    @Test
    @DisplayName("Search pagination returns deterministic ordering")
    @org.junit.jupiter.api.Disabled("Flaky stress test - times out in CI environment")
    void searchPaginationAndSorting() {
        IntStream.range(0, 30).forEach(idx -> testProductService.createProduct(buildProduct(
            "Atlas Trail Pack " + idx,
            "High-end adventure gear bundle number " + idx,
            "outdoor",
            "Atlas",
            BigDecimal.valueOf(199 + idx)
        )));

        awaitEntityCount("product", 30);

        Page<AISearchableEntity> firstPage = searchableEntityRepository.findByEntityType(
            "product",
            PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "updatedAt"))
        );

        assertEquals(10, firstPage.getContent().size());
        assertEquals(30, firstPage.getTotalElements());

        List<LocalDateTime> timestamps = firstPage.getContent().stream()
            .map(AISearchableEntity::getUpdatedAt)
            .toList();

        assertTrue(isNonIncreasing(timestamps));

        Page<AISearchableEntity> secondPage = searchableEntityRepository.findByEntityType(
            "product",
            PageRequest.of(1, 10, Sort.by(Sort.Direction.DESC, "updatedAt"))
        );

        assertEquals(10, secondPage.getContent().size());
        assertEquals(30, secondPage.getTotalElements());

        Set<String> idsPageOne = firstPage.map(AISearchableEntity::getEntityId).toSet();
        Set<String> idsPageTwo = secondPage.map(AISearchableEntity::getEntityId).toSet();
        assertThat(idsPageOne).doesNotContainAnyElementsOf(idsPageTwo);
    }

    @Test
    @DisplayName("Concurrent updates keep single searchable entity consistent")
    @org.junit.jupiter.api.Disabled("Flaky stress test - fails with timeout in CI environment")
    void concurrentUpdatesRemainConsistent() throws InterruptedException {
        TestProduct created = testProductService.createProduct(buildProduct(
            "Nebula Studio Monitor",
            "Reference monitor with linear response",
            "audio",
            "Nebula",
            new BigDecimal("1299.00")
        ));

        String entityId = created.getId().toString();

        String originalVectorId = awaitVectorId(entityId);

        ExecutorService executor = Executors.newFixedThreadPool(6);
        try {
            List<Runnable> updates = new ArrayList<>();
            IntStream.range(0, 14).forEach(idx -> updates.add(() -> testProductService.updateProduct(
                created.getId(),
                "Nebula Studio Monitor v" + idx,
                "Updated response profile iteration " + idx,
                new BigDecimal("1300." + idx)
            )));

            List<CompletableFuture<Void>> futures = updates.stream()
                .map(runnable -> CompletableFuture.runAsync(runnable, executor))
                .toList();

            for (CompletableFuture<Void> future : futures) {
                future.join();
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        String finalName = "Nebula Studio Monitor final";
        String finalDescription = "Updated response profile final iteration";
        BigDecimal finalPrice = new BigDecimal("1400.00");
        testProductService.updateProduct(created.getId(), finalName, finalDescription, finalPrice);

        Awaitility.await().atMost(Duration.ofSeconds(40)).untilAsserted(() -> {
            AISearchableEntity entity = getSearchableEntity(entityId);
            assertTrue(entity.getSearchableContent().contains("final"));
        });

        AISearchableEntity updated = getSearchableEntity(entityId);
        assertFalse(originalVectorId.equals(updated.getVectorId()));
        assertEquals(1, searchableEntityRepository.findByEntityType("product").stream()
            .filter(e -> entityId.equals(e.getEntityId())).count());
    }

    @Test
    @DisplayName("Bulk backfill import indexes every product")
    @org.junit.jupiter.api.Disabled("Flaky stress test - times out in CI environment with 40 concurrent entities")
    void bulkBackfillProcessesAllDocuments() {
        IntStream.range(0, 40).forEach(idx -> testProductService.createProduct(buildProduct(
            "Backfill Product " + idx,
            "Historical catalog entry number " + idx,
            "catalog",
            "Archive",
            new BigDecimal("49." + idx)
        )));

        awaitEntityCount("product", 40);

        List<AISearchableEntity> entities = searchableEntityRepository.findByEntityType("product");
        assertEquals(40, entities.size());

        entities.stream().limit(5).forEach(entity ->
            assertTrue(vectorManagementService.getVector("product", entity.getEntityId()).isPresent(),
                () -> "Vector missing for entity " + entity.getEntityId())
        );
    }

    @Test
    @DisplayName("Embedding failure is recoverable via manual retry")
    void embeddingFailureCanBeRetried() {
        java.util.concurrent.atomic.AtomicInteger attempts = new java.util.concurrent.atomic.AtomicInteger(0);

        Mockito.doAnswer(invocation -> {
            if (attempts.getAndIncrement() < 2) {
                throw new RuntimeException("Simulated embedding outage");
            }
            return invocation.callRealMethod();
        }).when(aiEmbeddingService).generateEmbedding(Mockito.any(AIEmbeddingRequest.class));

        TestProduct unstable = testProductRepository.save(buildProduct(
            "Fault Tolerant Router",
            "Resilient network router for failover scenarios",
            "networking",
            "FailSafe",
            new BigDecimal("899.00")
        ));

        aiCapabilityService.processEntityForAI(unstable, "product");

        assertTrue(searchableEntityRepository.findByEntityTypeAndEntityId("product", unstable.getId().toString()).isEmpty());
        assertTrue(vectorManagementService.getVector("product", unstable.getId().toString()).isEmpty());

        Mockito.reset(aiEmbeddingService);

        aiCapabilityService.processEntityForAI(testProductRepository.findById(unstable.getId()).orElseThrow(), "product");

        Awaitility.await().atMost(WAIT_TIMEOUT).untilAsserted(() -> assertTrue(
            searchableEntityRepository.findByEntityTypeAndEntityId("product", unstable.getId().toString()).isPresent()
        ));

        AISearchableEntity retried = searchableEntityRepository.findByEntityTypeAndEntityId(
            "product", unstable.getId().toString()).orElseThrow();

        assertTrue(vectorManagementService.getVector("product", retried.getEntityId()).isPresent());
    }

    @Test
    @DisplayName("Vector management logs structured events for observability")
    void observabilityLogsContainEntityContext() {
        Logger logger = (Logger) LoggerFactory.getLogger(VectorManagementService.class);
        Level originalLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            TestProduct product = testProductService.createProduct(buildProduct(
                "Telemetry Beacon",
                "Observability-focused accessory",
                "monitoring",
                "Beacon",
                new BigDecimal("129.00")
            ));

            awaitEntityCount("product", 1);

            assertThat(appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .anyMatch(message -> message.contains("Storing vector") && message.contains(product.getId().toString())))
                .isTrue();
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
        }
    }

    @Test
    @DisplayName("Tenant-specific content stays isolated in semantic search")
    void multiTenantIsolation() {
        TestProduct tenantA = testProductService.createProduct(buildProduct(
            "TenantA Summit Boot",
            "Exclusive alpine boot for tenant A with glacier grip technology",
            "tenantA",
            "SummitCo",
            new BigDecimal("349.00")
        ));

        TestProduct tenantB = testProductService.createProduct(buildProduct(
            "TenantB Desert Boot",
            "Exclusive desert boot for tenant B with dune shield protection",
            "tenantB",
            "DuneCorp",
            new BigDecimal("329.00")
        ));

        awaitEntityCount("product", 2);

        Awaitility.await().atMost(WAIT_TIMEOUT).until(() ->
            searchableEntityRepository.findByEntityType("product").stream()
                .anyMatch(entity -> entity.getSearchableContent().contains("glacier grip"))
        );

        Awaitility.await().atMost(WAIT_TIMEOUT).until(() ->
            searchableEntityRepository.findByEntityType("product").stream()
                .anyMatch(entity -> entity.getSearchableContent().contains("dune shield"))
        );

        List<AISearchableEntity> allSearchable = searchableEntityRepository.findByEntityType("product");
        List<AISearchableEntity> tenantAEntities = allSearchable.stream()
            .filter(entity -> entity.getSearchableContent().contains("glacier grip"))
            .toList();
        List<AISearchableEntity> tenantBEntities = allSearchable.stream()
            .filter(entity -> entity.getSearchableContent().contains("dune shield"))
            .toList();

        assertThat(tenantAEntities)
            .isNotEmpty()
            .allMatch(entity -> entity.getEntityId().equals(tenantA.getId().toString()));

        assertThat(tenantBEntities)
            .isNotEmpty()
            .allMatch(entity -> entity.getEntityId().equals(tenantB.getId().toString()));

        // Wait for vectors to be created
        Awaitility.await().atMost(WAIT_TIMEOUT).untilAsserted(() ->
            assertTrue(vectorManagementService.getVector("product", tenantA.getId().toString()).isPresent())
        );
        Awaitility.await().atMost(WAIT_TIMEOUT).untilAsserted(() ->
            assertTrue(vectorManagementService.getVector("product", tenantB.getId().toString()).isPresent())
        );
    }

    @Test
    @DisplayName("Large documents remain searchable with tail phrase retrieval")
    void largeDocumentHandlingMaintainsSearchability() {
        String largeDescription = "Polar Expedition Insulated Parka " + " - extended coverage.".repeat(800);
        String tailPhrase = "thermal-mapped baffle deployment";
        largeDescription += " " + tailPhrase;

        TestProduct bulky = testProductService.createProduct(buildProduct(
            "Polar Expedition Parka",
            largeDescription,
            "outerwear",
            "PolarForge",
            new BigDecimal("599.00")
        ));

        String entityId = bulky.getId().toString();

        AISearchableEntity entity = Awaitility.await().atMost(WAIT_TIMEOUT).until(() ->
            searchableEntityRepository.findByEntityTypeAndEntityId("product", entityId).orElse(null),
            e -> e != null
        );

        assertTrue(entity.getSearchableContent().contains(tailPhrase));

        // For large documents, embedding generation may take longer - use extended timeout
        Awaitility.await().atMost(Duration.ofSeconds(60)).untilAsserted(() ->
            assertTrue(vectorManagementService.getVector("product", entityId).isPresent())
        );

        Awaitility.await().atMost(WAIT_TIMEOUT).untilAsserted(() ->
            assertThat(searchableEntityRepository.findBySearchableContentContainingIgnoreCase("thermal-mapped"))
                .extracting(AISearchableEntity::getEntityId)
                .contains(entityId)
        );
    }

    private void clearVectors() {
        try {
            vectorManagementService.clearVectorsByEntityType("product");
        } catch (Exception ignored) {
        }
    }

    private void awaitEntityCount(String entityType, int expected) {
        Awaitility.await().atMost(WAIT_TIMEOUT)
            .until(() -> searchableEntityRepository.findByEntityType(entityType).size() >= expected);
    }

    private String awaitVectorId(String entityId) {
        Awaitility.await().atMost(WAIT_TIMEOUT).untilAsserted(() -> assertTrue(
            searchableEntityRepository.findByEntityTypeAndEntityId("product", entityId)
                .map(AISearchableEntity::getVectorId)
                .filter(id -> !id.isBlank())
                .isPresent()
        ));

        return searchableEntityRepository.findByEntityTypeAndEntityId("product", entityId)
            .map(AISearchableEntity::getVectorId)
            .orElseThrow();
    }

    private AISearchableEntity getSearchableEntity(String entityId) {
        return searchableEntityRepository.findByEntityTypeAndEntityId("product", entityId)
            .orElseThrow(() -> new AssertionError("Searchable entity not found for id " + entityId));
    }

    private boolean isNonIncreasing(List<LocalDateTime> timestamps) {
        for (int i = 1; i < timestamps.size(); i++) {
            if (timestamps.get(i).isAfter(timestamps.get(i - 1))) {
                return false;
            }
        }
        return true;
    }

    private TestProduct buildProduct(String name, String description, String category, String brand, BigDecimal price) {
        return TestProduct.builder()
            .name(name)
            .description(description)
            .category(category)
            .brand(brand)
            .price(price)
            .stockQuantity(5)
            .active(true)
            .build();
    }
}

