package com.ai.infrastructure.intent;

import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.IntentType;
import com.ai.infrastructure.dto.MultiIntentResponse;
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
        forceExplicitRelationshipQueryDirective(response, originalQuery);
        coerceMisclassifiedActionIntents(response);
        validateResponse(response, originalQuery);

        if (!response.hasIntents()) {
            log.warn("Intent extractor returned no intents for query '{}'", originalQuery);
        }

        return response;
    }

    /**
     * Deterministic override: if the user explicitly prefixes the query with
     * {@code relationship_query:} / {@code relationship query:} / {@code relationship-query:},
     * we treat the request as an explicit invocation of the {@code relationship_query} action.
     *
     * <p>This keeps the "hint prefix" contract stable even when LLM intent extraction is flaky.</p>
     */
    private void forceExplicitRelationshipQueryDirective(MultiIntentResponse response, String originalQuery) {
        if (response == null || !StringUtils.hasText(originalQuery)) {
            return;
        }

        String trimmed = originalQuery.trim();
        if (trimmed.isEmpty()) {
            return;
        }

        String lower = trimmed.toLowerCase(Locale.ROOT);
        String[] prefixes = { "relationship query:", "relationship_query:", "relationship-query:" };
        boolean prefixed = false;
        for (String prefix : prefixes) {
            if (lower.startsWith(prefix)) {
                prefixed = true;
                break;
            }
        }
        if (!prefixed) {
            return;
        }

        RelationshipQueryDirectiveSplitter.SplitResult split = RelationshipQueryDirectiveSplitter.split(trimmed);
        if (split == null || !StringUtils.hasText(split.relationalQuery())) {
            return;
        }

        boolean hasGenerationInstructions = split != null && StringUtils.hasText(split.generationInstructions());

        Intent forced = Intent.builder()
            .type(IntentType.ACTION)
            .action("relationship_query")
            .confidence(1.0d)
            .requiresRetrieval(false)
            .requiresGeneration(hasGenerationInstructions)
            .generationInstructions(hasGenerationInstructions ? split.generationInstructions() : null)
            .actionParams(Map.of(
                "query", split.relationalQuery(),
                "entityTypes", List.of()
            ))
            .build();

        response.setIntents(List.of(forced));
        response.setCompound(false);
        response.setOrchestrationStrategy("DIRECT_ACTION");
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
                throw new AIServiceException("Intent is missing the 'intent' or 'action' field");
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

        Map<String, Object> params = intent.getActionParams();
        Map<String, Object> mutable = params != null ? new LinkedHashMap<>(params) : new LinkedHashMap<>();

        Object rawQuery = mutable.get("query");
        RelationshipQueryDirectiveSplitter.SplitResult split;
        if (rawQuery instanceof String text && StringUtils.hasText(text)) {
            split = RelationshipQueryDirectiveSplitter.split(text);
        } else {
            split = RelationshipQueryDirectiveSplitter.split(originalQuery);
        }
        if (split != null && StringUtils.hasText(split.relationalQuery())) {
            mutable.put("query", split.relationalQuery());
        }
        if (split != null && StringUtils.hasText(split.generationInstructions())
            && !StringUtils.hasText(intent.getGenerationInstructions())) {
            intent.setGenerationInstructions(split.generationInstructions());
            intent.setRequiresGeneration(true);
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
}
