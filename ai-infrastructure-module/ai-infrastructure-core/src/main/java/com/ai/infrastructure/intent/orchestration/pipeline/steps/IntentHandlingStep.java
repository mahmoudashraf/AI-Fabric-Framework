package com.ai.infrastructure.intent.orchestration.pipeline.steps;

import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.dto.NextStepRecommendation;
import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.ai.infrastructure.intent.action.ActionHandler;
import com.ai.infrastructure.intent.action.ActionHandlerRegistry;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationResultType;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineStep;
import com.ai.infrastructure.spi.ContentRetriever;
import com.ai.infrastructure.spi.ContentRetriever.RetrievalResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Pipeline step that handles the extracted intents (ACTION, INFORMATION, etc.).
 * 
 * <p>This step routes intents to appropriate handlers based on intent type:</p>
 * <ul>
 *   <li>{@code ACTION} - Executes via registered action handlers</li>
 *   <li>{@code INFORMATION} - Performs retrieval via ContentRetriever</li>
 *   <li>{@code OUT_OF_SCOPE} - Returns guidance message</li>
 *   <li>{@code COMPOUND} - Processes multiple intents</li>
 * </ul>
 * 
 * <p><strong>Order:</strong> 60 (after intent extraction)</p>
 * 
 * <p><strong>RAG Dependency:</strong> ContentRetriever is optional. If not available,
 * INFORMATION intents will return a message indicating retrieval is unavailable.</p>
 * 
 * <p><strong>Security:</strong> Actions are blocked for anonymous users.
 * Action handlers must implement {@link ActionHandler#validateActionAllowed}.</p>
 * 
 * @see ActionHandlerRegistry
 * @see ContentRetriever
 * @see PipelineStep
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntentHandlingStep implements PipelineStep {
    
    // =========================================================================
    // Constants
    // =========================================================================
    
    private static final String STEP_NAME = "IntentHandling";
    private static final int STEP_ORDER = 60;
    
    // Retrieval defaults
    private static final double DEFAULT_RETRIEVAL_THRESHOLD = 0.6;
    private static final int DEFAULT_RETRIEVAL_LIMIT = 5;
    
    // Data keys
    private static final String DATA_KEY_ACTION = "action";
    private static final String DATA_KEY_METADATA = "metadata";
    private static final String DATA_KEY_ACTION_RESULT = "actionResult";
    private static final String DATA_KEY_CONFIRMATION_MESSAGE = "confirmationMessage";
    private static final String DATA_KEY_ANSWER = "answer";
    private static final String DATA_KEY_DOCUMENTS = "documents";
    private static final String DATA_KEY_RETRIEVAL_RESULT = "retrievalResult";
    private static final String DATA_KEY_REQUIRES_GENERATION = "requiresGeneration";
    private static final String DATA_KEY_DETAILS = "details";
    private static final String DATA_KEY_RESULTS = "results";
    private static final String DATA_KEY_CONTEXT = "context";
    
    // Metadata keys
    private static final String METADATA_KEY_SOURCE = "source";
    private static final String METADATA_KEY_USER_ID = "userId";
    private static final String METADATA_KEY_SESSION_ID = "sessionId";
    private static final String METADATA_KEY_AUTHENTICATED = "authenticated";
    private static final String METADATA_KEY_OPTIMIZED_QUERY = "optimizedQuery";
    
    // Metadata values
    private static final String METADATA_VALUE_ORCHESTRATOR = "orchestrator";
    
    // Error/Message constants
    private static final String ERROR_MSG_UNKNOWN_INTENT = "Unknown intent type: ";
    private static final String ERROR_MSG_MISSING_ACTION_NAME = "Intent is missing an action name.";
    private static final String ERROR_MSG_NO_HANDLER = "No action handler registered for action '";
    private static final String ERROR_MSG_ACTION_NOT_PERMITTED_ANON = "Action not permitted for anonymous users.";
    private static final String ERROR_MSG_ACTION_NOT_PERMITTED_USER = "Action not permitted for this user.";
    private static final String ERROR_MSG_COMPOUND_MISSING = "Compound intent payload is missing component intents.";
    private static final String ERROR_MSG_COMPOUND_EMPTY = "Compound intent payload did not include any child intents.";
    private static final String MSG_SEARCH_COMPLETED = "Search completed.";
    private static final String MSG_RETRIEVAL_UNAVAILABLE = "Retrieval service is not available.";
    private static final String MSG_OUT_OF_SCOPE = "I'm not able to help with that request. Please contact support for further assistance.";
    private static final String MSG_ALL_PROCESSED = "All intents processed successfully.";
    private static final String MSG_SOME_FAILED = "Some intents failed. See results for details.";
    
    // Intent params key
    private static final String PARAM_KEY_INTENTS = "intents";
    
    // =========================================================================
    // Dependencies
    // =========================================================================
    
    private final ActionHandlerRegistry actionHandlerRegistry;
    
    /**
     * Optional ContentRetriever - RAG module may not be present.
     * Core module does NOT depend on RAG module.
     */
    @Autowired(required = false)
    private ContentRetriever contentRetriever;
    
    // =========================================================================
    // PipelineStep Implementation
    // =========================================================================
    
    /**
     * {@inheritDoc}
     */
    @Override
    public String getStepName() {
        return STEP_NAME;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public int getOrder() {
        return STEP_ORDER;
    }
    
    /**
     * Handle the extracted intents.
     * 
     * <p>Routes single or compound intents to appropriate handlers and
     * builds the orchestration result.</p>
     * 
     * @param context the current pipeline context with intent response
     * @return updated context with intent result
     */
    @Override
    public PipelineContext process(PipelineContext context) {
        log.debug("Handling intent for request {}", context.getRequestId());
        
        MultiIntentResponse intentResponse = context.getIntentResponse();
        OrchestrationContext orchContext = context.getOrchestrationContext();
        
        OrchestrationResult result;
        if (intentResponse.isCompound() || intentResponse.getIntents().size() > 1) {
            result = handleCompoundIntents(intentResponse, orchContext);
        } else {
            result = handleSingleIntent(intentResponse.getIntents().getFirst(), orchContext);
        }
        
        if (result == null) {
            log.error("Intent handling produced null result for request {}", context.getRequestId());
            result = OrchestrationResult.error("Internal error: intent handling failed");
        }
        
        return context.toBuilder()
            .intentResult(result)
            .build();
    }
    
    // =========================================================================
    // Intent Handling Methods
    // =========================================================================
    
    private OrchestrationResult handleSingleIntent(Intent intent, OrchestrationContext context) {
        return switch (intent.getType()) {
            case ACTION -> handleAction(intent, context);
            case INFORMATION -> handleInformation(intent, context);
            case OUT_OF_SCOPE -> handleOutOfScope(intent);
            case COMPOUND -> handleSyntheticCompound(intent, context);
            default -> OrchestrationResult.error(ERROR_MSG_UNKNOWN_INTENT + intent.getType());
        };
    }
    
    private OrchestrationResult handleAction(Intent intent, OrchestrationContext context) {
        if (context.isAnonymous()) {
            return OrchestrationResult.builder()
                .type(OrchestrationResultType.ACTION_DENIED)
                .success(false)
                .message(ERROR_MSG_ACTION_NOT_PERMITTED_ANON)
                .nextSteps(extractNextSteps(intent))
                .build();
        }
        
        String actionName = StringUtils.hasText(intent.getAction()) ? intent.getAction() : intent.getIntent();
        if (!StringUtils.hasText(actionName)) {
            return OrchestrationResult.error(ERROR_MSG_MISSING_ACTION_NAME);
        }
        
        Optional<ActionHandler> maybeHandler = actionHandlerRegistry.findHandler(actionName);
        if (maybeHandler.isEmpty()) {
            return OrchestrationResult.error(ERROR_MSG_NO_HANDLER + actionName + "'");
        }
        
        ActionHandler handler = maybeHandler.get();
        Map<String, Object> params = intent.getActionParams();
        String identifier = context.getIdentifier();
        
        if (!handler.validateActionAllowed(identifier)) {
            AIActionMetaData metadata = getMetadataForAction(actionName);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put(DATA_KEY_ACTION, actionName);
            if (metadata != null) {
                data.put(DATA_KEY_METADATA, metadata);
            }
            return OrchestrationResult.builder()
                .type(OrchestrationResultType.ACTION_DENIED)
                .success(false)
                .message(ERROR_MSG_ACTION_NOT_PERMITTED_USER)
                .data(Collections.unmodifiableMap(data))
                .nextSteps(extractNextSteps(intent))
                .build();
        }
        
        String confirmationMessage = handler.getConfirmationMessage(params);
        try {
            ActionResult actionResult = handler.executeAction(params, identifier);
            boolean success = actionResult != null && actionResult.isSuccess();
            
            Map<String, Object> data = new LinkedHashMap<>();
            data.put(DATA_KEY_ACTION, actionName);
            data.put(DATA_KEY_CONFIRMATION_MESSAGE, confirmationMessage);
            data.put(DATA_KEY_METADATA, getMetadataForAction(actionName));
            if (actionResult != null) {
                data.put(DATA_KEY_ACTION_RESULT, actionResult);
            }
            
            return OrchestrationResult.builder()
                .type(OrchestrationResultType.ACTION_EXECUTED)
                .success(success)
                .message(actionResult != null ? actionResult.getMessage() : null)
                .data(Collections.unmodifiableMap(data))
                .nextSteps(extractNextSteps(intent))
                .build();
        } catch (Exception ex) {
            log.error("Action handler {} threw exception executing '{}': {}", 
                handler.getClass().getName(), actionName, ex.getMessage(), ex);
            ActionResult errorResult = handler.handleError(ex, identifier);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put(DATA_KEY_ACTION, actionName);
            data.put(DATA_KEY_METADATA, getMetadataForAction(actionName));
            if (errorResult != null) {
                data.put(DATA_KEY_ACTION_RESULT, errorResult);
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
    
    private OrchestrationResult handleInformation(Intent intent, OrchestrationContext context) {
        // Check if retrieval is available (RAG module present)
        if (contentRetriever == null || !contentRetriever.isAvailable()) {
            log.warn("ContentRetriever not available for INFORMATION intent");
            return OrchestrationResult.builder()
                .type(OrchestrationResultType.INFORMATION_PROVIDED)
                .success(false)
                .message(MSG_RETRIEVAL_UNAVAILABLE)
                .nextSteps(extractNextSteps(intent))
                .build();
        }
        
        boolean needsGeneration = intent.requiresGenerationOrDefault(false);
        String optimizedQuery = StringUtils.hasText(intent.getOptimizedQuery()) ? intent.getOptimizedQuery() : null;
        String query = StringUtils.hasText(optimizedQuery) ? optimizedQuery : intent.getIntentOrAction();
        
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(METADATA_KEY_SOURCE, METADATA_VALUE_ORCHESTRATOR);
        metadata.put(METADATA_KEY_USER_ID, context.getIdentifier());
        metadata.put(METADATA_KEY_SESSION_ID, context.getSessionId());
        metadata.put(METADATA_KEY_AUTHENTICATED, context.isAuthenticated());
        metadata.put(DATA_KEY_REQUIRES_GENERATION, needsGeneration);
        if (optimizedQuery != null) {
            metadata.put(METADATA_KEY_OPTIMIZED_QUERY, optimizedQuery);
        }
        
        // Use ContentRetriever (generic interface from core)
        RetrievalResult retrievalResult = contentRetriever.retrieve(
            query,
            intent.getVectorSpace(),
            DEFAULT_RETRIEVAL_LIMIT,
            DEFAULT_RETRIEVAL_THRESHOLD,
            Collections.unmodifiableMap(metadata)
        );
        
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(DATA_KEY_CONTEXT, retrievalResult.buildContext());
        data.put(DATA_KEY_DOCUMENTS, retrievalResult.documents());
        data.put(DATA_KEY_RETRIEVAL_RESULT, retrievalResult);
        data.put(DATA_KEY_REQUIRES_GENERATION, needsGeneration);
        
        // Get extended result (e.g., RAGResponse) if available via SPI
        try {
            Object extendedResult = contentRetriever.getExtendedResult(
                query,
                intent.getVectorSpace(),
                DEFAULT_RETRIEVAL_LIMIT,
                DEFAULT_RETRIEVAL_THRESHOLD,
                Collections.unmodifiableMap(metadata)
            );
            if (extendedResult != null) {
                data.put("ragResponse", extendedResult);
                log.debug("Added ragResponse to orchestration result data (ContentRetriever: {})", 
                    contentRetriever.getClass().getSimpleName());
            } else {
                log.debug("getExtendedResult returned null (ContentRetriever: {})", 
                    contentRetriever.getClass().getSimpleName());
            }
        } catch (Exception e) {
            log.warn("Failed to get extended result from ContentRetriever ({}): {}", 
                contentRetriever.getClass().getSimpleName(), e.getMessage(), e);
        }
        
        String message = retrievalResult.success() ? MSG_SEARCH_COMPLETED : retrievalResult.errorMessage();
        
        return OrchestrationResult.builder()
            .type(OrchestrationResultType.INFORMATION_PROVIDED)
            .success(retrievalResult.success())
            .message(message)
            .data(Collections.unmodifiableMap(data))
            .nextSteps(extractNextSteps(intent))
            .build();
    }
    
    private OrchestrationResult handleOutOfScope(Intent intent) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (!CollectionUtils.isEmpty(intent.getActionParams())) {
            data.put(DATA_KEY_DETAILS, intent.getActionParams());
        }
        return OrchestrationResult.builder()
            .type(OrchestrationResultType.OUT_OF_SCOPE)
            .success(true)
            .message(MSG_OUT_OF_SCOPE)
            .data(Collections.unmodifiableMap(data))
            .nextSteps(extractNextSteps(intent))
            .build();
    }
    
    private OrchestrationResult handleSyntheticCompound(Intent intent, OrchestrationContext context) {
        if (CollectionUtils.isEmpty(intent.getActionParams())) {
            return OrchestrationResult.error(ERROR_MSG_COMPOUND_MISSING);
        }
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawChildren = (List<Map<String, Object>>) intent.getActionParams().get(PARAM_KEY_INTENTS);
        if (CollectionUtils.isEmpty(rawChildren)) {
            return OrchestrationResult.error(ERROR_MSG_COMPOUND_EMPTY);
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
        return handleCompoundIntents(syntheticResponse, context);
    }
    
    private OrchestrationResult handleCompoundIntents(MultiIntentResponse response, OrchestrationContext context) {
        List<OrchestrationResult> childResults = new ArrayList<>();
        List<NextStepRecommendation> nextSteps = new ArrayList<>();
        
        for (Intent intent : response.getIntents()) {
            OrchestrationResult child = handleSingleIntent(intent, context);
            if (child == null) {
                log.error("handleSingleIntent returned null for intent type: {}", 
                    intent != null ? intent.getType() : "NULL_INTENT");
                continue;
            }
            childResults.add(child);
            nextSteps.addAll(child.getNextSteps());
        }
        
        boolean success = childResults.stream().allMatch(OrchestrationResult::isSuccess);
        Map<String, Object> data = Map.of(DATA_KEY_RESULTS, childResults);
        
        return OrchestrationResult.builder()
            .type(OrchestrationResultType.COMPOUND_HANDLED)
            .success(success)
            .message(success ? MSG_ALL_PROCESSED : MSG_SOME_FAILED)
            .children(Collections.unmodifiableList(childResults))
            .nextSteps(Collections.unmodifiableList(nextSteps))
            .data(data)
            .build();
    }
    
    // =========================================================================
    // Helper Methods
    // =========================================================================
    
    private List<NextStepRecommendation> extractNextSteps(Intent intent) {
        if (intent.getNextStepRecommended() == null) {
            return List.of();
        }
        return List.of(intent.getNextStepRecommended());
    }
    
    private AIActionMetaData getMetadataForAction(String actionName) {
        try {
            Optional<AIActionMetaData> optional = actionHandlerRegistry.findMetadata(actionName);
            return optional != null ? optional.orElse(null) : null;
        } catch (Exception ex) {
            log.debug("Unable to resolve metadata for action {}: {}", actionName, ex.getMessage());
            return null;
        }
    }
}
