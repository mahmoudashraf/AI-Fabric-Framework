package com.ai.behavior.integration;

import com.ai.behavior.model.BehaviorEventEntity;
import com.ai.behavior.model.BehaviorInsights;
import com.ai.behavior.policy.BehaviorAnalysisPolicy;
import com.ai.behavior.repository.BehaviorEventRepository;
import com.ai.behavior.repository.BehaviorInsightsRepository;
import com.ai.behavior.service.BehaviorEventIngestionService;
import com.ai.behavior.worker.BehaviorAnalysisWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "ai.behavior.test.mock-policy=false")
@Import(PolicyOverrideIntegrationTest.CustomPolicyConfiguration.class)
@Disabled
public class PolicyOverrideIntegrationTest extends BehaviorAnalyticsIntegrationTest {

    @Autowired
    private BehaviorEventIngestionService ingestionService;

    @Autowired
    private BehaviorAnalysisWorker analysisWorker;

    @Autowired
    private BehaviorInsightsRepository insightsRepository;

    @Autowired
    private BehaviorEventRepository eventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanDatabase() {
        insightsRepository.deleteAll();
        eventRepository.deleteAll();
        CustomPolicyConfiguration.reset();
    }

    @Test
    void customPolicyShouldDrivePersistedInsights() throws Exception {
        UUID userId = UUID.randomUUID();
        ingestionService.ingestBatchEvents(List.of(
            newEvent(userId, "custom.eventA"),
            newEvent(userId, "custom.eventB")
        ));

        analysisWorker.processUnprocessedEvents();

        BehaviorInsights insights = insightsRepository.findTopByUserIdOrderByAnalyzedAtDesc(userId)
            .orElseThrow();

        assertThat(insights.getSegment()).isEqualTo("loyalist");
        assertThat(insights.getPatterns()).containsExactly("blue_ocean", "alpha_wave");
        assertThat(insights.getRecommendations()).containsExactly("surge_campaign");
        assertThat(insights.getScores()).containsEntry("confidenceScore", 0.42);
        assertThat(CustomPolicyConfiguration.detectPatternsCalls).isEqualTo(1);
        assertThat(CustomPolicyConfiguration.determineSegmentCalls).isEqualTo(1);
    }

    private BehaviorEventEntity newEvent(UUID userId, String eventType) throws Exception {
        JsonNode payload = objectMapper.readTree("""
            {"value": 99, "source": "policy-override"}
            """);
        return BehaviorEventEntity.builder()
            .userId(userId)
            .eventType(eventType)
            .eventData(payload)
            .source("policy-override")
            .createdAt(OffsetDateTime.now())
            .build();
    }

    @TestConfiguration
    @ConditionalOnProperty(name = "ai.behavior.test.policy-override.enabled", havingValue = "true", matchIfMissing = false)
    static class CustomPolicyConfiguration {
        static int detectPatternsCalls = 0;
        static int determineSegmentCalls = 0;

        static void reset() {
            detectPatternsCalls = 0;
            determineSegmentCalls = 0;
        }

        @Bean
        @Primary
        BehaviorAnalysisPolicy customBehaviorAnalysisPolicy() {
            return new BehaviorAnalysisPolicy() {
                @Override
                public List<String> detectPatterns(UUID userId, List<BehaviorEventEntity> events, Map<String, Double> scores) {
                    detectPatternsCalls++;
                    return List.of("blue_ocean", "alpha_wave");
                }

                @Override
                public String determineSegment(UUID userId, List<BehaviorEventEntity> events, Map<String, Double> scores) {
                    determineSegmentCalls++;
                    return "loyalist";
                }

                @Override
                public List<String> generateRecommendations(UUID userId, List<BehaviorEventEntity> events, Map<String, Double> scores) {
                    return List.of("surge_campaign");
                }

                @Override
                public double calculateConfidence(UUID userId, List<BehaviorEventEntity> events, Map<String, Double> scores) {
                    return 0.42;
                }
            };
        }
    }
}
