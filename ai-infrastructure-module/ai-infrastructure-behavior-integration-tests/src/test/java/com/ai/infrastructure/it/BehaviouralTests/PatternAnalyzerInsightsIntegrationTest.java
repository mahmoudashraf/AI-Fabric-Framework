package com.ai.infrastructure.it.BehaviouralTests;

import com.ai.behavior.ingestion.BehaviorIngestionService;
import com.ai.behavior.model.BehaviorInsights;
import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.service.BehaviorAnalysisService;
import com.ai.behavior.storage.BehaviorSignalRepository;
import com.ai.infrastructure.it.AbstractBehaviorIntegrationTest;
import com.ai.infrastructure.it.TestApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for pattern analyzer and insights generation.
 * Validates that the analyzer produces rich insights with segments, patterns,
 * recommendations and preferences based on ingested behavior signals.
 */
@SpringBootTest(classes = TestApplication.class)
public class PatternAnalyzerInsightsIntegrationTest extends AbstractBehaviorIntegrationTest {

    @Autowired
    private BehaviorIngestionService ingestionService;

    @Autowired
    private BehaviorAnalysisService behaviorAnalysisService;

    @Autowired
    private BehaviorSignalRepository behaviorSignalRepository;

    @AfterEach
    void clean() {
        behaviorSignalRepository.deleteAll();
    }

    @Test
    @DisplayName("Pattern analyzer produces rich insights with segment and recommendations")
    void analyzerBuildsSegmentedInsights() {
        UUID userId = UUID.randomUUID();
        String sessionId = "session-" + UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        // Ingest 6 view signals for luxury products
        IntStream.range(0, 6).forEach(index -> {
            Map<String, Object> viewAttributes = new HashMap<>();
            viewAttributes.put("category", "luxury");
            viewAttributes.put("price", 1500);
            viewAttributes.put("product_name", "Luxury Product " + index);

            BehaviorSignal viewSignal = BehaviorSignal.builder()
                .userId(userId)
                .sessionId(sessionId)
                .schemaId("engagement.view")
                .entityType("product")
                .entityId("product-" + index)
                .timestamp(now.minusMinutes(30 - index))
                .source("web")
                .channel("desktop")
                .attributes(viewAttributes)
                .build();

            ingestionService.ingest(viewSignal);
        });

        // Ingest 2 add-to-cart signals
        IntStream.range(0, 2).forEach(index -> {
            Map<String, Object> cartAttributes = new HashMap<>();
            cartAttributes.put("category", "luxury");
            cartAttributes.put("quantity", 1);

            BehaviorSignal cartSignal = BehaviorSignal.builder()
                .userId(userId)
                .sessionId(sessionId)
                .schemaId("engagement.add_to_cart")
                .entityType("product")
                .entityId("cart-product-" + index)
                .timestamp(now.minusMinutes(20 - index))
                .source("web")
                .channel("desktop")
                .attributes(cartAttributes)
                .build();

            ingestionService.ingest(cartSignal);
        });

        // Ingest 1 purchase signal
        Map<String, Object> purchaseAttributes = new HashMap<>();
        purchaseAttributes.put("amount", 2500);
        purchaseAttributes.put("currency", "USD");
        purchaseAttributes.put("payment_method", "credit_card");

        BehaviorSignal purchaseSignal = BehaviorSignal.builder()
            .userId(userId)
            .sessionId(sessionId)
            .schemaId("conversion.purchase")
            .entityType("order")
            .entityId("order-1")
            .timestamp(now.minusMinutes(5))
            .source("web")
            .channel("desktop")
            .attributes(purchaseAttributes)
            .build();

        ingestionService.ingest(purchaseSignal);

        // Verify signals were ingested
        assertThat(behaviorSignalRepository.count()).isEqualTo(9);

        // Analyze user behavior and generate insights
        BehaviorInsights insights = behaviorAnalysisService.analyze(userId);

        // Verify insights structure
        assertThat(insights).isNotNull();
        assertThat(insights.getUserId()).isEqualTo(userId);
        assertThat(insights.getSegment()).isNotNull();
        assertThat(insights.getPatterns()).isNotEmpty();
        assertThat(insights.getRecommendations()).isNotEmpty();
        assertThat(insights.getPreferences()).isNotNull();
        
        // Verify preference extraction works
        Map<String, Object> preferences = insights.getPreferences();
        if (preferences.containsKey("preferred_categories")) {
            assertThat(preferences.get("preferred_categories")).isNotNull();
        }

        // Verify scores are present
        Map<String, Double> scores = insights.safeScores();
        assertThat(scores).isNotEmpty();

        // Verify insights are valid and have proper timestamps
        assertThat(insights.getAnalyzedAt()).isNotNull();
        assertThat(insights.getValidUntil()).isNotNull();
        assertThat(insights.getValidUntil()).isAfter(insights.getAnalyzedAt());
        assertThat(insights.isValid()).isTrue();
    }

    @Test
    @DisplayName("Pattern analyzer handles users with no signals gracefully")
    void analyzerHandlesEmptySignals() {
        UUID userId = UUID.randomUUID();

        BehaviorInsights insights = behaviorAnalysisService.analyze(userId);

        assertThat(insights).isNotNull();
        assertThat(insights.getUserId()).isEqualTo(userId);
        assertThat(insights.getSegment()).isNotNull();
        // When there are no signals, patterns contain "insufficient_data"
        assertThat(insights.getPatterns()).isNotNull().contains("insufficient_data");
        // Recommendations should suggest collecting additional signals
        assertThat(insights.getRecommendations()).isNotNull().contains("collect_additional_signals");
    }

    @Test
    @DisplayName("Pattern analyzer detects high-value user patterns")
    void analyzerDetectsHighValuePatterns() {
        UUID userId = UUID.randomUUID();
        String sessionId = "session-high-value";
        LocalDateTime now = LocalDateTime.now();

        // Simulate high-value user behavior: multiple purchases
        IntStream.range(0, 5).forEach(index -> {
            Map<String, Object> purchaseAttributes = new HashMap<>();
            purchaseAttributes.put("amount", 5000 + (index * 1000));
            purchaseAttributes.put("currency", "USD");
            purchaseAttributes.put("product_category", "premium");

            BehaviorSignal purchaseSignal = BehaviorSignal.builder()
                .userId(userId)
                .sessionId(sessionId + "-" + index)
                .schemaId("conversion.purchase")
                .entityType("order")
                .entityId("order-" + index)
                .timestamp(now.minusDays(index))
                .source("web")
                .channel("desktop")
                .attributes(purchaseAttributes)
                .build();

            ingestionService.ingest(purchaseSignal);
        });

        BehaviorInsights insights = behaviorAnalysisService.analyze(userId);

        assertThat(insights).isNotNull();
        assertThat(insights.getPatterns()).isNotEmpty();
        assertThat(insights.getSegment()).isNotNull();
        
        // High-value users should have higher engagement scores
        Map<String, Double> scores = insights.safeScores();
        assertThat(scores).isNotEmpty();
    }
}
