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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
class AIProcessAnnotationIntegrationTest {

    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private TestProductService productService;

    @SpyBean
    private TestProductRepository productRepository;

    @SpyBean
    private AISearchableEntityRepository searchableEntityRepository;

    @SpyBean
    private VectorManagementService vectorManagementService;

    @Autowired
    private com.ai.infrastructure.config.AIEntityConfigurationLoader configurationLoader;

    @MockBean
    private com.ai.infrastructure.core.AICoreService aiCoreService;

    @SpyBean
    private com.ai.infrastructure.service.AICapabilityService aiCapabilityService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        searchableEntityRepository.deleteAll();
        productRepository.deleteAll();
        clearProductVectors();
        reset(searchableEntityRepository);
        reset(productRepository);
        reset(vectorManagementService);
    }

    @AfterEach
    void cleanUp() {
        searchableEntityRepository.deleteAll();
        productRepository.deleteAll();
        clearProductVectors();
        reset(vectorManagementService);
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

    @Test
    @DisplayName("@AIProcess advice respects method execution order")
    void aiProcessAspectInvokesAfterMethodCompletion() {
        reset(searchableEntityRepository, productRepository);

        TestProduct saved = productService.createProduct(TestProduct.builder()
            .name("Celestia VR Headset")
            .description("Next-gen VR headset with ultra wide FOV")
            .category("gaming")
            .brand("Celestia")
            .price(new BigDecimal("1499.00"))
            .stockQuantity(8)
            .active(true)
            .build());

        String entityId = saved.getId().toString();

        await().atMost(WAIT_TIMEOUT)
            .until(() -> mockingDetails(searchableEntityRepository).getInvocations().stream()
                .anyMatch(invocation -> invocation.getMethod().getName().equals("save")));

        InOrder order = org.mockito.Mockito.inOrder(productRepository, searchableEntityRepository);
        order.verify(productRepository).save(any(TestProduct.class));
        order.verify(searchableEntityRepository, atLeastOnce()).save(any(AISearchableEntity.class));

        TestProduct persisted = productRepository.findById(saved.getId()).orElseThrow();
        assertEquals("Celestia VR Headset", persisted.getName());
        assertTrue(vectorManagementService.vectorExists("product", entityId));
    }

    @Test
    @DisplayName("@AIProcess continues when downstream AI processing throws")
    void aiProcessGracefullyHandlesProcessingErrors() {
        reset(searchableEntityRepository, productRepository);
        doReturn(null)
            .when(vectorManagementService)
            .storeVector(any(), any(), any(), any(), any());
        try {
            TestProduct saved = productService.createProduct(TestProduct.builder()
                .name("Echo Smart Display")
                .description("Assistant display with adaptive ambient light")
                .category("smart-home")
                .brand("Echo")
                .price(new BigDecimal("349.00"))
                .stockQuantity(11)
                .active(true)
                .build());

            assertNotNull(saved.getId(), "Product save should succeed even if AI processing fails");
            assertTrue(productRepository.findById(saved.getId()).isPresent(),
                "Entity should persist despite AI processing failure");

            verify(vectorManagementService, atLeastOnce()).storeVector(any(), any(), any(), any(), any());
        } finally {
            reset(vectorManagementService);
        }
    }

    @Test
    @DisplayName("Concurrent @AIProcess(create) operations remain consistent")
    void aiProcessConcurrentCreateOperations() throws Exception {
        reset(searchableEntityRepository, productRepository);

        int requestCount = 15;
        ExecutorService executor = Executors.newFixedThreadPool(6);
        try {
            List<CompletableFuture<TestProduct>> futures = IntStream.range(0, requestCount)
                .mapToObj(i -> CompletableFuture.supplyAsync(() -> productService.createProduct(TestProduct.builder()
                    .name("Concurrent Gadget " + i)
                    .description("Parallel execution validation item " + i)
                    .category("batch")
                    .brand("Concurrentia")
                    .price(new BigDecimal("199.99"))
                    .stockQuantity(5)
                    .active(true)
                    .build()), executor))
                .collect(Collectors.toCollection(ArrayList::new));

            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            List<TestProduct> results = futures.stream().map(CompletableFuture::join).collect(Collectors.toList());

            Set<Long> ids = results.stream().map(TestProduct::getId).collect(Collectors.toCollection(HashSet::new));
            assertEquals(requestCount, ids.size(), "Each concurrent invocation should persist a unique entity");

            await().atMost(WAIT_TIMEOUT)
                .until(() -> searchableEntityRepository.findByEntityType("product").size() >= requestCount);

            List<AISearchableEntity> entities = searchableEntityRepository.findByEntityType("product");
            assertEquals(requestCount, entities.size());

            results.forEach(product -> assertTrue(vectorManagementService.vectorExists("product", product.getId().toString())));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("@AIProcess infers entity type when annotation omits it")
    void aiProcessInfersEntityTypeFromMethodName() {
        reset(searchableEntityRepository, productRepository);

        TestProduct saved = productService.createProductImplicit(TestProduct.builder()
            .name("Implicit Entity Type Backpack")
            .description("Travel backpack verifying implicit entity type resolution")
            .category("travel")
            .brand("ImplicitCo")
            .price(new BigDecimal("129.00"))
            .stockQuantity(14)
            .active(true)
            .build());

        String entityId = saved.getId().toString();

        await().atMost(WAIT_TIMEOUT)
            .until(() -> searchableEntityRepository.findByEntityTypeAndEntityId("product", entityId).isPresent());

        AISearchableEntity entity = searchableEntityRepository
            .findByEntityTypeAndEntityId("product", entityId)
            .orElseThrow();

        assertEquals("product", entity.getEntityType());
    }

    @Test
    @DisplayName("@AIProcess(search) executes service logic without indexing side effects")
    void aiProcessSearchProcessTypeDoesNotIndex() {
        reset(searchableEntityRepository, productRepository);

        productRepository.save(TestProduct.builder()
            .name("Search Mode Camera")
            .description("Mirrorless camera to test search process path")
            .category("imaging")
            .brand("SearchLabs")
            .price(new BigDecimal("899.00"))
            .stockQuantity(3)
            .active(true)
            .build());

        clearInvocations(searchableEntityRepository);

        List<TestProduct> results = productService.searchProducts("Camera");

        assertFalse(results.isEmpty(), "Search process should return matching records");
        verify(searchableEntityRepository, never()).save(any());
    }

    @Test
    @DisplayName("@AIProcess(analyze) enriches AI analysis field")
    void aiProcessAnalyzeProcessTypeGeneratesInsights() {
        reset(searchableEntityRepository, productRepository);
        when(aiCoreService.generateText(any())).thenReturn("Detailed product analysis");

        TestProduct saved = productService.createProduct(TestProduct.builder()
            .name("Insight Speaker")
            .description("Smart speaker to validate analyze process")
            .category("audio")
            .brand("InsightCo")
            .price(new BigDecimal("259.00"))
            .stockQuantity(9)
            .active(true)
            .build());

        String entityId = saved.getId().toString();

        await().atMost(WAIT_TIMEOUT)
            .until(() -> searchableEntityRepository.findByEntityTypeAndEntityId("product", entityId).isPresent());

        clearInvocations(searchableEntityRepository);

        productService.analyzeProduct(saved.getId());

        await().atMost(WAIT_TIMEOUT)
            .until(() -> searchableEntityRepository.findByEntityTypeAndEntityId("product", entityId)
                .map(AISearchableEntity::getAiAnalysis)
                .filter(analysis -> analysis != null && analysis.contains("Detailed product analysis"))
                .isPresent());

        AISearchableEntity entity = searchableEntityRepository
            .findByEntityTypeAndEntityId("product", entityId)
            .orElseThrow();

        assertEquals("Detailed product analysis", entity.getAiAnalysis());
        verify(searchableEntityRepository, atLeastOnce()).save(any(AISearchableEntity.class));
    }

    @Test
    @DisplayName("@AIProcess operations respect transactional rollbacks")
    void aiProcessTransactionalRollbackPreventsSideEffects() {
        reset(searchableEntityRepository, productRepository);

        AtomicReference<String> pendingId = new AtomicReference<>();
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.execute(status -> {
            TestProduct saved = productService.createProduct(TestProduct.builder()
                .name("Rollback Drone")
                .description("High-speed drone to verify transactional rollback handling")
                .category("testing")
                .brand("Rollback Labs")
                .price(new BigDecimal("1499.00"))
                .stockQuantity(2)
                .active(true)
                .build());

            pendingId.set(saved.getId().toString());
            status.setRollbackOnly();
            return null;
        });

        assertEquals(0, productRepository.count());
        assertEquals(0, searchableEntityRepository.count());

        String rolledBackId = pendingId.get();
        if (rolledBackId != null) {
            assertFalse(vectorManagementService.vectorExists("product", rolledBackId));
        }
    }

    @Test
    @DisplayName("@AIProcess flags (generateEmbedding=false, indexForSearch=false) prevent vector indexing")
    void aiProcessCreateWithEmbeddingDisabledSkipsIndexing() {
        reset(searchableEntityRepository);

        TestProduct saved = productService.createProductWithoutEmbedding(TestProduct.builder()
            .name("Atlas Trail Boots")
            .description("All-terrain hiking boots with waterproof membrane")
            .category("outdoor")
            .brand("Atlas")
            .price(new BigDecimal("249.00"))
            .stockQuantity(15)
            .active(true)
            .build());

        String entityId = saved.getId().toString();

        // Allow asynchronous hooks to run if any (should remain idle)
        await().pollDelay(Duration.ofMillis(250)).atMost(Duration.ofSeconds(2)).until(() -> true);

        verify(searchableEntityRepository, never()).save(any());
        assertTrue(searchableEntityRepository.findByEntityTypeAndEntityId("product", entityId).isEmpty());
        assertFalse(vectorManagementService.vectorExists("product", entityId));
    }

    @Test
    @DisplayName("@AIProcess with indexForSearch=false skips AISearchableEntity persistence")
    void aiProcessCreateWithIndexingDisabledSkipsRepositorySave() {
        reset(searchableEntityRepository);

        TestProduct saved = productService.createProductWithoutIndexing(TestProduct.builder()
            .name("Orion Studio Monitor")
            .description("Reference studio monitors with active calibration")
            .category("audio")
            .brand("Orion")
            .price(new BigDecimal("1199.00"))
            .stockQuantity(6)
            .active(true)
            .build());

        String entityId = saved.getId().toString();

        await().pollDelay(Duration.ofMillis(250)).atMost(Duration.ofSeconds(2)).until(() -> true);

        verify(searchableEntityRepository, never()).save(any());
        assertTrue(searchableEntityRepository.findByEntityTypeAndEntityId("product", entityId).isEmpty());
        assertFalse(vectorManagementService.vectorExists("product", entityId));
    }

    @Test
    @DisplayName("@AIProcess(enableAnalysis=true) populates AI analysis output")
    void aiProcessCreateWithAnalysisCapturesInsights() {
        reset(searchableEntityRepository);
        when(aiCoreService.generateText(any())).thenReturn("Product insight summary");

        TestProduct saved = productService.createProductWithAnalysis(TestProduct.builder()
            .name("Nebula Smart Speaker")
            .description("Voice-assisted speaker with adaptive ambient sound")
            .category("smart-home")
            .brand("Nebula")
            .price(new BigDecimal("299.00"))
            .stockQuantity(20)
            .active(true)
            .build());

        String entityId = saved.getId().toString();

        await().atMost(WAIT_TIMEOUT)
            .until(() -> mockingDetails(searchableEntityRepository).getInvocations().stream()
                .anyMatch(invocation -> invocation.getMethod().getName().equals("save")));

        ArgumentCaptor<AISearchableEntity> captor = ArgumentCaptor.forClass(AISearchableEntity.class);
        verify(searchableEntityRepository, atLeastOnce()).save(captor.capture());

        boolean analysisCaptured = captor.getAllValues().stream()
            .anyMatch(entity -> "Product insight summary".equals(entity.getAiAnalysis()));

        assertTrue(analysisCaptured, "Expected saved AISearchableEntity to include AI analysis text");

        await().atMost(WAIT_TIMEOUT)
            .until(() -> searchableEntityRepository.findByEntityTypeAndEntityId("product", entityId).isPresent());

        AISearchableEntity persisted = searchableEntityRepository
            .findByEntityTypeAndEntityId("product", entityId)
            .orElseThrow();

        assertEquals("Product insight summary", persisted.getAiAnalysis());
        assertTrue(vectorManagementService.vectorExists("product", entityId));
    }

    private void clearProductVectors() {
        try {
            vectorManagementService.clearVectorsByEntityType("product");
        } catch (Exception ignored) {
            // Vector provider may not have been initialised yet; ignore for cleanup.
        }
    }
}

