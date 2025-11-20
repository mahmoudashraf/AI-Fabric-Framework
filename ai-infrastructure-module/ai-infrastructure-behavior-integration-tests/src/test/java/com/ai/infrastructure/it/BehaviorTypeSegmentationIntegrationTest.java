package com.ai.infrastructure.it;

import com.ai.infrastructure.dto.BehaviorRequest;
import com.ai.infrastructure.dto.BehaviorResponse;
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
import com.ai.infrastructure.it.config.PostgresTestContainerConfig;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;

/**
 * Integration coverage for TEST-BEHAVIOR-008: Behavior Scoring.
 * <p>
 * While explicit scoring logic is not implemented in the service layer yet, this
 * test ensures the foundational data retrieval needed for scoring scenarios is
 * reliable by segmenting behaviors per type and computing a simple aggregate score.
 * </p>
 */
@SpringBootTest(classes = TestApplication.class)
@Import(PostgresTestContainerConfig.class)
class BehaviorTypeSegmentationIntegrationTest {

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
    @DisplayName("Behavior type segmentation supports downstream engagement scoring")
    void behaviorTypeSegmentationSupportsScoring() {
        UUID userId = UUID.randomUUID();

        // Seed engagement funnel: views, add-to-cart, purchase, share
        createBehavior(userId, Behavior.BehaviorType.PRODUCT_VIEW, "product", "watch-101", "VIEW");
        createBehavior(userId, Behavior.BehaviorType.PRODUCT_VIEW, "product", "watch-102", "VIEW");
        createBehavior(userId, Behavior.BehaviorType.ADD_TO_CART, "product", "watch-101", "ADD_TO_CART");
        createBehavior(userId, Behavior.BehaviorType.PURCHASE, "order", "order-42", "PURCHASE");
        createBehavior(userId, Behavior.BehaviorType.SHARE, "product", "watch-101", "SHARE");

        List<BehaviorResponse> views = behaviorService.getBehaviorsByUserIdAndType(userId, Behavior.BehaviorType.PRODUCT_VIEW);
        List<BehaviorResponse> cartActions = behaviorService.getBehaviorsByUserIdAndType(userId, Behavior.BehaviorType.ADD_TO_CART);
        List<BehaviorResponse> purchases = behaviorService.getBehaviorsByUserIdAndType(userId, Behavior.BehaviorType.PURCHASE);
        List<BehaviorResponse> shares = behaviorService.getBehaviorsByUserIdAndType(userId, Behavior.BehaviorType.SHARE);

        assertEquals(2, views.size());
        assertEquals(1, cartActions.size());
        assertEquals(1, purchases.size());
        assertEquals(1, shares.size());

        int engagementScore = computeEngagementScore(Map.of(
            Behavior.BehaviorType.PRODUCT_VIEW, views.size(),
            Behavior.BehaviorType.ADD_TO_CART, cartActions.size(),
            Behavior.BehaviorType.PURCHASE, purchases.size(),
            Behavior.BehaviorType.SHARE, shares.size()
        ));

        assertTrue(engagementScore >= 0, "Engagement score should be non-negative");
        assertEquals(19, engagementScore);

        assertNotNull(behaviorService.getBehaviorsByUserId(userId));
        Mockito.verify(aiCapabilityService, times(5)).processEntityForAI(any(Behavior.class), eq("behavior"));
    }

    private void createBehavior(UUID userId, Behavior.BehaviorType behaviorType, String entityType, String entityId, String action) {
        behaviorService.createBehavior(
            BehaviorRequest.builder()
                .userId(userId.toString())
                .behaviorType(behaviorType.name())
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .sessionId("session-" + userId)
                .build()
        );
    }

    private int computeEngagementScore(Map<Behavior.BehaviorType, Integer> counts) {
        return counts.entrySet().stream()
            .mapToInt(entry -> points(entry.getKey()) * entry.getValue())
            .sum();
    }

    private int points(Behavior.BehaviorType type) {
        return switch (type) {
            case PRODUCT_VIEW -> 1;
            case ADD_TO_CART -> 5;
            case PURCHASE -> 10;
            case SHARE -> 2;
            default -> 0;
        };
    }
}

