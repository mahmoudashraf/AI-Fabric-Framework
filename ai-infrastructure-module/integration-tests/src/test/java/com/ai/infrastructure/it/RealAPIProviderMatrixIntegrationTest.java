package com.ai.infrastructure.it;

import com.ai.infrastructure.embedding.EmbeddingProvider;
import com.ai.infrastructure.it.support.RealAPITestSupport;
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
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/**
 * Executes the entire Real API integration test suite for every available provider combination.
 *
 * <p>The combinations can be supplied via {@code -Dai.providers.real-api.matrix} (or
 * {@code AI_PROVIDERS_REAL_API_MATRIX}) using the syntax {@code llm:embedding,llm2:embedding2}.
 * When the matrix is not provided, all discovered provider pairings are exercised.</p>
 */
class RealAPIProviderMatrixIntegrationTest {

    private static final String MATRIX_PROPERTY = "ai.providers.real-api.matrix";
    private static final String MATRIX_ENV = "AI_PROVIDERS_REAL_API_MATRIX";

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

        return combinations.stream()
            .map(combo -> DynamicTest.dynamicTest(
                "LLM=" + combo.llmProvider() + " | Embedding=" + combo.embeddingProvider(),
                () -> executeSuite(combo)
            ));
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
        configureProviderProperties("openai", "onnx");

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
            clearProviderProperties();
        }
    }

    private List<ProviderCombination> parseMatrixSpec(String matrixSpec) {
        return Arrays.stream(matrixSpec.split(","))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .map(entry -> entry.split(":", 2))
            .map(parts -> {
                if (parts.length != 2 || !StringUtils.hasText(parts[0]) || !StringUtils.hasText(parts[1])) {
                    throw new IllegalArgumentException(
                        "Invalid provider matrix entry: '" + Arrays.toString(parts) + "'. Expected format llm:embedding");
                }
                return new ProviderCombination(parts[0].trim(), parts[1].trim());
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
        configureProviderProperties(combination.llmProvider(), combination.embeddingProvider());

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
            if (summary.getTotalFailureCount() > 0) {
                String failures = summary.getFailures().stream()
                    .map(failure -> failure.getTestIdentifier().getDisplayName() + " -> " + failure.getException().getMessage())
                    .collect(Collectors.joining(System.lineSeparator()));
                throw new AssertionError("Failures detected for combination " + combination + System.lineSeparator() + failures);
            }
        } finally {
            clearProviderProperties();
        }
    }

    private void configureProviderProperties(String llmProvider, String embeddingProvider) {
        System.setProperty("LLM_PROVIDER", llmProvider);
        System.setProperty("ai.providers.llm-provider", llmProvider);
        System.setProperty("EMBEDDING_PROVIDER", embeddingProvider);
        System.setProperty("ai.providers.embedding-provider", embeddingProvider);
    }

    private void clearProviderProperties() {
        System.clearProperty("LLM_PROVIDER");
        System.clearProperty("ai.providers.llm-provider");
        System.clearProperty("EMBEDDING_PROVIDER");
        System.clearProperty("ai.providers.embedding-provider");
    }

    private record ProviderCombination(String llmProvider, String embeddingProvider) {
        @Override
        public String toString() {
            return llmProvider + "/" + embeddingProvider;
        }
    }
}
