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
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("real-api-test")
@Transactional
public class RealAPISmartSuggestionsIntegrationTest {

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
            System.setProperty("ai.providers.openai.api-key", apiKey);
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
    public void testSmartSuggestionsWithEnrichmentMetadataAndSanitization() {
        assumeOpenAIConfigured();

        // Create multiple products targeting different vector spaces and search contexts
        TestProduct product1 = persistProduct(
            "Advanced Threat Detection System",
            """
                Real-time threat detection and response platform for enterprise networks.
                Integrates with SIEM systems, correlates security events, and provides automated incident response.
                Features include machine learning-based anomaly detection, behavioral analytics, and threat hunting capabilities.
                """,
            "Security",
            "DefenseFirst",
            new BigDecimal("4999.99")
        );

        TestProduct product2 = persistProduct(
            "Network Traffic Analysis Suite",
            """
                Deep packet inspection and network flow analysis for complete visibility.
                Monitors bandwidth usage, identifies security threats, detects unauthorized access patterns.
                Supports threat intelligence integration and provides forensic capabilities for incident investigation.
                """,
            "Security",
            "NetGuard",
            new BigDecimal("3499.99")
        );

        TestProduct product3 = persistProduct(
            "Cloud Security Posture Management",
            """
                Continuous compliance monitoring across multi-cloud environments.
                Evaluates security configurations, identifies compliance gaps, and recommends remediation actions.
                Supports AWS, Azure, GCP, and on-premises deployments with unified dashboard.
                """,
            "Compliance",
            "CloudShield",
            new BigDecimal("2999.99")
        );

        String userId = "smart-suggestions-user";
        String query = """
            I need comprehensive security solutions for my enterprise.
            Please search the knowledge base for recommendations on advanced threat detection systems
            and their integration with network monitoring capabilities.
            Include best practices for cloud security posture management.
            """;

        OrchestrationResult result = orchestrator.orchestrate(query, userId);

        assertNotNull(result, "Orchestrator should return a result");
        assertThat(result.isSuccess()).isTrue();

        // Verify sanitized payload contains all expected metadata
        Map<String, Object> sanitizedPayload = result.getSanitizedPayload();
        assertThat(sanitizedPayload).isNotEmpty();
        assertThat(sanitizedPayload.get("success")).isEqualTo(Boolean.TRUE);

        // Verify sanitization metadata is present
        @SuppressWarnings("unchecked")
        Map<String, Object> sanitization = (Map<String, Object>) sanitizedPayload.get("sanitization");
        assertThat(sanitization).isNotNull();
        assertThat(sanitization.get("risk")).isNotNull();

        // Verify suggestions/recommendations are enriched with metadata
        Object suggestionsObj = sanitizedPayload.get("suggestions");
        if (suggestionsObj instanceof List<?> suggestions) {
            assertThat(suggestions).isNotEmpty()
                .as("Smart suggestions should be provided");

            for (Object suggestion : suggestions) {
                if (suggestion instanceof Map<?, ?> suggestionMap) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> sMap = (Map<String, Object>) suggestionMap;

                    // Verify enrichment metadata present
                    assertThat(sMap).containsKeys("intent", "confidence")
                        .as("Suggestion should have intent and confidence");

                    // Verify sanitization metadata propagated to suggestions
                    Object suggestSanitization = sMap.get("sanitization");
                    if (suggestSanitization != null) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> suggestSanit = (Map<String, Object>) suggestSanitization;
                        assertThat(suggestSanit).containsKey("risk")
                            .as("Suggestion should have sanitization risk metadata");
                    }
                }
            }
        }

        Object smartSuggestionObj = sanitizedPayload.get("smartSuggestion");
        if (smartSuggestionObj instanceof Map<?, ?> smartSugg) {
            @SuppressWarnings("unchecked")
            Map<String, Object> smartSuggestion = (Map<String, Object>) smartSugg;

            // Verify smart suggestion has enrichment fields
            assertThat(smartSuggestion).containsKeys("intent", "confidence")
                .as("Smart suggestion should contain intent and confidence");

            // Verify document alignment with searchable fields
            Object documents = smartSuggestion.get("documents");
            if (documents instanceof List<?> docsList) {
                assertThat(docsList).isNotEmpty()
                    .as("Smart suggestion should retrieve relevant documents");

                for (Object doc : docsList) {
                    if (doc instanceof Map<?, ?> docMap) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> document = (Map<String, Object>) docMap;

                        // Verify document fields match searchable content
                        Object content = document.get("content");
                        if (content instanceof String contentStr) {
                            String lowerContent = contentStr.toLowerCase();
                            boolean hasRelevantKeywords = lowerContent.contains("threat") || 
                                                        lowerContent.contains("security") ||
                                                        lowerContent.contains("detection") ||
                                                        lowerContent.contains("network");
                            assertThat(hasRelevantKeywords)
                                .as("Document should contain security/threat/detection keywords")
                                .isTrue();
                        }
                    }
                }
            }

            // Verify response is sanitized
            Object response = smartSuggestion.get("response");
            if (response instanceof String responseStr) {
                assertThat(responseStr).isNotEmpty();
                // Should not contain original PII if any was in the knowledge base
                assertThat(responseStr).doesNotContain("credit", "card", "api", "secret")
                    .as("Response should be sanitized");
            }
        }

        // Verify metadata about orchestration is captured
        Map<String, Object> metadata = result.getMetadata();
        if (metadata != null && !metadata.isEmpty()) {
            assertThat(metadata.get("intentsCount")).isNotNull()
                .as("Intent count should be recorded in metadata");
            assertThat(metadata.get("compound")).isNotNull()
                .as("Compound flag should be recorded in metadata");
        }

        // Verify intent history captured the query
        List<IntentHistory> history = intentHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId);
        assertThat(history).isNotEmpty()
            .as("Intent history should capture the request");

        IntentHistory record = history.getFirst();
        assertThat(Boolean.TRUE.equals(record.getSuccess())).isTrue();
        assertNotNull(record.getExecutionStatus());
        assertThat(record.getRedactedQuery()).isNotEmpty();

        System.out.println("✅ Smart Suggestions Enrichment Test");
        System.out.println("   - Products created: 3");
        System.out.println("   - Suggestions provided: " + (suggestionsObj instanceof List ? ((List<?>) suggestionsObj).size() : "0"));
        System.out.println("   - Intent history records: " + history.size());
        System.out.println("   - Sanitization risk level: " + sanitization.get("risk"));
    }

    private void assumeOpenAIConfigured() {
        Assumptions.assumeTrue(
            StringUtils.hasText(System.getProperty(OPENAI_KEY_PROPERTY)),
            "OPENAI_API_KEY not configured; skipping smart suggestions tests."
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
            .stockQuantity(50)
            .active(true)
            .build();
        product = productRepository.save(product);
        capabilityService.processEntityForAI(product, "test-product");
        return product;
    }
}
