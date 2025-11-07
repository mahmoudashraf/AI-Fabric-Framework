package com.ai.infrastructure.intent;

import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.IntentType;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.exception.AIServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntentQueryExtractorTest {

    @Mock
    private AICoreService aiCoreService;

    @Mock
    private EnrichedPromptBuilder enrichedPromptBuilder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldParseJsonResponseIntoMultiIntentResponse() {
        when(enrichedPromptBuilder.buildSystemPrompt("user-123")).thenReturn("system-prompt");

        String json = """
            {
              "intents": [
                {
                  "type": "ACTION",
                  "intent": "cancel_subscription",
                  "confidence": 0.92,
                  "action": "cancel_subscription",
                  "actionParams": {"reason": "too expensive"},
                  "requiresRetrieval": false
                }
              ],
              "isCompound": false,
              "orchestrationStrategy": null
            }
            """;

        when(aiCoreService.generateContent(org.mockito.ArgumentMatchers.any()))
            .thenReturn(AIGenerationResponse.builder().content(json).build());

        IntentQueryExtractor extractor = new IntentQueryExtractor(aiCoreService, enrichedPromptBuilder, objectMapper);

        MultiIntentResponse response = extractor.extract("Cancel my subscription", "user-123");

        assertThat(response.getIntents()).hasSize(1);
        Intent intent = response.getIntents().getFirst();
        assertThat(intent.getIntent()).isEqualTo("cancel_subscription");
        assertThat(intent.getActionParams()).containsEntry("reason", "too expensive");
        assertThat(response.getOrchestrationStrategy()).isEqualTo("DIRECT_ACTION");
    }

    @Test
    void shouldParseInformationIntentAndMarkRetrieval() {
        when(enrichedPromptBuilder.buildSystemPrompt("user-456")).thenReturn("system-prompt");

        String json = """
            {
              "intents": [
                {
                  "type": "INFORMATION",
                  "intent": "refund_policy",
                  "confidence": 0.81,
                  "requiresRetrieval": true,
                  "vectorSpace": "policies"
                }
              ],
              "isCompound": false
            }
            """;

        when(aiCoreService.generateContent(org.mockito.ArgumentMatchers.any()))
            .thenReturn(AIGenerationResponse.builder().content(json).build());

        IntentQueryExtractor extractor = new IntentQueryExtractor(aiCoreService, enrichedPromptBuilder, objectMapper);

        MultiIntentResponse response = extractor.extract("What is your refund policy?", "user-456");

        assertThat(response.getIntents()).hasSize(1);
        Intent intent = response.getIntents().getFirst();
        assertThat(intent.getType()).isEqualTo(IntentType.INFORMATION);
        assertThat(intent.getRequiresRetrieval()).isTrue();
        assertThat(response.getOrchestrationStrategy()).isEqualTo("RETRIEVE_AND_GENERATE");
    }

    @Test
    void shouldParseOutOfScopeIntent() {
        when(enrichedPromptBuilder.buildSystemPrompt("user-789")).thenReturn("system-prompt");

        String json = """
            {
              "intents": [
                {
                  "type": "OUT_OF_SCOPE",
                  "intent": "out_of_scope",
                  "confidence": 0.55,
                  "actionParams": {"reason": "Unsupported domain"}
                }
              ],
              "isCompound": false,
              "orchestrationStrategy": "ADMIT_UNKNOWN"
            }
            """;

        when(aiCoreService.generateContent(org.mockito.ArgumentMatchers.any()))
            .thenReturn(AIGenerationResponse.builder().content(json).build());

        IntentQueryExtractor extractor = new IntentQueryExtractor(aiCoreService, enrichedPromptBuilder, objectMapper);

        MultiIntentResponse response = extractor.extract("Build me a spaceship", "user-789");

        assertThat(response.getIntents()).hasSize(1);
        Intent intent = response.getIntents().getFirst();
        assertThat(intent.getType()).isEqualTo(IntentType.OUT_OF_SCOPE);
        assertThat(intent.getActionParams()).containsEntry("reason", "Unsupported domain");
        assertThat(response.getOrchestrationStrategy()).isEqualTo("ADMIT_UNKNOWN");
    }

    @Test
    void shouldParseNextStepRecommendation() {
        when(enrichedPromptBuilder.buildSystemPrompt("user-321")).thenReturn("system-prompt");

        String json = """
            {
              "intents": [
                {
                  "type": "ACTION",
                  "intent": "update_payment_method",
                  "confidence": 0.77,
                  "action": "update_payment_method",
                  "nextStepRecommended": {
                    "intent": "view_billing_history",
                    "query": "Show my billing history",
                    "rationale": "Users updating payment methods often review billing history next.",
                    "confidence": 0.71
                  }
                }
              ],
              "isCompound": false
            }
            """;

        when(aiCoreService.generateContent(org.mockito.ArgumentMatchers.any()))
            .thenReturn(AIGenerationResponse.builder().content(json).build());

        IntentQueryExtractor extractor = new IntentQueryExtractor(aiCoreService, enrichedPromptBuilder, objectMapper);

        MultiIntentResponse response = extractor.extract("Update my payment details", "user-321");

        Intent intent = response.getIntents().getFirst();
        assertThat(intent.getNextStepRecommended()).isNotNull();
        assertThat(intent.getNextStepRecommended().getIntent()).isEqualTo("view_billing_history");
        assertThat(intent.getNextStepRecommended().getConfidence()).isEqualTo(0.71);
    }

    @Test
    void shouldRejectBlankQuery() {
        IntentQueryExtractor extractor = new IntentQueryExtractor(aiCoreService, enrichedPromptBuilder, objectMapper);

        assertThatThrownBy(() -> extractor.extract("   ", "user-123"))
            .isInstanceOf(AIServiceException.class)
            .hasMessageContaining("Query cannot be blank");
    }

    @Test
    void shouldRaiseExceptionWhenJsonInvalid() {
        when(enrichedPromptBuilder.buildSystemPrompt("user-123")).thenReturn("system-prompt");
        when(aiCoreService.generateContent(org.mockito.ArgumentMatchers.any()))
            .thenReturn(AIGenerationResponse.builder().content("not-json").build());

        IntentQueryExtractor extractor = new IntentQueryExtractor(aiCoreService, enrichedPromptBuilder, objectMapper);

        assertThatThrownBy(() -> extractor.extract("Cancel my subscription", "user-123"))
            .isInstanceOf(AIServiceException.class)
            .hasMessageContaining("Unable to parse intent extraction response");
    }
}
