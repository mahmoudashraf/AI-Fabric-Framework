package com.ai.infrastructure.intent;

import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.IntentType;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.dto.NextStepRecommendation;
import com.ai.infrastructure.exception.AIServiceException;
import com.ai.infrastructure.intent.action.ActionHandlerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Applies deterministic normalization and guardrails to extracted intents.
 *
 * <p>This component must not perform additional LLM calls.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntentExtractionPostProcessor {

    private final ActionHandlerRegistry actionHandlerRegistry;

    public MultiIntentResponse postProcess(MultiIntentResponse response, String originalQuery) {
        if (response == null) {
            throw new AIServiceException("Intent extraction response is null");
        }

        response.normalize();
        coerceMisclassifiedActionIntents(response);
        validateResponse(response, originalQuery);

        if (!response.hasIntents()) {
            log.warn("Intent extractor returned no intents for query '{}'", originalQuery);
        }

        return response;
    }

    /**
     * Provider-agnostic guardrail: some models mislabel "summarize / explain" requests as ACTION intents
     * with an unregistered action name (e.g., "summarize"). When the intent also clearly requests RAG
     * (requiresRetrieval + requiresGeneration + vectorSpace), treat it as INFORMATION instead so the
     * pipeline can satisfy it via retrieval/generation rather than failing with ACTION_NOT_FOUND.
     */
    private void coerceMisclassifiedActionIntents(MultiIntentResponse response) {
        if (response == null || response.getIntents() == null || response.getIntents().isEmpty()) {
            return;
        }

        for (Intent intent : response.getIntents()) {
            if (intent == null || intent.getType() != IntentType.ACTION) {
                continue;
            }

            String actionName = intent.getIntentOrAction();
            if (!StringUtils.hasText(actionName)) {
                continue;
            }

            if (actionHandlerRegistry != null && actionHandlerRegistry.findHandler(actionName).isPresent()) {
                continue;
            }

            boolean looksLikeRagRequest =
                Boolean.TRUE.equals(intent.getRequiresRetrieval())
                    && Boolean.TRUE.equals(intent.getRequiresGeneration())
                    && StringUtils.hasText(intent.getVectorSpace());

            if (looksLikeRagRequest) {
                log.warn(
                    "Intent extractor misclassified a RAG request as ACTION (action='{}', vectorSpace='{}'). Treating as INFORMATION.",
                    actionName,
                    intent.getVectorSpace()
                );
                intent.setType(IntentType.INFORMATION);
                intent.setAction(null);
            }
        }
    }

    private void validateResponse(MultiIntentResponse response, String originalQuery) {
        if (response.getIntents() == null) {
            response.setIntents(List.of());
        }

        for (Intent intent : response.getIntents()) {
            if (intent == null) {
                throw new AIServiceException("Intent is null");
            }
            if (!intent.hasValidType()) {
                throw new AIServiceException("Intent is missing a valid type attribute");
            }
            if (!intent.hasMeaningfulName()) {
                // Provider-agnostic tolerance: some models emit OUT_OF_SCOPE without a stable name.
                // For OUT_OF_SCOPE we do not require `intent` / `action` because execution does not depend on it.
                if (intent.getType() == IntentType.OUT_OF_SCOPE) {
                    intent.setIntent("out_of_scope");
                    intent.setAction(null);
                } else {
                    throw new AIServiceException("Intent is missing the 'intent' or 'action' field");
                }
            }
            validateRelationshipActionParams(intent, originalQuery);
            if (intent.getRequiresRetrieval() == null) {
                intent.setRequiresRetrieval(intent.getType() == IntentType.INFORMATION || intent.getType() == IntentType.COMPOUND);
            }
            if (Boolean.TRUE.equals(intent.getRequiresRetrieval()) && !StringUtils.hasText(intent.getVectorSpace())) {
                log.debug("Intent requires retrieval but vectorSpace is missing; deferring routing to VectorSpaceResolutionStep");
            }
        }

        if (response.getOrchestrationStrategy() == null) {
            response.setOrchestrationStrategy(deriveOrchestrationStrategy(response));
        }
    }

    private String deriveOrchestrationStrategy(MultiIntentResponse response) {
        boolean hasAction = response.getIntents().stream().anyMatch(Intent::isActionable);
        boolean requiresRetrieval = response.getIntents().stream()
            .anyMatch(intent -> Boolean.TRUE.equals(intent.getRequiresRetrieval()));

        if (hasAction && !requiresRetrieval) {
            return "DIRECT_ACTION";
        }
        if (requiresRetrieval) {
            return "RETRIEVE_AND_GENERATE";
        }
        return "ADMIT_UNKNOWN";
    }

    private void validateRelationshipActionParams(Intent intent, String originalQuery) {
        if (intent.getType() != IntentType.ACTION) {
            return;
        }

        String actionName = StringUtils.hasText(intent.getAction()) ? intent.getAction() : intent.getIntent();
        String canonicalActionName = actionName;
        if (actionHandlerRegistry != null && StringUtils.hasText(actionName)) {
            var metadataOpt = actionHandlerRegistry.findMetadata(actionName);
            if (metadataOpt != null) {
                canonicalActionName = metadataOpt
                    .map(com.ai.infrastructure.intent.action.AIActionMetaData::getName)
                    .orElse(actionName);
            }
        }
        if (!"relationship_query".equalsIgnoreCase(canonicalActionName)) {
            return;
        }

        coerceRelationshipQueryPostActionSummaryRequest(intent);

        Map<String, Object> params = intent.getActionParams();
        Map<String, Object> mutable = params != null ? new LinkedHashMap<>(params) : new LinkedHashMap<>();

        Object rawQuery = mutable.get("query");
        if (rawQuery instanceof String text && StringUtils.hasText(text)) {
            mutable.put("query", RelationshipQueryHintPrefix.stripIfPresent(text));
        } else if (StringUtils.hasText(originalQuery)) {
            String stripped = RelationshipQueryHintPrefix.stripIfPresent(originalQuery);
            if (StringUtils.hasText(stripped)) {
                mutable.put("query", stripped);
            }
        }

        Object rawEntityTypes = mutable.get("entityTypes");

        List<String> normalizedEntityTypes;
        if (rawEntityTypes == null) {
            log.warn("Relationship query intent missing entityTypes - defaulting to empty list for actionParams");
            normalizedEntityTypes = List.of();
        } else if (rawEntityTypes instanceof List<?> list) {
            normalizedEntityTypes = list.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(String::toLowerCase)
                .toList();
        } else if (rawEntityTypes instanceof String value) {
            String trimmed = value.trim();
            normalizedEntityTypes = trimmed.isEmpty() ? List.of() : List.of(trimmed.toLowerCase());
        } else {
            log.warn("Relationship query entityTypes should be List<String> or String but was {}", rawEntityTypes.getClass().getSimpleName());
            normalizedEntityTypes = List.of();
        }

        mutable.put("entityTypes", normalizedEntityTypes);
        intent.setActionParams(mutable);
    }

    private void coerceRelationshipQueryPostActionSummaryRequest(Intent intent) {
        if (intent == null) {
            return;
        }

        if (StringUtils.hasText(intent.getGenerationInstructions())) {
            intent.setRequiresGeneration(true);
            return;
        }
        if (Boolean.TRUE.equals(intent.getRequiresGeneration())) {
            return;
        }

        NextStepRecommendation next = intent.getNextStepRecommended();
        if (next == null) {
            return;
        }

        // If the model recommends a follow-up that targets a vector space, keep it as a smart suggestion.
        // Post-action generation should be grounded in the already retrieved SQL results, not trigger retrieval.
        if (StringUtils.hasText(next.getVectorSpace())) {
            return;
        }
        if (!StringUtils.hasText(next.getQuery())) {
            return;
        }

        // Provider-agnostic: some models represent "then summarize/explain the results" as nextStepRecommended
        // instead of the dedicated post-action generation fields. Convert it into a post-action generation request
        // for relationship_query so the pipeline can run a grounded summary after executing the action.
        intent.setRequiresGeneration(true);
        intent.setGenerationInstructions(next.getQuery().trim());
    }
}
