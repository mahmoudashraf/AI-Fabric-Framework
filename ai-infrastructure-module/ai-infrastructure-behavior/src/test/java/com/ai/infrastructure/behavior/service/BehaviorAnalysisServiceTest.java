package com.ai.infrastructure.behavior.service;

import com.ai.infrastructure.behavior.entity.BehaviorInsights;
import com.ai.infrastructure.behavior.model.ExternalEvent;
import com.ai.infrastructure.behavior.model.SentimentLabel;
import com.ai.infrastructure.behavior.model.UserEventBatch;
import com.ai.infrastructure.behavior.spi.ExternalEventProvider;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AIGenerationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BehaviorAnalysisServiceTest {

    @Mock
    private ExternalEventProvider eventProvider;
    @Mock
    private BehaviorStorageAdapter storageAdapter;
    @Mock
    private AICoreService aiCoreService;

    private ObjectMapper objectMapper;

    @InjectMocks
    private BehaviorAnalysisService service;

    @BeforeEach
    void setup() {
        objectMapper = new ObjectMapper();
        service = new BehaviorAnalysisService(eventProvider, storageAdapter, aiCoreService, objectMapper);
    }

    @Test
    void analyzeUser_returnsExistingWhenNoEvents() {
        UUID userId = UUID.randomUUID();
        BehaviorInsights existing = BehaviorInsights.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .segment("existing")
            .analyzedAt(LocalDateTime.now())
            .build();

        when(storageAdapter.findByUserId(userId)).thenReturn(Optional.of(existing));
        when(eventProvider.getEventsForUser(userId, null, null)).thenReturn(List.of());

        BehaviorInsights result = service.analyzeUser(userId);

        assertThat(result).isSameAs(existing);
        verify(storageAdapter, never()).save(any());
    }

    @Test
    void analyzeUser_processesEventsAndSaves() {
        UUID userId = UUID.randomUUID();
        List<ExternalEvent> events = List.of(
            ExternalEvent.builder()
                .eventType("purchase")
                .timestamp(LocalDateTime.now())
                .eventData(Map.of("amount", 50))
                .source("web")
                .build()
        );

        when(storageAdapter.findByUserId(userId)).thenReturn(Optional.empty());
        when(eventProvider.getEventsForUser(userId, null, null)).thenReturn(events);
        when(aiCoreService.generateContent(any())).thenReturn(
            AIGenerationResponse.builder()
                .content("{\"segment\":\"Power\",\"patterns\":[\"buy\"],\"recommendations\":[\"upsell\"],\"insights\":{},\"confidence\":0.9}")
                .model("gpt-4o")
                .build()
        );
        when(storageAdapter.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BehaviorInsights result = service.analyzeUser(userId);

        assertThat(result).isNotNull();
        assertThat(result.getSegment()).isEqualTo("Power");
        assertThat(result.getPatterns()).containsExactly("buy");
        assertThat(result.getRecommendations()).containsExactly("upsell");
        assertThat(result.getConfidence()).isEqualTo(0.9);
        verify(storageAdapter).save(any(BehaviorInsights.class));
    }

    @Test
    void processNextUser_usesContextAndSaves() {
        UUID userId = UUID.randomUUID();
        UserEventBatch batch = UserEventBatch.builder()
            .userId(userId)
            .events(List.of(
                ExternalEvent.builder()
                    .eventType("login")
                    .timestamp(LocalDateTime.now())
                    .eventData(Map.of("device", "mobile"))
                    .source("app")
                    .build()
            ))
            .totalEventCount(1)
            .userContext(Map.of("tier", "gold"))
            .build();

        when(eventProvider.getNextUserEvents()).thenReturn(batch);
        when(storageAdapter.findByUserId(userId)).thenReturn(Optional.empty());
        when(aiCoreService.generateContent(any())).thenReturn(
            AIGenerationResponse.builder()
                .content("{\"segment\":\"Active\",\"patterns\":[\"login\"],\"recommendations\":[\"notify\"],\"insights\":{},\"confidence\":0.75}")
                .model("gpt-4o")
                .build()
        );
        when(storageAdapter.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BehaviorInsights result = service.processNextUser();

        assertThat(result).isNotNull();
        assertThat(result.getSegment()).isEqualTo("Active");

        ArgumentCaptor<AIGenerationResponse> captor = ArgumentCaptor.forClass(AIGenerationResponse.class);
        verify(aiCoreService).generateContent(any());
        verify(storageAdapter).save(any(BehaviorInsights.class));
    }

    @Test
    void clampsAndDefaultsInvalidSentimentAndChurn() {
        UUID userId = UUID.randomUUID();
        when(eventProvider.getEventsForUser(userId, null, null)).thenReturn(
            List.of(ExternalEvent.builder().eventType("test").build())
        );
        when(storageAdapter.findByUserId(userId)).thenReturn(Optional.empty());
        when(aiCoreService.generateContent(any())).thenReturn(
            AIGenerationResponse.builder()
                .content("""
                    {
                      "segment": "Test",
                      "patterns": [],
                      "sentiment": {"score": 1.5, "label": "INVALID"},
                      "churn": {"risk": -0.2},
                      "trend": "STABLE",
                      "recommendations": [],
                      "insights": {},
                      "confidence": 0.5
                    }
                    """)
                .build()
        );
        when(storageAdapter.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BehaviorInsights result = service.analyzeUser(userId);

        assertThat(result.getSentimentScore()).isBetween(-1.0, 1.0);
        assertThat(result.getChurnRisk()).isBetween(0.0, 1.0);
        assertThat(result.getSentimentLabel()).isEqualTo(SentimentLabel.NEUTRAL);
    }
}
