package com.ai.behavior.integration;

import com.ai.behavior.config.BehaviorModuleProperties;
import com.ai.behavior.dto.OrchestratedQueryRequest;
import com.ai.behavior.model.BehaviorEventEntity;
import com.ai.behavior.service.BehaviorEventIngestionService;
import com.ai.infrastructure.dto.PIIDetectionResult;
import com.ai.infrastructure.privacy.pii.PIIDetectionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {
    "ai.behavior.security.rate-limiting.enabled=true",
    "ai.behavior.security.rate-limiting.requests=1",
    "ai.behavior.security.rate-limiting.refresh-period=PT1H"
})
public class ProductionHardeningIntegrationTest extends BehaviorAnalyticsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BehaviorEventIngestionService ingestionService;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private BehaviorModuleProperties behaviorModuleProperties;

    @Autowired
    private PIIDetectionService piiDetectionService;

    @Test
    void rateLimiterShouldThrottleAfterThreshold() throws Exception {
        OrchestratedQueryRequest request = baseQueryRequest();
        String payload = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/behavior/search/orchestrated")
                .header("X-User-Id", "rate-limit-user")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(header().exists("X-Rate-Limit-Remaining"));

        mockMvc.perform(post("/api/behavior/search/orchestrated")
                .header("X-User-Id", "rate-limit-user")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isTooManyRequests())
            .andExpect(content().string(containsString("rate_limit_exceeded")));
    }

    @Test
    void metricsShouldCaptureIngestionSearchAndPii(CapturedOutput output) throws Exception {
        double ingestedBefore = counterValue("ai.behavior.events.ingested");
        double searchBefore = counterValue("ai.behavior.search.count");
        double piiBefore = counterValue("ai.behavior.pii.detections");

        ingestionService.ingestSingleEvent(newEvent(UUID.randomUUID(), "engagement.view"));

        mockMvc.perform(post("/api/behavior/search/orchestrated")
                .header("X-User-Id", "metrics-user")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(baseQueryRequest())))
            .andExpect(status().isOk());

        OrchestratedQueryRequest piiRequest = new OrchestratedQueryRequest();
        piiRequest.setQuery("user ssn 111-11-1111");
        piiRequest.setLimit(5);

        mockMvc.perform(post("/api/behavior/search/orchestrated")
                .header("X-User-Id", "metrics-user-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(piiRequest)))
            .andExpect(status().isBadRequest());

        assertThat(counterValue("ai.behavior.events.ingested") - ingestedBefore).isEqualTo(1d);
        assertThat(counterValue("ai.behavior.search.count") - searchBefore).isEqualTo(1d);
        assertThat(counterValue("ai.behavior.pii.detections") - piiBefore).isEqualTo(1d);
        assertThat(output.getOut()).contains("behavior_audit event=ingested");
    }

    private OrchestratedQueryRequest baseQueryRequest() {
        OrchestratedQueryRequest request = new OrchestratedQueryRequest();
        request.setQuery("segment:power_user");
        request.setLimit(5);
        request.setIncludeExplanation(true);
        return request;
    }

    private BehaviorEventEntity newEvent(UUID userId, String eventType) throws Exception {
        JsonNode payload = objectMapper.readTree("""
            {"value": 7, "source": "production-hardening"}
            """);
        return BehaviorEventEntity.builder()
            .userId(userId)
            .eventType(eventType)
            .eventData(payload)
            .source("production-hardening")
            .createdAt(OffsetDateTime.now())
            .build();
    }

    private double counterValue(String name) {
        Counter counter = meterRegistry.find(name).counter();
        return counter != null ? counter.count() : 0d;
    }

    @BeforeEach
    void configureEnvironment() {
        behaviorModuleProperties.getSecurity().getRateLimiting().setEnabled(true);
        behaviorModuleProperties.getSecurity().getRateLimiting().setRequests(1);
        behaviorModuleProperties.getSecurity().getRateLimiting().setRefreshPeriod(Duration.ofHours(1));

        reset(piiDetectionService);
        when(piiDetectionService.detectAndProcess(argThat(query ->
                query != null && query.toLowerCase().contains("ssn"))))
            .thenReturn(PIIDetectionResult.builder()
                .originalQuery("user ssn 111-11-1111")
                .processedQuery("user ssn ****")
                .piiDetected(true)
                .build());
        when(piiDetectionService.detectAndProcess(argThat(query ->
                query == null || !query.toLowerCase().contains("ssn"))))
            .thenAnswer(invocation -> {
                String q = invocation.getArgument(0, String.class);
                return PIIDetectionResult.builder()
                    .originalQuery(q)
                    .processedQuery(q)
                    .piiDetected(false)
                    .build();
            });
    }
}
