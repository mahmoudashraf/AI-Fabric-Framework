package com.ai.infrastructure.it;

import com.ai.infrastructure.config.ResponseSanitizationProperties;
import com.ai.infrastructure.entity.IntentHistory;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("real-api-test")
@Transactional
public class RealAPIPIIEdgeSpectrumIntegrationTest {

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
    public void testPIIDetectionEdgeSpectrum() {
        assumeOpenAIConfigured();

        System.out.println("\n=== PII Detection Edge Spectrum Test ===");

        System.out.println("\n=== Phase 1: Create Test Products ===");
        
        TestProduct product1 = persistProduct(
            "Security Platform",
            """
                Enterprise security and compliance solution.
                User identity verification and data protection services.
                """,
            "Security",
            "SecureTech",
            new BigDecimal("9999.99")
        );

        System.out.println("✅ Created test products");

        System.out.println("\n=== Phase 2: Test Credit Card PII Detection ===");
        
        String userId1 = "pii-test-creditcard";
        String creditCardQuery = "I used card 4111-1111-1111-1111 for my subscription purchase today.";
        
        OrchestrationResult result1 = orchestrator.orchestrate(creditCardQuery, userId1);
        assertNotNull(result1);
        
        // Verify credit card was detected
        List<IntentHistory> ccHistory = intentHistoryRepository
            .findByUserIdOrderByCreatedAtDesc(userId1);
        
        assertThat(ccHistory).isNotEmpty();
        
        IntentHistory ccRecord = ccHistory.getFirst();
        Boolean hasSensitive = ccRecord.getHasSensitiveData();
        
        System.out.println("✅ Credit card PII detection result: " + (hasSensitive != null && hasSensitive));
        assertThat(hasSensitive).isNotNull().isTrue();
        
        System.out.println("   Sensitive data types: " + ccRecord.getSensitiveDataTypes());

        System.out.println("\n=== Phase 3: Test Email PII Detection ===");
        
        String userId2 = "pii-test-email";
        String emailQuery = "Contact john.smith@example.com for account issues.";
        
        OrchestrationResult result2 = orchestrator.orchestrate(emailQuery, userId2);
        assertNotNull(result2);
        
        List<IntentHistory> emailHistory = intentHistoryRepository
            .findByUserIdOrderByCreatedAtDesc(userId2);
        
        assertThat(emailHistory).isNotEmpty();
        
        IntentHistory emailRecord = emailHistory.getFirst();
        Boolean emailSensitive = emailRecord.getHasSensitiveData();
        
        System.out.println("✅ Email PII detection result: " + (emailSensitive != null && emailSensitive));
        assertThat(emailSensitive).isNotNull().isTrue();

        System.out.println("\n=== Phase 4: Test Phone Number PII Detection ===");
        
        String userId3 = "pii-test-phone";
        String phoneQuery = "Call me at (555) 123-4567 for technical support.";
        
        OrchestrationResult result3 = orchestrator.orchestrate(phoneQuery, userId3);
        assertNotNull(result3);
        
        List<IntentHistory> phoneHistory = intentHistoryRepository
            .findByUserIdOrderByCreatedAtDesc(userId3);
        
        assertThat(phoneHistory).isNotEmpty();
        
        IntentHistory phoneRecord = phoneHistory.getFirst();
        Boolean phoneSensitive = phoneRecord.getHasSensitiveData();
        
        System.out.println("✅ Phone PII detection result: " + (phoneSensitive != null && phoneSensitive));
        assertThat(phoneSensitive).isNotNull().isTrue();

        System.out.println("\n=== Phase 5: Test SSN PII Detection ===");
        
        String userId4 = "pii-test-ssn";
        String ssnQuery = "My social security number is 123-45-6789 for verification.";
        
        OrchestrationResult result4 = orchestrator.orchestrate(ssnQuery, userId4);
        assertNotNull(result4);
        
        List<IntentHistory> ssnHistory = intentHistoryRepository
            .findByUserIdOrderByCreatedAtDesc(userId4);
        
        assertThat(ssnHistory).isNotEmpty();
        
        IntentHistory ssnRecord = ssnHistory.getFirst();
        Boolean ssnSensitive = ssnRecord.getHasSensitiveData();
        
        System.out.println("✅ SSN PII detection result: " + (ssnSensitive != null && ssnSensitive));
        assertThat(ssnSensitive).isNotNull().isTrue();

        System.out.println("\n=== Phase 6: Test Query Without PII (Clean Query) ===");
        
        String userId5 = "pii-test-clean";
        String cleanQuery = "What security features does your platform offer?";
        
        OrchestrationResult result5 = orchestrator.orchestrate(cleanQuery, userId5);
        assertNotNull(result5);
        assertThat(result5.isSuccess()).isTrue();
        
        List<IntentHistory> cleanHistory = intentHistoryRepository
            .findByUserIdOrderByCreatedAtDesc(userId5);
        
        assertThat(cleanHistory).isNotEmpty();
        
        IntentHistory cleanRecord = cleanHistory.getFirst();
        Boolean cleanSensitive = cleanRecord.getHasSensitiveData();
        
        System.out.println("✅ Clean query PII detection result: " + (cleanSensitive == null || !cleanSensitive));
        assertThat(cleanSensitive).isNotNull().isFalse();

        System.out.println("\n=== Phase 7: Test Multiple PII Types in Single Query ===");
        
        String userId6 = "pii-test-multi";
        String multiQuery = "User john.doe@company.com with SSN 987-65-4321 and phone 555-987-6543 requested data.";
        
        OrchestrationResult result6 = orchestrator.orchestrate(multiQuery, userId6);
        assertNotNull(result6);
        
        List<IntentHistory> multiHistory = intentHistoryRepository
            .findByUserIdOrderByCreatedAtDesc(userId6);
        
        assertThat(multiHistory).isNotEmpty();
        
        IntentHistory multiRecord = multiHistory.getFirst();
        Boolean multiSensitive = multiRecord.getHasSensitiveData();
        String multiTypes = multiRecord.getSensitiveDataTypes();
        
        System.out.println("✅ Multiple PII detection result: " + (multiSensitive != null && multiSensitive));
        System.out.println("   Detected types: " + multiTypes);
        assertThat(multiSensitive).isNotNull().isTrue();

        System.out.println("\n=== Phase 8: Verify Redaction in History ===");
        
        List<IntentHistory> allHistory = intentHistoryRepository.findAll();
        
        long redactedCount = allHistory.stream()
            .filter(h -> h.getRedactedQuery() != null && !h.getRedactedQuery().isEmpty())
            .count();
        
        System.out.println("✅ Records with redacted queries: " + redactedCount + "/" + allHistory.size());
        assertThat(redactedCount).isGreaterThanOrEqualTo(4L); // At least 4 PII queries redacted

        System.out.println("\n=== Phase 9: Verify Sanitization Metadata ===");
        
        long withSanitizationData = allHistory.stream()
            .filter(h -> h.getHasSensitiveData() != null || h.getSensitiveDataTypes() != null)
            .count();
        
        System.out.println("✅ Records with sanitization metadata: " + withSanitizationData + "/" + allHistory.size());
        assertThat(withSanitizationData).isGreaterThanOrEqualTo(4L);

        System.out.println("\n=== Phase 10: PII Detection Coverage Summary ===");
        
        long creditCardDetected = allHistory.stream()
            .filter(h -> h.getUserId().contains("creditcard") && 
                        h.getHasSensitiveData() != null && h.getHasSensitiveData())
            .count();
        
        long emailDetected = allHistory.stream()
            .filter(h -> h.getUserId().contains("email") && 
                        h.getHasSensitiveData() != null && h.getHasSensitiveData())
            .count();
        
        long phoneDetected = allHistory.stream()
            .filter(h -> h.getUserId().contains("phone") && 
                        h.getHasSensitiveData() != null && h.getHasSensitiveData())
            .count();
        
        long ssnDetected = allHistory.stream()
            .filter(h -> h.getUserId().contains("ssn") && 
                        h.getHasSensitiveData() != null && h.getHasSensitiveData())
            .count();

        System.out.println("📊 PII Detection Edge Spectrum:");
        System.out.println("   Credit Card Detection: " + (creditCardDetected > 0 ? "✓" : "✗"));
        System.out.println("   Email Detection: " + (emailDetected > 0 ? "✓" : "✗"));
        System.out.println("   Phone Detection: " + (phoneDetected > 0 ? "✓" : "✗"));
        System.out.println("   SSN Detection: " + (ssnDetected > 0 ? "✓" : "✗"));
        System.out.println("   Clean Query Handling: ✓");
        System.out.println("   Multi-Type Detection: " + (multiSensitive != null && multiSensitive ? "✓" : "✗"));
        System.out.println("   Query Redaction: " + redactedCount + " records");
        System.out.println("   Sanitization Metadata: " + withSanitizationData + " records");

        System.out.println("\n✅ PII Edge Spectrum Test Complete:");
        System.out.println("   ✓ Credit card pattern detection working");
        System.out.println("   ✓ Email pattern detection working");
        System.out.println("   ✓ Phone pattern detection working");
        System.out.println("   ✓ SSN pattern detection working");
        System.out.println("   ✓ Multiple PII types detected simultaneously");
        System.out.println("   ✓ Clean queries remain unaffected");
        System.out.println("   ✓ Query redaction applied");
        System.out.println("   ✓ Sanitization metadata tracked");
        System.out.println("   ✓ History records PII information");
    }

    private void assumeOpenAIConfigured() {
        Assumptions.assumeTrue(
            StringUtils.hasText(System.getProperty(OPENAI_KEY_PROPERTY)),
            "OPENAI_API_KEY not configured; skipping PII detection tests."
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
