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

import java.util.Map;

/**
 * Executes the Real API provider matrix suite, iterating over every
 * discovered LLM/Embedding(/Vector DB) combination.
 */
public class RealAPIProviderMatrixIntegrationTest extends AbstractProviderMatrixIntegrationTest {

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
        return REAL_API_TEST_CLASSES;
    }

    @Override
    protected Map<String, Object> additionalDiscoveryProperties() {
        return Map.of(
            "spring.liquibase.enabled", "false"
        );
    }
}
