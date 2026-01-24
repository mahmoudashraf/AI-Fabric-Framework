package com.ai.infrastructure.intent.extraction;

import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.core.LlmPurpose;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.intent.EnrichedPromptBuilder;
import com.ai.infrastructure.intent.IntentExtractionJsonSupport;
import com.ai.infrastructure.intent.IntentExtractionValidator;
import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.ai.infrastructure.intent.action.AIActionRegistry;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Completion step for contract-incomplete (but structurally valid) intent extraction outputs.
 *
 * <p>This is not structural repair; it is used to fill required action fields (action name, actionParams, etc.)
 * guided by deterministic validator signals and action metadata.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompletionIntentExtractionStrategy {

    private static final String ENTITY_TYPE = "intent_extraction";
    private static final String GENERATION_TYPE = "intent_extraction_completion";

    private final AICoreService aiCoreService;
    private final EnrichedPromptBuilder enrichedPromptBuilder;
    private final IntentExtractionJsonSupport jsonSupport;
    private final AIActionRegistry actionHandlerRegistry;
    private final IntentExtractionValidator validator;

    public ExtractionAttempt attemptComplete(String query, OrchestrationContext context, ExtractionAttempt previousAttempt) {
        if (!StringUtils.hasText(query)) {
            return ExtractionAttempt.builder()
                .success(false)
                .strategyName(getStrategyName())
                .errorMessage("Completion requires a non-empty query")
                .llmCalls(0)
                .build();
        }
        if (previousAttempt == null || previousAttempt.getResponse() == null) {
            return ExtractionAttempt.builder()
                .success(false)
                .strategyName(getStrategyName())
                .errorMessage("Completion requires a previous attempt with a parsed response")
                .llmCalls(0)
                .build();
        }

        OrchestrationContext safeContext = context != null ? context : OrchestrationContext.anonymous();
        safeContext.validate();

        IntentExtractionValidator.ValidationResult priorValidation = previousAttempt.getValidationResult();
        List<IntentExtractionValidator.ValidationIssue> errorIssues = priorValidation != null && priorValidation.issues() != null
            ? priorValidation.issues().stream()
                .filter(Objects::nonNull)
                .filter(issue -> issue.severity() == IntentExtractionValidator.Severity.ERROR)
                .toList()
            : List.of();

        String systemPrompt = enrichedPromptBuilder.buildSystemPrompt(safeContext) + "\n\n" + """
            COMPLETION MODE:
            - You will receive a PARTIAL JSON response that is structurally valid but contract-incomplete.
            - Fix ONLY the missing/invalid contract fields listed in VALIDATION ISSUES.
            - Do NOT invent new actions. ACTION names MUST come from the allowed actions list.
            - Do NOT guess vectorSpace or other routing values. Leave them null/empty if not explicit.
            - For relationship_query: actionParams.query is REQUIRED and MUST NOT include the hint prefix (e.g., \"relationship_query:\"). If missing, derive it from the user request after the prefix.
            - If required info is missing from the user request, choose a safe fallback (OUT_OF_SCOPE) and include a helpful nextStepRecommended.query asking for the missing information.
            - Output MUST be a single JSON object matching the schema above. No markdown. No commentary.
            """;

        String allowedActions = buildAllowedActionsSpec();
        String issuesPayload = formatIssues(errorIssues);
        String partialJson = toJson(previousAttempt.getResponse());

        String prompt = """
            You are completing an intent extraction response.
            Your job is to output corrected JSON that satisfies all VALIDATION ISSUES while preserving the user's meaning.

            ALLOWED ACTIONS (do NOT invent):
            %s

            VALIDATION ISSUES (must be resolved if possible):
            %s

            USER REQUEST:
            %s

            PARTIAL JSON (to complete):
            %s
            """.formatted(allowedActions, issuesPayload, query, partialJson);

        AIGenerationRequest request = AIGenerationRequest.builder()
            .entityId("intent-" + UUID.randomUUID())
            .entityType(ENTITY_TYPE)
            .generationType(GENERATION_TYPE)
            .systemPrompt(systemPrompt)
            .prompt(prompt)
            .parameters(jsonSupport.jsonOnlyResponseParameters())
            .userId(safeContext.getUserId())
            .build();

        int llmCalls = 1;
        try {
            AIGenerationResponse response = aiCoreService.generateContent(request, LlmPurpose.ORCHESTRATION);
            String content = response != null ? response.getContent() : null;
            if (!StringUtils.hasText(content)) {
                return ExtractionAttempt.builder()
                    .success(false)
                    .strategyName(getStrategyName())
                    .generationRequest(request)
                    .rawContent(content)
                    .errorMessage("Completion attempt returned an empty response from provider")
                    .llmCalls(llmCalls)
                    .build();
            }

            String sanitized = jsonSupport.stripCodeFences(content);
            MultiIntentResponse parsed = jsonSupport.parseResponse(sanitized);
            IntentExtractionValidator.ValidationResult validation = validator.validate(parsed);

            return ExtractionAttempt.builder()
                .success(validation.valid())
                .response(parsed)
                .validationResult(validation)
                .rawContent(sanitized)
                .generationRequest(request)
                .strategyName(getStrategyName())
                .llmCalls(llmCalls)
                .build();
        } catch (Exception ex) {
            log.warn("Completion extraction failed: {}", ex.getMessage());
            return ExtractionAttempt.builder()
                .success(false)
                .strategyName(getStrategyName())
                .errorMessage(ex.getMessage())
                .exception(ex)
                .generationRequest(request)
                .llmCalls(llmCalls)
                .build();
        }
    }

    public String getStrategyName() {
        return "completion";
    }

    private String buildAllowedActionsSpec() {
        if (actionHandlerRegistry == null) {
            return "- <unavailable>";
        }
        List<AIActionMetaData> actions = actionHandlerRegistry.getAllMetadata();
        if (actions == null || actions.isEmpty()) {
            return "- <none>";
        }

        return actions.stream()
            .filter(Objects::nonNull)
            .filter(meta -> StringUtils.hasText(meta.getName()))
            .map(meta -> {
                Set<String> required = meta.getRequiredParameters() != null ? meta.getRequiredParameters() : Set.of();
                String requiredText = required.isEmpty() ? "" : " required=" + required;
                Map<String, String> params = meta.getParameters() != null ? meta.getParameters() : Map.of();
                String paramsText = params.isEmpty()
                    ? ""
                    : " params=" + params.entrySet().stream()
                        .filter(e -> e.getKey() != null && e.getValue() != null)
                        .map(e -> e.getKey() + ":" + e.getValue())
                        .collect(Collectors.joining(", "));
                String desc = StringUtils.hasText(meta.getDescription()) ? " - " + meta.getDescription().trim() : "";
                return "- " + meta.getName().trim() + desc + requiredText + paramsText;
            })
            .collect(Collectors.joining("\n"));
    }

    private String formatIssues(List<IntentExtractionValidator.ValidationIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return "- <none>";
        }
        return issues.stream()
            .filter(Objects::nonNull)
            .map(issue -> "- " + issue.code() + " field=" + issue.field() + " intentIndex=" + issue.intentIndex() + " message=" + issue.message())
            .collect(Collectors.joining("\n"));
    }

    private String toJson(MultiIntentResponse response) {
        if (response == null) {
            return "{}";
        }
        try {
            return jsonSupport.objectMapper().writeValueAsString(response);
        } catch (Exception ex) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("intents", List.of());
            fallback.put("metadata", Map.of("serializationError", ex.getMessage()));
            try {
                return jsonSupport.objectMapper().writeValueAsString(fallback);
            } catch (Exception ignored) {
                return "{}";
            }
        }
    }
}
