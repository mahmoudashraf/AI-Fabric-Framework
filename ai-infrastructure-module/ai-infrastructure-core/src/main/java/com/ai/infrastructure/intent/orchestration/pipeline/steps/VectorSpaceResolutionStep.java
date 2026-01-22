package com.ai.infrastructure.intent.orchestration.pipeline.steps;

import com.ai.infrastructure.config.OrchestrationProperties;
import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.IntentType;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.intent.KnowledgeBaseOverview;
import com.ai.infrastructure.intent.KnowledgeBaseOverviewService;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationResultType;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineStep;
import com.ai.infrastructure.intent.vectorspace.RoutingResult;
import com.ai.infrastructure.intent.vectorspace.VectorSpaceRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pipeline step that resolves missing vectorSpace for retrieval intents.
 *
 * <p><strong>Order:</strong> 55 (after IntentExtractionStep, before IntentHandlingStep)</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VectorSpaceResolutionStep implements PipelineStep {

    private static final String STEP_NAME = "VectorSpaceResolution";
    private static final int STEP_ORDER = 55;

    private static final String METADATA_KEY_ROUTING = "vectorSpaceRouting";

    private static final String MSG_CLARIFICATION_PREFIX = "Which knowledge base domain should I search?";

    private final VectorSpaceRouter vectorSpaceRouter;
    private final OrchestrationProperties orchestrationProperties;
    private final ObjectProvider<KnowledgeBaseOverviewService> knowledgeBaseOverviewServiceProvider;

    @Override
    public String getStepName() {
        return STEP_NAME;
    }

    @Override
    public int getOrder() {
        return STEP_ORDER;
    }

    @Override
    public PipelineContext process(PipelineContext context) {
        MultiIntentResponse intentResponse = context.getIntentResponse();
        if (intentResponse == null || intentResponse.getIntents() == null || intentResponse.getIntents().isEmpty()) {
            return context;
        }

        boolean deterministic = orchestrationProperties != null
            && orchestrationProperties.getInformationMode() == OrchestrationProperties.InformationMode.DETERMINISTIC_RAG_GENERATE;

        List<Map<String, Object>> routingEvents = new ArrayList<>();
        boolean anyUpdate = false;

        List<Intent> intents = intentResponse.getIntents();
        for (int i = 0; i < intents.size(); i++) {
            Intent intent = intents.get(i);
            if (!requiresResolution(intent)) {
                continue;
            }

            if (deterministic) {
                List<String> allSpaces = resolveAllVectorSpaces();
                if (!allSpaces.isEmpty()) {
                    intent.setVectorSpace(String.join(",", allSpaces));
                    anyUpdate = true;
                }
                // Deterministic mode must not terminate with clarification; leave vectorSpace blank if unknown.
                continue;
            }

            RoutingResult routing = vectorSpaceRouter.route(intent, context.getOriginalQuery());
            routingEvents.add(toRoutingEvent(i, routing));

            if (routing == null || !routing.isSuccess()) {
                List<String> candidates = routing != null ? routing.getCandidateSpaces() : List.of();
                return context.terminate(buildClarificationResult(candidates, context, routingEvents));
            }

            String resolvedVectorSpace = resolveVectorSpaceString(routing);
            if (!hasText(resolvedVectorSpace)) {
                return context.terminate(buildClarificationResult(List.of(), context, routingEvents));
            }

            intent.setVectorSpace(resolvedVectorSpace);
            anyUpdate = true;
        }

        if (routingEvents.isEmpty() && !anyUpdate) {
            return context;
        }

        PipelineContext updated = context;
        if (!routingEvents.isEmpty()) {
            updated = updated.withMetadata(METADATA_KEY_ROUTING, Collections.unmodifiableList(routingEvents));
        }
        if (anyUpdate) {
            updated = updated.toBuilder()
                .intentResponse(intentResponse)
                .build();
        }

        return updated;
    }

    private List<String> resolveAllVectorSpaces() {
        KnowledgeBaseOverviewService overviewService = knowledgeBaseOverviewServiceProvider != null
            ? knowledgeBaseOverviewServiceProvider.getIfAvailable()
            : null;
        if (overviewService == null) {
            return List.of();
        }

        KnowledgeBaseOverview overview = overviewService.getOverview();
        if (overview == null) {
            return List.of();
        }

        List<String> entityTypes = overview.getEntityTypes();
        if (entityTypes == null || entityTypes.isEmpty()) {
            var byType = overview.getDocumentsByType();
            if (byType != null && !byType.isEmpty()) {
                entityTypes = byType.keySet().stream().toList();
            }
        }
        if (entityTypes == null || entityTypes.isEmpty()) {
            return List.of();
        }

        return entityTypes.stream()
            .filter(this::hasText)
            .map(String::trim)
            .distinct()
            .toList();
    }

    private boolean requiresResolution(Intent intent) {
        if (intent == null) {
            return false;
        }
        if (intent.getType() != IntentType.INFORMATION) {
            return false;
        }
        if (!Boolean.TRUE.equals(intent.getRequiresRetrieval())) {
            return false;
        }
        return !hasText(intent.getVectorSpace());
    }

    private String resolveVectorSpaceString(RoutingResult routing) {
        if (routing == null) {
            return null;
        }
        if (routing.requiresFanOut()) {
            return String.join(",", routing.getCandidateSpaces());
        }

        // Provider-agnostic robustness: some routers return FAN_OUT strategy with a single candidate when fan-out is
        // configured to max 1 space. Treat that single candidate as a resolved vectorSpace rather than forcing
        // clarification due to routing.getVectorSpace() being null.
        if (routing.getStrategy() == com.ai.infrastructure.intent.vectorspace.RoutingStrategy.FAN_OUT
            && routing.getCandidateSpaces() != null
            && routing.getCandidateSpaces().size() == 1) {
            String only = routing.getCandidateSpaces().getFirst();
            return hasText(only) ? only.trim() : null;
        }

        return routing.getVectorSpace();
    }

    private OrchestrationResult buildClarificationResult(List<String> candidates,
                                                         PipelineContext context,
                                                         List<Map<String, Object>> routingEvents) {
        List<String> safeCandidates = candidates != null ? candidates : List.of();
        String message = safeCandidates.isEmpty()
            ? MSG_CLARIFICATION_PREFIX
            : MSG_CLARIFICATION_PREFIX + " Options: " + String.join(", ", safeCandidates);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("candidateVectorSpaces", safeCandidates);

        Map<String, Object> metadata = new LinkedHashMap<>();
        if (context != null && context.getMetadata() != null && !context.getMetadata().isEmpty()) {
            metadata.putAll(context.getMetadata());
        }
        if (routingEvents != null && !routingEvents.isEmpty()) {
            metadata.put(METADATA_KEY_ROUTING, Collections.unmodifiableList(new ArrayList<>(routingEvents)));
        }

        return OrchestrationResult.builder()
            .type(OrchestrationResultType.CLARIFICATION_REQUIRED)
            .success(false)
            .message(message)
            .data(Collections.unmodifiableMap(data))
            .metadata(Collections.unmodifiableMap(metadata))
            .build();
    }

    private Map<String, Object> toRoutingEvent(int intentIndex, RoutingResult routing) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("intentIndex", intentIndex);
        if (routing == null) {
            event.put("success", false);
            event.put("strategy", null);
            event.put("confidence", 0.0d);
            event.put("rationale", "Router returned null result");
            return Collections.unmodifiableMap(event);
        }
        event.put("success", routing.isSuccess());
        event.put("strategy", routing.getStrategy() != null ? routing.getStrategy().name() : null);
        event.put("confidence", routing.getConfidence());
        event.put("rationale", routing.getRationale());
        event.put("vectorSpace", routing.getVectorSpace());
        event.put("candidateSpaces", routing.getCandidateSpaces());
        return Collections.unmodifiableMap(event);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
