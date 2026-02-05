package com.ai.infrastructure.intent.extraction;

import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.core.LlmPurpose;
import com.ai.infrastructure.config.PromptBundleProperties;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.intent.IntentExtractionJsonSupport;
import com.ai.infrastructure.intent.IntentExtractionValidator;
import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.ai.infrastructure.intent.action.AIActionRegistry;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.prompt.ClasspathPromptTemplateStore;
import com.ai.infrastructure.prompt.PromptRenderer;
import com.ai.infrastructure.prompt.PromptTemplateResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MultiStepIntentExtractionStrategyTest {

    @Mock
    private AICoreService aiCoreService;

    @Mock
    private AIActionRegistry actionHandlerRegistry;

    @Mock
    private IntentExtractionValidator validator;

    private IntentExtractionJsonSupport jsonSupport;
    private PromptRenderer promptRenderer;
    private PromptTemplateResolver promptTemplateResolver;

    private static IntentExtractionInput input(String userQuery) {
        return new IntentExtractionInput(userQuery, userQuery, List.of());
    }

    @BeforeEach
    void setUp() {
        jsonSupport = new IntentExtractionJsonSupport(new ObjectMapper());
        promptRenderer = new PromptRenderer();
        promptTemplateResolver = new PromptTemplateResolver(
            new ClasspathPromptTemplateStore(new DefaultResourceLoader()),
            new PromptBundleProperties()
        );
    }

    @Test
    void shouldCountActionSelectionCallEvenWhenSelectionParsingFails() {
        when(actionHandlerRegistry.getAllMetadata()).thenReturn(List.of(AIActionMetaData.builder().name("cancel_subscription").build()));
        when(validator.validate(any())).thenReturn(
            new IntentExtractionValidator.ValidationResult(true, IntentExtractionValidator.ErrorCategory.NONE, List.of(), List.of())
        );

        when(aiCoreService.generateContent(any(AIGenerationRequest.class), eq(LlmPurpose.ORCHESTRATION)))
            .thenReturn(AIGenerationResponse.builder().content(classificationWithAction()).build())
            .thenReturn(AIGenerationResponse.builder().content("not-json").build());

        MultiStepIntentExtractionStrategy strategy = new MultiStepIntentExtractionStrategy(
            aiCoreService,
            actionHandlerRegistry,
            jsonSupport,
            validator,
            promptRenderer,
            promptTemplateResolver
        );

        ExtractionAttempt attempt = strategy.attemptExtract(input("Cancel my subscription"), OrchestrationContext.forUser("user-1"));

        assertThat(attempt.getLlmCalls()).isEqualTo(2);
        verify(aiCoreService, times(2)).generateContent(any(AIGenerationRequest.class), eq(LlmPurpose.ORCHESTRATION));
    }

    @Test
    void shouldCountOnlyClassificationCallWhenNoActionIntentsPresent() {
        when(validator.validate(any())).thenReturn(
            new IntentExtractionValidator.ValidationResult(true, IntentExtractionValidator.ErrorCategory.NONE, List.of(), List.of())
        );
        when(aiCoreService.generateContent(any(AIGenerationRequest.class), eq(LlmPurpose.ORCHESTRATION)))
            .thenReturn(AIGenerationResponse.builder().content(classificationInformationOnly()).build());

        MultiStepIntentExtractionStrategy strategy = new MultiStepIntentExtractionStrategy(
            aiCoreService,
            actionHandlerRegistry,
            jsonSupport,
            validator,
            promptRenderer,
            promptTemplateResolver
        );

        ExtractionAttempt attempt = strategy.attemptExtract(input("What is your refund policy?"), OrchestrationContext.forUser("user-2"));

        assertThat(attempt.getLlmCalls()).isEqualTo(1);
        verify(aiCoreService, times(1)).generateContent(any(AIGenerationRequest.class), eq(LlmPurpose.ORCHESTRATION));
    }

    @Test
    void shouldCountActionSelectionCallWhenProviderThrows() {
        when(actionHandlerRegistry.getAllMetadata()).thenReturn(List.of(AIActionMetaData.builder().name("cancel_subscription").build()));

        when(aiCoreService.generateContent(any(AIGenerationRequest.class), eq(LlmPurpose.ORCHESTRATION)))
            .thenReturn(AIGenerationResponse.builder().content(classificationWithAction()).build())
            .thenThrow(new IllegalStateException("provider down"));

        MultiStepIntentExtractionStrategy strategy = new MultiStepIntentExtractionStrategy(
            aiCoreService,
            actionHandlerRegistry,
            jsonSupport,
            validator,
            promptRenderer,
            promptTemplateResolver
        );

        ExtractionAttempt attempt = strategy.attemptExtract(input("Cancel my subscription"), OrchestrationContext.forUser("user-3"));

        assertThat(attempt.isSuccess()).isFalse();
        assertThat(attempt.getLlmCalls()).isEqualTo(2);
        verify(aiCoreService, times(2)).generateContent(any(AIGenerationRequest.class), eq(LlmPurpose.ORCHESTRATION));
    }

    private String classificationWithAction() {
        return """
            {
              "isCompound": false,
              "intents": [
                {
                  "type": "ACTION",
                  "intent": "cancel_subscription",
                  "actionHint": "cancel subscription",
                  "requiresRetrieval": false,
                  "requiresGeneration": false
                }
              ]
            }
            """;
    }

    private String classificationInformationOnly() {
        return """
            {
              "isCompound": false,
              "intents": [
                {
                  "type": "INFORMATION",
                  "intent": "refund_policy",
                  "requiresRetrieval": true,
                  "requiresGeneration": false,
                  "vectorSpace": "policies"
                }
              ]
            }
            """;
    }
}
