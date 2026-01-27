package com.ai.infrastructure.security;

import com.ai.infrastructure.config.ResponseSanitizationProperties;
import com.ai.infrastructure.dto.NextStepRecommendation;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.intent.action.ActionResultContracts;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationResultType;
import com.ai.infrastructure.privacy.pii.PIIDetectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import com.ai.infrastructure.testsupport.SimplePIIDetectionService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ResponseSanitizerTest {

    private ResponseSanitizer sanitizer;
    private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        ResponseSanitizationProperties sanitizationProperties = new ResponseSanitizationProperties();
        sanitizationProperties.setSuggestionLimit(3);
        sanitizationProperties.setHighRiskTypes(Set.of("CREDIT_CARD"));
        sanitizationProperties.setGuidanceMessage("Please avoid sharing card numbers.");

        PIIDetectionService piiDetectionService = new SimplePIIDetectionService();
        @SuppressWarnings("unchecked")
        ObjectProvider<PIIDetectionService> piiProvider = mock(ObjectProvider.class);
        when(piiProvider.getIfAvailable()).thenReturn(piiDetectionService);
        sanitizer = new ResponseSanitizer(piiProvider, sanitizationProperties);
        eventPublisher = mock(ApplicationEventPublisher.class);
        ReflectionTestUtils.setField(sanitizer, "eventPublisher", eventPublisher);
    }

    @Test
    void shouldSanitizeMessageDataAndSuggestions() {
        OrchestrationResult result = OrchestrationResult.builder()
            .type(OrchestrationResultType.ACTION_EXECUTED)
            .success(true)
            .message("Email user@example.com and confirm card 4111-1111-1111-1111.")
            .data(buildData())
            .nextSteps(List.of(
                NextStepRecommendation.builder()
                    .intent("follow_up_email")
                    .query("Send a confirmation to user@example.com")
                    .rationale("User requested email confirmation")
                    .confidence(0.92)
                    .build()
            ))
            .build();

        result.setSmartSuggestion(Map.of(
            "query", "Call (415) 555-4321 to confirm details",
            "intent", "call_support"
        ));

        Map<String, Object> payload = sanitizer.sanitize(result, "user-123");

        assertThat(payload).containsEntry("success", true);
        assertThat(payload).containsKey("message");
        assertThat(payload.get("message").toString()).doesNotContain("user@example.com");
        assertThat(payload.get("message").toString()).doesNotContain("4111-1111-1111-1111");

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) payload.get("data");
        assertThat(data).doesNotContainKey("metadata");
        assertThat(data).containsKey("actionResult");
        @SuppressWarnings("unchecked")
        Map<String, Object> actionResult = (Map<String, Object>) data.get("actionResult");
        assertThat(actionResult.get("message").toString()).doesNotContain("4111-1111-1111-1111");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> suggestions = (List<Map<String, Object>>) payload.get("suggestions");
        assertThat(suggestions).isNotEmpty();
        Map<String, Object> suggestion = suggestions.getFirst();
        assertThat(suggestion.get("query").toString()).doesNotContain("user@example.com");

        @SuppressWarnings("unchecked")
        Map<String, Object> smartSuggestion = (Map<String, Object>) payload.get("smartSuggestion");
        assertThat(smartSuggestion.get("query").toString()).doesNotContain("(415) 555-4321");

        assertThat(payload).containsKey("warning");
        @SuppressWarnings("unchecked")
        Map<String, Object> warning = (Map<String, Object>) payload.get("warning");
        assertThat(warning).containsEntry("level", "BLOCK");
        assertThat(warning.get("message").toString()).contains("Sensitive information");

        assertThat(payload).containsEntry("guidance", "Please avoid sharing card numbers.");
        assertThat(payload).containsKey("safeSummary");

        @SuppressWarnings("unchecked")
        Map<String, Object> sanitization = (Map<String, Object>) payload.get("sanitization");
        assertThat(sanitization).containsEntry("risk", "HIGH");

        @SuppressWarnings("unchecked")
        Map<String, Object> enrichedSuggestion = (Map<String, Object>) ((List<?>) payload.get("suggestions")).getFirst();
        @SuppressWarnings("unchecked")
        Map<String, Object> suggestionMetadata = (Map<String, Object>) enrichedSuggestion.get("sanitization");
        assertThat(suggestionMetadata).containsEntry("redacted", true);

        ArgumentCaptor<SanitizationEvent> eventCaptor = ArgumentCaptor.forClass(SanitizationEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getRiskLevel()).isEqualTo(ResponseSanitizer.RiskLevel.HIGH);
    }

    @Test
    void shouldSkipEventsWhenNoPiiPresent() {
        OrchestrationResult result = OrchestrationResult.builder()
            .type(OrchestrationResultType.INFORMATION_PROVIDED)
            .success(true)
            .message("General help response.")
            .build();

        Map<String, Object> payload = sanitizer.sanitize(result, "user-456");
        assertThat(payload).doesNotContainKey("warning");
        assertThat(payload).containsKey("safeSummary");

        verifyNoInteractions(eventPublisher);
    }

    private Map<String, Object> buildData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("confirmationMessage", "Confirmation sent to user@example.com");
        data.put("metadata", Map.of("debug", true));
        data.put("actionResult", ActionResult.builder()
            .success(true)
            .message("Card 4111-1111-1111-1111 processed successfully.")
            .data(ActionResultContracts.object(Map.of("card", "4111-1111-1111-1111")))
            .build());
        return data;
    }
}
