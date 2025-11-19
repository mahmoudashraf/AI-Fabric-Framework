package com.ai.infrastructure.it.realapi;

import com.ai.infrastructure.it.BehaviouralTests.AggregatedBehaviorProviderIntegrationTest;
import com.ai.infrastructure.it.BehaviouralTests.AnomalyDetectionWorkerIntegrationTest;
import com.ai.infrastructure.it.BehaviouralTests.DatabaseSinkApiRoundtripIntegrationTest;
import com.ai.infrastructure.it.BehaviouralTests.ExternalAnalyticsAdapterContractTest;
import com.ai.infrastructure.it.BehaviouralTests.HybridEventSinkIntegrationTest;
import com.ai.infrastructure.it.BehaviouralTests.KafkaEventSinkIntegrationTest;
import com.ai.infrastructure.it.BehaviouralTests.PatternAnalyzerInsightsIntegrationTest;
import com.ai.infrastructure.it.BehaviouralTests.RedisEventSinkIntegrationTest;
import com.ai.infrastructure.it.BehaviouralTests.S3EventSinkIntegrationTest;
import com.ai.infrastructure.it.BehaviouralTests.UserSegmentationWorkerIntegrationTest;

import java.util.List;

/**
 * Behaviour-focused analogue to {@link RealAPIProviderMatrixIntegrationTest}.
 * Runs the deterministic Behaviour / sink integration tests across provider
 * matrix combinations so we can verify hybrid + fallback behaviour end-to-end.
 */
public class RealAPIProviderBehaviourMatrixIntegrationTest extends AbstractProviderMatrixIntegrationTest {

    private static final Class<?>[] BEHAVIOUR_TEST_CLASSES = {
        DatabaseSinkApiRoundtripIntegrationTest.class,
        KafkaEventSinkIntegrationTest.class,
        RedisEventSinkIntegrationTest.class,
        HybridEventSinkIntegrationTest.class,
        S3EventSinkIntegrationTest.class,
        AggregatedBehaviorProviderIntegrationTest.class,
        ExternalAnalyticsAdapterContractTest.class,
        AnomalyDetectionWorkerIntegrationTest.class,
        UserSegmentationWorkerIntegrationTest.class,
        PatternAnalyzerInsightsIntegrationTest.class
    };

    @Override
    protected Class<?>[] suiteTestClasses() {
        return BEHAVIOUR_TEST_CLASSES;
    }

    @Override
    protected String matrixPropertyKey() {
        return "ai.providers.behaviour.matrix";
    }

    @Override
    protected String matrixEnvVariable() {
        return "AI_PROVIDERS_BEHAVIOUR_MATRIX";
    }

    @Override
    protected String discoveryProfile() {
        return "dev";
    }

    @Override
    protected String suiteDisplayName() {
        return "Behaviour Provider Matrix Integration Tests";
    }

    @Override
    protected List<ProviderCombination> availableProviderCombinations() {
        return List.of(new ProviderCombination(defaultLlmProvider(), defaultEmbeddingProvider()));
    }
}
