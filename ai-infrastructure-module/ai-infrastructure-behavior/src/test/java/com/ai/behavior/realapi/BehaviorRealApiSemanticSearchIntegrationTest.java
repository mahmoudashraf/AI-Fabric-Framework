package com.ai.behavior.realapi;

import com.ai.behavior.model.BehaviorEventEntity;
import com.ai.behavior.model.BehaviorInsights;
import com.ai.behavior.repository.BehaviorEventRepository;
import com.ai.behavior.repository.BehaviorInsightsRepository;
import com.ai.behavior.service.BehaviorEventIngestionService;
import com.ai.behavior.worker.BehaviorAnalysisWorker;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.ai.infrastructure.service.VectorManagementService;
import com.ai.infrastructure.service.AICapabilityService;
import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import com.ai.infrastructure.dto.AIEntityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "ai.behavior.realapi.enabled", matches = "true")
public class BehaviorRealApiSemanticSearchIntegrationTest extends BehaviorRealApiIntegrationTest {

    @Autowired
    private BehaviorEventIngestionService ingestionService;

    @Autowired
    private BehaviorAnalysisWorker analysisWorker;

    @Autowired
    private BehaviorInsightsRepository insightsRepository;

    @Autowired
    private BehaviorEventRepository eventRepository;

    @Autowired
    private AISearchableEntityRepository searchableEntityRepository;

    @Autowired
    private VectorManagementService vectorManagementService;

    @Autowired
    private AICoreService aiCoreService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AICapabilityService aiCapabilityService;

    @Autowired
    private AIEntityConfigurationLoader entityConfigurationLoader;

    @BeforeEach
    void cleanState() {
        searchableEntityRepository.deleteAll();
        insightsRepository.deleteAll();
        eventRepository.deleteAll();
        vectorManagementService.clearVectorsByEntityType("behavior-insight");
    }

    @Test
    void insightsAreIndexedAndDiscoverableViaSemanticSearch() {
        assumeRealApiAvailable();

        UUID userId = UUID.randomUUID();
        ingestScenario(userId);
        analysisWorker.processUnprocessedEvents();

        BehaviorInsights insights = Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .until(() -> insightsRepository.findTopByUserIdOrderByAnalyzedAtDesc(userId).orElse(null),
                Objects::nonNull);

        AIEntityConfig config = entityConfigurationLoader.getEntityConfig("behavior-insight");
        assertThat(config).as("behavior-insight configuration should be registered").isNotNull();
        aiCapabilityService.generateEmbeddings(insights, config);
        aiCapabilityService.indexForSearch(insights, config);

        Awaitility.await()
            .atMost(Duration.ofMinutes(2))
            .untilAsserted(() -> assertThat(searchableEntityRepository
                .findByEntityTypeAndEntityId("behavior-insight", insights.getId().toString())).isPresent());

        Awaitility.await()
            .atMost(Duration.ofSeconds(45))
            .untilAsserted(() -> assertThat(vectorManagementService
                .vectorExists("behavior-insight", insights.getId().toString())).isTrue());

        AISearchResponse response = Awaitility.await()
            .pollInterval(Duration.ofSeconds(2))
            .atMost(Duration.ofMinutes(2))
            .until(() -> aiCoreService.performSearch(AISearchRequest.builder()
                    .query("enterprise loyalty user researching premium workspace analytics plan")
                    .entityType("behavior-insight")
                    .threshold(0.1)
                    .limit(5)
                    .build()),
                result -> result != null && result.getResults() != null && !result.getResults().isEmpty());

        assertThat(response.getResults())
            .anySatisfy(result -> assertThat(result.get("id")).isEqualTo(insights.getId().toString()));
    }

    private void ingestScenario(UUID userId) {
        List<BehaviorEventEntity> events = List.of(
            event(userId, "intent.search", Map.of(
                "query", "compare enterprise workspace analytics",
                "channel", "web"
            )),
            event(userId, "engagement.view", Map.of(
                "category", "analytics",
                "durationSeconds", 180
            )),
            event(userId, "conversion.purchase", Map.of(
                "plan", "enterprise_plus",
                "currency", "USD",
                "amount", 12000
            ))
        );

        events.forEach(ingestionService::ingestSingleEvent);
    }

    private BehaviorEventEntity event(UUID userId, String eventType, Map<String, Object> payload) {
        return BehaviorEventEntity.builder()
            .userId(userId)
            .eventType(eventType)
            .eventData(objectMapper.valueToTree(payload))
            .source("realapi-test")
            .createdAt(OffsetDateTime.now())
            .expiresAt(OffsetDateTime.now().plusDays(2))
            .build();
    }
}
