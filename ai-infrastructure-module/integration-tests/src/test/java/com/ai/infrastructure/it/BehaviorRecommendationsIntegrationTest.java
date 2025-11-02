package com.ai.infrastructure.it;

import com.ai.infrastructure.dto.BehaviorAnalysisResult;
import com.ai.infrastructure.dto.BehaviorRequest;
import com.ai.infrastructure.entity.Behavior;
import com.ai.infrastructure.repository.BehaviorRepository;
import com.ai.infrastructure.service.AICapabilityService;
import com.ai.infrastructure.service.BehaviorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;

/**
 * Integration coverage for TEST-BEHAVIOR-004: Behavior-Based Recommendations.
 * <p>
 * The current {@link BehaviorService#analyzeBehaviors(UUID)} implementation returns
 * a synthesized analysis record. This test exercises that workflow end-to-end to
 * ensure meaningful data is produced once the behavior history is populated.
 * </p>
 */
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("dev")
class BehaviorRecommendationsIntegrationTest {

    @Autowired
    private BehaviorService behaviorService;

    @Autowired
    private BehaviorRepository behaviorRepository;

    @MockBean
    private AICapabilityService aiCapabilityService;

    @AfterEach
    void tearDown() {
        behaviorRepository.deleteAll();
        Mockito.reset(aiCapabilityService);
    }

    @Test
    @DisplayName("Behavior analysis returns recommendation scaffold after representative activity")
    void analyzeBehaviorsProducesRecommendations() {
        UUID userId = UUID.randomUUID();

        // Browsing phase: user explores luxury watches
        IntStream.range(0, 8).forEach(index -> behaviorService.createBehavior(
            BehaviorRequest.builder()
                .userId(userId.toString())
                .behaviorType(Behavior.BehaviorType.PRODUCT_VIEW.name())
                .entityType("product")
                .entityId("watch-" + index)
                .action("VIEW")
                .sessionId("luxury-session")
                .build()
        ));

        // Cart commitment
        behaviorService.createBehavior(
            BehaviorRequest.builder()
                .userId(userId.toString())
                .behaviorType(Behavior.BehaviorType.ADD_TO_CART.name())
                .entityType("product")
                .entityId("watch-3")
                .action("ADD_TO_CART")
                .value(new BigDecimal("4800").toPlainString())
                .sessionId("luxury-session")
                .build()
        );

        // Purchase completion
        behaviorService.createBehavior(
            BehaviorRequest.builder()
                .userId(userId.toString())
                .behaviorType(Behavior.BehaviorType.PURCHASE.name())
                .entityType("order")
                .entityId("order-1")
                .action("PURCHASE")
                .value(new BigDecimal("5200").toPlainString())
                .sessionId("luxury-session")
                .build()
        );

        BehaviorAnalysisResult analysisResult = behaviorService.analyzeBehaviors(userId);

        assertNotNull(analysisResult, "Analysis result must not be null");
        assertNotNull(analysisResult.getAnalysisId(), "Analysis result should have an identifier");
        assertEquals(userId.toString(), analysisResult.getUserId(), "Recorded user ID should match the analyzed subject");
        assertFalse(analysisResult.getRecommendations().isEmpty(), "Recommendations list should contain entries");
        assertTrue(analysisResult.getConfidenceScore() >= 0.7, "Default confidence score should reflect a trusted analysis baseline");

        Mockito.verify(aiCapabilityService, atLeast(10)).processEntityForAI(any(Behavior.class), eq("behavior"));
    }
}

