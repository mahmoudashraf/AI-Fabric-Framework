package com.ai.behavior.realapi;

import com.ai.behavior.dto.OrchestratedQueryRequest;
import com.ai.behavior.model.BehaviorEventEntity;
import com.ai.behavior.model.BehaviorInsights;
import com.ai.behavior.repository.BehaviorEventRepository;
import com.ai.behavior.repository.BehaviorInsightsRepository;
import com.ai.behavior.service.BehaviorEventIngestionService;
import com.ai.behavior.worker.BehaviorAnalysisWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIfSystemProperty(named = "ai.behavior.realapi.enabled", matches = "true")
public class BehaviorRealApiOrchestratedSearchIntegrationTest extends BehaviorRealApiIntegrationTest {

    @Autowired
    private BehaviorEventIngestionService ingestionService;

    @Autowired
    private BehaviorAnalysisWorker analysisWorker;

    @Autowired
    private BehaviorInsightsRepository insightsRepository;

    @Autowired
    private BehaviorEventRepository eventRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void resetState() {
        insightsRepository.deleteAll();
        eventRepository.deleteAll();
    }

    @Test
    void orchestratedSearchRespondsWithMatchedUsers() throws Exception {
        assumeRealApiAvailable();
        UUID userId = UUID.randomUUID();
        ingestHighEngagementSignals(userId);
        analysisWorker.processUnprocessedEvents();

        BehaviorInsights insights = Awaitility.await()
            .atMost(Duration.ofSeconds(20))
            .until(() -> insightsRepository.findTopByUserIdOrderByAnalyzedAtDesc(userId).orElse(null),
                java.util.Objects::nonNull);
        assertThat(insights.getSegment()).isNotBlank();

        OrchestratedQueryRequest request = new OrchestratedQueryRequest();
        request.setQuery("segment:" + insights.getSegment() + " confidence>=0.4 include enterprise insights");
        request.setLimit(5);
        request.setIncludeExplanation(true);
        request.setUserId(userId.toString());

        mockMvc.perform(post("/api/behavior/search/orchestrated")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.results.totalMatches").value(greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.results.matchedUsers[0]").value(userId.toString()))
            .andExpect(jsonPath("$.results.insights[0].segment").value(insights.getSegment()));
    }

    private void ingestHighEngagementSignals(UUID userId) {
        for (int i = 0; i < 8; i++) {
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("category", "luxury");
            attributes.put("price", 2000 + i * 100);
            attributes.put("channel", "web");

            BehaviorEventEntity event = BehaviorEventEntity.builder()
                .userId(userId)
                .eventType("engagement.view")
                .eventData(objectMapper.valueToTree(attributes))
                .source("realapi-test")
                .createdAt(OffsetDateTime.now().minusMinutes(i))
                .expiresAt(OffsetDateTime.now().plusDays(2))
                .build();
            ingestionService.ingestSingleEvent(event);
        }
    }
}
