package com.ai.infrastructure.intent.extraction;

import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.core.LlmPurpose;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.intent.EnrichedPromptBuilder;
import com.ai.infrastructure.intent.IntentExtractionJsonSupport;
import com.ai.infrastructure.intent.IntentExtractionValidator;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

/**
 * Standard compound intent extraction (fast path).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompoundIntentExtractionStrategy implements IntentExtractionStrategy {

    private static final String ENTITY_TYPE = "intent_extraction";
    private static final String GENERATION_TYPE = "intent_extraction";

    private final AICoreService aiCoreService;
    private final EnrichedPromptBuilder enrichedPromptBuilder;
    private final IntentExtractionJsonSupport jsonSupport;
    private final IntentExtractionValidator validator;

    @Override
    public ExtractionAttempt attemptExtract(String query, OrchestrationContext context) {
        String systemPrompt = enrichedPromptBuilder.buildSystemPrompt(context);
        String userPrompt = enrichedPromptBuilder.buildUserPrompt(query);

        AIGenerationRequest request = AIGenerationRequest.builder()
            .entityId("intent-" + UUID.randomUUID())
            .entityType(ENTITY_TYPE)
            .generationType(GENERATION_TYPE)
            .systemPrompt(systemPrompt)
            .prompt(userPrompt)
            .parameters(jsonSupport.jsonOnlyResponseParameters())
            .userId(context != null ? context.getUserId() : null)
            .build();

        try {
            AIGenerationResponse response = aiCoreService.generateContent(request, LlmPurpose.ORCHESTRATION);
            String content = response != null ? response.getContent() : null;
            if (!StringUtils.hasText(content)) {
                return ExtractionAttempt.builder()
                    .success(false)
                    .errorMessage("Empty response from LLM")
                    .strategyName(getStrategyName())
                    .generationRequest(request)
                    .rawContent(content)
                    .llmCalls(1)
                    .build();
            }

            String sanitized = jsonSupport.stripCodeFences(content);
            MultiIntentResponse parsed;
            try {
                parsed = jsonSupport.parseResponse(sanitized);
            } catch (Exception parseException) {
                IntentExtractionValidator.ValidationResult validationResult = new IntentExtractionValidator.ValidationResult(
                    false,
                    IntentExtractionValidator.ErrorCategory.STRUCTURAL,
                    List.of("Unable to parse intent extraction JSON: " + parseException.getMessage()),
                    List.of()
                );

                return ExtractionAttempt.builder()
                    .success(false)
                    .strategyName(getStrategyName())
                    .generationRequest(request)
                    .rawContent(sanitized)
                    .validationResult(validationResult)
                    .errorMessage(parseException.getMessage())
                    .exception(parseException)
                    .llmCalls(1)
                    .build();
            }

            IntentExtractionValidator.ValidationResult validation = validator.validate(parsed);

            return ExtractionAttempt.builder()
                .success(validation.valid())
                .response(parsed)
                .validationResult(validation)
                .rawContent(sanitized)
                .generationRequest(request)
                .strategyName(getStrategyName())
                .llmCalls(1)
                .build();
        } catch (Exception ex) {
            log.warn("Compound extraction failed: {}", ex.getMessage());
            return ExtractionAttempt.builder()
                .success(false)
                .errorMessage(ex.getMessage())
                .exception(ex)
                .strategyName(getStrategyName())
                .generationRequest(request)
                .llmCalls(1)
                .build();
        }
    }

    @Override
    public String getStrategyName() {
        return "compound";
    }
}
