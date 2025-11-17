package com.ai.infrastructure.it.BehaviouralTests;

import com.ai.behavior.model.BehaviorInsights;
import com.ai.behavior.service.BehaviorAnalysisService;
import com.ai.behavior.storage.BehaviorEventRepository;
import com.ai.infrastructure.dto.BehaviorRequest;
import com.ai.infrastructure.it.TestApplication;
import com.ai.infrastructure.service.BehaviorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("dev")
class PatternAnalyzerInsightsIntegrationTest {

    @Autowired
    private BehaviorService behaviorService;

    @Autowired
    private BehaviorAnalysisService behaviorAnalysisService;

    @Autowired
    private BehaviorEventRepository behaviorEventRepository;

    @AfterEach
    void clean() {
        behaviorEventRepository.deleteAll();
    }

    @Test
    @DisplayName("Pattern analyzer produces rich insights with segment and recommendations")
    void analyzerBuildsSegmentedInsights() {
        UUID userId = UUID.randomUUID();

        IntStream.range(0, 6).forEach(index -> {
            behaviorService.createBehavior(BehaviorRequest.builder()
                .userId(userId.toString())
                .behaviorType("VIEW")
                .entityType("product")
                .entityId("product-" + index)
                .action("VIEW")
                .metadata("{\"category\":\"luxury\",\"price\":\"1500\"}")
                .build());
        });

        IntStream.range(0, 2).forEach(index -> {
            behaviorService.createBehavior(BehaviorRequest.builder()
                .userId(userId.toString())
                .behaviorType("ADD_TO_CART")
                .entityType("product")
                .entityId("cart-" + index)
                .action("ADD_TO_CART")
                .metadata("{\"category\":\"luxury\"}")
                .build());
        });

        behaviorService.createBehavior(BehaviorRequest.builder()
            .userId(userId.toString())
            .behaviorType("PURCHASE")
            .entityType("order")
            .entityId("order-1")
            .action("PURCHASE")
            .metadata("{\"amount\":\"2500\"}")
            .build());

        BehaviorInsights insights = behaviorAnalysisService.analyze(userId);

        assertThat(insights.getSegment()).isEqualTo("needs_nurturing");
        assertThat(insights.getPatterns()).isNotEmpty();
        assertThat(insights.getRecommendations()).isNotEmpty();
        assertThat(insights.getPreferences()).containsKey("preferred_categories");
    }
}
