package com.ai.infrastructure.it;

import com.ai.infrastructure.config.PIIDetectionProperties;
import com.ai.infrastructure.config.ResponseSanitizationProperties;
import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.IntentType;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.dto.NextStepRecommendation;
import com.ai.infrastructure.dto.RAGRequest;
import com.ai.infrastructure.dto.RAGResponse;
import com.ai.infrastructure.intent.IntentQueryExtractor;
import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.ai.infrastructure.intent.action.ActionHandler;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.repository.IntentHistoryRepository;
import com.ai.infrastructure.entity.IntentHistory;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationResultType;
import com.ai.infrastructure.intent.orchestration.RAGOrchestrator;
import com.ai.infrastructure.rag.RAGService;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.ai.infrastructure.security.SanitizationEvent;
import com.ai.infrastructure.intent.history.IntentHistoryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;

/**
 * End-to-end integration coverage for the 6-layer RAG pipeline with sanitization extras.
 */
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
@Transactional
@Import(RAGSixLayerIntegrationTest.ListenerConfiguration.class)
class RAGSixLayerIntegrationTest {

    @Autowired
    private RAGOrchestrator orchestrator;

    @Autowired
    private VectorDatabaseService vectorDatabaseService;

    @Autowired
    private IntentHistoryRepository intentHistoryRepository;

    @Autowired
    private ResponseSanitizationProperties sanitizationProperties;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SanitizationEventRecorder sanitizationEventRecorder;

    @Autowired
    private IntentHistoryService intentHistoryService;

    @Autowired
    private PIIDetectionProperties piiDetectionProperties;

    @MockBean
    private IntentQueryExtractor intentQueryExtractor;

    @SpyBean(name = "ragService")
    private RAGService ragService;

    @BeforeEach
    void setUp() {
        vectorDatabaseService.clearVectors();
        intentHistoryRepository.deleteAll();
        sanitizationEventRecorder.clear();
    }

    @Test
    void shouldExecuteActionFlowWithHighRiskSanitization() throws Exception {
        // Arrange - seed vector index so the action has work to do
        vectorDatabaseService.storeVector(
            "doc",
            "vector-001",
            "Customer receipt containing card 4111-1111-1111-1111 for audit.",
            List.of(0.12, 0.23, 0.34, 0.45),
            Map.of("source", "integration-test")
        );

        NextStepRecommendation recommendation = NextStepRecommendation.builder()
            .intent("check_refund_status")
            .query("Call customer about card 4111-1111-1111-1111 and email user.alert@example.com")
            .confidence(0.82)
            .rationale("Customers typically verify refund status after clearing indices.")
            .build();

        Intent actionIntent = Intent.builder()
            .type(IntentType.ACTION)
            .action("clear_vector_index")
            .confidence(0.94)
            .actionParams(Map.of("reason", "customer_request"))
            .nextStepRecommended(recommendation)
            .build();

        MultiIntentResponse multiIntentResponse = MultiIntentResponse.builder()
            .intents(List.of(actionIntent))
            .metadata(Map.of("sessionId", "session-action-123"))
            .build();

        doReturn(RAGResponse.builder()
            .response("Refund investigations typically complete within 3 business days.")
            .documents(List.of())
            .success(true)
            .build()).when(ragService).performRag(any(RAGRequest.class));

        Mockito.when(intentQueryExtractor.extract("Please revoke my card number 4111-1111-1111-1111 immediately", "user-action"))
            .thenReturn(multiIntentResponse);

        // Act
        OrchestrationResult result = orchestrator.orchestrate(
            "Please revoke my card number 4111-1111-1111-1111 immediately",
            "user-action"
        );

        // Assert orchestrator response
        assertThat(result.getType()).isEqualTo(OrchestrationResultType.ACTION_EXECUTED);
        assertThat(result.isSuccess()).isTrue();

        Map<String, Object> sanitizedPayload = result.getSanitizedPayload();
        assertThat(sanitizedPayload).isNotNull();
        String sanitizedMessage = (String) sanitizedPayload.get("message");
        if (sanitizedMessage != null) {
            assertThat(sanitizedMessage).doesNotContain("4111");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> warning = (Map<String, Object>) sanitizedPayload.get("warning");
        assertThat(warning).isNotNull();
        assertThat(warning.get("level")).isEqualTo("BLOCK");
        assertThat(String.valueOf(warning.get("message")))
            .isEqualTo(sanitizationProperties.getHighRiskWarningMessage());

        assertThat(sanitizedPayload.get("guidance"))
            .isEqualTo(sanitizationProperties.getGuidanceMessage());

        @SuppressWarnings("unchecked")
        Map<String, Object> sanitization = (Map<String, Object>) sanitizedPayload.get("sanitization");
        assertThat(sanitization.get("risk")).isEqualTo("HIGH");
        @SuppressWarnings("unchecked")
        List<String> detectedTypes = (List<String>) sanitization.get("detectedTypes");
        assertThat(detectedTypes).contains("CREDIT_CARD");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> suggestions = (List<Map<String, Object>>) sanitizedPayload.get("suggestions");
        assertThat(suggestions).isNotEmpty();

        Map<String, Object> sanitizedSuggestion = suggestions.getFirst();
        assertThat(sanitizedSuggestion.get("query").toString())
            .doesNotContain("user.alert@example.com");

        @SuppressWarnings("unchecked")
        Map<String, Object> suggestionMetadata = (Map<String, Object>) sanitizedSuggestion.get("sanitization");
        assertThat(suggestionMetadata)
            .containsEntry("risk", "HIGH")
            .containsEntry("redacted", true);

        @SuppressWarnings("unchecked")
        Map<String, Object> smartSuggestion = (Map<String, Object>) sanitizedPayload.get("smartSuggestion");
        assertThat(smartSuggestion.get("response").toString())
            .contains("Refund investigations")
            .doesNotContain("user.alert@example.com");

        // Assert intent history persistence
        List<IntentHistory> history = intentHistoryRepository.findByUserIdOrderByCreatedAtDesc("user-action");
        assertThat(history).hasSize(1);

        IntentHistory record = history.getFirst();
        assertThat(record.getRedactedQuery()).doesNotContain("4111");
        assertThat(record.getSensitiveDataTypes()).contains("CREDIT_CARD");
        assertThat(record.getIntentCount()).isEqualTo(1);

        JsonNode resultJson = objectMapper.readTree(record.getResultJson());
        assertThat(resultJson.get("sanitization").get("risk").asText()).isEqualTo("HIGH");
        assertThat(resultJson.get("warning").get("level").asText()).isEqualTo("BLOCK");
        assertThat(resultJson.get("safeSummary").asText()).doesNotContain("4111");

        // Assert sanitization event publication
        List<SanitizationEvent> events = sanitizationEventRecorder.getEvents();
        assertThat(events).hasSize(1);
        SanitizationEvent emitted = events.getFirst();
        assertThat(String.valueOf(emitted.getRiskLevel())).isEqualTo("HIGH");
        assertThat(emitted.getDetectedTypes()).contains("CREDIT_CARD");
    }

    @Test
    void shouldHandleInformationFlowWithMediumRiskWarning() throws Exception {
        Intent infoIntent = Intent.builder()
            .type(IntentType.INFORMATION)
            .intent("show_refund_policy")
            .confidence(0.88)
            .vectorSpace("policies")
            .nextStepRecommended(NextStepRecommendation.builder()
                .intent("start_refund_process")
                .query("Email refund instructions to user.medium@example.com")
                .confidence(0.76)
                .build())
            .build();

        MultiIntentResponse response = MultiIntentResponse.builder()
            .intents(List.of(infoIntent))
            .metadata(Map.of("sessionId", "session-info-456"))
            .build();

        doReturn(RAGResponse.builder()
            .response("Refunds are processed within 5-7 business days once approved.")
            .documents(List.of())
            .success(true)
            .build()).when(ragService).performRag(any(RAGRequest.class));

        Mockito.when(intentQueryExtractor.extract("Please email me the refund process at user.medium@example.com", "user-info"))
            .thenReturn(response);

        OrchestrationResult result = orchestrator.orchestrate(
            "Please email me the refund process at user.medium@example.com",
            "user-info"
        );

        assertThat(result.getType()).isEqualTo(OrchestrationResultType.INFORMATION_PROVIDED);

        Map<String, Object> payload = result.getSanitizedPayload();
        Object maybeMessage = payload.get("message");
        if (maybeMessage instanceof String msg) {
            assertThat(msg).doesNotContain("user.medium@example.com");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> warning = (Map<String, Object>) payload.get("warning");
        if (warning != null) {
            assertThat(warning.get("level")).isEqualTo("WARN");
            assertThat(String.valueOf(warning.get("message")))
                .isEqualTo(sanitizationProperties.getMediumRiskWarningMessage());
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> sanitization = (Map<String, Object>) payload.get("sanitization");
        assertThat(sanitization.get("risk")).isEqualTo("MEDIUM");

        List<IntentHistory> history = intentHistoryRepository.findByUserIdOrderByCreatedAtDesc("user-info");
        assertThat(history).hasSize(1);
        IntentHistory record = history.getFirst();
        assertThat(record.getRedactedQuery()).doesNotContain("user.medium@example.com");
        assertThat(Objects.requireNonNullElse(record.getSensitiveDataTypes(), "")).contains("EMAIL");

        List<SanitizationEvent> events = sanitizationEventRecorder.getEvents();
        assertThat(events).hasSize(1);
        assertThat(String.valueOf(events.getFirst().getRiskLevel())).isEqualTo("MEDIUM");
    }

    @Test
    void shouldProcessCompoundIntentsAndAggregateRisk() throws Exception {
        Intent actionIntent = Intent.builder()
            .type(IntentType.ACTION)
            .action("clear_vector_index")
            .confidence(0.9)
            .actionParams(new LinkedHashMap<>(Map.of("reason", "compliance_request")))
            .nextStepRecommended(NextStepRecommendation.builder()
                .intent("schedule_follow_up_call")
                .query("Call customer at (415) 555-9876 and verify card 4444-3333-2222-1111")
                .confidence(0.71)
                .build())
            .build();

        Intent infoIntent = Intent.builder()
            .type(IntentType.INFORMATION)
            .intent("retrieve_discount_catalog")
            .confidence(0.86)
            .vectorSpace("offers")
            .build();

        MultiIntentResponse compound = MultiIntentResponse.builder()
            .intents(List.of(actionIntent, infoIntent))
            .compound(true)
            .metadata(Map.of("sessionId", "session-compound-789"))
            .build();

        vectorDatabaseService.storeVector(
            "doc",
            "vector-002",
            "Support transcript referencing card 5555-2222-3333-4444 for investigation.",
            List.of(0.41, 0.32, 0.23, 0.14),
            Map.of()
        );

        doReturn(RAGResponse.builder()
            .response("Here are the current promotional discounts available.")
            .documents(List.of())
            .success(true)
            .build()).when(ragService).performRag(any(RAGRequest.class));

        Mockito.when(intentQueryExtractor.extract(
            "Customer wants card 5555-2222-3333-4444 wiped and discount catalog emailed to hq@company.com",
            "user-compound"))
            .thenReturn(compound);

        OrchestrationResult result = orchestrator.orchestrate(
            "Customer wants card 5555-2222-3333-4444 wiped and discount catalog emailed to hq@company.com",
            "user-compound"
        );

        assertThat(result.getType()).isEqualTo(OrchestrationResultType.COMPOUND_HANDLED);
        assertThat(result.getChildren()).hasSize(2);

        Map<String, Object> payload = result.getSanitizedPayload();
        @SuppressWarnings("unchecked")
        Map<String, Object> sanitization = (Map<String, Object>) payload.get("sanitization");
        assertThat(sanitization.get("risk")).isEqualTo("HIGH");

        @SuppressWarnings("unchecked")
        List<String> detectedTypes = (List<String>) sanitization.get("detectedTypes");
        assertThat(detectedTypes).contains("CREDIT_CARD");

        @SuppressWarnings("unchecked")
        Map<String, Object> warning = (Map<String, Object>) payload.get("warning");
        assertThat(warning.get("level")).isEqualTo("BLOCK");

        List<IntentHistory> history = intentHistoryRepository.findByUserIdOrderByCreatedAtDesc("user-compound");
        assertThat(history).hasSize(1);
        IntentHistory record = history.getFirst();
        assertThat(record.getIntentCount()).isEqualTo(2);
        assertThat(record.getSensitiveDataTypes()).contains("CREDIT_CARD").contains("EMAIL");

        JsonNode resultJson = objectMapper.readTree(record.getResultJson());
        assertThat(resultJson.get("sanitization").get("detectedTypes").toString()).contains("CREDIT_CARD");

        List<SanitizationEvent> events = sanitizationEventRecorder.getEvents();
        assertThat(events).hasSize(1);
        assertThat(String.valueOf(events.getFirst().getRiskLevel())).isEqualTo("HIGH");
    }

    @Test
    void shouldSanitizeOutOfScopeIntentWithoutRagInvocation() {
        Intent outOfScope = Intent.builder()
            .type(IntentType.OUT_OF_SCOPE)
            .intent("unknown_request")
            .confidence(0.4)
            .build();

        MultiIntentResponse response = MultiIntentResponse.builder()
            .intents(List.of(outOfScope))
            .metadata(Map.of("sessionId", "session-oos-001"))
            .build();

        Mockito.when(intentQueryExtractor.extract("Explain why card 4333-2222-1111-0000 is invalid", "user-oos"))
            .thenReturn(response);

        sanitizationEventRecorder.clear();

        OrchestrationResult result = orchestrator.orchestrate(
            "Explain why card 4333-2222-1111-0000 is invalid",
            "user-oos"
        );

        assertThat(result.getType()).isEqualTo(OrchestrationResultType.OUT_OF_SCOPE);
        Mockito.verify(ragService, never()).performRag(any(RAGRequest.class));

        Map<String, Object> payload = result.getSanitizedPayload();
        assertThat(payload.get("guidance")).isEqualTo(sanitizationProperties.getGuidanceMessage());

        @SuppressWarnings("unchecked")
        Map<String, Object> warning = (Map<String, Object>) payload.get("warning");
        assertThat(warning).isNotNull();
        assertThat(warning.get("level")).isEqualTo("BLOCK");

        @SuppressWarnings("unchecked")
        Map<String, Object> sanitization = (Map<String, Object>) payload.get("sanitization");
        assertThat(sanitization.get("risk")).isEqualTo("HIGH");

        List<IntentHistory> history = intentHistoryRepository.findByUserIdOrderByCreatedAtDesc("user-oos");
        assertThat(history).hasSize(1);
        IntentHistory record = history.getFirst();
        assertThat(record.getRedactedQuery()).doesNotContain("4333-2222-1111-0000");
        assertThat(Boolean.TRUE.equals(record.getSuccess())).isFalse();

        List<SanitizationEvent> events = sanitizationEventRecorder.getEvents();
        assertThat(events).hasSize(1);
        assertThat(String.valueOf(events.getFirst().getRiskLevel())).isEqualTo("HIGH");
    }

    @Test
    void shouldRespectSanitizationTogglesWhenDisabled() throws Exception {
        boolean previousWarning = sanitizationProperties.isWarningEnabled();
        boolean previousGuidance = sanitizationProperties.isGuidanceEnabled();
        boolean previousEvents = sanitizationProperties.isPublishEvents();

        sanitizationProperties.setWarningEnabled(false);
        sanitizationProperties.setGuidanceEnabled(false);
        sanitizationProperties.setPublishEvents(false);
        sanitizationEventRecorder.clear();

        try {
            shouldExecuteActionFlowWithHighRiskSanitization();

            Map<String, Object> payload = orchestrator.orchestrate(
                "Please revoke my card number 4111-1111-1111-1111 immediately",
                "user-action"
            ).getSanitizedPayload();

            assertThat(payload).doesNotContainKeys("warning", "guidance");
            assertThat(sanitizationEventRecorder.getEvents()).isEmpty();
        } finally {
            sanitizationProperties.setWarningEnabled(previousWarning);
            sanitizationProperties.setGuidanceEnabled(previousGuidance);
            sanitizationProperties.setPublishEvents(previousEvents);
            sanitizationEventRecorder.clear();
        }
    }

    @Test
    void shouldMaintainSuggestionMetadataWhenNoRedactionNeeded() throws Exception {
        Intent infoIntent = Intent.builder()
            .type(IntentType.INFORMATION)
            .intent("list_featured_products")
            .confidence(0.92)
            .vectorSpace("catalog")
            .nextStepRecommended(NextStepRecommendation.builder()
                .intent("view_featured_collection")
                .query("Show featured collection")
                .confidence(0.87)
                .rationale("Customer usually browses featured items after inquiry.")
                .build())
            .build();

        MultiIntentResponse response = MultiIntentResponse.builder()
            .intents(List.of(infoIntent))
            .metadata(Map.of("sessionId", "session-suggestion-001"))
            .build();

        doReturn(RAGResponse.builder()
            .response("Our featured collection includes curated items updated daily.")
            .documents(List.of())
            .success(true)
            .build()).when(ragService).performRag(any(RAGRequest.class));

        Mockito.when(intentQueryExtractor.extract("What are your featured products today?", "user-suggest"))
            .thenReturn(response);

        sanitizationEventRecorder.clear();

        OrchestrationResult result = orchestrator.orchestrate(
            "What are your featured products today?",
            "user-suggest"
        );

        Map<String, Object> payload = result.getSanitizedPayload();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> suggestions = (List<Map<String, Object>>) payload.get("suggestions");
        assertThat(suggestions).isNotEmpty();

        Map<String, Object> suggestion = suggestions.getFirst();
        assertThat(suggestion.get("intent")).isEqualTo("view_featured_collection");
        assertThat(suggestion.get("confidence")).isEqualTo(0.87);

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) suggestion.get("sanitization");
        assertThat(metadata)
            .containsEntry("redacted", false)
            .containsEntry("risk", "NONE");
        assertThat(((List<?>) metadata.get("detectedTypes"))).isEmpty();

        assertThat(sanitizationEventRecorder.getEvents()).isEmpty();
    }

    @Test
    void shouldCleanupExpiredIntentHistoryRecords() throws Exception {
        sanitizationEventRecorder.clear();
        orchestrator.orchestrate(
            "Please revoke my card number 4111-1111-1111-1111 immediately",
            "user-ttl"
        );

        List<IntentHistory> history = intentHistoryRepository.findByUserIdOrderByCreatedAtDesc("user-ttl");
        assertThat(history).hasSize(1);

        IntentHistory expired = history.getFirst();
        expired.setExpiresAt(expired.getCreatedAt().minusDays(1));
        intentHistoryRepository.save(expired);

        intentHistoryService.cleanupExpiredHistory();

        assertThat(intentHistoryRepository.findByUserIdOrderByCreatedAtDesc("user-ttl")).isEmpty();
    }

    @Test
    void shouldFallbackToDefaultReplacementWhenMaskedValueMissing() throws Exception {
        PIIDetectionProperties.PatternConfig previous = piiDetectionProperties.getPatterns().get("API_KEY");
        piiDetectionProperties.getPatterns().put("API_KEY", PIIDetectionProperties.PatternConfig.builder()
            .fieldName("api_key")
            .regex("sk-[A-Za-z0-9]{12,}")
            .replacement(null)
            .enabled(true)
            .confidence(1.0d)
            .contextNote("API key redacted")
            .build());

        try {
            sanitizationEventRecorder.clear();

            Intent infoIntent = Intent.builder()
                .type(IntentType.INFORMATION)
                .intent("api_key_usage_policy")
                .confidence(0.9)
                .vectorSpace("policies")
                .build();

            MultiIntentResponse response = MultiIntentResponse.builder()
                .intents(List.of(infoIntent))
                .metadata(Map.of("sessionId", "session-api-001"))
                .build();

            doReturn(RAGResponse.builder()
                .response("API keys should never be shared.")
                .documents(List.of())
                .success(true)
                .build()).when(ragService).performRag(any(RAGRequest.class));

            Mockito.when(intentQueryExtractor.extract("My api key is sk-THISSHOULDBEREDACTED, is that safe?", "user-api"))
                .thenReturn(response);

            OrchestrationResult result = orchestrator.orchestrate(
                "My api key is sk-THISSHOULDBEREDACTED, is that safe?",
                "user-api"
            );

            Map<String, Object> payload = result.getSanitizedPayload();
            assertThat(String.valueOf(payload.get("message"))).contains("[REDACTED_API_KEY]");

            @SuppressWarnings("unchecked")
            Map<String, Object> sanitization = (Map<String, Object>) payload.get("sanitization");
            assertThat(sanitization.get("risk")).isEqualTo("HIGH");

            List<SanitizationEvent> events = sanitizationEventRecorder.getEvents();
            assertThat(events).hasSize(1);
            assertThat(String.valueOf(events.getFirst().getRiskLevel())).isEqualTo("HIGH");
        } finally {
            if (previous != null) {
                piiDetectionProperties.getPatterns().put("API_KEY", previous);
            } else {
                piiDetectionProperties.getPatterns().remove("API_KEY");
            }
        }
    }

    @Test
    void shouldSanitizeActionErrorResponses() {
        Intent intent = Intent.builder()
            .type(IntentType.ACTION)
            .action("raise_exception")
            .confidence(0.8)
            .build();

        MultiIntentResponse response = MultiIntentResponse.builder()
            .intents(List.of(intent))
            .metadata(Map.of("sessionId", "session-error-001"))
            .build();

        Mockito.when(intentQueryExtractor.extract("Run failing action with card 4999-8888-7777-6666", "user-error"))
            .thenReturn(response);

        sanitizationEventRecorder.clear();

        OrchestrationResult result = orchestrator.orchestrate(
            "Run failing action with card 4999-8888-7777-6666",
            "user-error"
        );

        assertThat(result.getType()).isEqualTo(OrchestrationResultType.ERROR);
        Map<String, Object> payload = result.getSanitizedPayload();

        @SuppressWarnings("unchecked")
        Map<String, Object> warning = (Map<String, Object>) payload.get("warning");
        assertThat(warning.get("level")).isEqualTo("BLOCK");

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) payload.get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> actionResult = (Map<String, Object>) data.get("actionResult");
        assertThat(String.valueOf(actionResult.get("message"))).doesNotContain("4999-8888-7777-6666");

        List<SanitizationEvent> events = sanitizationEventRecorder.getEvents();
        assertThat(events).hasSize(1);
        assertThat(String.valueOf(events.getFirst().getRiskLevel())).isEqualTo("HIGH");

        List<IntentHistory> history = intentHistoryRepository.findByUserIdOrderByCreatedAtDesc("user-error");
        assertThat(history).hasSize(1);
        IntentHistory record = history.getFirst();
        assertThat(Boolean.TRUE.equals(record.getSuccess())).isFalse();
        assertThat(record.getSensitiveDataTypes()).contains("CREDIT_CARD");
    }

    @TestConfiguration
    static class ListenerConfiguration {
        @Bean
        SanitizationEventRecorder sanitizationEventRecorder() {
            return new SanitizationEventRecorder();
        }

        @Bean
        ActionHandler failingActionHandler() {
            return new ActionHandler() {
                @Override
                public AIActionMetaData getActionMetadata() {
                    return AIActionMetaData.builder()
                        .actionName("raise_exception")
                        .description("Throws for testing")
                        .build();
                }

                @Override
                public boolean validateActionAllowed(String userId) {
                    return true;
                }

                @Override
                public String getConfirmationMessage(Map<String, Object> params) {
                    return "Are you sure?";
                }

                @Override
                public ActionResult executeAction(Map<String, Object> params, String userId) {
                    throw new IllegalStateException("Card 4999-8888-7777-6666 failure for emergency@example.com");
                }

                @Override
                public ActionResult handleError(Exception e, String userId) {
                    return ActionResult.builder()
                        .success(false)
                        .message("Error processing 4999-8888-7777-6666 for emergency@example.com")
                        .build();
                }
            };
        }
    }

    static class SanitizationEventRecorder {
        private final List<SanitizationEvent> events = new ArrayList<>();

        @EventListener
        public void onSanitizationEvent(SanitizationEvent event) {
            events.add(event);
        }

        void clear() {
            events.clear();
        }

        List<SanitizationEvent> getEvents() {
            if (CollectionUtils.isEmpty(events)) {
                return List.of();
            }
            return List.copyOf(events);
        }
    }
}
