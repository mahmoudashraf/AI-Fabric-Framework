package com.ai.infrastructure.it;

import com.ai.infrastructure.dto.BehaviorAnalysisResult;
import com.ai.infrastructure.dto.BehaviorRequest;
import com.ai.infrastructure.entity.Behavior;
import com.ai.infrastructure.repository.BehaviorRepository;
import com.ai.infrastructure.service.AICapabilityService;
import com.ai.infrastructure.service.BehaviorService;
import com.ai.infrastructure.it.AbstractBehaviorIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;

@SpringBootTest(classes = TestApplication.class)
class BehaviorPatternDetectionIntegrationTest extends AbstractBehaviorIntegrationTest {

    @Autowired
    private BehaviorService behaviorService;

    @Autowired
    private BehaviorRepository behaviorRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private AICapabilityService aiCapabilityService;

    @AfterEach
    void tearDown() {
        behaviorRepository.deleteAll();
        Mockito.reset(aiCapabilityService);
    }

    @Test
    @DisplayName("Behavior analysis detects funnels, temporal preferences, and category affinity")
    void behaviorPatternDetectionIdentifiesKeySignals() {
        UUID userId = UUID.randomUUID();
        LocalDate baseWeek = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        for (int dayOffset = 0; dayOffset < 7; dayOffset++) {
            LocalDate currentDate = baseWeek.plusDays(dayOffset);
            boolean weekend = currentDate.getDayOfWeek() == DayOfWeek.SATURDAY
                || currentDate.getDayOfWeek() == DayOfWeek.SUNDAY;
            int funnelsForDay = weekend ? 5 : 1;
            for (int funnelIndex = 0; funnelIndex < funnelsForDay; funnelIndex++) {
                recordBehaviorSequence(userId, currentDate, weekend, funnelIndex);
            }
        }

        BehaviorAnalysisResult analysisResult = behaviorService.analyzeBehaviors(userId);

        assertTrue(analysisResult.getPatterns().stream().anyMatch(pattern -> pattern.contains("Browse")),
            "Analysis should flag browse → cart → purchase funnel");
        assertTrue(analysisResult.getPatterns().stream().anyMatch(pattern -> pattern.contains("Evening")),
            "Analysis should capture evening shopping preference");
        assertTrue(analysisResult.getPatterns().stream().anyMatch(pattern -> pattern.contains("Weekend")),
            "Analysis should detect weekend engagement spike");
        assertTrue(analysisResult.getPatterns().stream().anyMatch(pattern -> pattern.contains("Category affinity")),
            "Analysis should surface dominant category affinity");
        assertTrue(analysisResult.getConfidenceScore() >= 0.7,
            "Confidence score should reflect high-quality signal detection");

        List<Behavior> refreshed = behaviorRepository.findByUserIdOrderByCreatedAtDesc(userId);
        assertFalse(refreshed.isEmpty(), "Persisted behaviors should remain accessible");
        String patternFlags = refreshed.get(0).getPatternFlags();
        assertNotNull(patternFlags, "Pattern flags should be persisted on behaviors");
        assertTrue(patternFlags.contains("FUNNEL_COMPLETED"), "Pattern flags should include funnel detection");
        assertTrue(patternFlags.contains("EVENING_SHOPPER"), "Pattern flags should include evening preference");
        assertTrue(patternFlags.contains("WEEKEND_PREFERENCE"), "Pattern flags should include weekend preference");
        assertTrue(patternFlags.toLowerCase().contains("category"), "Pattern flags should encode category affinity");

        Mockito.verify(aiCapabilityService, atLeast(analysisResult.getPatterns().size()))
            .processEntityForAI(any(Behavior.class), eq("behavior"));
    }

    private void recordBehaviorSequence(UUID userId, LocalDate date, boolean weekend, int sequenceIndex) {
        String category = weekend ? "watches" : "handbags";
        LocalTime baseTime = LocalTime.of(19, 0).plusMinutes(sequenceIndex * 10L);
        String sessionKey = "session-" + date.getDayOfMonth() + "-" + sequenceIndex;

        BehaviorRequest searchRequest = BehaviorRequest.builder()
            .userId(userId.toString())
            .behaviorType(Behavior.BehaviorType.SEARCH_QUERY.name())
            .entityType("search")
            .entityId("query-" + sessionKey)
            .action("SEARCH")
            .metadata(String.format("{\"category\":\"%s\",\"keywords\":[\"luxury\"]}", category))
            .sessionId(sessionKey)
            .build();
        storeBehaviorAndAdjustTimestamp(searchRequest, date.atTime(baseTime));

        for (int viewIndex = 0; viewIndex < 4; viewIndex++) {
            BehaviorRequest viewRequest = BehaviorRequest.builder()
                .userId(userId.toString())
                .behaviorType(Behavior.BehaviorType.PRODUCT_VIEW.name())
                .entityType("product")
                .entityId("product-" + date.getDayOfMonth() + "-" + sequenceIndex + "-" + viewIndex)
                .action("VIEW")
                .metadata(String.format("{\"category\":\"%s\",\"brand\":\"Rolex\",\"viewIndex\":%d}", category, viewIndex))
                .sessionId(sessionKey)
                .build();
            storeBehaviorAndAdjustTimestamp(viewRequest, date.atTime(baseTime.plusMinutes(1 + viewIndex)));
        }

        BehaviorRequest recommendationViewRequest = BehaviorRequest.builder()
            .userId(userId.toString())
            .behaviorType(Behavior.BehaviorType.RECOMMENDATION_VIEW.name())
            .entityType("product")
            .entityId("recommendation-" + sessionKey)
            .action("RECOMMENDATION_VIEW")
            .metadata(String.format("{\"category\":\"%s\",\"source\":\"personalized\"}", category))
            .sessionId(sessionKey)
            .build();
        storeBehaviorAndAdjustTimestamp(recommendationViewRequest, date.atTime(baseTime.plusMinutes(5)));

        BehaviorRequest addToCartRequest = BehaviorRequest.builder()
            .userId(userId.toString())
            .behaviorType(Behavior.BehaviorType.ADD_TO_CART.name())
            .entityType("product")
            .entityId("product-" + date.getDayOfMonth() + "-" + sequenceIndex + "-primary")
            .action("ADD_TO_CART")
            .metadata(String.format("{\"category\":\"%s\",\"step\":\"cart\"}", category))
            .sessionId(sessionKey)
            .build();
        storeBehaviorAndAdjustTimestamp(addToCartRequest, date.atTime(baseTime.plusMinutes(6)));

        BehaviorRequest purchaseRequest = BehaviorRequest.builder()
            .userId(userId.toString())
            .behaviorType(Behavior.BehaviorType.PURCHASE.name())
            .entityType("order")
            .entityId("order-" + sessionKey)
            .action("PURCHASE")
            .metadata(String.format("{\"category\":\"%s\",\"orderValue\":\"12500\"}", category))
            .sessionId(sessionKey)
            .build();
        storeBehaviorAndAdjustTimestamp(purchaseRequest, date.atTime(baseTime.plusMinutes(7)));
    }

    private UUID storeBehaviorAndAdjustTimestamp(BehaviorRequest request, LocalDateTime timestamp) {
        UUID behaviorId = UUID.fromString(behaviorService.createBehavior(request).getId());
        jdbcTemplate.update("UPDATE behaviors SET created_at = ? WHERE id = ?",
            Timestamp.valueOf(timestamp), behaviorId);
        return behaviorId;
    }
}

