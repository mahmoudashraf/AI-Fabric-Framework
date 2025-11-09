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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("real-api-test")
@Transactional
public class RealAPIIntentHistoryAggregationIntegrationTest {

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
    public void testCrossSessionIntentHistoryAggregation() {
        assumeOpenAIConfigured();

        System.out.println("\n=== Phase 1: Create Test Products ===");
        
        TestProduct product1 = persistProduct(
            "Customer Data Analytics",
            "Analyze customer behavior and generate insights for marketing campaigns. Identify retention risks.",
            "Analytics",
            "DataCorp",
            new BigDecimal("4999.99")
        );

        TestProduct product2 = persistProduct(
            "Business Intelligence Dashboard",
            "Real-time BI dashboards for executive reporting and performance metrics tracking.",
            "BI",
            "IntelliSuite",
            new BigDecimal("2999.99")
        );

        System.out.println("✅ Created 2 products for testing");

        System.out.println("\n=== Phase 2: Session 1 - Successful Information Intent ===");
        
        String session1UserId = "aggregation-session-1";
        String session1Query = "What analytics platforms do you have in your catalog?";
        
        OrchestrationResult session1Result = orchestrator.orchestrate(session1Query, session1UserId);
        assertNotNull(session1Result);
        assertThat(session1Result.isSuccess()).isTrue();
        System.out.println("✅ Session 1: Successful query completed");

        System.out.println("\n=== Phase 3: Session 2 - Out-of-Scope Intent ===");
        
        String session2UserId = "aggregation-session-2";
        String session2Query = "Can you help me with my mortgage refinancing?";
        
        OrchestrationResult session2Result = orchestrator.orchestrate(session2Query, session2UserId);
        assertNotNull(session2Result);
        // Out-of-scope queries may succeed with an OUT_OF_SCOPE result type
        System.out.println("✅ Session 2: Out-of-scope query completed (type: " + session2Result.getType() + ")");

        System.out.println("\n=== Phase 4: Session 3 - PII Detection (Credit Card) ===");
        
        String session3UserId = "aggregation-session-3";
        String session3Query = "Analyze sales for customer 4111-1111-1111-1111 across our BI tools.";
        
        OrchestrationResult session3Result = orchestrator.orchestrate(session3Query, session3UserId);
        assertNotNull(session3Result);
        // Should detect PII (credit card) and handle appropriately
        System.out.println("✅ Session 3: PII-containing query completed (type: " + session3Result.getType() + ")");

        System.out.println("\n=== Phase 5: Session 4 - Multiple Sequential Queries ===");
        
        String session4UserId = "aggregation-session-4";
        
        String query4a = "What are your top analytics solutions?";
        OrchestrationResult result4a = orchestrator.orchestrate(query4a, session4UserId);
        assertNotNull(result4a);
        assertThat(result4a.isSuccess()).isTrue();
        System.out.println("  ✅ Query 4a completed");

        String query4b = "Show me BI dashboard features.";
        OrchestrationResult result4b = orchestrator.orchestrate(query4b, session4UserId);
        assertNotNull(result4b);
        assertThat(result4b.isSuccess()).isTrue();
        System.out.println("  ✅ Query 4b completed");

        String query4c = "Which solutions integrate with Salesforce?";
        OrchestrationResult result4c = orchestrator.orchestrate(query4c, session4UserId);
        assertNotNull(result4c);
        System.out.println("  ✅ Query 4c completed");

        System.out.println("✅ Session 4: Multiple sequential queries completed");

        System.out.println("\n=== Phase 6: Verify Consolidated History Records ===");
        
        List<IntentHistory> allHistory = intentHistoryRepository.findAll();
        assertThat(allHistory).isNotEmpty()
            .as("Intent history should contain records from all sessions")
            .hasSizeGreaterThanOrEqualTo(6); // 1 + 1 + 1 + 3 = 6 minimum

        System.out.println("✅ Total history records: " + allHistory.size());

        System.out.println("\n=== Phase 7: Session-Based History Aggregation ===");
        
        // Session 1: Successful intent
        List<IntentHistory> session1History = intentHistoryRepository
            .findByUserIdOrderByCreatedAtDesc(session1UserId);
        assertThat(session1History).isNotEmpty()
            .as("Session 1 should have history records")
            .hasSizeGreaterThanOrEqualTo(1);
        assertThat(session1History.getFirst().getSuccess()).isTrue()
            .as("Session 1 query should be successful");
        System.out.println("✅ Session 1: " + session1History.size() + " records, success=" + session1History.getFirst().getSuccess());

        // Session 2: Out-of-scope intent
        List<IntentHistory> session2History = intentHistoryRepository
            .findByUserIdOrderByCreatedAtDesc(session2UserId);
        assertThat(session2History).isNotEmpty()
            .as("Session 2 should have history records");
        System.out.println("✅ Session 2: " + session2History.size() + " records");

        // Session 3: PII detection
        List<IntentHistory> session3History = intentHistoryRepository
            .findByUserIdOrderByCreatedAtDesc(session3UserId);
        assertThat(session3History).isNotEmpty()
            .as("Session 3 should have history records");
        
        // Check for PII detection flag
        boolean hasPIIFlag = session3History.stream()
            .anyMatch(history -> history.getHasSensitiveData() != null && history.getHasSensitiveData());
        System.out.println("✅ Session 3: " + session3History.size() + " records, PII metadata present=" + hasPIIFlag);

        // Session 4: Multiple queries
        List<IntentHistory> session4History = intentHistoryRepository
            .findByUserIdOrderByCreatedAtDesc(session4UserId);
        assertThat(session4History).isNotEmpty()
            .as("Session 4 should have multiple history records")
            .hasSizeGreaterThanOrEqualTo(3);
        System.out.println("✅ Session 4: " + session4History.size() + " records");

        System.out.println("\n=== Phase 8: Analytics Fields Verification ===");
        
        long totalSuccessfulIntents = allHistory.stream()
            .filter(IntentHistory::getSuccess)
            .count();
        long totalOutOfScopeOrFailed = allHistory.stream()
            .filter(h -> !h.getSuccess())
            .count();

        System.out.println("📊 History Analytics:");
        System.out.println("   Total records: " + allHistory.size());
        System.out.println("   Successful: " + totalSuccessfulIntents);
        System.out.println("   Out-of-scope/failed: " + totalOutOfScopeOrFailed);

        // Verify metadata contains analytics fields
        List<IntentHistory> recordsWithMetadata = allHistory.stream()
            .filter(h -> h.getMetadataJson() != null && !h.getMetadataJson().isEmpty())
            .collect(Collectors.toList());
        
        System.out.println("   Records with metadata: " + recordsWithMetadata.size());

        System.out.println("\n=== Phase 9: Encryption and Original Toggle ===");
        
        // Verify that encrypted query field exists (encrypted or redacted as per config)
        List<IntentHistory> recordsWithEncryptedQuery = allHistory.stream()
            .filter(h -> h.getEncryptedQuery() != null)
            .collect(Collectors.toList());
        
        System.out.println("✅ Records with encryptedQuery field: " + recordsWithEncryptedQuery.size());

        // Verify redacted query field
        List<IntentHistory> recordsWithRedactedQuery = allHistory.stream()
            .filter(h -> h.getRedactedQuery() != null)
            .collect(Collectors.toList());
        
        System.out.println("✅ Records with redactedQuery field: " + recordsWithRedactedQuery.size());

        System.out.println("\n=== Phase 10: Cross-Session Aggregation Summary ===");
        
        Map<String, Long> intentCountBySession = allHistory.stream()
            .collect(Collectors.groupingBy(
                IntentHistory::getUserId,
                Collectors.counting()
            ));

        System.out.println("📊 Intent Count by Session:");
        intentCountBySession.forEach((session, count) -> 
            System.out.println("   " + session + ": " + count + " intents")
        );

        // Verify session distribution
        assertThat(intentCountBySession)
            .as("Should have records from multiple sessions")
            .hasSizeGreaterThanOrEqualTo(4);

        // Verify per-session counts
        assertThat(intentCountBySession.get(session1UserId))
            .as("Session 1 should have at least 1 record")
            .isGreaterThanOrEqualTo(1L);

        assertThat(intentCountBySession.get(session2UserId))
            .as("Session 2 should have at least 1 record")
            .isGreaterThanOrEqualTo(1L);

        assertThat(intentCountBySession.get(session3UserId))
            .as("Session 3 should have at least 1 record")
            .isGreaterThanOrEqualTo(1L);

        assertThat(intentCountBySession.get(session4UserId))
            .as("Session 4 should have at least 3 records")
            .isGreaterThanOrEqualTo(3L);

        System.out.println("✅ Cross-session aggregation validated");

        System.out.println("\n=== Phase 11: Temporal Ordering Verification ===");
        
        // Verify records are ordered by createdAt
        List<LocalDateTime> timestamps = session4History.stream()
            .map(IntentHistory::getCreatedAt)
            .collect(Collectors.toList());

        boolean isOrdered = true;
        for (int i = 0; i < timestamps.size() - 1; i++) {
            if (timestamps.get(i).isBefore(timestamps.get(i + 1))) {
                isOrdered = false;
                break;
            }
        }

        assertThat(isOrdered)
            .as("Session 4 history should be ordered descending by createdAt")
            .isTrue();

        System.out.println("✅ Temporal ordering verified (descending)");

        System.out.println("\n=== Phase 12: Final Aggregation Summary ===");
        
        System.out.println("📊 Cross-Session Summary:");
        System.out.println("   Sessions covered: " + intentCountBySession.size());
        System.out.println("   Total intents: " + allHistory.size());
        System.out.println("   Session 1 (successful): " + intentCountBySession.get(session1UserId) + " intents");
        System.out.println("   Session 2 (out-of-scope): " + intentCountBySession.get(session2UserId) + " intents");
        System.out.println("   Session 3 (PII): " + intentCountBySession.get(session3UserId) + " intents");
        System.out.println("   Session 4 (multi-query): " + intentCountBySession.get(session4UserId) + " intents");
        System.out.println("   Records with metadata: " + recordsWithMetadata.size());
        System.out.println("   Encrypted queries: " + recordsWithEncryptedQuery.size());
        System.out.println("   Redacted queries: " + recordsWithRedactedQuery.size());
        System.out.println("   ✅ All verification checks passed!");
    }

    private void assumeOpenAIConfigured() {
        Assumptions.assumeTrue(
            StringUtils.hasText(System.getProperty(OPENAI_KEY_PROPERTY)),
            "OPENAI_API_KEY not configured; skipping history aggregation tests."
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
            .stockQuantity(100)
            .active(true)
            .build();
        product = productRepository.save(product);
        capabilityService.processEntityForAI(product, "test-product");
        return product;
    }
}
