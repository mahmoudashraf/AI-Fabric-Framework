package com.ai.infrastructure.intent.extraction;

import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.core.LlmPurpose;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.IntentType;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.intent.IntentExtractionJsonSupport;
import com.ai.infrastructure.intent.IntentExtractionValidator;
import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.ai.infrastructure.intent.action.ActionHandlerRegistry;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Multi-step extraction fallback that decomposes the task into smaller prompts.
 *
 * <p>Prompt design is intentionally conservative and hard-constrains ACTION intents to the registered action list.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MultiStepIntentExtractionStrategy implements IntentExtractionStrategy {

    private static final String ENTITY_TYPE = "intent_extraction";
    private static final String GENERATION_TYPE = "intent_extraction_multi_step";

    private final AICoreService aiCoreService;
    private final ActionHandlerRegistry actionHandlerRegistry;
    private final IntentExtractionJsonSupport jsonSupport;
    private final IntentExtractionValidator validator;

    @Override
    public ExtractionAttempt attemptExtract(String query, OrchestrationContext context) {
        try {
            ClassificationResponse classification = classify(query, context);
            if (classification == null || CollectionUtils.isEmpty(classification.getIntents())) {
                return ExtractionAttempt.builder()
                    .success(false)
                    .strategyName(getStrategyName())
                    .errorMessage("Multi-step classification returned no intents")
                    .llmCalls(1)
                    .build();
            }

            ActionSelectionResult selectionResult = selectActionsIfNeeded(query, context, classification);
            // Count LLM calls: 1 for classification + 1 if action selection was attempted
            int llmCalls = selectionResult.wasLlmCallAttempted() ? 2 : 1;

            MultiIntentResponse response = buildResponse(query, classification, selectionResult.getSelectedActions());
            IntentExtractionValidator.ValidationResult validation = validator.validate(response);

            return ExtractionAttempt.builder()
                .success(validation.valid())
                .response(response)
                .validationResult(validation)
                .strategyName(getStrategyName())
                .llmCalls(llmCalls)
                .build();
        } catch (Exception ex) {
            log.warn("Multi-step extraction failed: {}", ex.getMessage());
            return ExtractionAttempt.builder()
                .success(false)
                .strategyName(getStrategyName())
                .errorMessage(ex.getMessage())
                .exception(ex)
                .llmCalls(1)
                .build();
        }
    }

    @Override
    public String getStrategyName() {
        return "multi_step";
    }

    private ClassificationResponse classify(String query, OrchestrationContext context) {
        String prompt = """
            You are classifying a user request into one or more intents.
            Output MUST be valid JSON and MUST match the following schema:

            {
              "isCompound": false,
              "intents": [
                {
                  "type": "ACTION | INFORMATION | OUT_OF_SCOPE",
                  "intent": "canonical_intent_name",
                  "actionHint": "short verb phrase (only when type=ACTION)",
                  "requiresRetrieval": true,
                  "requiresGeneration": false,
                  "needsAdvancedRAG": false,
                  "optimizedQuery": "optional optimized query",
                  "vectorSpace": "optional domain hint"
                }
              ]
            }

            Rules:
            - Keep it simple and deterministic.
            - Do NOT invent action names; for ACTION use actionHint only.
            - If unsure, prefer INFORMATION with requiresRetrieval=false and requiresGeneration=false.

            USER REQUEST:
            %s
            """.formatted(query);

        AIGenerationRequest request = AIGenerationRequest.builder()
            .entityId("intent-" + UUID.randomUUID())
            .entityType(ENTITY_TYPE)
            .generationType(GENERATION_TYPE + "_classify")
            .systemPrompt("Return JSON only.")
            .prompt(prompt)
            .parameters(jsonSupport.jsonOnlyResponseParameters())
            .userId(context != null ? context.getUserId() : null)
            .build();

        AIGenerationResponse response = aiCoreService.generateContent(request, LlmPurpose.ORCHESTRATION);
        String content = response != null ? response.getContent() : null;
        if (!StringUtils.hasText(content)) {
            return null;
        }
        String sanitized = jsonSupport.stripCodeFences(content);
        try {
            return jsonSupport.objectMapper().readValue(sanitized, ClassificationResponse.class);
        } catch (Exception ex) {
            log.warn("Failed to parse multi-step classification JSON: {}", ex.getMessage());
            return null;
        }
    }

    private ActionSelectionResult selectActionsIfNeeded(String query,
                                                        OrchestrationContext context,
                                                        ClassificationResponse classification) {
        List<ClassificationIntent> actionIntents = new ArrayList<>();
        for (int i = 0; i < classification.getIntents().size(); i++) {
            ClassificationIntent intent = classification.getIntents().get(i);
            if (intent != null && IntentType.ACTION.name().equalsIgnoreCase(intent.getType())) {
                actionIntents.add(intent.withIndex(i));
            }
        }

        if (actionIntents.isEmpty()) {
            return new ActionSelectionResult(Map.of(), false);
        }

        List<AIActionMetaData> actions = actionHandlerRegistry != null ? actionHandlerRegistry.getAllMetadata() : List.of();
        if (actions.isEmpty()) {
            return new ActionSelectionResult(Map.of(), false);
        }

        Map<String, String> allowedByNormalizedName = new LinkedHashMap<>();
        for (AIActionMetaData meta : actions) {
            if (meta == null || !StringUtils.hasText(meta.getName())) {
                continue;
            }
            allowedByNormalizedName.put(normalizeActionName(meta.getName()), meta.getName());
        }

        String actionsList = actions.stream()
            .map(meta -> "- " + meta.getName() + (StringUtils.hasText(meta.getDescription()) ? ": " + meta.getDescription() : ""))
            .collect(Collectors.joining("\n"));

        StringBuilder actionHints = new StringBuilder();
        for (ClassificationIntent intent : actionIntents) {
            actionHints.append("- intentIndex=").append(intent.getIndex())
                .append(" actionHint=").append(intent.getActionHint() != null ? intent.getActionHint() : "")
                .append("\n");
        }

        String prompt = """
            You are selecting registered actions for ACTION intents.
            You MUST pick from the allowed action names below, or return null if none match.
            Output MUST be valid JSON and MUST match:

            {
              "mappings": [
                {"intentIndex": 0, "selectedAction": "action_name_or_null"}
              ]
            }

            ALLOWED ACTIONS:
            %s

            ACTION INTENTS:
            %s

            USER REQUEST (context only):
            %s
            """.formatted(actionsList, actionHints, query);

        AIGenerationRequest request = AIGenerationRequest.builder()
            .entityId("intent-" + UUID.randomUUID())
            .entityType(ENTITY_TYPE)
            .generationType(GENERATION_TYPE + "_select_actions")
            .systemPrompt("Return JSON only.")
            .prompt(prompt)
            .parameters(jsonSupport.jsonOnlyResponseParameters())
            .userId(context != null ? context.getUserId() : null)
            .build();

        AIGenerationResponse response = aiCoreService.generateContent(request, LlmPurpose.ORCHESTRATION);
        String content = response != null ? response.getContent() : null;
        if (!StringUtils.hasText(content)) {
            return new ActionSelectionResult(Map.of(), true);
        }

        String sanitized = jsonSupport.stripCodeFences(content);
        ActionSelectionResponse parsed;
        try {
            parsed = jsonSupport.objectMapper().readValue(sanitized, ActionSelectionResponse.class);
        } catch (Exception ex) {
            log.warn("Failed to parse action selection JSON: {}", ex.getMessage());
            return new ActionSelectionResult(Map.of(), true);
        }

        if (parsed == null || CollectionUtils.isEmpty(parsed.getMappings())) {
            return new ActionSelectionResult(Map.of(), true);
        }

        Map<Integer, String> out = new LinkedHashMap<>();
        for (ActionMapping mapping : parsed.getMappings()) {
            if (mapping == null) {
                continue;
            }
            Integer idx = mapping.getIntentIndex();
            if (idx == null || idx < 0) {
                continue;
            }
            String normalizedSelected = normalizeActionName(mapping.getSelectedAction());
            String canonical = normalizedSelected != null ? allowedByNormalizedName.get(normalizedSelected) : null;
            if (canonical != null) {
                out.put(idx, canonical);
            }
        }
        return new ActionSelectionResult(Collections.unmodifiableMap(out), true);
    }

    private String normalizeActionName(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9_]+", "_");
        normalized = normalized.replaceAll("_+", "_");
        normalized = normalized.replaceAll("^_+|_+$", "");
        if (normalized.endsWith("_action")) {
            normalized = normalized.substring(0, normalized.length() - "_action".length());
        }
        if (normalized.isBlank() || "null".equalsIgnoreCase(normalized)) {
            return null;
        }
        return normalized;
    }

    private MultiIntentResponse buildResponse(String query,
                                              ClassificationResponse classification,
                                              Map<Integer, String> selectedActions) {
        List<Intent> intents = new ArrayList<>(classification.getIntents().size());
        for (int i = 0; i < classification.getIntents().size(); i++) {
            ClassificationIntent classified = classification.getIntents().get(i);
            if (classified == null) {
                continue;
            }

            IntentType type = parseIntentType(classified.getType());
            String intentName = StringUtils.hasText(classified.getIntent())
                ? classified.getIntent()
                : defaultIntentName(type);

            Intent.IntentBuilder builder = Intent.builder()
                .type(type)
                .intent(intentName)
                .confidence(classified.getConfidence())
                .requiresRetrieval(classified.getRequiresRetrieval())
                .requiresGeneration(classified.getRequiresGeneration())
                .needsAdvancedRAG(classified.getNeedsAdvancedRAG())
                .optimizedQuery(StringUtils.hasText(classified.getOptimizedQuery()) ? classified.getOptimizedQuery() : null)
                .vectorSpace(StringUtils.hasText(classified.getVectorSpace()) ? classified.getVectorSpace() : null);

            if (type == IntentType.ACTION) {
                String selected = selectedActions != null ? selectedActions.get(i) : null;
                if (!StringUtils.hasText(selected)) {
                    builder.type(IntentType.OUT_OF_SCOPE);
                    builder.intent("out_of_scope");
                    builder.action(null);
                    builder.actionParams(Map.of("reason", "No registered action matched '" + safeActionHint(classified.getActionHint()) + "'"));
                    builder.requiresRetrieval(false);
                    builder.requiresGeneration(false);
                } else {
                    builder.action(selected);
                }
            } else if (type == IntentType.OUT_OF_SCOPE) {
                builder.actionParams(Map.of("reason", "Classified as out of scope by multi-step fallback"));
                builder.requiresRetrieval(false);
                builder.requiresGeneration(false);
            }

            intents.add(builder.build());
        }

        boolean compound = Boolean.TRUE.equals(classification.getIsCompound()) || intents.size() > 1;
        return MultiIntentResponse.builder()
            .intents(intents)
            .compound(compound)
            .metadata(Map.of("extractionMode", "multi_step"))
            .build();
    }

    private String safeActionHint(String value) {
        return value != null ? value : "";
    }

    private IntentType parseIntentType(String raw) {
        if (!StringUtils.hasText(raw)) {
            return IntentType.OUT_OF_SCOPE;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        try {
            return IntentType.valueOf(normalized);
        } catch (Exception ex) {
            return IntentType.OUT_OF_SCOPE;
        }
    }

    private String defaultIntentName(IntentType type) {
        if (type == null) {
            return "unknown";
        }
        return switch (type) {
            case ACTION -> "action_request";
            case INFORMATION -> "information_request";
            case OUT_OF_SCOPE -> "out_of_scope";
            case COMPOUND -> "compound";
        };
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ClassificationResponse {
        private Boolean isCompound;
        private List<ClassificationIntent> intents = new ArrayList<>();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ClassificationIntent {
        private Integer index;
        private String type;
        private String intent;
        private String actionHint;
        private Double confidence;
        private Boolean requiresRetrieval;
        private Boolean requiresGeneration;
        private Boolean needsAdvancedRAG;
        private String optimizedQuery;
        private String vectorSpace;

        ClassificationIntent withIndex(int idx) {
            ClassificationIntent copy = new ClassificationIntent();
            copy.index = idx;
            copy.type = this.type;
            copy.intent = this.intent;
            copy.actionHint = this.actionHint;
            copy.confidence = this.confidence;
            copy.requiresRetrieval = this.requiresRetrieval;
            copy.requiresGeneration = this.requiresGeneration;
            copy.needsAdvancedRAG = this.needsAdvancedRAG;
            copy.optimizedQuery = this.optimizedQuery;
            copy.vectorSpace = this.vectorSpace;
            return copy;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ActionSelectionResponse {
        private List<ActionMapping> mappings = new ArrayList<>();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ActionMapping {
        private Integer intentIndex;
        private String selectedAction;
    }

    /**
     * Result of action selection that tracks both the selected actions and whether an LLM call was made.
     */
    static class ActionSelectionResult {
        private final Map<Integer, String> selectedActions;
        private final boolean llmCallAttempted;

        ActionSelectionResult(Map<Integer, String> selectedActions, boolean llmCallAttempted) {
            this.selectedActions = selectedActions;
            this.llmCallAttempted = llmCallAttempted;
        }

        Map<Integer, String> getSelectedActions() {
            return selectedActions;
        }

        boolean wasLlmCallAttempted() {
            return llmCallAttempted;
        }
    }
}
