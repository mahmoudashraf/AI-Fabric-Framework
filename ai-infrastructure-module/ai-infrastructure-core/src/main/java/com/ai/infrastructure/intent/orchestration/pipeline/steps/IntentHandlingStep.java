package com.ai.infrastructure.intent.orchestration.pipeline.steps;

import com.ai.infrastructure.config.AIServiceConfig;
import com.ai.infrastructure.config.PostActionGenerationProperties;
import com.ai.infrastructure.config.RelationshipQueryPostActionGenerationProperties;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AdvancedRAGRequest;
import com.ai.infrastructure.dto.AdvancedRAGResponse;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.dto.NextStepRecommendation;
import com.ai.infrastructure.dto.RAGRequest;
import com.ai.infrastructure.dto.RAGResponse;
import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.ai.infrastructure.intent.action.AIActionHandler;
import com.ai.infrastructure.intent.action.AIActionRegistry;
import com.ai.infrastructure.intent.action.ActionAccessMode;
import com.ai.infrastructure.intent.action.ActionContext;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.intent.action.ActionResultContracts;
import com.ai.infrastructure.intent.action.PendingAction;
import com.ai.infrastructure.intent.action.PendingActionStore;
import com.ai.infrastructure.intent.actiondraft.ActionDraft;
import com.ai.infrastructure.intent.actiondraft.ActionDraftStore;
import com.ai.infrastructure.intent.KnowledgeBaseOverview;
import com.ai.infrastructure.intent.KnowledgeBaseOverviewService;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.OrchestrationContextMetadataKeys;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationResultType;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineStep;
import com.ai.infrastructure.intent.orchestration.policy.OrchestrationPolicy;
import com.ai.infrastructure.intent.orchestration.targets.ResolvedTarget;
import com.ai.infrastructure.config.OrchestrationProperties;
import com.ai.infrastructure.config.VectorSpaceRoutingProperties;
import com.ai.infrastructure.core.LlmPurpose;
import com.ai.infrastructure.intent.vectorspace.RankBasedMerger;
import com.ai.infrastructure.prompt.PromptRenderer;
import com.ai.infrastructure.prompt.PromptTemplateResolver;
import com.ai.infrastructure.spi.AdvancedRAGProvider;
import com.ai.infrastructure.spi.RAGProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.time.Instant;

/**
 * Pipeline step that handles the extracted intents (ACTION, INFORMATION, etc.).
 * 
 * <p>This step routes intents to appropriate handlers based on intent type:</p>
 * <ul>
 *   <li>{@code ACTION} - Executes via registered action handlers</li>
 *   <li>{@code INFORMATION} - Performs RAG search/generation</li>
 *   <li>{@code OUT_OF_SCOPE} - Returns guidance message</li>
 *   <li>{@code COMPOUND} - Processes multiple intents</li>
 * </ul>
 * 
 * <p><strong>Order:</strong> 60 (after intent extraction)</p>
 * 
 * <p><strong>Security:</strong> Actions are blocked for anonymous users.
 * Action handlers may declare {@code @ActionAllowed} for additional access control.</p>
 * 
 * @see com.ai.infrastructure.intent.action.AIActionRegistry
 * @see RAGProvider
 * @see PipelineStep
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntentHandlingStep implements PipelineStep {
    private static final String ACTION_RELATIONSHIP_QUERY = "relationship_query";
    
    // =========================================================================
    // Constants
    // =========================================================================
    
    private static final String STEP_NAME = "IntentHandling";
    private static final int STEP_ORDER = 60;
    
    // RAG defaults
    private static final double DEFAULT_RAG_THRESHOLD = 0.6;
    private static final int DEFAULT_RAG_LIMIT = 5;
    private static final double DEFAULT_FAN_OUT_RAG_THRESHOLD = 0.3d;
    
    // Data keys
    private static final String DATA_KEY_ACTION = "action";
    private static final String DATA_KEY_METADATA = "metadata";
    private static final String DATA_KEY_ACTION_RESULT = "actionResult";
    private static final String DATA_KEY_CONFIRMATION_MESSAGE = "confirmationMessage";
    private static final String DATA_KEY_ANSWER = "answer";
    private static final String DATA_KEY_DOCUMENTS = "documents";
    private static final String DATA_KEY_RAG_RESPONSE = "ragResponse";
    private static final String DATA_KEY_REQUIRES_GENERATION = "requiresGeneration";
    private static final String DATA_KEY_DETAILS = "details";
    private static final String DATA_KEY_RESULTS = "results";
    private static final String DATA_KEY_CANDIDATE_VECTOR_SPACES = "candidateVectorSpaces";
    private static final String DATA_KEY_ROUTING_STRATEGY = "vectorSpaceRoutingStrategy";

    // Advanced RAG data keys
    private static final String DATA_KEY_EXPANDED_QUERIES = "expandedQueries";
    private static final String DATA_KEY_CONFIDENCE_SCORE = "confidenceScore";
    private static final String DATA_KEY_RERANKING_STRATEGY = "rerankingStrategy";
    private static final String DATA_KEY_CONTEXT_OPTIMIZATION_LEVEL = "contextOptimizationLevel";
    
    // Metadata keys
    private static final String METADATA_KEY_SOURCE = "source";
    private static final String METADATA_KEY_USER_ID = "userId";
    private static final String METADATA_KEY_SESSION_ID = "sessionId";
    private static final String METADATA_KEY_AUTHENTICATED = "authenticated";
    private static final String METADATA_KEY_OPTIMIZED_QUERY = "optimizedQuery";
    private static final String METADATA_KEY_RETRIEVAL_QUERY_HINT_APPLIED = "retrievalQueryHintApplied";
    private static final String INTENT_METADATA_KEY_RETRIEVAL_QUERY_HINT = "retrievalQueryHint";
    private static final int MAX_RETRIEVAL_QUERY_HINT_LENGTH = 200;
    
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
    private static final String MSG_OUT_OF_SCOPE =
        "Sorry — I can’t help with that request. If you rephrase it into a task related to your indexed knowledge base (search/summarize/explain) or an available action, I’ll do my best to help.";
    private static final String MSG_ALL_PROCESSED = "All intents processed successfully.";
    private static final String MSG_SOME_FAILED = "Some intents failed. See results for details.";
    
    // Provider-agnostic error codes (for deterministic client handling)
    private static final String ERROR_CODE_ACTION_NOT_FOUND = "ACTION_NOT_FOUND";

    private static final String ERROR_MSG_RAG_NULL_RESPONSE = "RAG retrieval returned null response.";

    private static final String RAG_NO_CONTEXT_MESSAGE = "No relevant context found.";
    private static final String RAG_NO_INFO_MESSAGE_PREFIX = "I don't have enough information to answer your question: ";

    private static final String TEMPLATE_FAMILY_RAG_GENERATION = "rag/generation";
    private static final String TEMPLATE_RAG_ANSWER = "answer";
    private static final String TEMPLATE_RAG_NO_CONTEXT = "no-context";

    private static final String TEMPLATE_FAMILY_POST_ACTION_GENERATION = "orchestration/post-action-generation";
    private static final String TEMPLATE_POST_ACTION_SYSTEM = "system";
    private static final String TEMPLATE_POST_ACTION_USER_GENERIC = "user-generic";
    private static final String TEMPLATE_POST_ACTION_USER_RELATIONSHIP_QUERY = "user-relationship-query";

    private static final String PLACEHOLDER_QUERY = "query";
    private static final String PLACEHOLDER_CONTEXT = "context";
    private static final String PLACEHOLDER_ACTION_NAME = "action_name";
    private static final String PLACEHOLDER_INSTRUCTION = "instruction";
    private static final String PLACEHOLDER_FACTS = "facts";
    private static final String PLACEHOLDER_RELATIONAL_QUERY = "relational_query";

    private static final String DATA_KEY_GENERATION_ERROR = "generationError";

    private static final String CONTEXT_METADATA_KEY_ADVANCED_EXPANSION_LEVEL = "advancedRagExpansionLevel";
    private static final String CONTEXT_METADATA_KEY_ADVANCED_RERANKING_STRATEGY = "advancedRagRerankingStrategy";
    private static final String CONTEXT_METADATA_KEY_ADVANCED_CONTEXT_OPTIMIZATION_LEVEL = "advancedRagContextOptimizationLevel";

    private static final int ADVANCED_QUERY_LENGTH_THRESHOLD = 50;
    private static final int ADVANCED_QUERY_WORD_THRESHOLD = 8;

    private static final String DATA_KEY_CONFIRMATION_REQUIRED = "confirmationRequired";
    private static final String DATA_KEY_MISSING_REQUIRED_PARAMETERS = "missingRequiredParameters";
    private static final String DATA_KEY_PROVIDED_PARAMETERS = "providedParameters";
    
    // Intent params key
    private static final String PARAM_KEY_INTENTS = "intents";
    
    // =========================================================================
    // Dependencies
    // =========================================================================
    
    private final AIActionRegistry actionHandlerRegistry;
    private final ObjectProvider<RAGProvider> ragProvider;
    private final AICoreService aiCoreService;
    private final AIServiceConfig aiServiceConfig;
    private final ObjectProvider<AdvancedRAGProvider> advancedRagProvider;
    private final VectorSpaceRoutingProperties vectorSpaceRoutingProperties;
    private final RankBasedMerger rankBasedMerger;
    private final RelationshipQueryPostActionGenerationProperties relationshipQueryPostActionGenerationProperties;
    private final PostActionGenerationProperties postActionGenerationProperties;
    private final ObjectProvider<ObjectMapper> objectMapperProvider;
    private final OrchestrationProperties orchestrationProperties;
    private final ObjectProvider<KnowledgeBaseOverviewService> knowledgeBaseOverviewServiceProvider;
    private final PendingActionStore pendingActionStore;
    private final ActionDraftStore actionDraftStore;
    private final PromptTemplateResolver promptTemplateResolver;
    private final PromptRenderer promptRenderer;
    
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
            result = handleCompoundIntents(intentResponse, orchContext, context);
        } else {
            result = handleSingleIntent(intentResponse.getIntents().getFirst(), orchContext, context);
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
    
    private OrchestrationResult handleSingleIntent(Intent intent, OrchestrationContext context, PipelineContext pipelineContext) {
        return switch (intent.getType()) {
            case ACTION -> handleAction(intent, context, pipelineContext);
            case INFORMATION -> handleInformation(intent, context, pipelineContext);
            case CONFIRMATION_POSITIVE -> handleConfirmationPositive(context, pipelineContext);
            case CONFIRMATION_NEGATIVE -> handleConfirmationNegative(context, pipelineContext);
            case OUT_OF_SCOPE -> handleOutOfScope(intent);
            case COMPOUND -> handleSyntheticCompound(intent, context, pipelineContext);
            default -> OrchestrationResult.error(ERROR_MSG_UNKNOWN_INTENT + intent.getType());
        };
    }
    
    private OrchestrationResult handleAction(Intent intent, OrchestrationContext context, PipelineContext pipelineContext) {
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
        
        Optional<AIActionHandler> maybeHandler = actionHandlerRegistry.findHandler(actionName);
        if (maybeHandler.isEmpty()) {
            // Deterministic contract: missing handler is a canonical ERROR with ACTION_NOT_FOUND.
            String message = ERROR_MSG_NO_HANDLER + actionName + "'";
            Map<String, Object> data = new LinkedHashMap<>();
            data.put(DATA_KEY_ACTION, actionName);
            data.put(DATA_KEY_ACTION_RESULT, ActionResult.builder()
                .success(false)
                .message(message)
                .errorCode(ERROR_CODE_ACTION_NOT_FOUND)
                .build());
            return OrchestrationResult.builder()
                .type(OrchestrationResultType.ERROR)
                .success(false)
                .message(message)
                .errorCode(ERROR_CODE_ACTION_NOT_FOUND)
                .data(Collections.unmodifiableMap(data))
                .nextSteps(extractNextSteps(intent))
                .build();
        }
        
        AIActionHandler handler = maybeHandler.get();
        Map<String, Object> params = intent.getActionParams();
        String identifier = context.getIdentifier();
        ActionContext actionContext = new ActionContext(context, pipelineContext);

        Map<String, Object> effectiveParams = params != null ? new LinkedHashMap<>(params) : new LinkedHashMap<>();
        ResolvedPostActionGeneration postActionRequest = null;

        if (!handler.validateActionAllowed(actionContext)) {
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

        AIActionMetaData meta = getMetadataForAction(actionName);
        mergeResolvedTargetsIntoActionParams(meta, effectiveParams, pipelineContext);
        postActionRequest = resolvePostActionGeneration(actionName, intent, pipelineContext, effectiveParams);

        boolean confirmedThisRequest = pipelineContext != null && pipelineContext.isActionConfirmed(actionName);
        String originalQuery = pipelineContext != null ? pipelineContext.getOriginalQuery() : null;
        String evidenceText = pipelineContext != null ? pipelineContext.getProcessedQuery() : null;
        boolean skipEvidenceCheck = confirmedThisRequest || !context.hasConversation();
        List<String> missingRequired = findMissingRequiredParams(meta, effectiveParams, originalQuery, evidenceText, skipEvidenceCheck);
        if (!missingRequired.isEmpty()) {
            if (context.hasConversation() && actionDraftStore != null) {
                String missingSummary = String.join(", ", missingRequired);
                ActionDraft draft = new ActionDraft(
                    actionName,
                    Collections.unmodifiableMap(new LinkedHashMap<>(effectiveParams)),
                    missingSummary,
                    Instant.now(),
                    Instant.now()
                );
                actionDraftStore.saveDraft(context.getConversationId(), identifier, draft);
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put(DATA_KEY_ACTION, actionName);
            data.put(DATA_KEY_MISSING_REQUIRED_PARAMETERS, List.copyOf(missingRequired));
            data.put(DATA_KEY_PROVIDED_PARAMETERS, Collections.unmodifiableMap(new LinkedHashMap<>(effectiveParams)));
            if (meta != null) {
                data.put(DATA_KEY_METADATA, meta);
            }

            String message = "To proceed, please provide: " + String.join(", ", missingRequired) + ".";
            List<NextStepRecommendation> nextSteps = new ArrayList<>(extractNextSteps(intent));
            nextSteps.add(NextStepRecommendation.builder()
                .intent("provide_missing_action_params")
                .query("Please provide: " + String.join(", ", missingRequired) + ".")
                .rationale("These parameters are required to execute the requested action.")
                .confidence(1.0d)
                .build());
            return OrchestrationResult.builder()
                .type(OrchestrationResultType.CLARIFICATION_REQUIRED)
                .success(false)
                .message(message)
                .data(Collections.unmodifiableMap(data))
                .nextSteps(Collections.unmodifiableList(nextSteps))
                .build();
        }
        
        boolean requiresConfirmation = requiresActionConfirmation(handler);

        // Confirmation message is only meaningful for confirmable actions.
        // For safe actions, expose a deterministic execution indicator (used by tests/UI).
        String confirmationMessage = null;
        if (requiresConfirmation) {
            // Never let confirmation-message formatting crash the pipeline.
            // Some handlers validate required params inside getConfirmationMessage().
            try {
                confirmationMessage = handler.getConfirmationMessage(effectiveParams, actionContext);
            } catch (Exception ex) {
                log.debug("Action handler {} failed to build confirmation message for '{}': {}",
                    handler.getClass().getName(), actionName, ex.getMessage());
            }
        } else {
            confirmationMessage = "Executing " + actionName;
        }

        if (requiresConfirmation && !confirmedThisRequest && context.hasConversation()) {
            if (context.hasConversation() && actionDraftStore != null) {
                actionDraftStore.clearDrafts(context.getConversationId(), identifier);
            }

            PendingAction pending = new PendingAction(
                actionName,
                Collections.unmodifiableMap(new LinkedHashMap<>(effectiveParams)),
                confirmationMessage,
                Instant.now()
            );
            if (pendingActionStore != null) {
                pendingActionStore.pushPendingAction(context.getConversationId(), identifier, pending);
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put(DATA_KEY_ACTION, actionName);
            data.put(DATA_KEY_CONFIRMATION_MESSAGE, confirmationMessage);
            data.put(DATA_KEY_CONFIRMATION_REQUIRED, true);
            data.put(DATA_KEY_METADATA, getMetadataForAction(actionName));

            String message = StringUtils.hasText(confirmationMessage)
                ? confirmationMessage
                : "Please confirm to proceed.";

            return OrchestrationResult.builder()
                .type(OrchestrationResultType.CONFIRMATION_REQUIRED)
                .success(false)
                .message(message)
                .data(Collections.unmodifiableMap(data))
                .nextSteps(extractNextSteps(intent))
                .build();
        }

        try {
            if (context.hasConversation() && actionDraftStore != null) {
                actionDraftStore.clearDrafts(context.getConversationId(), identifier);
            }
            ActionResult actionResult = handler.executeAction(effectiveParams, actionContext);
            boolean success = actionResult != null && actionResult.isSuccess();
            
            Map<String, Object> data = new LinkedHashMap<>();
            data.put(DATA_KEY_ACTION, actionName);
            data.put(DATA_KEY_CONFIRMATION_MESSAGE, confirmationMessage);
            data.put(DATA_KEY_METADATA, getMetadataForAction(actionName));
            if (actionResult != null) {
                data.put(DATA_KEY_ACTION_RESULT, actionResult);
            }

            OrchestrationResult readFallback = maybeFallbackReadActionToRag(intent, meta, actionResult, context, pipelineContext);
            if (readFallback != null) {
                return readFallback;
            }

            String message = actionResult != null ? actionResult.getMessage() : null;
            Map<String, Object> resultData = Collections.unmodifiableMap(data);

            PostActionGenerationOutcome postActionGeneration = maybeGeneratePostActionSummary(
                actionName,
                handler,
                intent,
                actionResult,
                context,
                pipelineContext,
                postActionRequest
            );
            if (postActionGeneration != null) {
                message = postActionGeneration.message();
                Map<String, Object> enriched = new LinkedHashMap<>(data);
                enriched.put("postActionGeneration", postActionGeneration.metadata());
                if (postActionGeneration.summary() != null) {
                    enriched.put("summary", postActionGeneration.summary());
                }
                resultData = Collections.unmodifiableMap(enriched);
            }

            return OrchestrationResult.builder()
                .type(OrchestrationResultType.ACTION_EXECUTED)
                .success(success)
                .message(message)
                .data(resultData)
                .nextSteps(extractNextSteps(intent))
                .build();
        } catch (Exception ex) {
            log.error("Action handler {} threw exception executing '{}': {}", 
                handler.getClass().getName(), actionName, ex.getMessage(), ex);
            ActionResult errorResult = handler.handleError(ex, actionContext);
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

    /**
     * For READ actions, treat the handler as a "helper tool": if it returns an empty successful payload,
     * run RAG (+ generation when enabled) and return that result instead of the action output.
     */
    private OrchestrationResult maybeFallbackReadActionToRag(Intent intent,
                                                            AIActionMetaData meta,
                                                            ActionResult actionResult,
                                                            OrchestrationContext context,
                                                            PipelineContext pipelineContext) {
        if (meta == null || meta.getAccessMode() != ActionAccessMode.READ) {
            return null;
        }
        if (actionResult == null || !actionResult.isSuccess()) {
            return null;
        }
        if (!isEmptyActionResultPayload(actionResult.getData())) {
            return null;
        }
        if (ragProvider == null || ragProvider.getIfAvailable() == null) {
            return null;
        }

        List<String> vectorSpaces = parseVectorSpaces(intent != null ? intent.getVectorSpace() : null);
        if (vectorSpaces.isEmpty()) {
            vectorSpaces = resolveAllVectorSpaces();
        }
        if (vectorSpaces.isEmpty()) {
            return null;
        }

        boolean generationEnabled = aiServiceConfig == null
            || aiServiceConfig.getFeatures() == null
            || Boolean.TRUE.equals(aiServiceConfig.getFeatures().getEnableGeneration());

        Intent infoIntent = new Intent();
        infoIntent.setType(com.ai.infrastructure.dto.IntentType.INFORMATION);
        infoIntent.setRequiresRetrieval(true);
        infoIntent.setRequiresGeneration(generationEnabled);

        String query = intent != null && StringUtils.hasText(intent.getOptimizedQuery())
            ? intent.getOptimizedQuery()
            : (pipelineContext != null ? pipelineContext.getEffectiveQuery() : null);
        if (StringUtils.hasText(query)) {
            infoIntent.setOptimizedQuery(query);
            infoIntent.setIntent(query);
        }
        infoIntent.setVectorSpace(String.join(",", vectorSpaces));

        return handleInformation(infoIntent, context, pipelineContext);
    }

    private boolean isEmptyActionResultPayload(com.ai.infrastructure.intent.action.ActionPayload data) {
        if (data instanceof com.ai.infrastructure.intent.action.ActionListPayload listPayload) {
            return listPayload.isEmpty();
        }
        return false;
    }

    private List<String> extractVectorSpacesUsed(OrchestrationResult ragResult, String fallbackVectorSpace) {
        if (ragResult != null && ragResult.getData() instanceof Map<?, ?> map) {
            Object candidates = map.get(DATA_KEY_CANDIDATE_VECTOR_SPACES);
            if (candidates instanceof List<?> list) {
                List<String> out = new ArrayList<>();
                for (Object item : list) {
                    if (item != null && StringUtils.hasText(item.toString())) {
                        out.add(item.toString());
                    }
                }
                if (!out.isEmpty()) {
                    return Collections.unmodifiableList(out);
                }
            }
        }
        if (StringUtils.hasText(fallbackVectorSpace)) {
            return parseVectorSpaces(fallbackVectorSpace);
        }
        return List.of();
    }

    private Map<String, Object> summarizeRagResult(OrchestrationResult ragResult) {
        if (ragResult == null) {
            return Map.of(
                "type", OrchestrationResultType.ERROR.name(),
                "success", false,
                "message", "RAG fallback returned null result",
                "data", Map.of()
            );
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("type", ragResult.getType() != null ? ragResult.getType().name() : null);
        summary.put("success", ragResult.isSuccess());
        summary.put("message", ragResult.getMessage());
        summary.put("data", ragResult.getData() != null ? ragResult.getData() : Map.of());
        return Collections.unmodifiableMap(summary);
    }

    private String extractAnswer(OrchestrationResult ragResult) {
        if (ragResult == null || ragResult.getType() != OrchestrationResultType.INFORMATION_PROVIDED || !ragResult.isSuccess()) {
            return null;
        }
        if (ragResult.getData() instanceof Map<?, ?> map) {
            Object value = map.get(DATA_KEY_ANSWER);
            if (value != null && StringUtils.hasText(value.toString())) {
                return value.toString();
            }
        }
        return null;
    }

    private boolean requiresActionConfirmation(AIActionHandler handler) {
        if (handler == null) {
            return false;
        }
        return handler.requiresConfirmation();
    }

    private OrchestrationResult handleConfirmationPositive(OrchestrationContext context, PipelineContext pipelineContext) {
        if (context == null || !context.hasConversation() || pendingActionStore == null) {
            return OrchestrationResult.builder()
                .type(OrchestrationResultType.INFORMATION_PROVIDED)
                .success(true)
                .message("There is no pending action to confirm.")
                .build();
        }

        PendingAction pending = pendingActionStore.popPendingAction(context.getConversationId(), context.getIdentifier()).orElse(null);
        if (pending == null) {
            return OrchestrationResult.builder()
                .type(OrchestrationResultType.INFORMATION_PROVIDED)
                .success(true)
                .message("There is no pending action to confirm.")
                .build();
        }
        if (actionDraftStore != null) {
            actionDraftStore.clearDrafts(context.getConversationId(), context.getIdentifier());
        }

        Intent synthetic = Intent.builder()
            .type(com.ai.infrastructure.dto.IntentType.ACTION)
            .action(pending.action())
            .actionParams(pending.actionParams() != null ? pending.actionParams() : Map.of())
            .build();

        PipelineContext marked = pipelineContext != null
            ? pipelineContext.toBuilder()
            .confirmedActions(java.util.Set.of(pending.action()))
            .build()
            : pipelineContext;

        return handleAction(synthetic, context, marked);
    }

    private OrchestrationResult handleConfirmationNegative(OrchestrationContext context, PipelineContext pipelineContext) {
        if (context == null || !context.hasConversation() || pendingActionStore == null) {
            return OrchestrationResult.builder()
                .type(OrchestrationResultType.INFORMATION_PROVIDED)
                .success(true)
                .message("Okay —  All sorted, You do not need to do any further action.")
                .build();
        }
        pendingActionStore.popPendingAction(context.getConversationId(), context.getIdentifier());
        if (actionDraftStore != null) {
            actionDraftStore.clearDrafts(context.getConversationId(), context.getIdentifier());
        }
        return OrchestrationResult.builder()
            .type(OrchestrationResultType.INFORMATION_PROVIDED)
            .success(true)
            .message("Okay —  All sorted, You do not need to do any further action.")
            .build();
    }

    private PostActionGenerationOutcome maybeGeneratePostActionSummary(String actionName,
                                                                      AIActionHandler handler,
                                                                      Intent intent,
                                                                      ActionResult actionResult,
                                                                      OrchestrationContext context,
                                                                      PipelineContext pipelineContext,
                                                                      ResolvedPostActionGeneration request) {
        if (request == null || !request.shouldGenerate()) {
            return null;
        }
        if (actionResult == null || !actionResult.isSuccess()) {
            return null;
        }

        if (!ACTION_RELATIONSHIP_QUERY.equalsIgnoreCase(actionName)) {
            return maybeGenerateGenericPostActionSummary(handler, actionName, request, actionResult, context, pipelineContext);
        }

        Map<String, Object> actionData = coerceToMap(actionResult.getData());
        List<?> documents = actionData != null ? coerceToList(actionData.get(DATA_KEY_DOCUMENTS)) : null;

        int totalResults = coerceToInt(actionData != null ? actionData.get("totalResults") : null,
            documents != null ? documents.size() : 0);

        if (documents == null || documents.isEmpty()) {
            Map<String, Object> metadata = Map.of(
                "used", false,
                "skippedReason", "no_results",
                "returnedResults", 0,
                "totalResults", totalResults
            );
            String message = StringUtils.hasText(actionResult.getMessage()) ? actionResult.getMessage() : "No results found";
            return new PostActionGenerationOutcome(null, message, metadata);
        }

        FactsPayload facts = buildFactsPayload(documents,
            relationshipQueryPostActionGenerationProperties.getMaxItems(),
            relationshipQueryPostActionGenerationProperties.getMaxChars());

        String instruction = request.generationInstructions();
        if (!StringUtils.hasText(instruction)) {
            instruction = "Summarize the results for the user.";
        }
        if (instruction.length() > 500) {
            instruction = instruction.substring(0, 500);
        }

        String relationalQuery = null;
        Map<String, Object> params = intent.getActionParams();
        if (params != null && params.get("query") != null) {
            relationalQuery = params.get("query").toString();
        }

        String systemPrompt = promptRenderer.render(
            promptTemplateResolver.resolve(TEMPLATE_FAMILY_POST_ACTION_GENERATION, TEMPLATE_POST_ACTION_SYSTEM).template(),
            Map.of()
        );

        String userPrompt = buildPostActionUserPrompt(instruction, relationalQuery, facts.payload());

        AIGenerationRequest generationRequest = AIGenerationRequest.builder()
            .entityId("post-action-" + (pipelineContext != null ? pipelineContext.getRequestId() : UUID.randomUUID()))
            .entityType(ACTION_RELATIONSHIP_QUERY)
            .generationType("relationship_query_post_action_generation")
            .systemPrompt(systemPrompt)
            .prompt(userPrompt)
            .temperature(relationshipQueryPostActionGenerationProperties.getTemperature())
            .maxTokens(800)
            .userId(context != null ? context.getIdentifier() : null)
            .build();

        AIGenerationResponse generationResponse;
        try {
            generationResponse = aiCoreService.generateContent(generationRequest, LlmPurpose.GENERATION);
        } catch (Exception ex) {
            Map<String, Object> metadata = Map.of(
                "used", false,
                "skippedReason", "generation_failed",
                "error", ex.getMessage(),
                "includedItems", facts.includedItems(),
                "truncated", facts.truncated()
            );
            String message = StringUtils.hasText(actionResult.getMessage()) ? actionResult.getMessage() : null;
            return new PostActionGenerationOutcome(null, message, metadata);
        }

        String summary = generationResponse != null ? generationResponse.getContent() : null;
        if (!StringUtils.hasText(summary)) {
            Map<String, Object> metadata = Map.of(
                "used", false,
                "skippedReason", "empty_generation_response",
                "includedItems", facts.includedItems(),
                "truncated", facts.truncated()
            );
            return new PostActionGenerationOutcome(null, StringUtils.hasText(actionResult.getMessage()) ? actionResult.getMessage() : null, metadata);
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("used", true);
        metadata.put("purpose", "GENERATION");
        metadata.put("includedItems", facts.includedItems());
        metadata.put("truncated", facts.truncated());
        metadata.put("totalResults", totalResults);
        if (generationResponse != null && StringUtils.hasText(generationResponse.getModel())) {
            metadata.put("model", generationResponse.getModel());
        }

        String message = summary.trim();
        return new PostActionGenerationOutcome(message, message, Collections.unmodifiableMap(metadata));
    }

    private ResolvedPostActionGeneration resolvePostActionGeneration(String actionName,
                                                                     Intent intent,
                                                                     PipelineContext pipelineContext,
                                                                     Map<String, Object> params) {
        boolean isRelationshipQuery = ACTION_RELATIONSHIP_QUERY.equalsIgnoreCase(actionName);
        if (isRelationshipQuery) {
            if (relationshipQueryPostActionGenerationProperties == null || !relationshipQueryPostActionGenerationProperties.isEnabled()) {
                return ResolvedPostActionGeneration.disabled();
            }
        } else {
            if (postActionGenerationProperties == null || !postActionGenerationProperties.isEnabled()) {
                return ResolvedPostActionGeneration.disabled();
            }
        }

        String instructions = null;
        boolean requested = intent != null && Boolean.TRUE.equals(intent.getRequiresGeneration());

        if (intent != null && StringUtils.hasText(intent.getGenerationInstructions())) {
            requested = true;
            instructions = intent.getGenerationInstructions();
        }

        return new ResolvedPostActionGeneration(requested, instructions);
    }

    private PostActionGenerationOutcome maybeGenerateGenericPostActionSummary(AIActionHandler handler,
                                                                             String actionName,
                                                                             ResolvedPostActionGeneration request,
                                                                             ActionResult actionResult,
                                                                             OrchestrationContext context,
                                                                             PipelineContext pipelineContext) {
        if (handler == null || postActionGenerationProperties == null || !postActionGenerationProperties.isEnabled()) {
            return null;
        }

        Optional<Map<String, Object>> factsOpt;
        try {
            factsOpt = handler.buildPostActionLlmFacts(actionResult, new ActionContext(context, pipelineContext));
        } catch (Exception ex) {
            log.warn("Action handler {} failed to build post-action facts for '{}': {}",
                handler.getClass().getName(), actionName, ex.getMessage());
            factsOpt = Optional.empty();
        }

        Map<String, Object> factsMap = factsOpt.orElse(null);
        if (factsMap == null || factsMap.isEmpty()) {
            Map<String, Object> metadata = Map.of(
                "used", false,
                "skippedReason", "handler_opt_out"
            );
            return new PostActionGenerationOutcome(null, actionResult.getMessage(), metadata);
        }

        FactsPayload facts = buildFactsPayload(factsMap, postActionGenerationProperties.getMaxChars());

        String instruction = request != null ? request.generationInstructions() : null;
        if (!StringUtils.hasText(instruction)) {
            instruction = "Summarize the action result for the user.";
        }
        if (instruction.length() > 500) {
            instruction = instruction.substring(0, 500);
        }

        String systemPrompt = promptRenderer.render(
            promptTemplateResolver.resolve(TEMPLATE_FAMILY_POST_ACTION_GENERATION, TEMPLATE_POST_ACTION_SYSTEM).template(),
            Map.of()
        );

        String safeActionName = StringUtils.hasText(actionName) ? actionName.trim() : "(unknown)";
        String safeFacts = facts.payload() != null ? facts.payload() : "";
        String userPrompt = promptRenderer.render(
            promptTemplateResolver.resolve(TEMPLATE_FAMILY_POST_ACTION_GENERATION, TEMPLATE_POST_ACTION_USER_GENERIC).template(),
            Map.of(
                PLACEHOLDER_ACTION_NAME, safeActionName,
                PLACEHOLDER_INSTRUCTION, instruction,
                PLACEHOLDER_FACTS, safeFacts
            )
        );

        AIGenerationRequest generationRequest = AIGenerationRequest.builder()
            .entityId("post-action-" + (pipelineContext != null ? pipelineContext.getRequestId() : UUID.randomUUID()))
            .entityType(actionName)
            .generationType("action_post_action_generation")
            .systemPrompt(systemPrompt)
            .prompt(userPrompt)
            .temperature(postActionGenerationProperties.getTemperature())
            .maxTokens(postActionGenerationProperties.getMaxTokens())
            .userId(context != null ? context.getIdentifier() : null)
            .build();

        AIGenerationResponse generationResponse;
        try {
            generationResponse = aiCoreService.generateContent(generationRequest, LlmPurpose.GENERATION);
        } catch (Exception ex) {
            Map<String, Object> metadata = Map.of(
                "used", false,
                "skippedReason", "generation_failed",
                "error", ex.getMessage(),
                "includedItems", facts.includedItems(),
                "truncated", facts.truncated()
            );
            return new PostActionGenerationOutcome(null, actionResult.getMessage(), metadata);
        }

        String summary = generationResponse != null ? generationResponse.getContent() : null;
        if (!StringUtils.hasText(summary)) {
            Map<String, Object> metadata = Map.of(
                "used", false,
                "skippedReason", "empty_generation_response",
                "includedItems", facts.includedItems(),
                "truncated", facts.truncated()
            );
            return new PostActionGenerationOutcome(null, actionResult.getMessage(), metadata);
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("used", true);
        metadata.put("purpose", "GENERATION");
        metadata.put("action", actionName);
        metadata.put("includedItems", facts.includedItems());
        metadata.put("truncated", facts.truncated());
        if (generationResponse != null && StringUtils.hasText(generationResponse.getModel())) {
            metadata.put("model", generationResponse.getModel());
        }

        String message = summary.trim();
        return new PostActionGenerationOutcome(message, message, Collections.unmodifiableMap(metadata));
    }

    private Map<String, Object> coerceToMap(Object value) {
        if (value instanceof com.ai.infrastructure.intent.action.ActionPayload payload) {
            return payload.toMap();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> {
                if (key != null) {
                    result.put(key.toString(), item);
                }
            });
            return result;
        }
        return null;
    }

    private List<?> coerceToList(Object value) {
        if (value instanceof List<?> list) {
            return list;
        }
        return null;
    }

    private int coerceToInt(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private FactsPayload buildFactsPayload(Map<String, Object> facts, int maxChars) {
        if (facts == null) {
            return new FactsPayload("(no facts)", 0, false);
        }

        String payload;
        try {
            ObjectMapper mapper = objectMapperProvider != null ? objectMapperProvider.getIfAvailable() : null;
            if (mapper != null) {
                payload = mapper.writeValueAsString(facts);
            } else {
                log.debug("No ObjectMapper available; falling back to Map.toString() for post-action facts payload");
                payload = facts.toString();
            }
        } catch (Exception ex) {
            payload = facts.toString();
        }

        boolean truncated = false;
        String normalized = StringUtils.hasText(payload) ? payload.trim() : "";
        if (StringUtils.hasText(normalized) && normalized.length() > maxChars) {
            normalized = normalized.substring(0, maxChars);
            truncated = true;
        }
        if (!StringUtils.hasText(normalized)) {
            normalized = "(no serializable facts)";
        }

        return new FactsPayload(normalized, facts.size(), truncated);
    }

    private FactsPayload buildFactsPayload(List<?> documents, int maxItems, int maxChars) {
        int limit = Math.min(Math.max(1, maxItems), documents.size());
        StringBuilder builder = new StringBuilder(Math.min(maxChars, 2048));

        int included = 0;
        boolean truncated = false;
        for (int i = 0; i < limit; i++) {
            Object doc = documents.get(i);
            String line = formatDocumentFact(i + 1, doc);
            if (!StringUtils.hasText(line)) {
                continue;
            }
            if (builder.length() + line.length() + 1 > maxChars) {
                truncated = true;
                break;
            }
            builder.append(line).append('\n');
            included++;
        }

        String payload = builder.toString().trim();
        if (!StringUtils.hasText(payload)) {
            payload = "(no serializable facts)";
        }
        return new FactsPayload(payload, included, truncated);
    }

    private String formatDocumentFact(int index, Object document) {
        String id = readProperty(document, "id");
        String content = readProperty(document, "content");
        String metadata = readProperty(document, "metadata");

        StringBuilder line = new StringBuilder();
        line.append(index).append(") ");
        if (StringUtils.hasText(id)) {
            line.append("id=").append(id).append(" ");
        }
        if (StringUtils.hasText(metadata)) {
            line.append("metadata=").append(metadata).append(" ");
        }
        if (StringUtils.hasText(content)) {
            line.append("content=").append(content);
        }

        String rendered = line.toString().trim();
        return rendered.isEmpty() ? null : rendered;
    }

    private String readProperty(Object target, String property) {
        if (target == null || !StringUtils.hasText(property)) {
            return null;
        }
        if (target instanceof Map<?, ?> map) {
            Object value = map.get(property);
            return value != null ? value.toString() : null;
        }
        try {
            String method = "get" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
            var reflected = target.getClass().getMethod(method);
            Object value = reflected.invoke(target);
            return value != null ? value.toString() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String buildPostActionUserPrompt(String instruction, String relationalQuery, String facts) {
        String queryPart = StringUtils.hasText(relationalQuery) ? relationalQuery : "(unknown)";
        String safeInstruction = StringUtils.hasText(instruction) ? instruction.trim() : "Summarize the results for the user.";
        String safeFacts = facts != null ? facts : "";

        return promptRenderer.render(
            promptTemplateResolver.resolve(TEMPLATE_FAMILY_POST_ACTION_GENERATION, TEMPLATE_POST_ACTION_USER_RELATIONSHIP_QUERY).template(),
            Map.of(
                PLACEHOLDER_INSTRUCTION, safeInstruction,
                PLACEHOLDER_RELATIONAL_QUERY, queryPart,
                PLACEHOLDER_FACTS, safeFacts
            )
        );
    }

    private record FactsPayload(String payload, int includedItems, boolean truncated) {
    }

    private record PostActionGenerationOutcome(String summary, String message, Map<String, Object> metadata) {
    }

    private record ResolvedPostActionGeneration(boolean shouldGenerate, String generationInstructions) {
        static ResolvedPostActionGeneration disabled() {
            return new ResolvedPostActionGeneration(false, null);
        }
    }
    
    private OrchestrationResult handleInformation(Intent intent, OrchestrationContext context, PipelineContext pipelineContext) {
        boolean deterministic = isDeterministicInformationMode(pipelineContext);
        boolean requiresRetrieval = intent.requiresRetrievalOrDefault(true);
        boolean llmRequiresGeneration = intent.requiresGenerationOrDefault(false);

        boolean skippedRetrievalForPinnedTargets = deterministic
            && requiresRetrieval
            && shouldSkipRetrievalForPinnedTargets(intent, pipelineContext);
        if (skippedRetrievalForPinnedTargets) {
            requiresRetrieval = false;
        }

        boolean needsGeneration = skippedRetrievalForPinnedTargets
            ? true
            : (requiresRetrieval ? (deterministic || llmRequiresGeneration) : llmRequiresGeneration);

        String optimizedQuery = StringUtils.hasText(intent.getOptimizedQuery()) ? intent.getOptimizedQuery() : null;
        String processedQuery = pipelineContext != null ? pipelineContext.getEffectiveQuery() : null;
        String retrievalFallbackQuery = extractUserQueryForRetrieval(processedQuery, pipelineContext != null ? pipelineContext.getOriginalQuery() : null);
        String retrievalBaseQuery = StringUtils.hasText(optimizedQuery)
            ? optimizedQuery
            : (StringUtils.hasText(retrievalFallbackQuery) ? retrievalFallbackQuery : intent.getIntentOrAction());
        String generationQuery = StringUtils.hasText(processedQuery) ? processedQuery : retrievalBaseQuery;
        
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(METADATA_KEY_SOURCE, METADATA_VALUE_ORCHESTRATOR);
        metadata.put(METADATA_KEY_USER_ID, context.getIdentifier());
        metadata.put(METADATA_KEY_SESSION_ID, context.getSessionId());
        metadata.put(METADATA_KEY_AUTHENTICATED, context.isAuthenticated());
        metadata.put(DATA_KEY_REQUIRES_GENERATION, needsGeneration);
        metadata.put("requiresRetrieval", requiresRetrieval);
        if (skippedRetrievalForPinnedTargets) {
            metadata.put("retrievalSkipped", true);
            metadata.put("retrievalSkipReason", "PINNED_TARGETS");
        }
        if (optimizedQuery != null) {
            metadata.put(METADATA_KEY_OPTIMIZED_QUERY, optimizedQuery);
        }
        if (pipelineContext != null && !pipelineContext.getDetectedPiiTypesView().isEmpty()) {
            metadata.put("piiProcessed", true);
            metadata.put("piiDetectedTypes", pipelineContext.getDetectedPiiTypesView());
        }

        if (!requiresRetrieval) {
            if (!needsGeneration) {
                if (hasPendingAction(context)) {
                    return OrchestrationResult.builder()
                        .type(OrchestrationResultType.CLARIFICATION_REQUIRED)
                        .success(false)
                        .message("Please confirm or reject the pending action.")
                        .build();
                }
                return handleInformationDirectAnswer(intent, context, pipelineContext);
            }
            return handleInformationGenerationOnly(intent, context, pipelineContext, generationQuery, metadata);
        }

        List<String> vectorSpaces = parseVectorSpaces(intent != null ? intent.getVectorSpace() : null);
        if (vectorSpaces.isEmpty()) {
            List<String> allSpaces = deterministic
                ? resolveDeterministicFallbackVectorSpaces()
                : resolveAllVectorSpaces();
            if (!allSpaces.isEmpty()) {
                vectorSpaces = allSpaces;
                intent.setVectorSpace(String.join(",", allSpaces));
            }
        }
        if (vectorSpaces.isEmpty()) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put(DATA_KEY_CANDIDATE_VECTOR_SPACES, List.of());
            return OrchestrationResult.builder()
                .type(OrchestrationResultType.CLARIFICATION_REQUIRED)
                .success(false)
                .message("Which knowledge base domain should I search?")
                .data(Collections.unmodifiableMap(data))
                .nextSteps(extractNextSteps(intent))
                .build();
        }

        String retrievalQuery = applyRetrievalQueryHint(retrievalBaseQuery, pipelineContext, intent, metadata);

        if (vectorSpaces.size() > 1) {
            return handleInformationFanOut(intent, context, pipelineContext, deterministic, needsGeneration, generationQuery, retrievalQuery, metadata, vectorSpaces);
        }

        String advancedDecisionQuery = StringUtils.hasText(optimizedQuery)
            ? optimizedQuery
            : (pipelineContext != null && StringUtils.hasText(pipelineContext.getOriginalQuery())
                ? pipelineContext.getOriginalQuery()
                : generationQuery);

        if (shouldUseAdvancedRag(intent, needsGeneration, advancedDecisionQuery, context, pipelineContext)) {
            OrchestrationResult advanced = handleInformationAdvanced(intent, context, pipelineContext, needsGeneration, generationQuery, retrievalQuery, metadata);
            if (advanced != null) {
                return advanced;
            }
        }

        return handleInformationBasic(intent, context, pipelineContext, needsGeneration, generationQuery, retrievalQuery, metadata);
    }

    private String extractUserQueryForRetrieval(String effectiveQuery, String originalQuery) {
        if (!StringUtils.hasText(effectiveQuery)) {
            return StringUtils.hasText(originalQuery) ? originalQuery : null;
        }

        String extracted = extractBetweenMarkers(effectiveQuery, "---BEGIN QUERY---", "---END QUERY---");
        if (StringUtils.hasText(extracted)) {
            return extracted;
        }

        extracted = extractBetweenMarkers(effectiveQuery, "---BEGIN MESSAGE---", "---END MESSAGE---");
        if (StringUtils.hasText(extracted)) {
            return extracted;
        }

        // If attachments were injected into the effective query, they are separated from the original user query by a blank line.
        String trimmed = effectiveQuery.trim();
        if (trimmed.startsWith("ATTACHMENTS (")) {
            int split = trimmed.indexOf("\n\n");
            if (split > 0 && split + 2 < trimmed.length()) {
                String remainder = trimmed.substring(split + 2).trim();
                if (StringUtils.hasText(remainder)) {
                    return remainder;
                }
            }
        }

        return trimmed;
    }

    private String extractBetweenMarkers(String text, String beginMarker, String endMarker) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(beginMarker) || !StringUtils.hasText(endMarker)) {
            return null;
        }
        int begin = text.indexOf(beginMarker);
        if (begin < 0) {
            return null;
        }
        begin += beginMarker.length();
        int end = text.indexOf(endMarker, begin);
        if (end < 0 || end <= begin) {
            return null;
        }
        String extracted = text.substring(begin, end);
        String trimmed = extracted != null ? extracted.trim() : null;
        return StringUtils.hasText(trimmed) ? trimmed : null;
    }

    private boolean hasPendingAction(OrchestrationContext context) {
        if (context == null || !context.hasConversation() || pendingActionStore == null) {
            return false;
        }
        try {
            return pendingActionStore.peekPendingAction(context.getConversationId(), context.getIdentifier()).isPresent();
        } catch (Exception ex) {
            return false;
        }
    }

    private OrchestrationResult handleInformationDirectAnswer(Intent intent,
                                                             OrchestrationContext context,
                                                             PipelineContext pipelineContext) {
        String answer = intent != null && StringUtils.hasText(intent.getDirectAnswer())
            ? intent.getDirectAnswer()
            : "Okay.";

        Map<String, Object> data = new LinkedHashMap<>();
        data.put(DATA_KEY_ANSWER, answer);
        data.put(DATA_KEY_DOCUMENTS, List.of());
        data.put(DATA_KEY_RAG_RESPONSE, null);
        data.put(DATA_KEY_REQUIRES_GENERATION, false);
        data.put("requiresRetrieval", false);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(METADATA_KEY_SOURCE, METADATA_VALUE_ORCHESTRATOR);
        metadata.put(METADATA_KEY_USER_ID, context != null ? context.getIdentifier() : null);
        metadata.put(METADATA_KEY_SESSION_ID, context != null ? context.getSessionId() : null);
        metadata.put(METADATA_KEY_AUTHENTICATED, context != null && context.isAuthenticated());
        data.put(DATA_KEY_METADATA, Collections.unmodifiableMap(metadata));

        return OrchestrationResult.builder()
            .type(OrchestrationResultType.INFORMATION_PROVIDED)
            .success(true)
            .message(answer)
            .data(Collections.unmodifiableMap(data))
            .nextSteps(extractNextSteps(intent))
            .build();
    }

    private OrchestrationResult handleInformationGenerationOnly(Intent intent,
                                                                OrchestrationContext context,
                                                                PipelineContext pipelineContext,
                                                                String query,
                                                                Map<String, Object> metadata) {
        String answer;
        try {
            String pinnedTargetsContext = prependPinnedTargetsContext(null, pipelineContext);
            if (StringUtils.hasText(pinnedTargetsContext)) {
                answer = generateRagAnswer(query, pinnedTargetsContext);
            } else {
                // Generation-only informational intent (no retrieval / no vectorSpace required).
                answer = aiCoreService.generateText(query, LlmPurpose.GENERATION);
            }
        } catch (Exception ex) {
            log.error("Generation-only response failed for request {}: {}",
                pipelineContext != null ? pipelineContext.getRequestId() : "unknown",
                ex.getMessage(),
                ex);
            Map<String, Object> errorData = new LinkedHashMap<>();
            errorData.put(DATA_KEY_ANSWER, null);
            errorData.put(DATA_KEY_DOCUMENTS, List.of());
            errorData.put(DATA_KEY_RAG_RESPONSE, null);
            errorData.put(DATA_KEY_REQUIRES_GENERATION, true);
            errorData.put(DATA_KEY_GENERATION_ERROR, ex.getMessage());
            return OrchestrationResult.builder()
                .type(OrchestrationResultType.ERROR)
                .success(false)
                .message("Failed to generate response: " + ex.getMessage())
                .data(Collections.unmodifiableMap(errorData))
                .nextSteps(extractNextSteps(intent))
                .build();
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put(DATA_KEY_ANSWER, answer);
        data.put(DATA_KEY_DOCUMENTS, List.of());
        data.put(DATA_KEY_RAG_RESPONSE, null);
        data.put(DATA_KEY_REQUIRES_GENERATION, true);
        data.put("requiresRetrieval", false);
        if (metadata != null && !metadata.isEmpty()) {
            data.put(DATA_KEY_METADATA, Collections.unmodifiableMap(new LinkedHashMap<>(metadata)));
        }

        String message = StringUtils.hasText(answer) ? answer : RAG_NO_CONTEXT_MESSAGE;
        return OrchestrationResult.builder()
            .type(OrchestrationResultType.INFORMATION_PROVIDED)
            .success(StringUtils.hasText(answer))
            .message(message)
            .data(Collections.unmodifiableMap(data))
            .nextSteps(extractNextSteps(intent))
            .build();
    }

    private OrchestrationResult handleInformationBasic(Intent intent,
                                                       OrchestrationContext context,
                                                       PipelineContext pipelineContext,
                                                       boolean needsGeneration,
                                                       String generationQuery,
                                                       String retrievalQuery,
                                                       Map<String, Object> metadata) {
        RAGProvider provider = ragProvider.getIfAvailable();
        if (provider == null) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put(DATA_KEY_ANSWER, null);
            data.put(DATA_KEY_DOCUMENTS, List.of());
            data.put(DATA_KEY_RAG_RESPONSE, null);
            data.put(DATA_KEY_REQUIRES_GENERATION, needsGeneration);
            data.put(DATA_KEY_DETAILS, "RAG module is not enabled (no RAGProvider bean present).");

            return OrchestrationResult.builder()
                .type(OrchestrationResultType.INFORMATION_PROVIDED)
                .success(false)
                .message(RAG_NO_CONTEXT_MESSAGE)
                .data(Collections.unmodifiableMap(data))
                .nextSteps(extractNextSteps(intent))
                .build();
        }

        RAGRequest ragRequest = RAGRequest.builder()
            .query(retrievalQuery)
            .entityType(intent.getVectorSpace())
            .limit(DEFAULT_RAG_LIMIT)
            .threshold(DEFAULT_RAG_THRESHOLD)
            .metadata(Collections.unmodifiableMap(metadata))
            .userId(context.getIdentifier())
            .build();

        // Use retrieval-only for search-only intents; use context-building query mode for generation flows.
        RAGResponse ragResponse = needsGeneration
            ? provider.performRAGQuery(ragRequest)
            : provider.performRag(ragRequest);
        if (ragResponse == null) {
            return OrchestrationResult.error(ERROR_MSG_RAG_NULL_RESPONSE);
        }

        String answer = null;
        if (needsGeneration) {
            try {
                String generationContext = prependPinnedTargetsContext(ragResponse.getContext(), pipelineContext);
                answer = generateRagAnswer(generationQuery, generationContext);
            } catch (Exception ex) {
                log.error("RAG generation failed for request {}: {}",
                    pipelineContext != null ? pipelineContext.getRequestId() : "unknown",
                    ex.getMessage(),
                    ex);

                Map<String, Object> errorData = new LinkedHashMap<>();
                errorData.put(DATA_KEY_ANSWER, null);
                errorData.put(DATA_KEY_DOCUMENTS, ragResponse.getDocuments());
                errorData.put(DATA_KEY_RAG_RESPONSE, ragResponse);
                errorData.put(DATA_KEY_REQUIRES_GENERATION, true);
                errorData.put(DATA_KEY_GENERATION_ERROR, ex.getMessage());

                return OrchestrationResult.builder()
                    .type(OrchestrationResultType.ERROR)
                    .success(false)
                    .message("Failed to generate response: " + ex.getMessage())
                    .data(Collections.unmodifiableMap(errorData))
                    .nextSteps(extractNextSteps(intent))
                    .build();
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put(DATA_KEY_ANSWER, answer);
        data.put(DATA_KEY_DOCUMENTS, ragResponse.getDocuments());
        data.put(DATA_KEY_RAG_RESPONSE, ragResponse);
        data.put(DATA_KEY_REQUIRES_GENERATION, needsGeneration);

        String message = StringUtils.hasText(answer)
            ? answer
            : MSG_SEARCH_COMPLETED;

        return OrchestrationResult.builder()
            .type(OrchestrationResultType.INFORMATION_PROVIDED)
            .success(Boolean.TRUE.equals(ragResponse.getSuccess()) || ragResponse.getSuccess() == null)
            .message(message)
            .data(Collections.unmodifiableMap(data))
            .nextSteps(extractNextSteps(intent))
            .build();
    }

    private OrchestrationResult handleInformationFanOut(Intent intent,
                                                        OrchestrationContext context,
                                                        PipelineContext pipelineContext,
                                                        boolean deterministic,
                                                        boolean needsGeneration,
                                                        String generationQuery,
                                                        String retrievalQuery,
                                                        Map<String, Object> metadata,
                                                        List<String> vectorSpaces) {
        RAGProvider provider = ragProvider.getIfAvailable();
        if (provider == null) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put(DATA_KEY_ANSWER, null);
            data.put(DATA_KEY_DOCUMENTS, List.of());
            data.put(DATA_KEY_RAG_RESPONSE, null);
            data.put(DATA_KEY_REQUIRES_GENERATION, needsGeneration);
            data.put(DATA_KEY_DETAILS, "RAG module is not enabled (no RAGProvider bean present).");
            data.put(DATA_KEY_CANDIDATE_VECTOR_SPACES, vectorSpaces);
            data.put(DATA_KEY_ROUTING_STRATEGY, "FAN_OUT");

            return OrchestrationResult.builder()
                .type(OrchestrationResultType.INFORMATION_PROVIDED)
                .success(false)
                .message(RAG_NO_CONTEXT_MESSAGE)
                .data(Collections.unmodifiableMap(data))
                .nextSteps(extractNextSteps(intent))
                .build();
        }

        int topKPerSpace = vectorSpaceRoutingProperties != null
            ? vectorSpaceRoutingProperties.getFanOutTopKPerSpace()
            : DEFAULT_RAG_LIMIT;

        double fanOutThreshold = vectorSpaceRoutingProperties != null
            ? vectorSpaceRoutingProperties.getFanOutRagThreshold()
            : DEFAULT_FAN_OUT_RAG_THRESHOLD;

        Map<String, List<RAGResponse.RAGDocument>> docsBySpace = new LinkedHashMap<>();
        for (String vectorSpace : vectorSpaces) {
            RAGRequest ragRequest = RAGRequest.builder()
                .query(retrievalQuery)
                .entityType(vectorSpace)
                .limit(topKPerSpace)
                .threshold(fanOutThreshold)
                .metadata(Collections.unmodifiableMap(new LinkedHashMap<>(metadata)))
                .userId(context.getIdentifier())
                .build();

            RAGResponse ragResponse = needsGeneration
                ? provider.performRAGQuery(ragRequest)
                : provider.performRag(ragRequest);

            List<RAGResponse.RAGDocument> docs = ragResponse != null && ragResponse.getDocuments() != null
                ? ragResponse.getDocuments()
                : List.of();

            List<RAGResponse.RAGDocument> tagged = docs.stream()
                .filter(java.util.Objects::nonNull)
                .map(doc -> tagDocumentWithVectorSpace(doc, vectorSpace))
                .collect(Collectors.toList());

            docsBySpace.put(vectorSpace, tagged);
        }

        List<RAGResponse.RAGDocument> merged = rankBasedMerger.mergeByRank(docsBySpace, topKPerSpace);
        merged = rankBasedMerger.dedupePreserveOrder(merged, doc -> doc != null ? doc.getId() : null);

        Double bestScore = bestDocumentScore(merged);
        double threshold = vectorSpaceRoutingProperties != null
            ? vectorSpaceRoutingProperties.getClarificationThreshold()
            : 0.4d;

        if (!deterministic && (merged.isEmpty() || (bestScore != null && bestScore < threshold))) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put(DATA_KEY_CANDIDATE_VECTOR_SPACES, vectorSpaces);
            data.put(DATA_KEY_ROUTING_STRATEGY, "FAN_OUT");
            if (bestScore != null) {
                data.put("bestScore", bestScore);
            }

            return OrchestrationResult.builder()
                .type(OrchestrationResultType.CLARIFICATION_REQUIRED)
                .success(false)
                .message("I couldn't confidently determine which domain to search. Please specify one of: "
                    + String.join(", ", vectorSpaces))
                .data(Collections.unmodifiableMap(data))
                .nextSteps(extractNextSteps(intent))
                .build();
        }

        int docsForContext = Math.min(DEFAULT_RAG_LIMIT, merged.size());
        String mergedContext = buildContextFromDocuments(merged.subList(0, docsForContext));
        RAGResponse mergedResponse = RAGResponse.builder()
            .documents(merged)
            .context(mergedContext)
            .originalQuery(generationQuery)
            .entityType(String.join(",", vectorSpaces))
            .success(true)
            .build();

        String answer = null;
        if (needsGeneration) {
            try {
                answer = generateRagAnswer(generationQuery, prependPinnedTargetsContext(mergedContext, pipelineContext));
            } catch (Exception ex) {
                log.error("Fan-out RAG generation failed for request {}: {}",
                    pipelineContext != null ? pipelineContext.getRequestId() : "unknown",
                    ex.getMessage(),
                    ex);

                Map<String, Object> errorData = new LinkedHashMap<>();
                errorData.put(DATA_KEY_ANSWER, null);
                errorData.put(DATA_KEY_DOCUMENTS, merged);
                errorData.put(DATA_KEY_RAG_RESPONSE, mergedResponse);
                errorData.put(DATA_KEY_REQUIRES_GENERATION, true);
                errorData.put(DATA_KEY_GENERATION_ERROR, ex.getMessage());
                errorData.put(DATA_KEY_CANDIDATE_VECTOR_SPACES, vectorSpaces);
                errorData.put(DATA_KEY_ROUTING_STRATEGY, "FAN_OUT");

                return OrchestrationResult.builder()
                    .type(OrchestrationResultType.ERROR)
                    .success(false)
                    .message("Failed to generate response: " + ex.getMessage())
                    .data(Collections.unmodifiableMap(errorData))
                    .nextSteps(extractNextSteps(intent))
                    .build();
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put(DATA_KEY_ANSWER, answer);
        data.put(DATA_KEY_DOCUMENTS, merged);
        data.put(DATA_KEY_RAG_RESPONSE, mergedResponse);
        data.put(DATA_KEY_REQUIRES_GENERATION, needsGeneration);
        data.put(DATA_KEY_CANDIDATE_VECTOR_SPACES, vectorSpaces);
        data.put(DATA_KEY_ROUTING_STRATEGY, "FAN_OUT");
        if (bestScore != null) {
            data.put("bestScore", bestScore);
        }

        String message = StringUtils.hasText(answer)
            ? answer
            : MSG_SEARCH_COMPLETED;

        return OrchestrationResult.builder()
            .type(OrchestrationResultType.INFORMATION_PROVIDED)
            .success(true)
            .message(message)
            .data(Collections.unmodifiableMap(data))
            .nextSteps(extractNextSteps(intent))
            .build();
    }

    private boolean isDeterministicInformationMode(PipelineContext pipelineContext) {
        OrchestrationPolicy policy = pipelineContext != null ? pipelineContext.getOrchestrationPolicy() : null;
        if (policy != null && policy.informationMode() != null) {
            return policy.informationMode() == OrchestrationProperties.InformationMode.DETERMINISTIC_RAG_GENERATE;
        }
        return orchestrationProperties != null
            && orchestrationProperties.getProfile() != null
            && orchestrationProperties.getProfile().defaultInformationMode() == OrchestrationProperties.InformationMode.DETERMINISTIC_RAG_GENERATE;
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
        Map<String, Long> byType = overview.getDocumentsByType();

        java.util.LinkedHashSet<String> ordered = new java.util.LinkedHashSet<>();
        if (byType != null && !byType.isEmpty()) {
            byType.entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank())
                .sorted(Map.Entry.<String, Long>comparingByValue(java.util.Comparator.nullsLast(Long::compareTo)).reversed())
                .map(Map.Entry::getKey)
                .forEach(ordered::add);
        }
        if (entityTypes != null && !entityTypes.isEmpty()) {
            entityTypes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .forEach(ordered::add);
        }
        if (ordered.isEmpty() && byType != null && !byType.isEmpty()) {
            byType.keySet().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .forEach(ordered::add);
        }

        entityTypes = ordered.isEmpty() ? null : new ArrayList<>(ordered);
        if (entityTypes == null || entityTypes.isEmpty()) {
            return List.of();
        }

        return entityTypes.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .distinct()
            .toList();
    }

    private List<String> resolveDeterministicFallbackVectorSpaces() {
        List<String> spaces = resolveAllVectorSpaces();
        if (spaces.isEmpty()) {
            return spaces;
        }

        int maxSpaces = vectorSpaceRoutingProperties != null ? vectorSpaceRoutingProperties.getFanOutMaxSpaces() : 3;
        if (maxSpaces <= 0) {
            return spaces;
        }
        return spaces.size() > maxSpaces ? spaces.subList(0, maxSpaces) : spaces;
    }

    private OrchestrationResult handleInformationAdvanced(Intent intent,
                                                          OrchestrationContext context,
                                                          PipelineContext pipelineContext,
                                                          boolean needsGeneration,
                                                          String generationQuery,
                                                          String retrievalQuery,
                                                          Map<String, Object> metadata) {
        AdvancedRAGProvider provider = advancedRagProvider.getIfAvailable();
        if (provider == null) {
            return null;
        }

        try {
            AdvancedRAGRequest request = buildAdvancedRagRequest(intent, context, retrievalQuery, metadata, pipelineContext);
            AdvancedRAGResponse advancedResponse = provider.performAdvancedRAG(request);
            if (advancedResponse == null) {
                log.warn("Advanced RAG returned null response for request {}", 
                    pipelineContext != null ? pipelineContext.getRequestId() : "unknown");
                return null;
            }

            if (Boolean.FALSE.equals(advancedResponse.getSuccess())) {
                log.warn("Advanced RAG failed for request {}: {}", 
                    pipelineContext != null ? pipelineContext.getRequestId() : "unknown",
                    advancedResponse.getErrorMessage());
                return null;
            }

            List<RAGResponse.RAGDocument> documents = convertToRagDocuments(advancedResponse.getDocuments());
            RAGResponse ragResponse = convertToRagResponse(advancedResponse, documents, generationQuery, intent.getVectorSpace());

            String answer = null;
            if (needsGeneration) {
                if (StringUtils.hasText(advancedResponse.getResponse())) {
                    answer = advancedResponse.getResponse();
                } else {
                    try {
                        answer = generateRagAnswer(generationQuery, prependPinnedTargetsContext(ragResponse.getContext(), pipelineContext));
                    } catch (Exception ex) {
                        log.error("Advanced RAG did not return response and generation fallback failed for request {}: {}",
                            pipelineContext != null ? pipelineContext.getRequestId() : "unknown",
                            ex.getMessage(),
                            ex);

                        Map<String, Object> errorData = new LinkedHashMap<>();
                        errorData.put(DATA_KEY_ANSWER, null);
                        errorData.put(DATA_KEY_DOCUMENTS, documents);
                        errorData.put(DATA_KEY_RAG_RESPONSE, ragResponse);
                        errorData.put(DATA_KEY_REQUIRES_GENERATION, true);
                        errorData.put(DATA_KEY_GENERATION_ERROR, ex.getMessage());
                        errorData.put(DATA_KEY_EXPANDED_QUERIES, advancedResponse.getExpandedQueries());
                        errorData.put(DATA_KEY_CONFIDENCE_SCORE, advancedResponse.getConfidenceScore());
                        errorData.put(DATA_KEY_RERANKING_STRATEGY, advancedResponse.getRerankingStrategy());
                        errorData.put(DATA_KEY_CONTEXT_OPTIMIZATION_LEVEL, advancedResponse.getContextOptimizationLevel());

                        return OrchestrationResult.builder()
                            .type(OrchestrationResultType.ERROR)
                            .success(false)
                            .message("Failed to generate response: " + ex.getMessage())
                            .data(Collections.unmodifiableMap(errorData))
                            .nextSteps(extractNextSteps(intent))
                            .build();
                    }
                }
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put(DATA_KEY_ANSWER, answer);
            data.put(DATA_KEY_DOCUMENTS, documents);
            data.put(DATA_KEY_RAG_RESPONSE, ragResponse);
            data.put(DATA_KEY_REQUIRES_GENERATION, needsGeneration);
            data.put(DATA_KEY_EXPANDED_QUERIES, advancedResponse.getExpandedQueries());
            data.put(DATA_KEY_CONFIDENCE_SCORE, advancedResponse.getConfidenceScore());
            data.put(DATA_KEY_RERANKING_STRATEGY, advancedResponse.getRerankingStrategy());
            data.put(DATA_KEY_CONTEXT_OPTIMIZATION_LEVEL, advancedResponse.getContextOptimizationLevel());

            String message = StringUtils.hasText(answer)
                ? answer
                : MSG_SEARCH_COMPLETED;

            return OrchestrationResult.builder()
                .type(OrchestrationResultType.INFORMATION_PROVIDED)
                .success(Boolean.TRUE.equals(advancedResponse.getSuccess()) || advancedResponse.getSuccess() == null)
                .message(message)
                .data(Collections.unmodifiableMap(data))
                .nextSteps(extractNextSteps(intent))
                .build();
        } catch (Exception ex) {
            log.error("Advanced RAG failed for request {}, falling back to basic RAG: {}", 
                pipelineContext != null ? pipelineContext.getRequestId() : "unknown",
                ex.getMessage(),
                ex);
            return null;
        }
    }

    private AdvancedRAGRequest buildAdvancedRagRequest(Intent intent,
                                                      OrchestrationContext context,
                                                      String query,
                                                      Map<String, Object> metadata,
                                                      PipelineContext pipelineContext) {
        Map<String, Object> ctxMetadata = context != null ? context.getMetadata() : null;

        AdvancedRAGRequest.AdvancedRAGRequestBuilder builder = AdvancedRAGRequest.builder()
            .query(query)
            .entityType(intent != null ? intent.getVectorSpace() : null)
            .maxResults(DEFAULT_RAG_LIMIT)
            .maxDocuments(DEFAULT_RAG_LIMIT)
            .similarityThreshold(DEFAULT_RAG_THRESHOLD)
            .userId(context != null ? context.getUserId() : null)
            .sessionId(context != null ? context.getSessionId() : null)
            .metadata(metadata != null ? Collections.unmodifiableMap(new LinkedHashMap<>(metadata)) : Map.of());

        String pinnedTargetsContext = prependPinnedTargetsContext(null, pipelineContext);
        if (StringUtils.hasText(pinnedTargetsContext)) {
            builder.context(pinnedTargetsContext);
        }

        Integer expansionLevel = readInteger(ctxMetadata, CONTEXT_METADATA_KEY_ADVANCED_EXPANSION_LEVEL);
        if (expansionLevel != null) {
            builder.expansionLevel(expansionLevel);
        }

        String reranking = readString(ctxMetadata, CONTEXT_METADATA_KEY_ADVANCED_RERANKING_STRATEGY);
        if (StringUtils.hasText(reranking)) {
            builder.rerankingStrategy(reranking);
        }

        String optimization = readString(ctxMetadata, CONTEXT_METADATA_KEY_ADVANCED_CONTEXT_OPTIMIZATION_LEVEL);
        if (StringUtils.hasText(optimization)) {
            builder.contextOptimizationLevel(optimization);
        }

        return builder.build();
    }

    private String applyRetrievalQueryHint(String baseQuery,
                                          PipelineContext pipelineContext,
                                          Intent intent,
                                          Map<String, Object> metadata) {
        boolean applied = false;
        String result = baseQuery;

        String hint = resolveValidRetrievalQueryHint(pipelineContext, intent);
        if (StringUtils.hasText(hint)) {
            result = baseQuery + " " + hint;
            applied = true;
        }

        if (metadata != null) {
            metadata.put(METADATA_KEY_RETRIEVAL_QUERY_HINT_APPLIED, applied);
        }
        return result;
    }

    private String resolveValidRetrievalQueryHint(PipelineContext pipelineContext, Intent currentIntent) {
        if (pipelineContext == null || currentIntent == null) {
            return null;
        }

        MultiIntentResponse response = pipelineContext.getIntentResponse();
        if (response == null) {
            return null;
        }

        if (!hasExactlyOneRetrievalIntent(response)) {
            return null;
        }

        Map<String, Object> metadata = response.getMetadata();
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }

        Object raw = metadata.get(INTENT_METADATA_KEY_RETRIEVAL_QUERY_HINT);
        if (!(raw instanceof String value)) {
            return null;
        }

        String hint = value.trim();
        if (!StringUtils.hasText(hint)) {
            return null;
        }

        if (!isSafeRetrievalQueryHint(hint)) {
            return null;
        }

        return hint;
    }

    private boolean hasExactlyOneRetrievalIntent(MultiIntentResponse response) {
        if (response == null || response.getIntents() == null || response.getIntents().isEmpty()) {
            return false;
        }

        long count = response.getIntents().stream()
            .filter(java.util.Objects::nonNull)
            .filter(intent -> Boolean.TRUE.equals(intent.getRequiresRetrieval()))
            .count();

        return count == 1;
    }

    private boolean isSafeRetrievalQueryHint(String hint) {
        if (!StringUtils.hasText(hint)) {
            return false;
        }
        if (hint.length() > MAX_RETRIEVAL_QUERY_HINT_LENGTH) {
            return false;
        }
        if (hint.indexOf('@') >= 0) {
            return false;
        }
        if (hint.indexOf('\n') >= 0 || hint.indexOf('\r') >= 0) {
            return false;
        }
        return !hasConsecutiveWhitespace(hint);
    }

    private boolean hasConsecutiveWhitespace(String value) {
        boolean lastWasWhitespace = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            boolean whitespace = Character.isWhitespace(ch);
            if (whitespace && lastWasWhitespace) {
                return true;
            }
            lastWasWhitespace = whitespace;
        }
        return false;
    }

    private boolean shouldUseAdvancedRag(Intent intent,
                                        boolean needsGeneration,
                                        String query,
                                        OrchestrationContext context,
                                        PipelineContext pipelineContext) {
        AdvancedRAGProvider provider = advancedRagProvider.getIfAvailable();
        if (provider == null) {
            return false;
        }

        AIServiceConfig.FeatureFlags features = aiServiceConfig != null ? aiServiceConfig.getFeatures() : null;
        if (features != null && Boolean.FALSE.equals(features.getEnableAdvancedRAG())) {
            return false;
        }

        Map<String, Object> ctxMetadata = context != null ? context.getMetadata() : null;
        Object advancedOverride = ctxMetadata != null ? ctxMetadata.get(OrchestrationContextMetadataKeys.USE_ADVANCED_RAG) : null;
        if (advancedOverride instanceof Boolean bool) {
            return bool;
        }

        // In deterministic information mode, Advanced RAG must be explicitly enabled (manual deep search).
        if (isDeterministicInformationMode(pipelineContext)) {
            return false;
        }

        if (!needsGeneration) {
            return false;
        }

        // LLM decides (when provided). Only fall back to heuristics when the LLM did not return a value.
        Boolean llmDecision = intent != null ? intent.getNeedsAdvancedRAG() : null;
        if (llmDecision != null) {
            return llmDecision;
        }

        boolean autoEnable = features != null
            && Boolean.TRUE.equals(features.getAutoEnableAdvancedRAGForComplexQueries());
        return autoEnable && isComplexQuery(query);
    }

    private boolean isComplexQuery(String query) {
        if (!StringUtils.hasText(query)) {
            return false;
        }
        if (query.length() >= ADVANCED_QUERY_LENGTH_THRESHOLD) {
            return true;
        }
        if (query.contains("?")) {
            return true;
        }
        int words = query.trim().split("\\s+").length;
        return words >= ADVANCED_QUERY_WORD_THRESHOLD;
    }

    private boolean isTrue(Object value) {
        return value instanceof Boolean bool && bool;
    }

    private Integer readInteger(Map<String, Object> metadata, String key) {
        if (metadata == null || key == null) {
            return null;
        }
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String str && StringUtils.hasText(str)) {
            try {
                return Integer.parseInt(str.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String readString(Map<String, Object> metadata, String key) {
        if (metadata == null || key == null) {
            return null;
        }
        Object value = metadata.get(key);
        if (value instanceof String str) {
            String trimmed = str.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
        return value != null ? value.toString() : null;
    }

    private List<RAGResponse.RAGDocument> convertToRagDocuments(List<AdvancedRAGResponse.RAGDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        return documents.stream()
            .filter(java.util.Objects::nonNull)
            .map(doc -> RAGResponse.RAGDocument.builder()
                .id(doc.getId())
                .content(doc.getContent())
                .title(doc.getTitle())
                .type(doc.getType())
                .score(doc.getScore())
                .similarity(doc.getSimilarity())
                .metadata(doc.getMetadata())
                .source(doc.getSource())
                .createdAt(doc.getCreatedAt())
                .author(doc.getAuthor())
                .tags(doc.getTags())
                .wordCount(doc.getWordCount())
                .language(doc.getLanguage())
                .build())
            .collect(Collectors.toList());
    }

    private RAGResponse convertToRagResponse(AdvancedRAGResponse advanced,
                                            List<RAGResponse.RAGDocument> documents,
                                            String originalQuery,
                                            String entityType) {
        return RAGResponse.builder()
            .documents(documents != null ? documents : List.of())
            .context(advanced != null ? advanced.getContext() : null)
            .totalDocuments(advanced != null ? advanced.getTotalDocuments() : null)
            .usedDocuments(advanced != null ? advanced.getUsedDocuments() : null)
            .relevanceScores(advanced != null ? advanced.getRelevanceScores() : null)
            .confidenceScore(advanced != null ? advanced.getConfidenceScore() : null)
            .processingTimeMs(advanced != null ? advanced.getProcessingTimeMs() : null)
            .requestId(advanced != null ? advanced.getRequestId() : null)
            .originalQuery(originalQuery)
            .entityType(entityType)
            .metadata(advanced != null ? advanced.getMetadata() : null)
            .timestamp(advanced != null ? advanced.getTimestamp() : null)
            .success(advanced != null ? advanced.getSuccess() : null)
            .errorMessage(advanced != null ? advanced.getErrorMessage() : null)
            .build();
    }

    private String generateRagAnswer(String query, String context) {
        if (!StringUtils.hasText(query)) {
            return null;
        }
        String safeQuery = query.trim();

        if (!StringUtils.hasText(context) || RAG_NO_CONTEXT_MESSAGE.equals(context)) {
            if (aiServiceConfig != null
                && aiServiceConfig.getFeatures() != null
                && Boolean.TRUE.equals(aiServiceConfig.getFeatures().getEnableGeneration())) {
                try {
                    String prompt = promptRenderer.render(
                        promptTemplateResolver.resolve(TEMPLATE_FAMILY_RAG_GENERATION, TEMPLATE_RAG_NO_CONTEXT).template(),
                        Map.of(PLACEHOLDER_QUERY, safeQuery)
                    );
                    String response = aiCoreService.generateText(prompt, LlmPurpose.GENERATION);
                    if (StringUtils.hasText(response)) {
                        return response;
                    }
                } catch (Exception ex) {
                    log.warn("No-context generation failed; falling back to static response: {}", ex.getMessage());
                }
            }
            return RAG_NO_INFO_MESSAGE_PREFIX + safeQuery;
        }

        String safeContext = context != null ? context : "";
        String prompt = promptRenderer.render(
            promptTemplateResolver.resolve(TEMPLATE_FAMILY_RAG_GENERATION, TEMPLATE_RAG_ANSWER).template(),
            Map.of(
                PLACEHOLDER_QUERY, safeQuery,
                PLACEHOLDER_CONTEXT, safeContext
            )
        );
        return aiCoreService.generateText(prompt, LlmPurpose.GENERATION);
    }

    private String prependPinnedTargetsContext(String ragContext, PipelineContext pipelineContext) {
        if (pipelineContext == null) {
            return ragContext;
        }
        List<ResolvedTarget> targets = pipelineContext.getResolvedTargets();
        if (targets == null || targets.isEmpty()) {
            return ragContext;
        }

        String block = buildPinnedTargetsBlock(targets);
        if (!StringUtils.hasText(block)) {
            return ragContext;
        }
        if (!StringUtils.hasText(ragContext)) {
            return block;
        }
        return block + "\n\n" + ragContext;
    }

    private String buildPinnedTargetsBlock(List<ResolvedTarget> targets) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("PINNED TARGETS (authoritative):\n");
        int index = 1;
        for (ResolvedTarget target : targets) {
            if (target == null || !StringUtils.hasText(target.getId())) {
                continue;
            }

            sb.append(index).append(") ");
            if (StringUtils.hasText(target.getVectorSpace())) {
                sb.append("vectorSpace=").append(target.getVectorSpace()).append(" ");
            }
            sb.append("id=").append(target.getId());

            if (target.getMetadata() != null && !target.getMetadata().isEmpty()) {
                sb.append(" metadata={");
                String meta = target.getMetadata().entrySet().stream()
                    .filter(e -> e != null && StringUtils.hasText(e.getKey()) && StringUtils.hasText(e.getValue()))
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining(", "));
                sb.append(meta).append("}");
            }

            if (StringUtils.hasText(target.getContentText())) {
                sb.append(" contentTextTruncated=").append(target.isContentTextTruncated());
                sb.append(" contentText=\"").append(target.getContentText()).append("\"");
            }

            sb.append("\n");
            index++;
        }

        return sb.toString().trim();
    }

    private boolean shouldSkipRetrievalForPinnedTargets(Intent intent, PipelineContext pipelineContext) {
        if (intent == null || pipelineContext == null) {
            return false;
        }

        List<ResolvedTarget> targets = pipelineContext.getResolvedTargets();
        if (targets == null || targets.isEmpty()) {
            return false;
        }

        boolean requiresTargetResolution = Boolean.TRUE.equals(intent.getRequiresTargetResolution());
        OrchestrationContext orchContext = pipelineContext.getOrchestrationContext();
        boolean hasActiveAttachments = orchContext != null
            && orchContext.getActiveAttachmentIdsResolved() != null
            && !orchContext.getActiveAttachmentIdsResolved().isEmpty();

        if (!requiresTargetResolution && !hasActiveAttachments) {
            return false;
        }

        String intentVectorSpace = intent.getVectorSpace();
        if (!StringUtils.hasText(intentVectorSpace)) {
            // No retrieval scope was provided; prefer answering from authoritative pinned targets.
            return true;
        }

        Set<String> targetSpaces = targets.stream()
            .filter(t -> t != null && StringUtils.hasText(t.getVectorSpace()))
            .map(t -> t.getVectorSpace().trim().toLowerCase(java.util.Locale.ROOT))
            .collect(Collectors.toSet());
        if (targetSpaces.isEmpty()) {
            return false;
        }

        List<String> requestedSpaces = parseVectorSpaces(intentVectorSpace).stream()
            .filter(StringUtils::hasText)
            .map(space -> space.trim().toLowerCase(java.util.Locale.ROOT))
            .toList();
        if (requestedSpaces.isEmpty()) {
            return true;
        }

        return requestedSpaces.stream().allMatch(targetSpaces::contains);
    }

    private List<String> parseVectorSpaces(String vectorSpace) {
        if (!StringUtils.hasText(vectorSpace)) {
            return List.of();
        }
        String[] parts = vectorSpace.split(",");
        Set<String> unique = new java.util.LinkedHashSet<>();
        for (String part : parts) {
            if (part == null) {
                continue;
            }
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                unique.add(trimmed);
            }
        }
        return List.copyOf(unique);
    }

    private RAGResponse.RAGDocument tagDocumentWithVectorSpace(RAGResponse.RAGDocument doc, String vectorSpace) {
        if (doc == null) {
            return null;
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        if (doc.getMetadata() != null && !doc.getMetadata().isEmpty()) {
            meta.putAll(doc.getMetadata());
        }
        meta.put("vectorSpace", vectorSpace);

        return RAGResponse.RAGDocument.builder()
            .id(doc.getId())
            .content(doc.getContent())
            .title(doc.getTitle())
            .type(doc.getType())
            .score(doc.getScore())
            .similarity(doc.getSimilarity())
            .metadata(Collections.unmodifiableMap(meta))
            .embeddings(doc.getEmbeddings())
            .highlightedContent(doc.getHighlightedContent())
            .source(doc.getSource())
            .url(doc.getUrl())
            .createdAt(doc.getCreatedAt())
            .modifiedAt(doc.getModifiedAt())
            .author(doc.getAuthor())
            .tags(doc.getTags())
            .wordCount(doc.getWordCount())
            .language(doc.getLanguage())
            .build();
    }

    private Double bestDocumentScore(List<RAGResponse.RAGDocument> docs) {
        if (docs == null || docs.isEmpty()) {
            return null;
        }
        Double best = null;
        for (RAGResponse.RAGDocument doc : docs) {
            if (doc == null) {
                continue;
            }
            Double score = doc.getScore() != null ? doc.getScore() : doc.getSimilarity();
            if (score == null) {
                continue;
            }
            if (best == null || score > best) {
                best = score;
            }
        }
        return best;
    }

    private String buildContextFromDocuments(List<RAGResponse.RAGDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return RAG_NO_CONTEXT_MESSAGE;
        }
        StringBuilder builder = new StringBuilder();
        for (RAGResponse.RAGDocument doc : documents) {
            if (doc == null) {
                continue;
            }
            String vectorSpace = null;
            if (doc.getMetadata() != null) {
                Object vs = doc.getMetadata().get("vectorSpace");
                if (vs instanceof String vsText && StringUtils.hasText(vsText)) {
                    vectorSpace = vsText.trim();
                }
            }

            if (StringUtils.hasText(vectorSpace) || StringUtils.hasText(doc.getId())) {
                builder.append("[");
                if (StringUtils.hasText(vectorSpace)) {
                    builder.append("vectorSpace=").append(vectorSpace);
                }
                if (StringUtils.hasText(doc.getId())) {
                    if (StringUtils.hasText(vectorSpace)) {
                        builder.append(" ");
                    }
                    builder.append("id=").append(doc.getId().trim());
                }
                builder.append("]\n");
            }
            if (StringUtils.hasText(doc.getTitle())) {
                builder.append(doc.getTitle()).append("\n");
            }
            if (StringUtils.hasText(doc.getContent())) {
                builder.append(doc.getContent()).append("\n");
            }
            builder.append("---\n");
        }
        return builder.toString();
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
    
    private OrchestrationResult handleSyntheticCompound(Intent intent, OrchestrationContext context, PipelineContext pipelineContext) {
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
        return handleCompoundIntents(syntheticResponse, context, pipelineContext);
    }
    
    private OrchestrationResult handleCompoundIntents(MultiIntentResponse response, OrchestrationContext context, PipelineContext pipelineContext) {
        List<OrchestrationResult> childResults = new ArrayList<>();
        List<NextStepRecommendation> nextSteps = new ArrayList<>();
        
        for (Intent intent : response.getIntents()) {
            OrchestrationResult child = handleSingleIntent(intent, context, pipelineContext);
            if (child == null) {
                log.error("handleSingleIntent returned null for intent type: {}", 
                    intent != null ? intent.getType() : "NULL_INTENT");
                continue;
            }
            childResults.add(child);
            nextSteps.addAll(child.getNextSteps());
        }
        
        // Compound requests often include a primary action plus optional follow-up intents (ex: confirmation/help text).
        // Treat the compound as successful if at least one child succeeded and we did not hit a hard ERROR.
        // This prevents "action succeeded but follow-up failed" scenarios from being recorded as a total failure.
        boolean anySuccess = childResults.stream().anyMatch(OrchestrationResult::isSuccess);
        boolean anyError = childResults.stream().anyMatch(result -> result.getType() == OrchestrationResultType.ERROR);
        boolean allSuccess = !childResults.isEmpty() && childResults.stream().allMatch(OrchestrationResult::isSuccess);
        boolean success = anySuccess && !anyError;
        Map<String, Object> data = Map.of(DATA_KEY_RESULTS, childResults);
        
        return OrchestrationResult.builder()
            .type(OrchestrationResultType.COMPOUND_HANDLED)
            .success(success)
            .message(allSuccess ? MSG_ALL_PROCESSED : MSG_SOME_FAILED)
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

    private void mergeResolvedTargetsIntoActionParams(AIActionMetaData meta,
                                                     Map<String, Object> params,
                                                     PipelineContext pipelineContext) {
        if (meta == null || meta.getRequiredParameters() == null || meta.getRequiredParameters().isEmpty()) {
            return;
        }
        if (params == null || pipelineContext == null) {
            return;
        }

        List<ResolvedTarget> targets = pipelineContext.getResolvedTargets();
        if (targets == null || targets.isEmpty() || targets.size() != 1) {
            return;
        }

        ResolvedTarget target = targets.getFirst();
        if (target == null) {
            return;
        }

        for (String required : meta.getRequiredParameters()) {
            if (!StringUtils.hasText(required)) {
                continue;
            }
            if (hasParamValue(params.get(required))) {
                continue;
            }

            String value = null;
            if (target.getMetadata() != null) {
                String candidate = target.getMetadata().get(required);
                if (StringUtils.hasText(candidate)) {
                    value = candidate.trim();
                }
            }

            if (!StringUtils.hasText(value) && "id".equalsIgnoreCase(required) && StringUtils.hasText(target.getId())) {
                value = target.getId().trim();
            }

            if (StringUtils.hasText(value)) {
                params.put(required, value);
            }
        }
    }

    private boolean hasParamValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String text) {
            return StringUtils.hasText(text);
        }
        return true;
    }

    private List<String> findMissingRequiredParams(AIActionMetaData meta,
                                                   Map<String, Object> params,
                                                   String originalQuery,
                                                   String evidenceText,
                                                   boolean skipEvidenceCheck) {
        if (meta == null || meta.getRequiredParameters() == null || meta.getRequiredParameters().isEmpty()) {
            return List.of();
        }
        String normalizedOriginalQuery = StringUtils.hasText(originalQuery) ? originalQuery.trim() : "";
        String evidenceLower = StringUtils.hasText(evidenceText)
            ? evidenceText.toLowerCase(java.util.Locale.ROOT)
            : "";
        List<String> missing = new ArrayList<>();
        for (String required : meta.getRequiredParameters()) {
            if (!StringUtils.hasText(required)) {
                continue;
            }
            Object value = params != null ? params.get(required) : null;
            if (value == null) {
                missing.add(required);
                continue;
            }

            String raw = value.toString();
            if (!StringUtils.hasText(raw)) {
                missing.add(required);
                continue;
            }

            // Simple guardrail: if the value looks like instruction text ("required/optional/example"),
            // treat it as missing and ask the user for the real value.
            String lowered = raw.trim().toLowerCase(java.util.Locale.ROOT);
            if (lowered.contains("required") || lowered.contains("optional") || lowered.contains("example") || lowered.contains("e.g")) {
                missing.add(required);
                continue;
            }

            // Guardrail: some intent extractors incorrectly "fill" missing values by copying the parameter
            // description from action metadata. Treat that as missing so we ask the user for the real value.
            Map<String, String> descriptions = meta.getParameters();
            String description = descriptions != null ? descriptions.get(required) : null;
            if (StringUtils.hasText(description) && raw.trim().equalsIgnoreCase(description.trim())) {
                missing.add(required);
                continue;
            }

            // Also treat "self-filled" placeholders like "shippingAddress" as missing.
            if (raw.trim().equalsIgnoreCase(required.trim())) {
                missing.add(required);
                continue;
            }

            // Guardrail: some extractors will "fill" a required string param with the entire user message.
            // This is often an instruction echo (e.g., "use action X"), but it can also be a valid single-value
            // reply (e.g., the user only sends an email or an address).
            //
            // Treat it as missing ONLY when the message looks like it is describing the action/params.
            if (value instanceof String && StringUtils.hasText(normalizedOriginalQuery)
                && raw.trim().equalsIgnoreCase(normalizedOriginalQuery)) {
                String originalLower = normalizedOriginalQuery.toLowerCase(java.util.Locale.ROOT);
                boolean looksLikeInstruction = false;
                if (StringUtils.hasText(meta.getName())
                    && originalLower.contains(meta.getName().toLowerCase(java.util.Locale.ROOT))) {
                    looksLikeInstruction = true;
                }
                if (!looksLikeInstruction && descriptions != null && !descriptions.isEmpty()) {
                    for (String key : descriptions.keySet()) {
                        if (StringUtils.hasText(key) && originalLower.contains(key.toLowerCase(java.util.Locale.ROOT))) {
                            looksLikeInstruction = true;
                            break;
                        }
                    }
                }
                if (looksLikeInstruction) {
                    missing.add(required);
                    continue;
                }
            }

            // Guardrail: if an extractor "filled" a required string param with a value that does not
            // appear anywhere in the processed user prompt (current message + any included history),
            // treat it as missing so we ask the user rather than executing on hallucinated values.
            if (!skipEvidenceCheck && value instanceof String && StringUtils.hasText(evidenceLower)) {
                String needle = raw.trim().toLowerCase(java.util.Locale.ROOT);
                if (StringUtils.hasText(needle) && !evidenceLower.contains(needle)) {
                    missing.add(required);
                }
            }
        }
        return missing;
    }
}
