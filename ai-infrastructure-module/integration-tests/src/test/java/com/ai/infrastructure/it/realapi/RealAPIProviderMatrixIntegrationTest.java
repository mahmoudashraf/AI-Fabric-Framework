package com.ai.infrastructure.it.realapi;

import com.ai.infrastructure.it.IndexingStrategyIntegrationTest;
import com.ai.infrastructure.it.RealAPIActionErrorRecoveryIntegrationTest;
import com.ai.infrastructure.it.RealAPIActionFlowIntegrationTest;
import com.ai.infrastructure.it.RealAPIHybridRetrievalToggleIntegrationTest;
import com.ai.infrastructure.it.RealAPIIntegrationTest;
import com.ai.infrastructure.it.RealAPIIntentHistoryAggregationIntegrationTest;
import com.ai.infrastructure.it.RealAPIONNXFallbackIntegrationTest;
import com.ai.infrastructure.it.RealAPIPIIEdgeSpectrumIntegrationTest;
import com.ai.infrastructure.it.RealAPISmartSuggestionsIntegrationTest;
import com.ai.infrastructure.it.RealAPISmartValidationIntegrationTest;
import com.ai.infrastructure.it.RealAPIVectorLifecycleIntegrationTest;
import com.ai.infrastructure.it.support.RealAPITestSupport;
import com.ai.infrastructure.it.RealAPIMultiProviderFailoverIntegrationTest;
import com.ai.infrastructure.it.RealAPICreativeAIScenariosIntegrationTest;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

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
        RealAPIActionFlowIntegrationTest.class
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
        RealAPIMultiProviderFailoverIntegrationTest.class,
        RealAPISmartSuggestionsIntegrationTest.class,
        RealAPIPIIEdgeSpectrumIntegrationTest.class,
        IndexingStrategyIntegrationTest.class,
        RealAPICreativeAIScenariosIntegrationTest.class
    };

    @Override
    protected void beforeMatrixExecution() {
        RealAPITestSupport.ensureOpenAIConfigured();
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
        return Map.of(
            "spring.liquibase.enabled", "false"
        );
    }
}
