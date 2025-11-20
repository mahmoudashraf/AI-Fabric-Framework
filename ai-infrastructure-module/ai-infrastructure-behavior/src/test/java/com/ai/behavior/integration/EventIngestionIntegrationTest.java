package com.ai.behavior.integration;

import com.ai.behavior.model.BehaviorEventEntity;
import com.ai.behavior.repository.BehaviorEventRepository;
import com.ai.behavior.service.BehaviorEventIngestionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class EventIngestionIntegrationTest extends BehaviorAnalyticsIntegrationTest {

    @Autowired
    private BehaviorEventIngestionService ingestionService;

    @Autowired
    private BehaviorEventRepository eventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanEvents() {
        eventRepository.deleteAll();
    }

    @Test
    void shouldPersistSingleEventWithTtl() throws Exception {
        UUID userId = UUID.randomUUID();
        BehaviorEventEntity event = ingestionService.ingestSingleEvent(newEvent(userId, "engagement.view"));

        BehaviorEventEntity persisted = eventRepository.findById(event.getId()).orElseThrow();
        assertThat(persisted.getUserId()).isEqualTo(userId);
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getExpiresAt()).isAfter(persisted.getCreatedAt());
        assertThat(Duration.between(persisted.getCreatedAt(), persisted.getExpiresAt()).toDays()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void shouldPersistBatchEvents() throws Exception {
        UUID userId = UUID.randomUUID();
        List<BehaviorEventEntity> saved = ingestionService.ingestBatchEvents(List.of(
            newEvent(userId, "conversion.purchase"),
            newEvent(userId, "search.query")
        ));

        assertThat(saved).hasSize(2);
        assertThat(eventRepository.findByUserIdAndProcessedIsFalse(userId)).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void shouldMarkEventsProcessed() throws Exception {
        UUID userId = UUID.randomUUID();
        BehaviorEventEntity event = ingestionService.ingestSingleEvent(newEvent(userId, "support.ticket"));

        ingestionService.markProcessed(event.getId(), null);

        BehaviorEventEntity updated = eventRepository.findById(event.getId()).orElseThrow();
        assertThat(updated.isProcessed()).isTrue();
        assertThat(updated.getProcessingStatus()).isNotNull();
    }

    private BehaviorEventEntity newEvent(UUID userId, String eventType) throws Exception {
        JsonNode payload = objectMapper.readTree("""
            {"value": 42, "source": "integration-test"}
            """);
        return BehaviorEventEntity.builder()
            .userId(userId)
            .eventType(eventType)
            .eventData(payload)
            .source("integration-test")
            .createdAt(OffsetDateTime.now())
            .build();
    }
}
