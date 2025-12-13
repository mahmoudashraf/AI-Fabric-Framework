package com.ai.infrastructure.it.realapi;

import com.ai.infrastructure.it.IndexingStrategyIntegrationTest;
import com.ai.infrastructure.it.RealAPIActionErrorRecoveryIntegrationTest;
import com.ai.infrastructure.it.RealAPIActionFlowIntegrationTest;
import com.ai.infrastructure.it.RealAPIHybridRetrievalToggleIntegrationTest;
import com.ai.infrastructure.it.RealAPIIntegrationTest;
import com.ai.infrastructure.it.RealAPIIntentHistoryAggregationIntegrationTest;
import com.ai.infrastructure.it.RealAPIIntentGenerationRoutingIntegrationTest;
import com.ai.infrastructure.it.RealAPIONNXFallbackIntegrationTest;
import com.ai.infrastructure.it.RealAPIPIIEdgeSpectrumIntegrationTest;
import com.ai.infrastructure.it.RealAPISmartSuggestionsIntegrationTest;
import com.ai.infrastructure.it.RealAPISmartValidationIntegrationTest;
import com.ai.infrastructure.it.RealAPIVectorLifecycleIntegrationTest;
import com.ai.infrastructure.it.support.RealAPITestSupport;
import com.ai.infrastructure.it.RealAPIMultiProviderFailoverIntegrationTest;
import com.ai.infrastructure.it.RealAPICreativeAIScenariosIntegrationTest;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Executes the Real API provider matrix suite, iterating over every
 * discovered LLM/Embedding(/Vector DB) combination.
 * 
 * Supports test chunking via system property: ai.providers.real-api.test-chunk
 * Available chunks: core, vector, intent-actions, advanced, all (default)
 */
public class RealAPIProviderMatrixIntegrationTest extends AbstractProviderMatrixIntegrationTest {

    // Test chunks for parallel execution
    private static final Class<?>[] CHUNK_CORE = {
        RealAPIIntegrationTest.class,
        RealAPIONNXFallbackIntegrationTest.class,
        RealAPISmartValidationIntegrationTest.class
    };

    private static final Class<?>[] CHUNK_VECTOR = {
        RealAPIVectorLifecycleIntegrationTest.class,
        RealAPIHybridRetrievalToggleIntegrationTest.class,
        IndexingStrategyIntegrationTest.class
    };

    private static final Class<?>[] CHUNK_INTENT_ACTIONS = {
        RealAPIIntentHistoryAggregationIntegrationTest.class,
        RealAPIActionErrorRecoveryIntegrationTest.class,
        RealAPIActionFlowIntegrationTest.class,
        RealAPIIntentGenerationRoutingIntegrationTest.class
    };

    private static final Class<?>[] CHUNK_ADVANCED = {
        RealAPIMultiProviderFailoverIntegrationTest.class,
        RealAPISmartSuggestionsIntegrationTest.class,
        RealAPIPIIEdgeSpectrumIntegrationTest.class,
        RealAPICreativeAIScenariosIntegrationTest.class
    };

    private static final Class<?>[] REAL_API_TEST_CLASSES = {
        RealAPIIntegrationTest.class,
        RealAPIONNXFallbackIntegrationTest.class,
        RealAPISmartValidationIntegrationTest.class,
        RealAPIVectorLifecycleIntegrationTest.class,
        RealAPIHybridRetrievalToggleIntegrationTest.class,
        RealAPIIntentHistoryAggregationIntegrationTest.class,
        RealAPIActionErrorRecoveryIntegrationTest.class,
        RealAPIActionFlowIntegrationTest.class,
        RealAPIIntentGenerationRoutingIntegrationTest.class,
        RealAPIMultiProviderFailoverIntegrationTest.class,
        RealAPISmartSuggestionsIntegrationTest.class,
        RealAPIPIIEdgeSpectrumIntegrationTest.class,
        IndexingStrategyIntegrationTest.class,
        RealAPICreativeAIScenariosIntegrationTest.class
    };

    @Override
    public Stream<DynamicTest> providerMatrix() {
        Assumptions.assumeTrue(hasOpenAIKey(),
            "OPENAI_API_KEY not configured; skipping Real API provider matrix.");
        return super.providerMatrix();
    }

    @Override
    protected void beforeMatrixExecution() {
        RealAPITestSupport.ensureOpenAIConfigured();
        Assumptions.assumeTrue(hasOpenAIKey(),
            "OPENAI_API_KEY not configured; skipping Real API provider matrix.");
    }

    @Override
    protected Class<?>[] suiteTestClasses() {
        String chunk = System.getProperty("ai.providers.real-api.test-chunk");
        if (!StringUtils.hasText(chunk)) {
            chunk = System.getenv("AI_PROVIDERS_REAL_API_TEST_CHUNK");
        }
        
        if (!StringUtils.hasText(chunk) || "all".equalsIgnoreCase(chunk)) {
            return REAL_API_TEST_CLASSES;
        }

        return switch (chunk.toLowerCase()) {
            case "core" -> CHUNK_CORE;
            case "vector" -> CHUNK_VECTOR;
            case "intent-actions", "intent_actions" -> CHUNK_INTENT_ACTIONS;
            case "advanced" -> CHUNK_ADVANCED;
            default -> {
                // Support comma-separated chunk names
                String[] chunks = chunk.split(",");
                Class<?>[] selected = Arrays.stream(chunks)
                    .map(String::trim)
                    .flatMap(c -> switch (c.toLowerCase()) {
                        case "core" -> Arrays.stream(CHUNK_CORE);
                        case "vector" -> Arrays.stream(CHUNK_VECTOR);
                        case "intent-actions", "intent_actions" -> Arrays.stream(CHUNK_INTENT_ACTIONS);
                        case "advanced" -> Arrays.stream(CHUNK_ADVANCED);
                        default -> Arrays.stream(new Class<?>[0]);
                    })
                    .distinct()
                    .collect(Collectors.toList())
                    .toArray(new Class<?>[0]);
                yield selected.length > 0 ? selected : REAL_API_TEST_CLASSES;
            }
        };
    }

    @Override
    protected Map<String, Object> additionalDiscoveryProperties() {
        String indexPath = "data/test-lucene-index/realapi-" + System.nanoTime();
        return Map.of(
            "spring.liquibase.enabled", "false",
            "ai.config.default-file", "ai-entity-config-realapi.yml",
            "ai.vector-db.lucene.index-path", indexPath
        );
    }

    @Override
    protected List<String> storageStrategies() {
        return List.of("PER_TYPE_TABLE", "SINGLE_TABLE", "CUSTOM");
    }

    @Override
    protected List<ProviderCombination> resolveProviderMatrix() {
        List<ProviderCombination> available = availableProviderCombinations();

        String matrixSpec = System.getProperty(matrixPropertyKey());
        if (!StringUtils.hasText(matrixSpec)) {
            matrixSpec = System.getenv(matrixEnvVariable());
        }
        if (!StringUtils.hasText(matrixSpec)) {
            return available;
        }

        List<ProviderCombination> requested = parseMatrixSpec(matrixSpec).stream()
            .filter(combo -> storageStrategies().contains(combo.storageStrategy()))
            .toList();

        LinkedHashSet<ProviderCombination> availableSet = new LinkedHashSet<>(available);
        List<ProviderCombination> accepted = requested.stream()
            .filter(availableSet::contains)
            .toList();

        return accepted.isEmpty() ? available : accepted;
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

                if (!StringUtils.hasText(storageStrategy)) {
                    storageStrategy = defaultStorageStrategy();
                }

                return new ProviderCombination(llm, embedding, vectorDb, storageStrategy);
            })
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private boolean hasOpenAIKey() {
        String apiKey = System.getProperty("OPENAI_API_KEY");
        if (!StringUtils.hasText(apiKey)) {
            apiKey = System.getenv("OPENAI_API_KEY");
        }
        return StringUtils.hasText(apiKey);
    }
}
