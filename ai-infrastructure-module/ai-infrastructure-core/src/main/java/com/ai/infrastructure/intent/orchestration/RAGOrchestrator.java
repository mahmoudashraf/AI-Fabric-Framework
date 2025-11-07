package com.ai.infrastructure.intent.orchestration;

import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.dto.NextStepRecommendation;
import com.ai.infrastructure.dto.RAGRequest;
import com.ai.infrastructure.dto.RAGResponse;
import com.ai.infrastructure.intent.IntentQueryExtractor;
import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.ai.infrastructure.intent.action.ActionHandler;
import com.ai.infrastructure.intent.action.ActionHandlerRegistry;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.rag.RAGService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RAGOrchestrator {

    private static final double DEFAULT_RAG_THRESHOLD = 0.6;
    private static final int DEFAULT_RAG_LIMIT = 5;

    private final IntentQueryExtractor intentQueryExtractor;
    private final ActionHandlerRegistry actionHandlerRegistry;
    private final RAGService ragService;

    public OrchestrationResult orchestrate(String query, String userId) {
        MultiIntentResponse multiIntentResponse = intentQueryExtractor.extract(query, userId);

        if (!multiIntentResponse.hasIntents()) {
            log.warn("No intents extracted for query '{}'", query);
            return OrchestrationResult.error("Unable to determine user intent.");
        }

        OrchestrationResult result;
        if (multiIntentResponse.isCompound() || multiIntentResponse.getIntents().size() > 1) {
            result = handleCompoundIntents(multiIntentResponse, userId);
        } else {
            result = handleSingleIntent(multiIntentResponse.getIntents().getFirst(), userId);
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("intentsCount", multiIntentResponse.getIntents().size());
        metadata.put("compound", multiIntentResponse.isCompound());
        if (!CollectionUtils.isEmpty(multiIntentResponse.getMetadata())) {
            metadata.put("intentMetadata", multiIntentResponse.getMetadata());
        }
        result.setMetadata(Collections.unmodifiableMap(metadata));

        return result;
    }

    private OrchestrationResult handleSingleIntent(Intent intent, String userId) {
        return switch (intent.getType()) {
            case ACTION -> handleAction(intent, userId);
            case INFORMATION -> handleInformation(intent, userId);
            case OUT_OF_SCOPE -> handleOutOfScope(intent);
            case COMPOUND -> handleSyntheticCompound(intent, userId);
            default -> OrchestrationResult.error("Unknown intent type: " + intent.getType());
        };
    }

    private OrchestrationResult handleAction(Intent intent, String userId) {
        String actionName = StringUtils.hasText(intent.getAction()) ? intent.getAction() : intent.getIntent();
        if (!StringUtils.hasText(actionName)) {
            return OrchestrationResult.error("Intent is missing an action name.");
        }

        Optional<ActionHandler> maybeHandler = actionHandlerRegistry.findHandler(actionName);
        if (maybeHandler.isEmpty()) {
            return OrchestrationResult.error("No action handler registered for action '" + actionName + "'");
        }

        ActionHandler handler = maybeHandler.get();
        Map<String, Object> params = intent.getActionParams();

        if (!handler.validateActionAllowed(userId)) {
            AIActionMetaData metadata = metadataForAction(actionName);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("action", actionName);
            if (metadata != null) {
                data.put("metadata", metadata);
            }
            return OrchestrationResult.builder()
                .type(OrchestrationResultType.ACTION_DENIED)
                .success(false)
                .message("Action not permitted for this user.")
                .data(Collections.unmodifiableMap(data))
                .nextSteps(extractNextSteps(intent))
                .build();
        }

        String confirmationMessage = handler.getConfirmationMessage(params);
        try {
            ActionResult actionResult = handler.executeAction(params, userId);
            boolean success = actionResult != null && actionResult.isSuccess();

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("action", actionName);
            data.put("confirmationMessage", confirmationMessage);
            data.put("metadata", metadataForAction(actionName));
            if (actionResult != null) {
                data.put("actionResult", actionResult);
            }

            return OrchestrationResult.builder()
                .type(OrchestrationResultType.ACTION_EXECUTED)
                .success(success)
                .message(actionResult != null ? actionResult.getMessage() : null)
                .data(Collections.unmodifiableMap(data))
                .nextSteps(extractNextSteps(intent))
                .build();
        } catch (Exception ex) {
            log.error("Action handler {} threw an exception executing action '{}'", handler.getClass().getName(), actionName, ex);
            ActionResult errorResult = handler.handleError(ex, userId);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("action", actionName);
            data.put("metadata", metadataForAction(actionName));
            if (errorResult != null) {
                data.put("actionResult", errorResult);
            }
            return OrchestrationResult.builder()
                .type(OrchestrationResultType.ERROR)
                .success(false)
                .message(errorResult != null ? errorResult.getMessage() : ex.getMessage())
                .data(Collections.unmodifiableMap(data))
                .nextSteps(extractNextSteps(intent))
                .build();
        }
    }

    private OrchestrationResult handleInformation(Intent intent, String userId) {
        String query = StringUtils.hasText(intent.getIntent()) ? intent.getIntent() : intent.getIntentOrAction();
        RAGRequest ragRequest = RAGRequest.builder()
            .query(query)
            .entityType(intent.getVectorSpace())
            .limit(DEFAULT_RAG_LIMIT)
            .threshold(DEFAULT_RAG_THRESHOLD)
            .metadata(Map.of(
                "source", "orchestrator",
                "userId", userId
            ))
            .build();

        RAGResponse ragResponse = ragService.performRag(ragRequest);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("answer", ragResponse.getResponse());
        data.put("documents", ragResponse.getDocuments());
        data.put("ragResponse", ragResponse);

        return OrchestrationResult.builder()
            .type(OrchestrationResultType.INFORMATION_PROVIDED)
            .success(true)
            .message(ragResponse.getResponse())
            .data(Collections.unmodifiableMap(data))
            .nextSteps(extractNextSteps(intent))
            .build();
    }

    private OrchestrationResult handleOutOfScope(Intent intent) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (!CollectionUtils.isEmpty(intent.getActionParams())) {
            data.put("details", intent.getActionParams());
        }
        return OrchestrationResult.builder()
            .type(OrchestrationResultType.OUT_OF_SCOPE)
            .success(true)
            .message("I'm not able to help with that request. Please contact support for further assistance.")
            .data(Collections.unmodifiableMap(data))
            .nextSteps(extractNextSteps(intent))
            .build();
    }

    private OrchestrationResult handleSyntheticCompound(Intent intent, String userId) {
        if (CollectionUtils.isEmpty(intent.getActionParams())) {
            return OrchestrationResult.error("Compound intent payload is missing component intents.");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawChildren = (List<Map<String, Object>>) intent.getActionParams().get("intents");
        if (CollectionUtils.isEmpty(rawChildren)) {
            return OrchestrationResult.error("Compound intent payload did not include any child intents.");
        }

        List<Intent> children = rawChildren.stream()
            .map(map -> Intent.builder()
                .type(intent.getType())
                .intent((String) map.get("intent"))
                .action((String) map.get("action"))
                .confidence(map.get("confidence") instanceof Number number ? number.doubleValue() : null)
                .actionParams(map instanceof Map ? (Map<String, Object>) map.get("actionParams") : Map.of())
                .build())
            .collect(Collectors.toList());

        MultiIntentResponse syntheticResponse = MultiIntentResponse.builder()
            .intents(children)
            .compound(true)
            .build();
        return handleCompoundIntents(syntheticResponse, userId);
    }

    private OrchestrationResult handleCompoundIntents(MultiIntentResponse response, String userId) {
        List<OrchestrationResult> childResults = new ArrayList<>();
        List<NextStepRecommendation> nextSteps = new ArrayList<>();

        for (Intent intent : response.getIntents()) {
            OrchestrationResult child = handleSingleIntent(intent, userId);
            childResults.add(child);
            nextSteps.addAll(child.getNextSteps());
        }

        boolean success = childResults.stream().allMatch(OrchestrationResult::isSuccess);
        Map<String, Object> data = Map.of(
            "results", childResults
        );

        return OrchestrationResult.builder()
            .type(OrchestrationResultType.COMPOUND_HANDLED)
            .success(success)
            .message(success ? "All intents processed successfully." : "Some intents failed. See results for details.")
            .children(Collections.unmodifiableList(childResults))
            .nextSteps(Collections.unmodifiableList(nextSteps))
            .data(data)
            .build();
    }

    private List<NextStepRecommendation> extractNextSteps(Intent intent) {
        if (intent.getNextStepRecommended() == null) {
            return List.of();
        }
        return List.of(intent.getNextStepRecommended());
    }

    private AIActionMetaData metadataForAction(String actionName) {
        try {
            Optional<AIActionMetaData> optional = actionHandlerRegistry.findMetadata(actionName);
            return optional != null ? optional.orElse(null) : null;
        } catch (Exception ex) {
            log.debug("Unable to resolve metadata for action {}: {}", actionName, ex.getMessage());
            return null;
        }
    }
}
