package com.ai.infrastructure.intent.extraction;

import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.core.LlmPurpose;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.intent.IntentExtractionJsonSupport;
import com.ai.infrastructure.intent.IntentExtractionValidator;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Structural repair step for malformed/invalid compound extraction outputs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RepairIntentExtractionStrategy {

    private final AICoreService aiCoreService;
    private final IntentExtractionJsonSupport jsonSupport;
    private final IntentExtractionValidator validator;

    public ExtractionAttempt attemptRepair(String query, OrchestrationContext context, ExtractionAttempt previousAttempt) {
        if (previousAttempt == null || previousAttempt.getGenerationRequest() == null || !StringUtils.hasText(previousAttempt.getRawContent())) {
            return ExtractionAttempt.builder()
                .success(false)
                .strategyName(getStrategyName())
                .errorMessage("Repair requires the original generation request and raw content")
                .llmCalls(0)
                .build();
        }

        AIGenerationRequest originalRequest = previousAttempt.getGenerationRequest();
        String originalSystemPrompt = originalRequest.getSystemPrompt();
        String repairSystemPrompt = (StringUtils.hasText(originalSystemPrompt) ? originalSystemPrompt.trim() + "\n\n" : "") + """
            You are repairing a previously malformed assistant response.
            Output MUST be a single JSON object that matches the schema above exactly.
            Include ALL schema fields (use null/false/empty values where appropriate) so downstream systems can operate safely.
            Never wrap the JSON in markdown code fences and never add commentary.
            """;

        String repairPrompt = """
            Convert the malformed assistant response into valid JSON that matches the schema in the system prompt.
            This is a STRUCTURAL repair step only: fix JSON/schema correctness, do NOT infer or guess semantic fields.
            Do NOT guess vectorSpace or other routing fields. If a semantic field is missing, leave it unset/null and keep the schema intact.
            If the assistant response cannot be repaired into a valid schema, choose a safe default (e.g., OUT_OF_SCOPE with neutral confidence).

            ORIGINAL USER REQUEST (for context):
            ---BEGIN USER REQUEST---
            %s
            ---END USER REQUEST---

            MALFORMED ASSISTANT RESPONSE:
            ---BEGIN MALFORMED---
            %s
            ---END MALFORMED---
            """.formatted(query, previousAttempt.getRawContent());

        AIGenerationRequest repairRequest = AIGenerationRequest.builder()
            .entityId(originalRequest.getEntityId() + "-repair")
            .entityType(originalRequest.getEntityType())
            .generationType("intent_extraction_repair")
            .systemPrompt(repairSystemPrompt)
            .prompt(repairPrompt)
            .parameters(jsonSupport.jsonOnlyResponseParameters())
            .userId(context != null ? context.getUserId() : null)
            .build();

        try {
            AIGenerationResponse repairResponse = aiCoreService.generateContent(repairRequest, LlmPurpose.ORCHESTRATION);
            String repairedContent = repairResponse != null ? repairResponse.getContent() : null;
            if (!StringUtils.hasText(repairedContent)) {
                return ExtractionAttempt.builder()
                    .success(false)
                    .strategyName(getStrategyName())
                    .generationRequest(repairRequest)
                    .rawContent(repairedContent)
                    .errorMessage("Intent extraction repair attempt returned an empty response from provider")
                    .llmCalls(1)
                    .build();
            }

            String sanitized = jsonSupport.stripCodeFences(repairedContent);
            MultiIntentResponse parsed = jsonSupport.parseResponse(sanitized);
            IntentExtractionValidator.ValidationResult validation = validator.validate(parsed);

            return ExtractionAttempt.builder()
                .success(validation.valid())
                .response(parsed)
                .validationResult(validation)
                .rawContent(sanitized)
                .generationRequest(repairRequest)
                .strategyName(getStrategyName())
                .llmCalls(1)
                .build();
        } catch (Exception ex) {
            log.warn("Repair extraction failed: {}", ex.getMessage());
            return ExtractionAttempt.builder()
                .success(false)
                .strategyName(getStrategyName())
                .errorMessage(ex.getMessage())
                .exception(ex)
                .generationRequest(repairRequest)
                .llmCalls(1)
                .build();
        }
    }

    public String getStrategyName() {
        return "repair";
    }
}

