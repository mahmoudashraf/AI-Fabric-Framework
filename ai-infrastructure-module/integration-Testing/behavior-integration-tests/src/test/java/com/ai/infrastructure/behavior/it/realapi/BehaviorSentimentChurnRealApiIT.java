package com.ai.infrastructure.behavior.it.realapi;

import com.ai.infrastructure.behavior.entity.BehaviorInsights;
import com.ai.infrastructure.behavior.it.BehaviorIntegrationTestApp;
import com.ai.infrastructure.behavior.it.support.TestEventProvider;
import com.ai.infrastructure.behavior.model.BehaviorTrend;
import com.ai.infrastructure.behavior.model.ExternalEvent;
import com.ai.infrastructure.behavior.model.SentimentLabel;
import com.ai.infrastructure.behavior.repository.BehaviorInsightsRepository;
import com.ai.infrastructure.behavior.service.BehaviorAnalysisService;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AIGenerationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(
    classes = BehaviorIntegrationTestApp.class,
    properties = {
        "spring.main.allow-bean-definition-overriding=true"
    }
)
@ActiveProfiles("integration")
class BehaviorSentimentChurnRealApiIT {

    @Autowired
    private BehaviorAnalysisService analysisService;

    @Autowired
    private BehaviorInsightsRepository repository;

    @Autowired
    private TestEventProvider eventProvider;

    @MockBean
    private AICoreService aiCoreService;

    @BeforeEach
    void setup() {
        repository.deleteAll();
        eventProvider.clear();
    }

    @Test
    void targetedAnalysis_populatesAllSentimentChurnFields() {
        String userId = "test-user-" + java.util.UUID.randomUUID().toString();
        eventProvider.setTargetedEvents(List.of(
            ExternalEvent.builder()
                .eventType("upgrade")
                .timestamp(LocalDateTime.now())
                .eventData(Map.of("plan", "pro"))
                .source("web")
                .build()
        ));

        when(aiCoreService.generateContent(ArgumentMatchers.any())).thenReturn(
            AIGenerationResponse.builder()
                .content("""
                    {
                      "segment": "Pro",
                      "patterns": ["upgrade"],
                      "sentiment": {"score": 0.9, "label": "DELIGHTED"},
                      "churn": {"risk": 0.05, "reason": "happy path"},
                      "trend": "IMPROVING",
                      "recommendations": ["celebrate"],
                      "insights": {"plan": "pro"},
                      "confidence": 0.92
                    }
                    """)
                .model("stub-model")
                .build()
        );

        BehaviorInsights insight = analysisService.analyzeUser(userId);

        assertThat(insight.getUserId()).isEqualTo(userId);
        assertThat(insight.getSegment()).isEqualTo("Pro");
        assertThat(insight.getPatterns()).contains("upgrade");
        assertThat(insight.getSentimentLabel()).isEqualTo(SentimentLabel.DELIGHTED);
        assertThat(insight.getSentimentScore()).isEqualTo(0.9);
        assertThat(insight.getChurnRisk()).isEqualTo(0.05);
        assertThat(insight.getChurnReason()).isEqualTo("happy path");
        assertThat(insight.getTrend()).isEqualTo(BehaviorTrend.IMPROVING);
        assertThat(insight.getRecommendations()).contains("celebrate");
        assertThat(insight.getConfidence()).isEqualTo(0.92);
    }

    @Test
    void trendRecomputedFromDeltasWhenStableReturned() {
        String userId = "test-user-" + java.util.UUID.randomUUID().toString();
        BehaviorInsights existing = repository.save(
            BehaviorInsights.builder()
                .userId(userId)
                .sentimentScore(0.4)
                .churnRisk(0.2)
                .trend(BehaviorTrend.STABLE)
                .analyzedAt(LocalDateTime.now().minusDays(1))
                .build()
        );

        eventProvider.setTargetedEvents(List.of(
            ExternalEvent.builder()
                .eventType("downgrade")
                .timestamp(LocalDateTime.now())
                .eventData(Map.of("from", "pro", "to", "basic"))
                .source("app")
                .build()
        ));

        when(aiCoreService.generateContent(ArgumentMatchers.any())).thenReturn(
            AIGenerationResponse.builder()
                .content("""
                    {
                      "segment": "AtRisk",
                      "patterns": ["downgrade"],
                      "sentiment": {"score": 0.0, "label": "NEUTRAL"},
                      "churn": {"risk": 0.8, "reason": "downgrade"},
                      "trend": "STABLE",
                      "recommendations": ["retain"],
                      "insights": {},
                      "confidence": 0.6
                    }
                    """)
                .model("stub-model")
                .build()
        );

        BehaviorInsights updated = analysisService.analyzeUser(userId);

        assertThat(updated.getPreviousSentimentScore()).isEqualTo(0.4);
        assertThat(updated.getPreviousChurnRisk()).isEqualTo(0.2);
        assertThat(updated.getSentimentDelta()).isCloseTo(-0.4, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(updated.getChurnDelta()).isCloseTo(0.6, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(updated.getTrend()).isEqualTo(BehaviorTrend.RAPIDLY_DECLINING);
        assertThat(updated.getChurnReason()).isEqualTo("downgrade");
        assertThat(updated.getId()).isEqualTo(existing.getId());
    }
}
