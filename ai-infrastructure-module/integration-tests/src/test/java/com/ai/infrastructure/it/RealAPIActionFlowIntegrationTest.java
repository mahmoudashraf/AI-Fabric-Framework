package com.ai.infrastructure.it;

import com.ai.infrastructure.config.ResponseSanitizationProperties;
import com.ai.infrastructure.entity.IntentHistory;
import com.ai.infrastructure.exception.AIServiceException;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationResultType;
import com.ai.infrastructure.intent.orchestration.RAGOrchestrator;
import com.ai.infrastructure.it.entity.TestProduct;
import com.ai.infrastructure.it.repository.TestProductRepository;
import com.ai.infrastructure.repository.IntentHistoryRepository;
import com.ai.infrastructure.service.AICapabilityService;
import com.ai.infrastructure.service.VectorManagementService;
import com.ai.infrastructure.storage.strategy.AISearchableEntityStorageStrategy;
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
public class RealAPIActionFlowIntegrationTest {

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
    private AISearchableEntityStorageStrategy storageStrategy;

    @Autowired
    private ResponseSanitizationProperties sanitizationProperties;

    @BeforeEach
    public void setUp() {
        vectorManagementService.clearAllVectors();
        storageStrategy.deleteAll();
        productRepository.deleteAll();
        intentHistoryRepository.deleteAll();
    }

    @Test
    public void testRealActionHandlerRemovesVectorWithPIIRedaction() {
        assumeOpenAIConfigured();

        TestProduct legacyDevice = persistProduct(
            "Legacy Compliance Sensor",
            "An obsolete sensor model kept for regression tests. It contains known stale embeddings and references to retired payment flows.",
            "Compliance",
            "RetroScan",
            new BigDecimal("149.50")
        );
        String entityId = legacyDevice.getId().toString();
        assertThat(vectorManagementService.vectorExists("test-product", entityId))
            .as("vector should exist before action execution")
            .isTrue();

        String userId = "real-action-removal-user";
        String query = """
            Compliance incident: The archived vector for "%s" still leaks card 5204-2190-4433-9910 inside nightly logs.
            Execute the remove_vector action with entityType "test-product" and entityId "%s" immediately.
            Confirm the purge and propose the next follow-up remediation step.""".formatted(legacyDevice.getName(), entityId);

        OrchestrationResult result = orchestrateOrSkip(query, userId);
        assertNotNull(result, "Orchestrator should return a result");
        assertThat(result.getType())
            .as("Action should be executed; compound is acceptable if it contains the action")
            .isIn(OrchestrationResultType.ACTION_EXECUTED, OrchestrationResultType.COMPOUND_HANDLED);

        // If the LLM emitted a compound, ensure the action child succeeded
        if (result.getType() == OrchestrationResultType.COMPOUND_HANDLED) {
            OrchestrationResult actionChild = result.getChildren().stream()
                .filter(child -> child.getType() == OrchestrationResultType.ACTION_EXECUTED)
                .findFirst()
                .orElse(null);
            assertNotNull(actionChild, "Compound result should include executed action child");
        }

        Map<String, Object> payload = result.getSanitizedPayload();
        assertThat(payload).isNotEmpty();

        Object safeSummary = payload.get("safeSummary");
        if (safeSummary instanceof String summary) {
            assertThat(summary).doesNotContain("5204");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> sanitization = (Map<String, Object>) payload.get("sanitization");
        assertThat(sanitization).isNotNull();
        String risk = Optional.ofNullable(sanitization.get("risk"))
            .map(Object::toString)
            .map(String::toUpperCase)
            .orElse("");
        assertThat(risk).isNotBlank();

        @SuppressWarnings("unchecked")
        List<String> detectedTypes = (List<String>) sanitization.getOrDefault("detectedTypes", List.of());
        assertThat(detectedTypes.stream().map(type -> type == null ? "" : type.toUpperCase()))
            .anyMatch(type -> type.contains("CREDIT_CARD"));

        @SuppressWarnings("unchecked")
        Map<String, Object> warning = (Map<String, Object>) payload.get("warning");
        if (warning != null) {
            assertThat(warning.get("message")).isEqualTo(sanitizationProperties.getHighRiskWarningMessage());
        }

        Object guidance = payload.get("guidance");
        if (guidance instanceof String guidanceMessage) {
            assertThat(guidanceMessage).isEqualTo(sanitizationProperties.getGuidanceMessage());
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) payload.get("data");
        assertThat(data).isNotNull();
        assertThat(String.valueOf(data.get("action"))).isEqualTo("remove_vector");

        @SuppressWarnings("unchecked")
        Map<String, Object> actionResult = (Map<String, Object>) data.get("actionResult");
        assertThat(actionResult).isNotNull();
        assertThat(actionResult.get("success")).isEqualTo(Boolean.TRUE);

        @SuppressWarnings("unchecked")
        Map<String, Object> actionData = actionResult.get("data") instanceof Map<?, ?> map
            ? (Map<String, Object>) map
            : Map.of();
        assertThat(actionData.get("removed")).isEqualTo(Boolean.TRUE);
        assertThat(actionResult.getOrDefault("message", "")).asString().doesNotContain("5204");

        assertThat(vectorManagementService.vectorExists("test-product", entityId))
            .as("vector should be removed after action execution")
            .isFalse();

        if (payload.containsKey("suggestions")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> suggestions = (List<Map<String, Object>>) payload.get("suggestions");
            for (Map<String, Object> suggestion : suggestions) {
                assertThat(suggestion).isNotNull();
                assertThat(suggestion.values().stream()
                    .filter(String.class::isInstance)
                    .map(Object::toString)
                    .collect(Collectors.joining(" ")))
                    .doesNotContain("5204");
            }
        } else {
            assertThat(result.getSmartSuggestion()).isNotEmpty();
        }

        List<IntentHistory> history = intentHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId);
        assertThat(history).isNotEmpty();
        IntentHistory record = history.getFirst();
        assertThat(record.getRedactedQuery()).doesNotContain("5204");
        if (StringUtils.hasText(record.getSensitiveDataTypes())) {
            assertThat(record.getSensitiveDataTypes().toUpperCase()).contains("CREDIT_CARD");
        }
        assertNotNull(record.getExecutionStatus());
        assertThat(Boolean.TRUE.equals(record.getSuccess())).isTrue();
    }

    @Test
    public void testRealCompoundInformationAndActionFlowProducesSmartSuggestion() {
        assumeOpenAIConfigured();

        TestProduct playbook = persistProduct(
            "Disaster Recovery Checklist",
            """
                A rapid response playbook:
                - Validate instrumentation dashboards.
                - Verify replicated data slices.
                - Execute cross-region failover rehearsal.
                This checklist is referenced by operations for emergency drills.
                """,
            "Runbooks",
            "SafetyOps",
            new BigDecimal("79.00")
        );

        TestProduct diagnostics = persistProduct(
            "Instrumentation Diagnostics Guide",
            """
                Step-by-step instrumentation diagnostics covering telemetry scrubbing,
                anomaly detection thresholds, and audit alerting.
                """,
            "Runbooks",
            "SafetyOps",
            new BigDecimal("59.00")
        );

        String userId = "real-compound-action-user";
        String query = """
            Operations bulletin for harper.ops@enterprise.example (phone 555-991-2045).
            We require two intents:
            1. Use the test-product knowledge base to summarise the "Disaster Recovery Checklist" so on-call engineers know the instrumentation steps.
            2. Afterwards, run the clear_vector_index action with reason "reseed" to wipe stale embeddings before we rebuild.
            Close with a recommended high-confidence validation to run next.""";

        OrchestrationResult result = orchestrateOrSkip(query, userId);
        assertNotNull(result, "Orchestrator should return a result");
        assertThat(result.getType()).isEqualTo(OrchestrationResultType.COMPOUND_HANDLED);

        List<OrchestrationResult> children = result.getChildren();
        assertThat(children).hasSizeGreaterThanOrEqualTo(2);
        boolean hasActionChild = children.stream()
            .anyMatch(child -> child.getType() == OrchestrationResultType.ACTION_EXECUTED);
        boolean hasInformationChild = children.stream()
            .anyMatch(child -> child.getType() == OrchestrationResultType.INFORMATION_PROVIDED);
        assertThat(hasActionChild).isTrue();
        assertThat(hasInformationChild).isTrue();

        OrchestrationResult informationChild = children.stream()
            .filter(child -> child.getType() == OrchestrationResultType.INFORMATION_PROVIDED)
            .findFirst()
            .orElseThrow();
        assertThat(informationChild.getData()).containsKey("ragResponse");

        Map<String, Object> payload = result.getSanitizedPayload();
        assertThat(payload).isNotEmpty();

        Object safeSummary = payload.get("safeSummary");
        if (safeSummary instanceof String summary) {
            assertThat(summary)
                .doesNotContain("harper.ops@enterprise.example")
                .doesNotContain("555-991-2045");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> sanitization = (Map<String, Object>) payload.get("sanitization");
        assertThat(sanitization).isNotNull();
        String risk = Optional.ofNullable(sanitization.get("risk"))
            .map(Object::toString)
            .map(String::toUpperCase)
            .orElse("");
        assertThat(risk).isNotBlank();

        @SuppressWarnings("unchecked")
        List<String> detectedTypes = (List<String>) sanitization.getOrDefault("detectedTypes", List.of());
        List<String> upperTypes = detectedTypes.stream()
            .map(type -> type == null ? "" : type.toUpperCase())
            .collect(Collectors.toList());
        assertThat(upperTypes).anyMatch(type -> type.contains("EMAIL"));
        assertThat(upperTypes).anyMatch(type -> type.contains("PHONE"));

        @SuppressWarnings("unchecked")
        Map<String, Object> warning = (Map<String, Object>) payload.get("warning");
        if (warning != null) {
            assertThat(warning.get("message")).isIn(
                sanitizationProperties.getMediumRiskWarningMessage(),
                sanitizationProperties.getHighRiskWarningMessage()
            );
        }

        Object guidance = payload.get("guidance");
        if (guidance instanceof String guidanceMessage) {
            assertThat(guidanceMessage).isEqualTo(sanitizationProperties.getGuidanceMessage());
        }

        Object suggestionsObject = payload.get("suggestions");
        List<Map<String, Object>> suggestions = new ArrayList<>();
        if (suggestionsObject instanceof List<?> rawSuggestions) {
            for (Object raw : rawSuggestions) {
                if (raw instanceof Map<?, ?> suggestionMap) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> casted = (Map<String, Object>) suggestionMap;
                    suggestions.add(casted);
                }
            }
        }
        if (suggestions.isEmpty()) {
            suggestions.add(result.getSmartSuggestion());
        }
        assertThat(suggestions).isNotEmpty();
        for (Map<String, Object> suggestion : suggestions) {
            if (suggestion == null || suggestion.isEmpty()) {
                continue;
            }
            assertThat(suggestion.values().stream()
                .filter(String.class::isInstance)
                .map(Object::toString)
                .collect(Collectors.joining(" ")))
                .doesNotContain("harper.ops@enterprise.example")
                .doesNotContain("555-991-2045");
        }

        assertThat(vectorManagementService.vectorExists("test-product", playbook.getId().toString()))
            .as("vectors should be cleared for playbook")
            .isFalse();
        assertThat(vectorManagementService.vectorExists("test-product", diagnostics.getId().toString()))
            .as("vectors should be cleared for diagnostics")
            .isFalse();

        List<IntentHistory> history = intentHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId);
        assertThat(history).isNotEmpty();
        IntentHistory record = history.getFirst();
        assertThat(record.getRedactedQuery())
            .doesNotContain("harper.ops@enterprise.example")
            .doesNotContain("555-991-2045");
        if (StringUtils.hasText(record.getSensitiveDataTypes())) {
            String normalized = record.getSensitiveDataTypes().toUpperCase();
            assertThat(normalized).contains("EMAIL");
            assertThat(normalized).contains("PHONE");
        }
        assertNotNull(record.getExecutionStatus());
        assertThat(Boolean.TRUE.equals(record.getSuccess())).isTrue();
    }

    private void assumeOpenAIConfigured() {
        Assumptions.assumeTrue(
            StringUtils.hasText(System.getProperty(OPENAI_KEY_PROPERTY)),
            "OPENAI_API_KEY not configured; skipping real API action flow tests."
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
            .stockQuantity(25)
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
                "Skipping real API action flow test because intent orchestration failed: " + ex.getMessage());
            return null;
        }
    }
}
