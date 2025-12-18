package com.ai.infrastructure.it.realapi;

import com.ai.infrastructure.embedding.EmbeddingProvider;
import com.ai.infrastructure.it.TestApplication;
import com.ai.infrastructure.provider.AIProvider;
import com.ai.infrastructure.provider.AIProviderManager;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/**
 * Shared harness for running suites of integration tests across multiple
 * provider combinations (LLM / Embedding / optional Vector DB).
 */
abstract class AbstractProviderMatrixIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(AbstractProviderMatrixIntegrationTest.class);

    private static final String VECTORDB_PROPERTY = "ai.vector-db.type";
    private static final String STORAGE_STRATEGY_PROPERTY = "ai-infrastructure.storage.strategy";
    private final AtomicInteger testCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);

    @TestFactory
    Stream<DynamicTest> providerMatrix() {
        beforeMatrixExecution();
        List<ProviderCombination> combinations = resolveProviderMatrix();
        Assertions.assertThat(combinations)
            .as("Provider matrix should not be empty")
            .isNotEmpty();

        log.info("═══════════════════════════════════════════════════════════════");
        log.info("{} - Starting", suiteDisplayName());
        log.info("Total combinations to test: {}", combinations.size());
        log.info("─────────────────────────────────────────────────────────────");
        combinations.forEach(combo -> log.info("  • {}", combo.displayName()));
        log.info("═══════════════════════════════════════════════════════════════");

        testCount.set(0);
        successCount.set(0);

        return combinations.stream()
            .map(combo -> DynamicTest.dynamicTest(
                combo.displayName(),
                () -> executeCombination(combinations.size(), combo)
            ));
    }

    private void executeCombination(int total, ProviderCombination combo) {
        testCount.incrementAndGet();
        log.info("[{}/{}] Running tests for: {}", testCount.get(), total, combo.displayName());

        long startTime = System.currentTimeMillis();
        configureProviderProperties(combo);

        try {
            SummaryGeneratingListener listener = new SummaryGeneratingListener();
            Launcher launcher = LauncherFactory.create();

            LauncherDiscoveryRequestBuilder requestBuilder = LauncherDiscoveryRequestBuilder.request();
            for (Class<?> testClass : suiteTestClasses()) {
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
                log.error("✗ FAILED: {} ({} ms)", combo.displayName(), duration);
                throw new AssertionError("Failures detected for " + combo.displayName() + " (" +
                    summary.getTotalFailureCount() + " failures)" + System.lineSeparator() + failures);
            }

            log.debug("Combination completed successfully in {} ms. Tests: {}, Failures: {}, Skipped: {}",
                duration, summary.getTestsFoundCount(), summary.getTotalFailureCount(), summary.getTestsSkippedCount());
            successCount.incrementAndGet();
            log.info("✓ [{}] PASSED: {}", testCount.get(), combo.displayName());

        } finally {
            clearProviderProperties(combo);
        }
    }

    protected List<ProviderCombination> resolveProviderMatrix() {
        List<ProviderCombination> availableCombinations = availableProviderCombinations();

        String matrixSpec = System.getProperty(matrixPropertyKey());
        if (!StringUtils.hasText(matrixSpec)) {
            matrixSpec = System.getenv(matrixEnvVariable());
        }

        if (!StringUtils.hasText(matrixSpec)) {
            return availableCombinations;
        }

        List<ProviderCombination> requestedCombinations = parseMatrixSpec(matrixSpec);
        validateRequestedCombinations(requestedCombinations, availableCombinations);
        return requestedCombinations;
    }

    protected List<ProviderCombination> availableProviderCombinations() {
        return expandWithStorageStrategies(discoverAvailableCombinations());
    }

    private List<ProviderCombination> discoverAvailableCombinations() {
        ProviderCombination defaultCombo = new ProviderCombination(defaultLlmProvider(), defaultEmbeddingProvider(), null, defaultStorageStrategy());
        configureProviderProperties(defaultCombo);

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(TestApplication.class)
            .profiles(discoveryProfile())
            .properties(defaultDiscoveryProperties())
            .properties(additionalDiscoveryProperties())
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

            List<String> vectorDbProviders = vectorDbProviders();

            List<ProviderCombination> combinations = new ArrayList<>();
            for (String llm : llmProviders) {
                for (String embedding : embeddingProviders) {
                    for (String vector : vectorDbProviders) {
                        combinations.add(new ProviderCombination(llm, embedding, vector, defaultStorageStrategy()));
                    }
                }
            }
            return combinations;
        } finally {
            clearProviderProperties(defaultCombo);
        }
    }

    private Map<String, Object> defaultDiscoveryProperties() {
        return Map.of(
            "ai.providers.llm-provider", defaultLlmProvider(),
            "ai.providers.embedding-provider", defaultEmbeddingProvider(),
            STORAGE_STRATEGY_PROPERTY, defaultStorageStrategy()
        );
    }

    private List<ProviderCombination> parseMatrixSpec(String matrixSpec) {
        return Arrays.stream(matrixSpec.split(","))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .map(entry -> {
                String[] parts = entry.split(":");
                if (parts.length < 2 || parts.length > 4) {
                    throw new IllegalArgumentException(
                        "Invalid provider matrix entry: '" + entry + "'. Expected llm:embedding[:vectordb][:storageStrategy]");
                }

                String llm = parts[0].trim();
                String embedding = parts[1].trim();
                String vectorDb = parts.length >= 3 ? parts[2].trim() : null;
                String storageStrategy = parts.length == 4 ? parts[3].trim() : defaultStorageStrategy();

                if (!StringUtils.hasText(llm) || !StringUtils.hasText(embedding)) {
                    throw new IllegalArgumentException(
                        "Invalid provider matrix entry: '" + entry + "'. LLM and embedding providers cannot be empty.");
                }

                if (!StringUtils.hasText(storageStrategy)) {
                    storageStrategy = defaultStorageStrategy();
                }

                return new ProviderCombination(llm, embedding, vectorDb, storageStrategy);
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

    private void configureProviderProperties(ProviderCombination combination) {
        System.setProperty("LLM_PROVIDER", combination.llmProvider());
        System.setProperty("ai.providers.llm-provider", combination.llmProvider());
        System.setProperty("EMBEDDING_PROVIDER", combination.embeddingProvider());
        System.setProperty("ai.providers.embedding-provider", combination.embeddingProvider());
        System.setProperty(STORAGE_STRATEGY_PROPERTY, combination.storageStrategy());

        if (StringUtils.hasText(combination.vectorDbProvider())) {
            System.setProperty(VECTORDB_PROPERTY, combination.vectorDbProvider());
        } else {
            System.clearProperty(VECTORDB_PROPERTY);
        }
    }

    private void clearProviderProperties(ProviderCombination combination) {
        System.clearProperty("LLM_PROVIDER");
        System.clearProperty("ai.providers.llm-provider");
        System.clearProperty("EMBEDDING_PROVIDER");
        System.clearProperty("ai.providers.embedding-provider");
        System.clearProperty(VECTORDB_PROPERTY);
        System.clearProperty(STORAGE_STRATEGY_PROPERTY);
        log.debug("Cleared provider properties at {}", Instant.now());
    }

    protected void beforeMatrixExecution() {
        cleanLuceneIndex();
    }

    private void cleanLuceneIndex() {
        Path indexPath = Path.of("data", "lucene-vector-index");
        if (!Files.exists(indexPath)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(indexPath)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            });
        } catch (IOException ignored) {
            // ignore
        }
    }

    protected String suiteDisplayName() {
        return getClass().getSimpleName();
    }

    protected String defaultLlmProvider() {
        return "openai";
    }

    protected String defaultEmbeddingProvider() {
        return "onnx";
    }

    protected String discoveryProfile() {
        return "real-api-test";
    }

    protected Map<String, Object> additionalDiscoveryProperties() {
        return Map.of();
    }

    protected abstract Class<?>[] suiteTestClasses();

    protected String matrixPropertyKey() {
        return "ai.providers.real-api.matrix";
    }

    protected String matrixEnvVariable() {
        return "AI_PROVIDERS_REAL_API_MATRIX";
    }

    protected record ProviderCombination(
        String llmProvider,
        String embeddingProvider,
        String vectorDbProvider,
        String storageStrategy
    ) {
        public ProviderCombination(String llmProvider, String embeddingProvider) {
            this(llmProvider, embeddingProvider, null, defaultStorageStrategy());
        }

        public ProviderCombination(String llmProvider, String embeddingProvider, String vectorDbProvider) {
            this(llmProvider, embeddingProvider, vectorDbProvider, defaultStorageStrategy());
        }

        public String displayName() {
            if (StringUtils.hasText(vectorDbProvider)) {
                return "LLM=" + llmProvider + " | Embedding=" + embeddingProvider + " | VectorDB=" + vectorDbProvider + " | Storage=" + storageStrategy;
            }
            return "LLM=" + llmProvider + " | Embedding=" + embeddingProvider + " | Storage=" + storageStrategy;
        }

        @Override
        public String toString() {
            if (StringUtils.hasText(vectorDbProvider)) {
                return llmProvider + "/" + embeddingProvider + "/" + vectorDbProvider + "/" + storageStrategy;
            }
            return llmProvider + "/" + embeddingProvider + "/" + storageStrategy;
        }
    }

    private List<ProviderCombination> expandWithStorageStrategies(List<ProviderCombination> base) {
        List<String> storageStrategies = storageStrategies();
        return base.stream()
            .flatMap(combo -> storageStrategies.stream()
                .map(storage -> new ProviderCombination(
                    combo.llmProvider(),
                    combo.embeddingProvider(),
                    combo.vectorDbProvider(),
                    storage)))
            .toList();
    }

    protected List<String> storageStrategies() {
        return List.of("SINGLE_TABLE", "PER_TYPE_TABLE");
    }

    protected static String defaultStorageStrategy() {
        return "PER_TYPE_TABLE";
    }

    protected List<String> vectorDbProviders() {
        return java.util.Arrays.asList("lucene", null);
    }
}
