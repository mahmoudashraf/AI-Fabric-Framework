package com.ai.behavior.integration;

import com.ai.behavior.config.BehaviorModuleProperties;
import com.ai.behavior.model.BehaviorEventEntity;
import com.ai.behavior.model.BehaviorInsights;
import com.ai.behavior.policy.BehaviorAnalysisPolicy;
import com.ai.behavior.policy.DefaultBehaviorAnalysisPolicy;
import com.ai.behavior.repository.BehaviorEventRepository;
import com.ai.behavior.repository.BehaviorInsightsRepository;
import com.ai.behavior.service.BehaviorEventIngestionService;
import com.ai.behavior.worker.BehaviorAnalysisWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
    "ai.behavior.events.batch-size=10"
})
@Import(DefaultPolicyIntegrationTest.DefaultPolicyConfig.class)
public class DefaultPolicyIntegrationTest extends BehaviorAnalyticsIntegrationTest {
    @DynamicPropertySource
    static void disableTestPolicy(DynamicPropertyRegistry registry) {
        registry.add("ai.behavior.test.mock-policy", () -> "false");
    }


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

    @Autowired
    private BehaviorAnalysisPolicy behaviorAnalysisPolicy;

    @BeforeEach
    void cleanRepositories() {
        insightsRepository.deleteAll();
        eventRepository.deleteAll();
    }

    @Test
    void defaultPolicyShouldProducePowerUserSegment() throws Exception {
        UUID userId = UUID.randomUUID();
        for (int i = 0; i < 8; i++) {
            ingestionService.ingestSingleEvent(newEvent(userId, "engagement.view"));
        }

        analysisWorker.processUnprocessedEvents();

        BehaviorInsights insights = insightsRepository.findTopByUserIdOrderByAnalyzedAtDesc(userId)
            .orElseThrow();

        assertThat(behaviorAnalysisPolicy).isInstanceOf(DefaultBehaviorAnalysisPolicy.class);
        assertThat(insights.getSegment()).isEqualTo("power_user");
        assertThat(insights.getPatterns()).contains("high_engagement", "recent_activity");
        assertThat(insights.getRecommendations()).contains("offer_loyalty_reward");
        assertThat(insights.getScores()).containsKeys("engagementScore", "recencyScore", "confidenceScore");
    }

    private BehaviorEventEntity newEvent(UUID userId, String eventType) throws Exception {
        JsonNode payload = objectMapper.readTree("""
            {"value": 7, "source": "default-policy"}
            """);
        return BehaviorEventEntity.builder()
            .userId(userId)
            .eventType(eventType)
            .eventData(payload)
            .source("default-policy")
            .createdAt(OffsetDateTime.now())
            .build();
    }

    @TestConfiguration
    static class DefaultPolicyConfig {
        @Bean
        @Primary
        BehaviorAnalysisPolicy defaultPolicyUnderTest(BehaviorModuleProperties properties) {
            return new DefaultBehaviorAnalysisPolicy(properties);
        }
    }
}
