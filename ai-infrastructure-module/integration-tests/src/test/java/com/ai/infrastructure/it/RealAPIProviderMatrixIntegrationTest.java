package com.ai.infrastructure.it;

import com.ai.infrastructure.embedding.EmbeddingProvider;
import com.ai.infrastructure.it.support.RealAPITestSupport;
import com.ai.infrastructure.provider.AIProvider;
import com.ai.infrastructure.provider.AIProviderManager;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/**
 * Executes the entire Real API integration test suite for every available provider combination.
 *
 * <p>Supports three syntax modes for specifying provider combinations:
 * <ul>
 *   <li><code>llm:embedding</code> - OpenAI:ONNX combination</li>
 *   <li><code>llm:embedding:vectordb</code> - OpenAI:ONNX:memory combination (extended syntax)</li>
 *   <li>Multiple combinations separated by commas</li>
 * </ul>
 *
 * <p>Can be supplied via:
 * <ul>
 *   <li>System property: <code>-Dai.providers.real-api.matrix</code></li>
 *   <li>Environment variable: <code>AI_PROVIDERS_REAL_API_MATRIX</code></li>
 *   <li>Auto-discovery if neither is provided (tests all available combinations)</li>
 * </ul>
 *
 * <p>Examples:
 * <pre>
 *   mvn test -Dai.providers.real-api.matrix=openai:onnx
 *   mvn test -Dai.providers.real-api.matrix=openai:openai,anthropic:openai
 *   mvn test -Dai.providers.real-api.matrix=openai:onnx:memory
 * </pre>
 */
@Slf4j
class RealAPIProviderMatrixIntegrationTest {

    private static final String MATRIX_PROPERTY = "ai.providers.real-api.matrix";
    private static final String MATRIX_ENV = "AI_PROVIDERS_REAL_API_MATRIX";
    private static final String VECTORDB_PROPERTY = "ai.vector-db.type";
    private static final AtomicInteger testCount = new AtomicInteger(0);
    private static final AtomicInteger successCount = new AtomicInteger(0);

    private static final Class<?>[] REAL_API_TEST_CLASSES = {
        RealAPIIntegrationTest.class,
        RealAPIONNXFallbackIntegrationTest.class,
        RealAPISmartValidationIntegrationTest.class,
        RealAPIVectorLifecycleIntegrationTest.class,
        RealAPIHybridRetrievalToggleIntegrationTest.class,
        RealAPIIntentHistoryAggregationIntegrationTest.class,
        RealAPIActionErrorRecoveryIntegrationTest.class,
        RealAPIActionFlowIntegrationTest.class,
        RealAPIMultiProviderFailoverIntegrationTest.class,
        RealAPISmartSuggestionsIntegrationTest.class,
        RealAPIPIIEdgeSpectrumIntegrationTest.class
    };

    static {
        RealAPITestSupport.ensureOpenAIConfigured();
    }

    @TestFactory
    Stream<DynamicTest> providerMatrix() {
        List<ProviderCombination> combinations = resolveProviderMatrix();
        Assertions.assertThat(combinations)
            .as("Provider matrix should not be empty")
            .isNotEmpty();

        log.info("═══════════════════════════════════════════════════════════════");
        log.info("Provider Matrix Integration Tests - Starting");
        log.info("Total combinations to test: {}", combinations.size());
        log.info("─────────────────────────────────────────────────────────────");
        combinations.forEach(combo ->
            log.info("  • {}", combo.displayName())
        );
        log.info("═══════════════════════════════════════════════════════════════");

        testCount.set(0);
        successCount.set(0);

        return combinations.stream()
            .map(combo -> DynamicTest.dynamicTest(
                combo.displayName(),
                () -> {
                    testCount.incrementAndGet();
                    log.info("[{}/{}] Running tests for: {}", 
                        testCount.get(), combinations.size(), combo.displayName());
                    executeSuite(combo);
                    successCount.incrementAndGet();
                    log.info("✓ [{}] PASSED: {}", testCount.get(), combo.displayName());
                }
            ))
            .peek(test -> {
                // Note: This is for visual feedback in test output
            });
    }

    private List<ProviderCombination> resolveProviderMatrix() {
        List<ProviderCombination> availableCombinations = discoverAvailableCombinations();

        String matrixSpec = System.getProperty(MATRIX_PROPERTY);
        if (!StringUtils.hasText(matrixSpec)) {
            matrixSpec = System.getenv(MATRIX_ENV);
        }

        if (!StringUtils.hasText(matrixSpec)) {
            return availableCombinations;
        }

        List<ProviderCombination> requestedCombinations = parseMatrixSpec(matrixSpec);
        validateRequestedCombinations(requestedCombinations, availableCombinations);
        return requestedCombinations;
    }

    private List<ProviderCombination> discoverAvailableCombinations() {
        ProviderCombination defaultCombo = new ProviderCombination("openai", "onnx", null);
        configureProviderProperties(defaultCombo);

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(TestApplication.class)
            .profiles("real-api-test")
            .properties(Map.of(
                "ai.providers.llm-provider", "openai",
                "ai.providers.embedding-provider", "onnx"
            ))
            .run()) {

            AIProviderManager providerManager = context.getBean(AIProviderManager.class);
            List<String> llmProviders = providerManager.getAvailableProviders().stream()
                .map(AIProvider::getProviderName)
                .sorted()
                .toList();

            List<String> embeddingProviders = context.getBeansOfType(EmbeddingProvider.class).values().stream()
                .filter(EmbeddingProvider::isAvailable)
                .map(EmbeddingProvider::getProviderName)
                .distinct()
                .sorted()
                .toList();

            List<ProviderCombination> combinations = new ArrayList<>();
            for (String llm : llmProviders) {
                for (String embedding : embeddingProviders) {
                    combinations.add(new ProviderCombination(llm, embedding));
                }
            }
            return combinations;
        } finally {
            clearProviderProperties(defaultCombo);
        }
    }

    private List<ProviderCombination> parseMatrixSpec(String matrixSpec) {
        return Arrays.stream(matrixSpec.split(","))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .map(entry -> {
                // Support two formats:
                // - llm:embedding (basic)
                // - llm:embedding:vectordb (extended)
                String[] parts = entry.split(":");
                if (parts.length < 2 || parts.length > 3) {
                    throw new IllegalArgumentException(
                        "Invalid provider matrix entry: '" + entry + "'. " +
                        "Expected format: llm:embedding or llm:embedding:vectordb");
                }
                
                String llm = parts[0].trim();
                String embedding = parts[1].trim();
                String vectorDb = parts.length == 3 ? parts[2].trim() : null;
                
                if (!StringUtils.hasText(llm) || !StringUtils.hasText(embedding)) {
                    throw new IllegalArgumentException(
                        "Invalid provider matrix entry: '" + entry + "'. " +
                        "LLM and embedding providers cannot be empty");
                }
                
                return new ProviderCombination(llm, embedding, vectorDb);
            })
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private void validateRequestedCombinations(List<ProviderCombination> requested,
                                               List<ProviderCombination> available) {
        Set<ProviderCombination> availableSet = new LinkedHashSet<>(available);
        List<ProviderCombination> missing = requested.stream()
            .filter(combo -> !availableSet.contains(combo))
            .toList();

        if (!missing.isEmpty()) {
            String availableSummary = available.stream()
                .map(ProviderCombination::toString)
                .collect(Collectors.joining(", "));
            String missingSummary = missing.stream()
                .map(ProviderCombination::toString)
                .collect(Collectors.joining(", "));
            throw new IllegalArgumentException(
                "Requested provider combinations are not available. Missing: [" + missingSummary + "] "
                    + "Available combinations: [" + availableSummary + "]");
        }
    }

    private void executeSuite(ProviderCombination combination) {
        long startTime = System.currentTimeMillis();
        configureProviderProperties(combination);

        try {
            SummaryGeneratingListener listener = new SummaryGeneratingListener();
            Launcher launcher = LauncherFactory.create();

            LauncherDiscoveryRequestBuilder requestBuilder = LauncherDiscoveryRequestBuilder.request();
            for (Class<?> testClass : REAL_API_TEST_CLASSES) {
                requestBuilder.selectors(selectClass(testClass));
            }

            LauncherDiscoveryRequest request = requestBuilder.build();
            launcher.execute(request, listener);

            TestExecutionSummary summary = listener.getSummary();
            long duration = System.currentTimeMillis() - startTime;
            
            if (summary.getTotalFailureCount() > 0) {
                String failures = summary.getFailures().stream()
                    .map(failure -> failure.getTestIdentifier().getDisplayName() + " -> " + 
                        (failure.getException() != null ? failure.getException().getMessage() : "Unknown error"))
                    .collect(Collectors.joining(System.lineSeparator()));
                log.error("✗ FAILED: {} ({} ms)", combination.displayName(), duration);
                throw new AssertionError("Failures detected for " + combination.displayName() + 
                    " (" + summary.getTotalFailureCount() + " failures)" +
                    System.lineSeparator() + failures);
            }
            
            log.debug("Combination completed successfully in {} ms. Tests: {}, Failures: {}, Skipped: {}", 
                duration, summary.getTestsFoundCount(), summary.getTotalFailureCount(), summary.getTestsSkippedCount());
                
        } finally {
            clearProviderProperties(combination);
        }
    }

    private void configureProviderProperties(ProviderCombination combination) {
        System.setProperty("LLM_PROVIDER", combination.llmProvider());
        System.setProperty("ai.providers.llm-provider", combination.llmProvider());
        System.setProperty("EMBEDDING_PROVIDER", combination.embeddingProvider());
        System.setProperty("ai.providers.embedding-provider", combination.embeddingProvider());
        
        if (StringUtils.hasText(combination.vectorDbProvider())) {
            System.setProperty("ai.vector-db.type", combination.vectorDbProvider());
        }
    }

    private void clearProviderProperties(ProviderCombination combination) {
        System.clearProperty("LLM_PROVIDER");
        System.clearProperty("ai.providers.llm-provider");
        System.clearProperty("EMBEDDING_PROVIDER");
        System.clearProperty("ai.providers.embedding-provider");
        System.clearProperty("ai.vector-db.type");
    }

    /**
     * Provider combination record supporting:
     * - Basic: llmProvider, embeddingProvider
     * - Extended: llmProvider, embeddingProvider, vectorDbProvider (optional)
     */
    private record ProviderCombination(
        String llmProvider,
        String embeddingProvider,
        String vectorDbProvider
    ) {
        /**
         * Creates a combination with just LLM and embedding providers.
         */
        public ProviderCombination(String llmProvider, String embeddingProvider) {
            this(llmProvider, embeddingProvider, null);
        }

        /**
         * Returns a display name for the combination.
         * Format: "LLM=openai | Embedding=onnx" or "LLM=openai | Embedding=onnx | VectorDB=memory"
         */
        public String displayName() {
            if (StringUtils.hasText(vectorDbProvider)) {
                return "LLM=" + llmProvider + " | Embedding=" + embeddingProvider + " | VectorDB=" + vectorDbProvider;
            }
            return "LLM=" + llmProvider + " | Embedding=" + embeddingProvider;
        }

        @Override
        public String toString() {
            if (StringUtils.hasText(vectorDbProvider)) {
                return llmProvider + "/" + embeddingProvider + "/" + vectorDbProvider;
            }
            return llmProvider + "/" + embeddingProvider;
        }
    }
}
