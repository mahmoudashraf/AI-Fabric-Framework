package com.ai.behavior.integration;

import com.ai.behavior.dto.OrchestratedQueryRequest;
import com.ai.behavior.model.BehaviorInsights;
import com.ai.behavior.repository.BehaviorInsightsRepository;
import com.ai.infrastructure.dto.PIIDetectionResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationResultType;
import com.ai.infrastructure.intent.orchestration.RAGOrchestrator;
import com.ai.infrastructure.privacy.pii.PIIDetectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BehaviorSearchIntegrationTest extends BehaviorAnalyticsIntegrationTest {

    @Autowired
    private BehaviorInsightsRepository insightsRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PIIDetectionService piiDetectionService;

    @MockBean
    private RAGOrchestrator ragOrchestrator;

    @Test
    void orchestratedSearchShouldReturnInsights() throws Exception {
        UUID userId = UUID.randomUUID();
        insightsRepository.save(BehaviorInsights.builder()
            .userId(userId)
            .segment("power_user")
            .patterns(java.util.List.of("high_engagement"))
            .recommendations(java.util.List.of("offer_loyalty_reward"))
            .scores(Map.of("confidenceScore", 0.9))
            .analyzedAt(LocalDateTime.now())
            .validUntil(LocalDateTime.now().plusDays(1))
            .analysisVersion("test")
            .build());

        when(piiDetectionService.detectAndProcess(any()))
            .thenReturn(PIIDetectionResult.builder()
                .originalQuery("segment:power_user")
                .processedQuery("segment:power_user")
                .piiDetected(false)
                .build());

        when(ragOrchestrator.orchestrate(any(), any())).thenReturn(OrchestrationResult.builder()
            .success(true)
            .type(OrchestrationResultType.INFORMATION_PROVIDED)
            .metadata(Map.of("suggestedSegment", "power_user"))
            .message("Query understood")
            .build());

        OrchestratedQueryRequest request = new OrchestratedQueryRequest();
        request.setQuery("segment:power_user");
        request.setLimit(10);
        request.setIncludeExplanation(true);

        mockMvc.perform(post("/api/behavior/search/orchestrated")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.results.matchedUsers[0]").exists())
            .andExpect(jsonPath("$.results.totalMatches").value(1))
            .andExpect(jsonPath("$.results.aiExplanation").value("Query understood"));
    }

    @Test
    void orchestratedSearchShouldBlockPiiQueries() throws Exception {
        when(piiDetectionService.detectAndProcess(any()))
            .thenReturn(PIIDetectionResult.builder()
                .originalQuery("user ssn 111-11-1111")
                .processedQuery("user ssn ****")
                .piiDetected(true)
                .build());

        OrchestratedQueryRequest request = new OrchestratedQueryRequest();
        request.setQuery("user ssn 111-11-1111");

        mockMvc.perform(post("/api/behavior/search/orchestrated")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.piiDetected").value(true));

        assertThat(insightsRepository.findAll()).isNotEmpty();
    }
}
