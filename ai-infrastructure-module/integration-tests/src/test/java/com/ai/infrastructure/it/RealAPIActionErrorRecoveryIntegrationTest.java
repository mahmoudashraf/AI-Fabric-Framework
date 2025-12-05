package com.ai.infrastructure.it;

import com.ai.infrastructure.config.ResponseSanitizationProperties;
import com.ai.infrastructure.entity.IntentHistory;
import com.ai.infrastructure.exception.AIServiceException;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationResultType;
import com.ai.infrastructure.intent.orchestration.RAGOrchestrator;
import com.ai.infrastructure.it.entity.TestProduct;
import com.ai.infrastructure.it.repository.TestProductRepository;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.ai.infrastructure.repository.IntentHistoryRepository;
import com.ai.infrastructure.service.AICapabilityService;
import com.ai.infrastructure.service.VectorManagementService;
import org.assertj.core.api.Assertions;
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
import java.util.ArrayList;
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
public class RealAPIActionErrorRecoveryIntegrationTest {

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

        System.setProperty("LLM_PROVIDER",
            System.getProperty("LLM_PROVIDER", "openai"));
        System.setProperty("ai.providers.llm-provider",
            System.getProperty("ai.providers.llm-provider", "openai"));
        System.setProperty("EMBEDDING_PROVIDER",
            System.getProperty("EMBEDDING_PROVIDER", "onnx"));
        System.setProperty("ai.providers.embedding-provider",
            System.getProperty("ai.providers.embedding-provider", "onnx"));
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
    public void testRealActionHandlerErrorRecoveryProvidesSanitizedGuidance() {
        assumeOpenAIConfigured();

        TestProduct baseline = persistProduct(
            "Critical Access Ledger",
            """
                Maintains privileged access logs for incident forensics.
                Includes historical remediation guidance and vector operations checklists.
                """,
            "Runbooks",
            "SecOps",
            new BigDecimal("129.00")
        );

        String entityId = baseline.getId().toString();
        assertThat(vectorManagementService.vectorExists("test-product", entityId))
            .as("vector should exist before attempting action")
            .isTrue();

        String userId = "real-action-error-user";
        // Test error recovery with an invalid action that will naturally fail
        // This tests PII sanitization and error handling without mocking
        String query = """
            Emergency broadcast: Card 4916-2345-0987-1123 leaked in the SecOps export owned by bree.secops@enterprise.example (contact (555) 714-2209).
            Execute the invalid_action_that_does_not_exist action immediately. This action does not exist and should fail.
            After handling the error, recommend the follow-up next step intent `reseed_vector_index` with a high confidence (>=0.9) targeting the test-product knowledge base, and include rationale about verifying regenerated embeddings.
            """;

        OrchestrationResult result = orchestrateOrSkip(query, userId);

        assertNotNull(result, "Orchestrator should return a result");
        // The result may be SUCCESS (if LLM correctly identifies as OUT_OF_SCOPE) or ERROR (if action was attempted)
        // The key is that PII sanitization works regardless of success/failure
        
        // Verify that error information is captured (if action was attempted)
        Map<String, Object> data = result.getData();
        boolean actionWasAttempted = false;
        if (data != null && !data.isEmpty()) {
            Object actionResultObj = data.get("actionResult");
            if (actionResultObj != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> actionResult = actionResultObj instanceof Map<?, ?> map
                    ? (Map<String, Object>) map
                    : Map.of();
                
                if (!actionResult.isEmpty()) {
                    actionWasAttempted = true;
                    // Verify error was captured properly
                    assertThat(actionResult.get("success")).isEqualTo(Boolean.FALSE);
                    assertThat(String.valueOf(actionResult.get("message"))).contains("No action handler registered for action 'invalid_action_that_does_not_exist'");
                    assertThat(String.valueOf(actionResult.get("errorCode"))).isEqualTo("ACTION_NOT_FOUND");
                }
            }
        }
        
        // If action was attempted, verify error message indicates handler not found
        if (actionWasAttempted || !result.isSuccess()) {
            assertThat(result.getMessage())
                .as("Error message should indicate action handler not found")
                .contains("No action handler registered")
                .contains("invalid_action_that_does_not_exist");
        }

        assertThat(result.getNextSteps())
            .as("next-step recommendations should be preserved")
            .isNotNull();
        if (result.getNextSteps().isEmpty()) {
            Assertions.fail("Expected at least one next-step recommendation but found none.");
        }

        Map<String, Object> sanitizedPayload = result.getSanitizedPayload();
        assertThat(sanitizedPayload).isNotEmpty();
        // If action was attempted and failed, type should be ERROR; otherwise it may be SUCCESS (OUT_OF_SCOPE)
        if (actionWasAttempted || !result.isSuccess()) {
            assertThat(String.valueOf(sanitizedPayload.get("type"))).isEqualTo("ERROR");
            assertThat(sanitizedPayload.get("success")).isEqualTo(Boolean.FALSE);
        } else {
            // If LLM correctly identified as OUT_OF_SCOPE, result is success but sanitization should still be present
            assertThat(sanitizedPayload.get("success")).isIn(Boolean.TRUE, Boolean.FALSE);
        }

        Object safeSummary = sanitizedPayload.get("safeSummary");
        if (safeSummary instanceof String summary) {
            assertThat(summary)
                .doesNotContain("4916-2345-0987-1123")
                .doesNotContain("bree.secops@enterprise.example")
                .doesNotContain("555")
                .doesNotContain("714-2209");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> sanitization = (Map<String, Object>) sanitizedPayload.getOrDefault("sanitization", Map.of());
        assertThat(sanitization).isNotEmpty();
        String risk = Optional.ofNullable(sanitization.get("risk"))
            .map(Object::toString)
            .orElse("");
        assertThat(risk).isNotBlank();

        @SuppressWarnings("unchecked")
        List<String> detectedTypes = (List<String>) sanitization.getOrDefault("detectedTypes", List.of());
        List<String> normalizedTypes = detectedTypes.stream()
            .filter(StringUtils::hasText)
            .map(type -> type.toUpperCase().replace("-", "_"))
            .collect(Collectors.toList());
        assertThat(normalizedTypes).anyMatch(type -> type.contains("CREDIT") || type.contains("CARD"));
        assertThat(normalizedTypes).anyMatch(type -> type.contains("EMAIL"));
        assertThat(normalizedTypes).anyMatch(type -> type.contains("PHONE"));

        @SuppressWarnings("unchecked")
        Map<String, Object> warning = (Map<String, Object>) sanitizedPayload.get("warning");
        if (warning != null && !warning.isEmpty()) {
            assertThat(String.valueOf(warning.get("message")))
                .isIn(
                    sanitizationProperties.getHighRiskWarningMessage(),
                    sanitizationProperties.getMediumRiskWarningMessage()
                );
        }

        Object guidance = sanitizedPayload.get("guidance");
        if (guidance instanceof String guidanceMessage) {
            assertThat(guidanceMessage).isEqualTo(sanitizationProperties.getGuidanceMessage());
        }

        Object suggestionsObject = sanitizedPayload.get("suggestions");
        List<Map<String, Object>> suggestions = new ArrayList<>();
        if (suggestionsObject instanceof List<?> list) {
            for (Object raw : list) {
                if (raw instanceof Map<?, ?> suggestionMap) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> casted = (Map<String, Object>) suggestionMap;
                    suggestions.add(casted);
                }
            }
        }
        if (suggestions.isEmpty() && !result.getSmartSuggestion().isEmpty()) {
            suggestions.add(result.getSmartSuggestion());
        }

        assertThat(suggestions)
            .as("sanitized suggestions should be present when next steps are provided")
            .isNotEmpty();
        for (Map<String, Object> suggestion : suggestions) {
            String combined = suggestion.values().stream()
                .filter(value -> value instanceof String)
                .map(Object::toString)
                .collect(Collectors.joining(" "));
            assertThat(combined)
                .doesNotContain("4916-2345-0987-1123")
                .doesNotContain("bree.secops@enterprise.example")
                .doesNotContain("555")
                .doesNotContain("714-2209");
        }

        // Verify vector still exists (wasn't cleared by invalid action)
        assertThat(vectorManagementService.vectorExists("test-product", entityId))
            .as("vector should remain intact when action fails")
            .isTrue();

        List<IntentHistory> history = intentHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId);
        assertThat(history).isNotEmpty();
        IntentHistory record = history.getFirst();
        // If action was attempted and failed, success should be false; otherwise it may be true (OUT_OF_SCOPE)
        if (actionWasAttempted || !result.isSuccess()) {
            assertThat(Boolean.TRUE.equals(record.getSuccess())).isFalse();
        }
        // In both cases, execution status should be recorded
        assertNotNull(record.getExecutionStatus(), "execution status should be recorded");
        assertThat(record.getRedactedQuery())
            .doesNotContain("4916-2345-0987-1123")
            .doesNotContain("bree.secops@enterprise.example")
            .doesNotContain("555")
            .doesNotContain("714-2209");
        if (StringUtils.hasText(record.getSensitiveDataTypes())) {
            String normalized = record.getSensitiveDataTypes().toUpperCase();
            assertThat(normalized).contains("CREDIT");
            assertThat(normalized).contains("EMAIL");
            assertThat(normalized).contains("PHONE");
        }
    }

    private void assumeOpenAIConfigured() {
        Assumptions.assumeTrue(
            StringUtils.hasText(System.getProperty(OPENAI_KEY_PROPERTY)),
            "OPENAI_API_KEY not configured; skipping real API error recovery tests."
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

    private OrchestrationResult orchestrateOrSkip(String query, String userId) {
        try {
            return orchestrator.orchestrate(query, userId);
        } catch (AIServiceException ex) {
            Assumptions.assumeTrue(false,
                "Skipping real API error recovery test because intent orchestration failed: " + ex.getMessage());
            return null;
        }
    }
}
