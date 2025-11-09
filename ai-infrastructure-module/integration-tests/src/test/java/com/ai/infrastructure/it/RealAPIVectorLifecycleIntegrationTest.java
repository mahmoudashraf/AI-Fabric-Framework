package com.ai.infrastructure.it;

import com.ai.infrastructure.config.ResponseSanitizationProperties;
import com.ai.infrastructure.entity.IntentHistory;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationResultType;
import com.ai.infrastructure.intent.orchestration.RAGOrchestrator;
import com.ai.infrastructure.it.entity.TestProduct;
import com.ai.infrastructure.it.repository.TestProductRepository;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.ai.infrastructure.repository.IntentHistoryRepository;
import com.ai.infrastructure.service.AICapabilityService;
import com.ai.infrastructure.service.VectorManagementService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("real-api-test")
@Transactional
public class RealAPIVectorLifecycleIntegrationTest {

    private static final String OPENAI_KEY_PROPERTY = "OPENAI_API_KEY";
    private static final Path[] CANDIDATE_ENV_PATHS = new Path[] {
        Paths.get("../env.dev"),
        Paths.get("../../env.dev"),
        Paths.get("../../../env.dev"),
        Paths.get("../backend/env.dev"),
        Paths.get("../../backend/env.dev"),
        Paths.get("/workspace/env.dev")
    };

    static {
        initializeOpenAIConfiguration();
    }

    private static void initializeOpenAIConfiguration() {
        String apiKey = System.getenv(OPENAI_KEY_PROPERTY);
        if (!StringUtils.hasText(apiKey)) {
            apiKey = locateKeyFromEnvFiles();
        }

        if (StringUtils.hasText(apiKey)) {
            System.setProperty(OPENAI_KEY_PROPERTY, apiKey);
            System.setProperty("ai.providers.openai-api-key", apiKey);
        }

        System.setProperty("EMBEDDING_PROVIDER",
            System.getProperty("EMBEDDING_PROVIDER", "openai"));
        System.setProperty("ai.providers.embedding-provider",
            System.getProperty("ai.providers.embedding-provider", "openai"));
    }

    private static String locateKeyFromEnvFiles() {
        for (Path path : CANDIDATE_ENV_PATHS) {
            if (Files.exists(path) && Files.isRegularFile(path)) {
                String key = readKeyFromEnvFile(path, OPENAI_KEY_PROPERTY);
                if (StringUtils.hasText(key)) {
                    return key;
                }
            }
        }
        return null;
    }

    private static String readKeyFromEnvFile(Path file, String keyName) {
        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            return lines
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#") && line.contains("="))
                .map(line -> line.split("=", 2))
                .filter(parts -> parts.length == 2 && keyName.equals(parts[0].trim()))
                .map(parts -> parts[1].trim())
                .findFirst()
                .orElse(null);
        } catch (IOException ex) {
            System.err.printf("Unable to read %s from %s: %s%n", keyName, file, ex.getMessage());
            return null;
        }
    }

    @Autowired
    private AICapabilityService capabilityService;

    @Autowired
    private VectorManagementService vectorManagementService;

    @Autowired
    private RAGOrchestrator orchestrator;

    @Autowired
    private IntentHistoryRepository intentHistoryRepository;

    @Autowired
    private TestProductRepository productRepository;

    @Autowired
    private AISearchableEntityRepository searchRepository;

    @Autowired
    private ResponseSanitizationProperties sanitizationProperties;

    @BeforeEach
    public void setUp() {
        vectorManagementService.clearAllVectors();
        searchRepository.deleteAll();
        productRepository.deleteAll();
        intentHistoryRepository.deleteAll();
    }

    @Test
    public void testVectorLifecycleSynchronization() {
        assumeOpenAIConfigured();

        System.out.println("\n=== Phase 1: Create Entities with Vectors ===");
        
        TestProduct product1 = persistProduct(
            "Enterprise AI Analytics",
            """
                Real-time analytics platform powered by machine learning.
                Processes high-volume data streams with predictive insights.
                """,
            "Analytics",
            "DataFlow",
            new BigDecimal("5999.99")
        );

        TestProduct product2 = persistProduct(
            "Compliance Automation Suite",
            """
                Automates regulatory compliance workflows across departments.
                Generates audit trails and compliance reports automatically.
                """,
            "Compliance",
            "ComplianceHub",
            new BigDecimal("3999.99")
        );

        String productId1 = product1.getId().toString();
        String productId2 = product2.getId().toString();

        // Verify vectors exist after creation
        assertThat(vectorManagementService.vectorExists("test-product", productId1))
            .as("Vector should exist after product creation")
            .isTrue();
        assertThat(vectorManagementService.vectorExists("test-product", productId2))
            .as("Vector should exist after product creation")
            .isTrue();

        System.out.println("✅ Both vectors created successfully");

        System.out.println("\n=== Phase 2: Query with Vectors Present ===");
        
        String userId1 = "lifecycle-user-phase1";
        String query1 = "What analytics solutions do you offer?";
        
        OrchestrationResult result1 = orchestrator.orchestrate(query1, userId1);
        assertNotNull(result1);
        assertThat(result1.isSuccess()).isTrue();
        
        System.out.println("✅ Query executed successfully with vectors present");

        System.out.println("\n=== Phase 3: Execute remove_vector Action ===");
        
        String userId2 = "lifecycle-user-phase3";
        String removeQuery = String.format(
            "Execute the remove_vector action with entityType 'test-product' and entityId '%s'. Confirm removal.",
            productId1
        );

        OrchestrationResult removeResult = orchestrator.orchestrate(removeQuery, userId2);
        assertNotNull(removeResult);
        assertThat(removeResult.getType()).isEqualTo(OrchestrationResultType.ACTION_EXECUTED);

        // Verify vector was removed
        assertThat(vectorManagementService.vectorExists("test-product", productId1))
            .as("Vector should be removed after remove_vector action")
            .isFalse();
        
        // Verify second vector still exists
        assertThat(vectorManagementService.vectorExists("test-product", productId2))
            .as("Other vector should remain intact")
            .isTrue();

        System.out.println("✅ remove_vector action executed successfully");

        System.out.println("\n=== Phase 4: Execute clear_vector_index Action ===");
        
        String userId3 = "lifecycle-user-phase4";
        String clearQuery = "Execute the clear_vector_index action with reason 'reseed'. Clear all vectors.";

        OrchestrationResult clearResult = orchestrator.orchestrate(clearQuery, userId3);
        assertNotNull(clearResult);
        assertThat(clearResult.getType()).isEqualTo(OrchestrationResultType.ACTION_EXECUTED);

        // Verify all vectors cleared
        assertThat(vectorManagementService.vectorExists("test-product", productId1))
            .as("Vector should be cleared")
            .isFalse();
        assertThat(vectorManagementService.vectorExists("test-product", productId2))
            .as("Vector should be cleared")
            .isFalse();

        System.out.println("✅ clear_vector_index action executed successfully");

        System.out.println("\n=== Phase 5: Rebuild Embeddings & Test Reseed ===");
        
        // Reprocess entities to rebuild vectors
        capabilityService.processEntityForAI(product1, "test-product");
        capabilityService.processEntityForAI(product2, "test-product");

        // Verify vectors exist again
        assertThat(vectorManagementService.vectorExists("test-product", productId1))
            .as("Vector should be rebuilt after reprocessing")
            .isTrue();
        assertThat(vectorManagementService.vectorExists("test-product", productId2))
            .as("Vector should be rebuilt after reprocessing")
            .isTrue();

        System.out.println("✅ Vectors rebuilt successfully");

        System.out.println("\n=== Phase 6: Query with Rebuilt Vectors ===");
        
        String userId4 = "lifecycle-user-phase6";
        String query2 = "Show me automation compliance solutions in your catalog.";

        OrchestrationResult result2 = orchestrator.orchestrate(query2, userId4);
        assertNotNull(result2);
        assertThat(result2.isSuccess()).isTrue();

        Map<String, Object> data = result2.getData();
        assertThat(data).isNotEmpty();

        System.out.println("✅ Query executed successfully with rebuilt vectors");

        System.out.println("\n=== Phase 7: Verify Intent History & TTL Tracking ===");
        
        List<IntentHistory> allHistory = intentHistoryRepository.findAll();
        assertThat(allHistory).isNotEmpty()
            .as("Intent history should record all operations")
            .hasSizeGreaterThanOrEqualTo(4);

        // Verify each phase recorded
        List<IntentHistory> phase1 = intentHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId1);
        List<IntentHistory> phase3 = intentHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId2);
        List<IntentHistory> phase4 = intentHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId3);
        List<IntentHistory> phase6 = intentHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId4);

        assertThat(phase1).isNotEmpty().as("Phase 1 (initial query) should be recorded");
        assertThat(phase3).isNotEmpty().as("Phase 3 (remove_vector) should be recorded");
        assertThat(phase4).isNotEmpty().as("Phase 4 (clear_vector_index) should be recorded");
        assertThat(phase6).isNotEmpty().as("Phase 6 (post-reseed query) should be recorded");

        // Verify execution statuses
        assertThat(phase1.getFirst().getSuccess()).isTrue();
        assertThat(phase3.getFirst().getSuccess()).isTrue();
        assertThat(phase4.getFirst().getSuccess()).isTrue();
        assertThat(phase6.getFirst().getSuccess()).isTrue();

        System.out.println("✅ Intent history properly tracked across all phases");

        System.out.println("\n=== Phase 8: Verify Lifecycle Transitions ===");
        
        System.out.println("📊 Vector Lifecycle Summary:");
        System.out.println("   1. ✅ Initial creation: 2 vectors");
        System.out.println("   2. ✅ Phase 1 query: vectors present");
        System.out.println("   3. ✅ Remove vector: 1 vector removed");
        System.out.println("   4. ✅ Clear index: all vectors cleared");
        System.out.println("   5. ✅ Reseed/rebuild: 2 vectors recreated");
        System.out.println("   6. ✅ Phase 6 query: rebuilt vectors used");
        System.out.println("   7. ✅ History tracking: 4+ phases recorded");
    }

    private void assumeOpenAIConfigured() {
        Assumptions.assumeTrue(
            StringUtils.hasText(System.getProperty(OPENAI_KEY_PROPERTY)),
            "OPENAI_API_KEY not configured; skipping vector lifecycle tests."
        );
    }

    private TestProduct persistProduct(String name,
                                       String description,
                                       String category,
                                       String brand,
                                       BigDecimal price) {
        TestProduct product = TestProduct.builder()
            .name(name)
            .description(description)
            .category(category)
            .brand(brand)
            .price(price)
            .stockQuantity(75)
            .active(true)
            .build();
        product = productRepository.save(product);
        capabilityService.processEntityForAI(product, "test-product");
        return product;
    }
}
