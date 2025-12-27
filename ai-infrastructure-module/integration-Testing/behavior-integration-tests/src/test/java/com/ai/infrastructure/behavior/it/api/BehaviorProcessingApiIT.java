package com.ai.infrastructure.behavior.it.api;

import com.ai.infrastructure.behavior.config.BehaviorProcessingProperties;
import com.ai.infrastructure.behavior.entity.BehaviorInsights;
import com.ai.infrastructure.behavior.model.BehaviorTrend;
import com.ai.infrastructure.behavior.model.SentimentLabel;
import com.ai.infrastructure.behavior.repository.BehaviorInsightsRepository;
import com.ai.infrastructure.behavior.it.BehaviorIntegrationTestApp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = BehaviorIntegrationTestApp.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "ai.behavior.processing.api-enabled=true",
        "ai.behavior.processing.api-max-batch-size=5"
    }
)
@ActiveProfiles("integration")
class BehaviorProcessingApiIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private BehaviorInsightsRepository repository;

    @Autowired
    private BehaviorProcessingProperties properties;

    @BeforeEach
    void setup() {
        repository.deleteAll();
    }

    @Test
    void analyzeUser_returnsInsight() {
        UUID userId = UUID.randomUUID();
        BehaviorInsights saved = repository.save(BehaviorInsights.builder()
            .userId(userId)
            .segment("Existing")
            .sentimentLabel(SentimentLabel.SATISFIED)
            .churnRisk(0.2)
            .trend(BehaviorTrend.STABLE)
            .analyzedAt(LocalDateTime.now())
            .build());

        ResponseEntity<BehaviorInsights> response = restTemplate.postForEntity(
            "/api/behavior/processing/users/" + userId,
            null,
            BehaviorInsights.class
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getUserId()).isEqualTo(saved.getUserId());
    }

    @Test
    void batch_honorsMaxBatchSizeAndReturnsCounts() {
        properties.setApiMaxBatchSize(1);
        // Seed one pending user to be processed by processNextUser (the service will return null afterwards)
        repository.save(BehaviorInsights.builder()
            .userId(UUID.randomUUID())
            .segment("Pending")
            .analyzedAt(LocalDateTime.now())
            .build());

        String body = "{\"maxUsers\":3,\"maxDurationMinutes\":1}";
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            "/api/behavior/processing/batch",
            HttpMethod.POST,
            new HttpEntity<>(body),
            new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsEntry("processedCount", 1);
    }

    @Test
    void continuous_canStartAndCancelJob() {
        String startBody = "{\"usersPerBatch\":1,\"intervalMinutes\":0,\"maxIterations\":1}";
        ResponseEntity<Map<String, Object>> startResponse = restTemplate.exchange(
            "/api/behavior/processing/continuous",
            HttpMethod.POST,
            new HttpEntity<>(startBody),
            new ParameterizedTypeReference<>() {}
        );

        assertThat(startResponse.getStatusCode().is2xxSuccessful()).isTrue();
        String jobId = (String) startResponse.getBody().get("jobId");
        assertThat(jobId).isNotBlank();

        ResponseEntity<Map<String, Object>> cancelResponse = restTemplate.exchange(
            "/api/behavior/processing/continuous/" + jobId + "/cancel",
            HttpMethod.POST,
            null,
            new ParameterizedTypeReference<>() {}
        );

        assertThat(cancelResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(cancelResponse.getBody()).containsEntry("status", "CANCELLED");
    }

    @Test
    void scheduled_pauseAndResumeTogglesFlag() {
        ResponseEntity<Map> pause = restTemplate.postForEntity(
            "/api/behavior/processing/scheduled/pause",
            null,
            Map.class
        );
        assertThat(pause.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(pause.getBody()).containsEntry("paused", true);

        ResponseEntity<Map> resume = restTemplate.postForEntity(
            "/api/behavior/processing/scheduled/resume",
            null,
            Map.class
        );
        assertThat(resume.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resume.getBody()).containsEntry("paused", false);
    }
}
