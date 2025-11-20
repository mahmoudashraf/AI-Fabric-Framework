package com.ai.behavior.integration;

import com.ai.behavior.model.BehaviorEventEntity;
import com.ai.behavior.model.BehaviorInsights;
import com.ai.behavior.repository.BehaviorEventRepository;
import com.ai.behavior.repository.BehaviorInsightsRepository;
import com.ai.behavior.service.BehaviorEventIngestionService;
import com.ai.behavior.worker.BehaviorAnalysisWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BehaviorAnalysisIntegrationTest extends BehaviorAnalyticsIntegrationTest {

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

    @Test
    void workerShouldCreateInsightsForUserEvents() throws Exception {
        UUID userId = UUID.randomUUID();
        ingestionService.ingestBatchEvents(List.of(
            newEvent(userId, "engagement.view"),
            newEvent(userId, "conversion.purchase")
        ));

        analysisWorker.processUnprocessedEvents();

        Optional<BehaviorInsights> latest = insightsRepository.findTopByUserIdOrderByAnalyzedAtDesc(userId);
        assertThat(latest).isPresent();
        assertThat(latest.get().getSegment()).isNotBlank();
        assertThat(latest.get().getPatterns()).isNotEmpty();
    }

    @Test
    void workerShouldCleanupExpiredEvents() throws Exception {
        UUID userId = UUID.randomUUID();
        BehaviorEventEntity expired = newEvent(userId, "support.ticket");
        expired.setExpiresAt(OffsetDateTime.now().minusDays(1));
        expired.setProcessed(true);

        eventRepository.save(expired);

        analysisWorker.cleanupExpiredEvents();

        assertThat(eventRepository.findById(expired.getId())).isEmpty();
    }

    private BehaviorEventEntity newEvent(UUID userId, String eventType) throws Exception {
        JsonNode payload = objectMapper.readTree("""
            {"metric": "value", "source": "analysis-test"}
            """);
        return BehaviorEventEntity.builder()
            .userId(userId)
            .eventType(eventType)
            .eventData(payload)
            .source("analysis-test")
            .createdAt(OffsetDateTime.now())
            .build();
    }
}
