package com.ai.infrastructure.intent.orchestration.pipeline.steps;

import com.ai.infrastructure.config.AIServiceConfig;
import com.ai.infrastructure.config.PostActionGenerationProperties;
import com.ai.infrastructure.config.RelationshipQueryPostActionGenerationProperties;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AdvancedRAGRequest;
import com.ai.infrastructure.dto.AdvancedRAGResponse;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.dto.AIChatMessage;
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
import com.ai.infrastructure.intent.orchestration.OrchestrationAuthContextResolver;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.OrchestrationContextMetadataKeys;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationResultType;
import com.ai.infrastructure.intent.orchestration.attachment.NormalizedAttachment;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineStep;
import com.ai.infrastructure.intent.orchestration.policy.OrchestrationPolicy;
import com.ai.infrastructure.intent.orchestration.rag.EmbeddingQueryComposer;
import com.ai.infrastructure.intent.orchestration.targets.ResolvedTarget;
import com.ai.infrastructure.intent.orchestration.targets.ResolvedTargetSource;
import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import com.ai.infrastructure.config.OrchestrationProperties;
import com.ai.infrastructure.config.VectorSpaceRoutingProperties;
import com.ai.infrastructure.core.LlmPurpose;
import com.ai.infrastructure.intent.vectorspace.RankBasedMerger;
import com.ai.infrastructure.prompt.ManagedPromptDefaults;
import com.ai.infrastructure.prompt.PromptPreviewOverlaySupport;
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
import java.util.Locale;
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
 * </ul>
 * 
 * <p><strong>Order:</strong> 60 (after intent extraction)</p>
 * 
 * <p><strong>Security:</strong> Actions are denied for anonymous users by default.
 * Anonymous execution must be explicitly enabled in the action contract, and action handlers may
 * declare {@code @ActionAllowed} for additional access control.</p>
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
    private static final String METADATA_KEY_ACTION_PARAM_VALIDATION = "actionParamValidation";

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
    private static final String TEMPLATE_RAG_ANSWER_MANAGED = "answer-managed";
    private static final String TEMPLATE_RAG_NO_CONTEXT = "no-context";
    private static final String TEMPLATE_RAG_NO_CONTEXT_MANAGED = "no-context-managed";

    private static final String TEMPLATE_FAMILY_CONFIRMATION_RESOLUTION = "intent-extraction/confirmation";
    private static final String TEMPLATE_CONFIRMATION_RESOLUTION_SYSTEM = "system";
    private static final String TEMPLATE_CONFIRMATION_RESOLUTION_USER = "user";

    private static final String TEMPLATE_FAMILY_POST_ACTION_GENERATION = "orchestration/post-action-generation";
    private static final String TEMPLATE_POST_ACTION_SYSTEM = "system";
    private static final String TEMPLATE_POST_ACTION_USER_GENERIC = "user-generic";
    private static final String TEMPLATE_POST_ACTION_USER_GENERIC_MANAGED = "user-generic-managed";
    private static final String TEMPLATE_POST_ACTION_USER_RELATIONSHIP_QUERY = "user-relationship-query";
    private static final String TEMPLATE_POST_ACTION_USER_RELATIONSHIP_QUERY_MANAGED = "user-relationship-query-managed";

    private static final String PLACEHOLDER_QUERY = "query";
    private static final String PLACEHOLDER_CONTEXT = "context";
    private static final String PLACEHOLDER_ACTION_NAME = "action_name";
    private static final String PLACEHOLDER_INSTRUCTION = "instruction";
    private static final String PLACEHOLDER_FACTS = "facts";
    private static final String PLACEHOLDER_RELATIONAL_QUERY = "relational_query";
    private static final String PLACEHOLDER_MANAGED_ANSWER_GENERATION_PROMPT = "managed_answer_generation_prompt";
    private static final String PLACEHOLDER_MANAGED_RETRIEVAL_PROMPT = "managed_retrieval_prompt";
    private static final String PLACEHOLDER_MANAGED_ASSISTANT_UI_PROMPT = "managed_assistant_ui_prompt";

    private static final String DATA_KEY_GENERATION_ERROR = "generationError";

    private static final String CONTEXT_METADATA_KEY_ADVANCED_EXPANSION_LEVEL = "advancedRagExpansionLevel";
    private static final String CONTEXT_METADATA_KEY_ADVANCED_RERANKING_STRATEGY = "advancedRagRerankingStrategy";
    private static final String CONTEXT_METADATA_KEY_ADVANCED_CONTEXT_OPTIMIZATION_LEVEL = "advancedRagContextOptimizationLevel";

    private static final int ADVANCED_QUERY_LENGTH_THRESHOLD = 50;
    private static final int ADVANCED_QUERY_WORD_THRESHOLD = 8;

    private static final String DATA_KEY_CONFIRMATION_REQUIRED = "confirmationRequired";
    private static final String DATA_KEY_MISSING_REQUIRED_PARAMETERS = "missingRequiredParameters";
    private static final String DATA_KEY_PROVIDED_PARAMETERS = "providedParameters";
    
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
    private final AIEntityConfigurationLoader entityConfigurationLoader;
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
        if (intentResponse.getIntents().size() > 1) {
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
            default -> OrchestrationResult.error(ERROR_MSG_UNKNOWN_INTENT + intent.getType());
        };
    }
    
    private OrchestrationResult handleAction(Intent intent, OrchestrationContext context, PipelineContext pipelineContext) {
        String actionName = StringUtils.hasText(intent.getAction()) ? intent.getAction() : intent.getIntent();
        if (!StringUtils.hasText(actionName)) {
            return OrchestrationResult.error(ERROR_MSG_MISSING_ACTION_NAME);
        }

        OrchestrationPolicy policy = pipelineContext != null ? pipelineContext.getOrchestrationPolicy() : null;
        if (policy != null
            && policy.capabilities() != null
            && !policy.capabilities().actionsEnabled()) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("reason", "ACTIONS_DISABLED_BY_POLICY");
            return OrchestrationResult.builder()
                .type(OrchestrationResultType.CLARIFICATION_REQUIRED)
                .success(false)
                .message("Actions are disabled by server policy for this request.")
                .data(Collections.unmodifiableMap(data))
                .nextSteps(extractNextSteps(intent))
                .build();
        }
        
        AIActionMetaData meta = getMetadataForAction(actionName);
        if (context.isAnonymous() && (meta == null || !meta.isAnonymousAllowed())) {
            return OrchestrationResult.builder()
                .type(OrchestrationResultType.ACTION_DENIED)
                .success(false)
                .message(ERROR_MSG_ACTION_NOT_PERMITTED_ANON)
                .nextSteps(extractNextSteps(intent))
                .build();
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

        postActionRequest = resolvePostActionGeneration(actionName, intent, pipelineContext, effectiveParams);

        effectiveParams = applyBatchTargetsDefaulting(meta, effectiveParams, pipelineContext);

        ActionParamValidation validation = validateRequiredActionParams(meta, effectiveParams, pipelineContext);
        List<String> missingRequired = validation != null ? validation.missingRequired() : List.of();
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
                .metadata(validation != null ? Map.of(METADATA_KEY_ACTION_PARAM_VALIDATION, validation.debugMetadata()) : Map.of())
                .data(Collections.unmodifiableMap(data))
                .nextSteps(Collections.unmodifiableList(nextSteps))
                .build();
        }

        boolean confirmedThisRequest = pipelineContext != null && pipelineContext.isActionConfirmed(actionName);
        
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
            // Loop breaker: if the LLM keeps re-issuing the same ACTION instead of emitting
            // CONFIRMATION_POSITIVE/NEGATIVE, resolve confirmation using a dedicated LLM prompt
            // (no backend string matching) and execute/cancel accordingly.
            OrchestrationResult resolved = maybeResolvePendingConfirmationForMisclassifiedAction(
                actionName,
                effectiveParams,
                confirmationMessage,
                context,
                pipelineContext
            );
            if (resolved != null) {
                return resolved;
            }

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
                .metadata(validation != null ? Map.of(METADATA_KEY_ACTION_PARAM_VALIDATION, validation.debugMetadata()) : Map.of())
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
                .metadata(validation != null ? Map.of(METADATA_KEY_ACTION_PARAM_VALIDATION, validation.debugMetadata()) : Map.of())
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
                .metadata(validation != null ? Map.of(METADATA_KEY_ACTION_PARAM_VALIDATION, validation.debugMetadata()) : Map.of())
                .data(Collections.unmodifiableMap(data))
                .nextSteps(extractNextSteps(intent))
                .build();
        }
    }

    /**
     * Default population for batch-capable array parameters using resolved targets (attachments / stored pinned targets).
     *
     * <p>This is schema-driven and domain-agnostic: for an action that exposes an array param marked {@code batchTargets},
     * the framework may populate/expand that array by mapping each resolved target's metadata into the item schema.</p>
     *
     * <p>This is intended as a safety net to align runtime behavior with the extraction contract ("apply to all pinned
     * targets by default") while remaining explicit and observable through the confirmation message.</p>
     */
    private Map<String, Object> applyBatchTargetsDefaulting(AIActionMetaData meta,
                                                           Map<String, Object> effectiveParams,
                                                           PipelineContext pipelineContext) {
        BatchParamSpec batchSpec = findBatchParamSpec(meta);
        if (batchSpec == null || !StringUtils.hasText(batchSpec.paramName())) {
            return effectiveParams;
        }
        if (pipelineContext == null || pipelineContext.getResolvedTargets() == null || pipelineContext.getResolvedTargets().isEmpty()) {
            return effectiveParams;
        }

        com.ai.infrastructure.intent.action.AIActionParamSchema schema = batchSpec.schema();
        com.ai.infrastructure.intent.action.AIActionParamSchema itemSchema = schema != null ? schema.getItems() : null;
        Map<String, com.ai.infrastructure.intent.action.AIActionParamSchema> props = itemSchema != null ? itemSchema.getProperties() : null;
        if (props == null || props.isEmpty()) {
            return effectiveParams;
        }

        Map<String, Object> params = effectiveParams != null ? effectiveParams : new LinkedHashMap<>();
        Object rawExisting = params.get(batchSpec.paramName());
        List<Object> existing = new ArrayList<>(coerceToObjectList(rawExisting));

        java.util.Set<String> existingKeys = new java.util.HashSet<>();
        for (Object element : existing) {
            if (element instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> item = (Map<String, Object>) map;
                existingKeys.add(buildBatchItemKey(item, props));
            }
        }

        List<Object> merged = new ArrayList<>(existing);
        for (ResolvedTarget target : pipelineContext.getResolvedTargets()) {
            if (target == null) {
                continue;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            for (String propName : props.keySet()) {
                if (!StringUtils.hasText(propName)) {
                    continue;
                }
                Object value = null;

                if ("id".equalsIgnoreCase(propName) && StringUtils.hasText(target.getId())) {
                    value = target.getId().trim();
                } else if ("vectorSpace".equalsIgnoreCase(propName) && StringUtils.hasText(target.getVectorSpace())) {
                    value = target.getVectorSpace().trim();
                } else if (target.getMetadata() != null && !target.getMetadata().isEmpty()) {
                    String metaValue = getMetadataValueIgnoreCase(target.getMetadata(), propName);
                    if (StringUtils.hasText(metaValue)) {
                        value = metaValue.trim();
                    }
                }

                if (value == null && "quantity".equalsIgnoreCase(propName)) {
                    // Default quantities for batch actions (e.g., add_to_cart) to 1 when not provided.
                    value = 1;
                }

                if (value != null) {
                    item.put(propName, value);
                }
            }

            if (item.isEmpty()) {
                continue;
            }

            String key = buildBatchItemKey(item, props);
            if (existingKeys.add(key)) {
                merged.add(Collections.unmodifiableMap(item));
            }
        }

        if (merged.equals(existing) || merged.isEmpty()) {
            return params;
        }

        Map<String, Object> updated = new LinkedHashMap<>(params);
        updated.put(batchSpec.paramName(), Collections.unmodifiableList(merged));
        return updated;
    }

    private String getMetadataValueIgnoreCase(Map<String, String> metadata, String key) {
        if (metadata == null || metadata.isEmpty() || !StringUtils.hasText(key)) {
            return null;
        }
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            if (entry == null || !StringUtils.hasText(entry.getKey())) {
                continue;
            }
            if (entry.getKey().trim().equalsIgnoreCase(key.trim())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String buildBatchItemKey(Map<String, Object> item,
                                     Map<String, com.ai.infrastructure.intent.action.AIActionParamSchema> props) {
        if (item == null || item.isEmpty()) {
            return "";
        }
        if (props == null || props.isEmpty()) {
            return item.toString();
        }
        StringBuilder sb = new StringBuilder(64);
        for (String prop : props.keySet()) {
            if (!StringUtils.hasText(prop)) {
                continue;
            }
            Object value = item.get(prop);
            if (value == null) {
                continue;
            }
            sb.append(prop.trim().toLowerCase(java.util.Locale.ROOT)).append('=')
                .append(value.toString().trim().toLowerCase(java.util.Locale.ROOT)).append(';');
        }
        String key = sb.toString();
        return StringUtils.hasText(key) ? key : item.toString();
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
        // In action-first modes (e.g., cart assistant / executor), an empty list is a valid, user-visible result.
        // Falling back to RAG here makes it look like the action wasn't executed.
        OrchestrationPolicy policy = pipelineContext != null ? pipelineContext.getOrchestrationPolicy() : null;
        if (policy != null
            && policy.capabilities() != null
            && policy.capabilities().actionsPreferred()) {
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

        OrchestrationResult result = handleInformation(infoIntent, context, pipelineContext);
        if (result == null) {
            return null;
        }

        boolean exposeProbe = policy != null
            && policy.capabilities() != null
            && policy.capabilities().exposeReadProbeFallbackAttempt();

        if (exposeProbe) {
            Map<String, Object> probe = new LinkedHashMap<>();
            probe.put("action", meta.getName());
            if (meta.getAccessMode() != null) {
                probe.put("accessMode", meta.getAccessMode().name());
            }
            probe.put("success", true);
            probe.put("emptyPayload", true);
            if (StringUtils.hasText(actionResult.getMessage())) {
                probe.put("message", actionResult.getMessage());
            }
            probe.put("fallbackToRag", true);

            Map<String, Object> merged = new LinkedHashMap<>();
            if (result.getMetadata() != null && !result.getMetadata().isEmpty()) {
                merged.putAll(result.getMetadata());
            }
            merged.put("readProbe", Collections.unmodifiableMap(probe));
            result.setMetadata(Collections.unmodifiableMap(merged));
        }

        return result;
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

	    /**
	     * Best-effort check for whether an INFORMATION result has any retrieval evidence.
	     *
	     * <p>This is used to decide whether deep-mode fallbacks should broaden retrieval (fan-out)
	     * rather than returning an ungrounded generated answer.</p>
	     */
	    private boolean isNoEvidenceRagResult(OrchestrationResult result) {
	        if (result == null || result.getData() == null) {
	            return false;
	        }
	        if (!(result.getData() instanceof Map<?, ?> data)) {
	            return false;
	        }

	        int docsCount = 0;
	        Object docsObj = data.get(DATA_KEY_DOCUMENTS);
	        if (docsObj instanceof List<?> list) {
	            docsCount = list.size();
	        }

	        String ctx = null;
	        Object ragObj = data.get(DATA_KEY_RAG_RESPONSE);
	        if (ragObj instanceof RAGResponse rag) {
	            ctx = rag.getContext();
	            if (rag.getDocuments() != null && !rag.getDocuments().isEmpty()) {
	                docsCount = Math.max(docsCount, rag.getDocuments().size());
	            }
	        } else if (ragObj != null) {
	            ctx = ragObj.toString();
	        }

	        Double confidence = null;
	        Object confObj = data.get(DATA_KEY_CONFIDENCE_SCORE);
	        if (confObj instanceof Number n) {
	            confidence = n.doubleValue();
	        }

	        boolean noDocs = docsCount <= 0;
	        boolean noContext = !StringUtils.hasText(ctx) || RAG_NO_CONTEXT_MESSAGE.equals(ctx);
	        boolean lowConfidence = confidence == null || confidence <= 0.0d;
	        return noDocs && noContext && lowConfidence;
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

    private enum ConfirmationResolutionDecision {
        POSITIVE,
        NEGATIVE,
        UNKNOWN
    }

    private record ConfirmationResolutionOutcome(
        ConfirmationResolutionDecision decision,
        double confidence,
        Map<String, Object> debugMetadata
    ) {
    }

    /**
     * If there is a pending action, and the LLM emitted the same ACTION again, try to interpret the user's
     * message as confirmation/rejection using a dedicated confirmation prompt.
     *
     * <p>This avoids brittle backend string-matching ("yes/no") while preventing repeated CONFIRMATION_REQUIRED loops.</p>
     */
    private OrchestrationResult maybeResolvePendingConfirmationForMisclassifiedAction(String actionName,
                                                                                      Map<String, Object> effectiveParams,
                                                                                      String confirmationMessage,
                                                                                      OrchestrationContext context,
                                                                                      PipelineContext pipelineContext) {
        if (!StringUtils.hasText(actionName)
            || context == null
            || !context.hasConversation()
            || pendingActionStore == null
            || aiCoreService == null
            || promptTemplateResolver == null
            || promptRenderer == null) {
            return null;
        }

        PendingAction pending;
        try {
            pending = pendingActionStore.peekPendingAction(context.getConversationId(), context.getIdentifier()).orElse(null);
        } catch (Exception ex) {
            return null;
        }
        if (pending == null || !StringUtils.hasText(pending.action())) {
            return null;
        }
        if (!actionName.trim().equalsIgnoreCase(pending.action().trim())) {
            return null;
        }

        Map<String, Object> pendingParams = pending.actionParams() != null ? pending.actionParams() : Map.of();
        Map<String, Object> currentParams = effectiveParams != null ? effectiveParams : Map.of();
        if (!actionParamsEquivalentOrSubset(currentParams, pendingParams)) {
            // The user might be adjusting params rather than confirming; do not force-confirm.
            return null;
        }

        // Use the original user message, never the processed query (which may include injected context blocks).
        String userMessage = pipelineContext != null ? pipelineContext.getOriginalQuery() : null;
        if (!StringUtils.hasText(userMessage)) {
            return null;
        }

        ConfirmationResolutionOutcome outcome = resolveConfirmationDecision(
            pending.action(),
            StringUtils.hasText(pending.description()) ? pending.description() : confirmationMessage,
            userMessage,
            context
        );
        if (outcome == null || outcome.decision() == null || outcome.decision() == ConfirmationResolutionDecision.UNKNOWN) {
            return null;
        }

        OrchestrationResult resolved;
        if (outcome.decision() == ConfirmationResolutionDecision.POSITIVE) {
            resolved = handleConfirmationPositive(context, pipelineContext);
        } else {
            resolved = handleConfirmationNegative(context, pipelineContext);
        }

        if (resolved == null) {
            return null;
        }

        Map<String, Object> merged = new LinkedHashMap<>(resolved.getMetadata() != null ? resolved.getMetadata() : Map.of());
        merged.put("pendingActionResolution", outcome.debugMetadata() != null ? outcome.debugMetadata() : Map.of());
        resolved.setMetadata(Collections.unmodifiableMap(merged));
        return resolved;
    }

    private boolean actionParamsEquivalentOrSubset(Map<String, Object> currentParams, Map<String, Object> pendingParams) {
        if (currentParams == null || currentParams.isEmpty()) {
            return true;
        }
        if (pendingParams == null || pendingParams.isEmpty()) {
            return currentParams.isEmpty();
        }
        if (pendingParams.equals(currentParams)) {
            return true;
        }
        // Subset check: allow missing keys in currentParams as long as provided keys match.
        for (Map.Entry<String, Object> entry : currentParams.entrySet()) {
            if (entry == null) {
                continue;
            }
            String key = entry.getKey();
            if (!StringUtils.hasText(key)) {
                continue;
            }
            if (!pendingParams.containsKey(key)) {
                return false;
            }
            Object pendingValue = pendingParams.get(key);
            if (!valuesEquivalentOrSubset(entry.getValue(), pendingValue)) {
                return false;
            }
        }
        return true;
    }

    private boolean valuesEquivalentOrSubset(Object currentValue, Object pendingValue) {
        if (currentValue == null) {
            return true;
        }
        if (pendingValue == null) {
            return false;
        }
        if (java.util.Objects.equals(currentValue, pendingValue)) {
            return true;
        }

        if (currentValue instanceof String currentText && pendingValue instanceof String pendingText) {
            return currentText.trim().equalsIgnoreCase(pendingText.trim());
        }

        if (currentValue instanceof Number currentNumber && pendingValue instanceof Number pendingNumber) {
            return Double.compare(currentNumber.doubleValue(), pendingNumber.doubleValue()) == 0;
        }

        if (currentValue instanceof Map<?, ?> currentMap && pendingValue instanceof Map<?, ?> pendingMap) {
            return mapEquivalentOrSubset(currentMap, pendingMap);
        }

        if (currentValue instanceof List<?> currentList && pendingValue instanceof List<?> pendingList) {
            return listEquivalentOrSubset(currentList, pendingList);
        }

        return false;
    }

    private boolean mapEquivalentOrSubset(Map<?, ?> currentMap, Map<?, ?> pendingMap) {
        if (currentMap == null || currentMap.isEmpty()) {
            return true;
        }
        if (pendingMap == null || pendingMap.isEmpty()) {
            return currentMap == null || currentMap.isEmpty();
        }
        for (Map.Entry<?, ?> entry : currentMap.entrySet()) {
            if (entry == null || entry.getKey() == null) {
                continue;
            }
            if (!pendingMap.containsKey(entry.getKey())) {
                return false;
            }
            if (!valuesEquivalentOrSubset(entry.getValue(), pendingMap.get(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }

    private boolean listEquivalentOrSubset(List<?> currentList, List<?> pendingList) {
        if (currentList == null || currentList.isEmpty()) {
            return true;
        }
        if (pendingList == null || pendingList.isEmpty()) {
            return false;
        }

        // Subset: each current element must appear in pending list (order-insensitive).
        for (Object currentElement : currentList) {
            boolean found = false;
            for (Object pendingElement : pendingList) {
                if (valuesEquivalentOrSubset(currentElement, pendingElement)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private ConfirmationResolutionOutcome resolveConfirmationDecision(String actionName,
                                                                     String confirmationMessage,
                                                                     String userMessage,
                                                                     OrchestrationContext context) {
        String safeAction = StringUtils.hasText(actionName) ? actionName.trim() : "";
        String safeUserMessage = StringUtils.hasText(userMessage) ? userMessage.trim() : "";

        String systemPrompt;
        String userPrompt;
        try {
            systemPrompt = promptRenderer.render(
                promptTemplateResolver.resolve(TEMPLATE_FAMILY_CONFIRMATION_RESOLUTION, TEMPLATE_CONFIRMATION_RESOLUTION_SYSTEM).template(),
                Map.of()
            );
            userPrompt = promptRenderer.render(
                promptTemplateResolver.resolve(TEMPLATE_FAMILY_CONFIRMATION_RESOLUTION, TEMPLATE_CONFIRMATION_RESOLUTION_USER).template(),
                Map.of(
                    "action_name", safeAction,
                    "confirmation_message", StringUtils.hasText(confirmationMessage) ? confirmationMessage.trim() : "",
                    "user_query", safeUserMessage
                )
            );
        } catch (Exception ex) {
            return null;
        }

        AIGenerationRequest request = AIGenerationRequest.builder()
            .entityId("confirm-" + UUID.randomUUID())
            .entityType("confirmation")
            .generationType("confirmation_resolution")
            .systemPrompt(systemPrompt)
            .prompt(userPrompt)
            .maxTokens(120)
            .temperature(0.0d)
            .authContext(context != null ? OrchestrationAuthContextResolver.from(context) : null)
            .build();

        AIGenerationResponse response;
        try {
            response = aiCoreService.generateContent(request, LlmPurpose.ORCHESTRATION);
        } catch (Exception ex) {
            return new ConfirmationResolutionOutcome(
                ConfirmationResolutionDecision.UNKNOWN,
                0.0d,
                Map.of("used", true, "error", "llm_call_failed")
            );
        }

        String content = response != null ? response.getContent() : null;
        ConfirmationResolutionDecision decision = parseConfirmationDecision(content);
        double confidence = parseConfirmationConfidence(content);

        Map<String, Object> debug = new LinkedHashMap<>();
        debug.put("used", true);
        debug.put("action", safeAction);
        debug.put("decision", decision != null ? decision.name() : "UNKNOWN");
        debug.put("confidence", confidence);
        debug.put("model", response != null ? response.getModel() : null);
        return new ConfirmationResolutionOutcome(
            decision != null ? decision : ConfirmationResolutionDecision.UNKNOWN,
            confidence,
            Collections.unmodifiableMap(debug)
        );
    }

    private ConfirmationResolutionDecision parseConfirmationDecision(String content) {
        if (!StringUtils.hasText(content)) {
            return ConfirmationResolutionDecision.UNKNOWN;
        }

        ObjectMapper mapper = objectMapperProvider != null ? objectMapperProvider.getIfAvailable() : null;
        if (mapper == null) {
            mapper = new ObjectMapper();
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = mapper.readValue(content, Map.class);
            Object value = map != null ? map.get("decision") : null;
            if (!(value instanceof String text) || !StringUtils.hasText(text)) {
                return ConfirmationResolutionDecision.UNKNOWN;
            }
            String normalized = text.trim().toUpperCase(java.util.Locale.ROOT);
            return switch (normalized) {
                case "POSITIVE", "CONFIRM", "CONFIRMED", "YES", "APPROVE", "APPROVED" -> ConfirmationResolutionDecision.POSITIVE;
                case "NEGATIVE", "REJECT", "REJECTED", "NO", "CANCEL", "CANCELLED" -> ConfirmationResolutionDecision.NEGATIVE;
                default -> ConfirmationResolutionDecision.UNKNOWN;
            };
        } catch (Exception ignored) {
            return ConfirmationResolutionDecision.UNKNOWN;
        }
    }

    private double parseConfirmationConfidence(String content) {
        if (!StringUtils.hasText(content)) {
            return 0.0d;
        }
        ObjectMapper mapper = objectMapperProvider != null ? objectMapperProvider.getIfAvailable() : null;
        if (mapper == null) {
            mapper = new ObjectMapper();
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = mapper.readValue(content, Map.class);
            Object value = map != null ? map.get("confidence") : null;
            if (value instanceof Number number) {
                double raw = number.doubleValue();
                if (Double.isNaN(raw) || Double.isInfinite(raw)) {
                    return 0.0d;
                }
                return Math.max(0.0d, Math.min(1.0d, raw));
            }
        } catch (Exception ignored) {
            // ignore
        }
        return 0.0d;
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

        String userPrompt = buildPostActionUserPrompt(
            instruction,
            relationalQuery,
            facts.payload(),
            extractPromptPreview(pipelineContext)
        );

        AIGenerationRequest generationRequest = AIGenerationRequest.builder()
            .entityId("post-action-" + (pipelineContext != null ? pipelineContext.getRequestId() : UUID.randomUUID()))
            .entityType(ACTION_RELATIONSHIP_QUERY)
            .generationType("relationship_query_post_action_generation")
            .systemPrompt(systemPrompt)
            .prompt(userPrompt)
            .temperature(relationshipQueryPostActionGenerationProperties.getTemperature())
            .maxTokens(800)
            .authContext(context != null ? OrchestrationAuthContextResolver.from(context) : null)
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
        String userPrompt = buildGenericPostActionUserPrompt(
            safeActionName,
            instruction,
            safeFacts,
            extractPromptPreview(pipelineContext)
        );

        AIGenerationRequest generationRequest = AIGenerationRequest.builder()
            .entityId("post-action-" + (pipelineContext != null ? pipelineContext.getRequestId() : UUID.randomUUID()))
            .entityType(actionName)
            .generationType("action_post_action_generation")
            .systemPrompt(systemPrompt)
            .prompt(userPrompt)
            .temperature(postActionGenerationProperties.getTemperature())
            .maxTokens(postActionGenerationProperties.getMaxTokens())
            .authContext(context != null ? OrchestrationAuthContextResolver.from(context) : null)
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

    private String buildPostActionUserPrompt(String instruction,
                                             String relationalQuery,
                                             String facts,
                                             Map<String, String> promptOverlay) {
        String queryPart = StringUtils.hasText(relationalQuery) ? relationalQuery : "(unknown)";
        String safeInstruction = StringUtils.hasText(instruction) ? instruction.trim() : "Summarize the results for the user.";
        String safeFacts = facts != null ? facts : "";

        if (hasManagedGenerationPromptOverride(promptOverlay)) {
            return renderManagedGenerationPrompt(
                TEMPLATE_FAMILY_POST_ACTION_GENERATION,
                TEMPLATE_POST_ACTION_USER_RELATIONSHIP_QUERY_MANAGED,
                Map.of(
                    PLACEHOLDER_INSTRUCTION, safeInstruction,
                    PLACEHOLDER_RELATIONAL_QUERY, queryPart,
                    PLACEHOLDER_FACTS, safeFacts
                ),
                promptOverlay
            );
        }

        return promptRenderer.render(
            promptTemplateResolver.resolve(TEMPLATE_FAMILY_POST_ACTION_GENERATION, TEMPLATE_POST_ACTION_USER_RELATIONSHIP_QUERY).template(),
            Map.of(
                PLACEHOLDER_INSTRUCTION, safeInstruction,
                PLACEHOLDER_RELATIONAL_QUERY, queryPart,
                PLACEHOLDER_FACTS, safeFacts
            )
        );
    }

    private String buildGenericPostActionUserPrompt(String actionName,
                                                    String instruction,
                                                    String facts,
                                                    Map<String, String> promptOverlay) {
        if (hasManagedGenerationPromptOverride(promptOverlay)) {
            return renderManagedGenerationPrompt(
                TEMPLATE_FAMILY_POST_ACTION_GENERATION,
                TEMPLATE_POST_ACTION_USER_GENERIC_MANAGED,
                Map.of(
                    PLACEHOLDER_ACTION_NAME, actionName,
                    PLACEHOLDER_INSTRUCTION, instruction,
                    PLACEHOLDER_FACTS, facts
                ),
                promptOverlay
            );
        }

        return promptRenderer.render(
            promptTemplateResolver.resolve(TEMPLATE_FAMILY_POST_ACTION_GENERATION, TEMPLATE_POST_ACTION_USER_GENERIC).template(),
            Map.of(
                PLACEHOLDER_ACTION_NAME, actionName,
                PLACEHOLDER_INSTRUCTION, instruction,
                PLACEHOLDER_FACTS, facts
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
	        OrchestrationPolicy policy = pipelineContext != null ? pipelineContext.getOrchestrationPolicy() : null;
	        OrchestrationPolicy.OrchestrationCapabilities capabilities = policy != null ? policy.capabilities() : null;
	        boolean retrievalEnabled = policy == null
	            || policy.capabilities() == null
	            || policy.capabilities().retrievalEnabled();
	        OrchestrationPolicy.RagBudgets ragBudgets = policy != null ? policy.ragBudgets() : null;
	        boolean deepRetrievalEnabled = capabilities != null && capabilities.deepRetrievalEnabled();
	        boolean retrievalAllowlistRequired = capabilities != null && capabilities.retrievalAllowlistRequired();
	        boolean vectorSpaceSelectionRequired = capabilities != null && capabilities.vectorSpaceSelectionRequired();
	        boolean fanoutAllowed = ragBudgets == null
	            || ragBudgets.fanoutEnabled() == null
	            || Boolean.TRUE.equals(ragBudgets.fanoutEnabled());

        boolean deterministic = isDeterministicInformationMode(pipelineContext);
        boolean requiresRetrieval = intent.requiresRetrievalOrDefault(true);
        boolean llmRequiresGeneration = intent.requiresGenerationOrDefault(false);
        boolean minimizeRagHeuristicEnabled = capabilities == null || capabilities.minimizeRagWhenPinnedTargetsCoverRequest();

        boolean forceRetrievalWhenTargetsPresent = capabilities != null && capabilities.forceRetrievalWhenTargetsPresent();
        boolean forceRetrievalConsiderStoredTargets = capabilities != null && capabilities.forceRetrievalConsiderStoredTargets();

        boolean ackLike = !requiresRetrieval
            && !llmRequiresGeneration
            && StringUtils.hasText(intent.getDirectAnswer());

        boolean retrievalForced = false;
        String retrievalForcedReason = null;

        if (!requiresRetrieval
            && retrievalEnabled
            && forceRetrievalWhenTargetsPresent
            && !ackLike
            && pipelineContext != null
            && pipelineContext.getResolvedTargets() != null
            && !pipelineContext.getResolvedTargets().isEmpty()) {

            List<ResolvedTarget> targets = pipelineContext.getResolvedTargets();
            boolean hasActiveTargets = targets.stream()
                .filter(java.util.Objects::nonNull)
                .anyMatch(target -> target.getSource() == ResolvedTargetSource.REQUEST_ATTACHMENTS);

            boolean hasStoredTargets = forceRetrievalConsiderStoredTargets && targets.stream()
                .filter(java.util.Objects::nonNull)
                .anyMatch(target -> target.getSource() == ResolvedTargetSource.SESSION_METADATA
                    || target.getSource() == ResolvedTargetSource.WORKING_SET);

            if (hasActiveTargets || hasStoredTargets) {
                requiresRetrieval = true;
                retrievalForced = true;
                retrievalForcedReason = hasActiveTargets ? "ACTIVE_TARGETS" : "STORED_TARGETS";
            }
        }

        boolean skippedRetrievalForPinnedTargets = deterministic
            && minimizeRagHeuristicEnabled
            && requiresRetrieval
            && shouldSkipRetrievalForPinnedTargets(intent, pipelineContext);
        if (skippedRetrievalForPinnedTargets) {
            requiresRetrieval = false;
        }

        boolean needsGeneration = skippedRetrievalForPinnedTargets
            ? true
            : (requiresRetrieval ? (deterministic || llmRequiresGeneration) : llmRequiresGeneration);
        if (requiresRetrieval
            && !needsGeneration
            && orchestrationProperties != null
            && orchestrationProperties.isAlwaysGenerateInformation()) {
            needsGeneration = true;
        }

        String optimizedQuery = StringUtils.hasText(intent.getOptimizedQuery()) ? intent.getOptimizedQuery() : null;
        String processedQuery = pipelineContext != null ? pipelineContext.getEffectiveQuery() : null;
        String retrievalBaseQuery = StringUtils.hasText(optimizedQuery)
            ? optimizedQuery
            : (StringUtils.hasText(processedQuery) ? processedQuery : intent.getIntentOrAction());
        String generationQuery = StringUtils.hasText(processedQuery) ? processedQuery : retrievalBaseQuery;
        
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(METADATA_KEY_SOURCE, METADATA_VALUE_ORCHESTRATOR);
        metadata.put(METADATA_KEY_USER_ID, context.getIdentifier());
        metadata.put(METADATA_KEY_SESSION_ID, context.getSessionId());
        metadata.put(METADATA_KEY_AUTHENTICATED, context.isAuthenticated());
        metadata.put(DATA_KEY_REQUIRES_GENERATION, needsGeneration);
        metadata.put("requiresRetrieval", requiresRetrieval);
        metadata.put("minimizeRagHeuristicEnabled", minimizeRagHeuristicEnabled);
        if (forceRetrievalWhenTargetsPresent) {
            metadata.put("forceRetrievalWhenTargetsPresentEnabled", true);
            metadata.put("forceRetrievalConsiderStoredTargetsEnabled", forceRetrievalConsiderStoredTargets);
        }
        if (retrievalForced) {
            metadata.put("retrievalForced", true);
            metadata.put("retrievalForcedReason", retrievalForcedReason);
        }
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

        if (requiresRetrieval && !retrievalEnabled) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("reason", "RETRIEVAL_DISABLED_BY_POLICY");
            return OrchestrationResult.builder()
                .type(OrchestrationResultType.CLARIFICATION_REQUIRED)
                .success(false)
                .message("Retrieval is disabled by server policy for this request.")
                .data(Collections.unmodifiableMap(data))
                .nextSteps(extractNextSteps(intent))
                .build();
        }

        if (requiresRetrieval
            && retrievalAllowlistRequired
            && (ragBudgets == null || !ragBudgets.hasVectorSpaceAllowlist())) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("reason", "RETRIEVAL_ALLOWLIST_REQUIRED");
            return OrchestrationResult.builder()
                .type(OrchestrationResultType.CLARIFICATION_REQUIRED)
                .success(false)
                .message("Retrieval requires a configured vectorSpace allowlist in this mode.")
                .data(Collections.unmodifiableMap(data))
                .nextSteps(extractNextSteps(intent))
                .build();
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

        List<String> vectorSpacesRaw = parseVectorSpaces(intent != null ? intent.getVectorSpace() : null);
        VectorSpaceValidation validation = validateRequestedVectorSpaces(vectorSpacesRaw);
        if (validation != null && validation.hasInvalid()) {
            metadata.put("vectorSpacesInvalidRequested", validation.invalid());
        }

        List<String> vectorSpaces = validation != null ? validation.valid() : vectorSpacesRaw;
        String vectorSpacesSelectionSource = !vectorSpaces.isEmpty()
            ? ((validation != null && validation.normalizedOrFiltered()) ? "LLM_VALIDATED" : "LLM")
            : null;

        // Keep intent.vectorSpace consistent with what we'll actually search.
        if (intent != null) {
            intent.setVectorSpace(vectorSpaces.isEmpty() ? null : String.join(",", vectorSpaces));
        }
        if (vectorSpaces.isEmpty()
            && ragBudgets != null
            && ragBudgets.hasVectorSpaceAllowlist()) {
            List<String> allowlist = ragBudgets.retrievalVectorSpacesAllowlist();
            if (vectorSpaceSelectionRequired && allowlist.size() > 1) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("allowedVectorSpaces", allowlist);
                data.put("reason", "VECTOR_SPACE_REQUIRED_BY_POLICY");
                return OrchestrationResult.builder()
                    .type(OrchestrationResultType.CLARIFICATION_REQUIRED)
                    .success(false)
                    .message("Which knowledge base domain should I search?")
                    .data(Collections.unmodifiableMap(data))
                    .nextSteps(extractNextSteps(intent))
                    .build();
            }

            vectorSpaces = allowlist;
            intent.setVectorSpace(String.join(",", vectorSpaces));
            vectorSpacesSelectionSource = allowlist.size() == 1 ? "ALLOWLIST_SINGLETON" : "ALLOWLIST";
        }
        if (vectorSpaces.isEmpty()) {
            List<String> allSpaces = deterministic
                ? resolveDeterministicFallbackVectorSpaces(ragBudgets)
                : resolveAllVectorSpaces();
            if (!allSpaces.isEmpty()) {
                allSpaces = capVectorSpacesToBudget(allSpaces, ragBudgets);
                vectorSpaces = allSpaces;
                intent.setVectorSpace(String.join(",", allSpaces));
                vectorSpacesSelectionSource = deterministic ? "DETERMINISTIC_FALLBACK" : "KB_OVERVIEW";
            }
        }

        if (!vectorSpaces.isEmpty()) {
            vectorSpaces = capVectorSpacesToBudget(vectorSpaces, ragBudgets);
            if (vectorSpacesSelectionSource == null) {
                vectorSpacesSelectionSource = "UNKNOWN";
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

        if (ragBudgets != null && ragBudgets.hasVectorSpaceAllowlist()) {
            List<String> allowlist = ragBudgets.retrievalVectorSpacesAllowlist();
            List<String> denied = vectorSpaces.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(vs -> !allowlist.contains(vs.toLowerCase(Locale.ROOT)))
                .toList();
            if (!denied.isEmpty()) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("allowedVectorSpaces", allowlist);
                data.put("requestedVectorSpaces", vectorSpaces);
                data.put("deniedVectorSpaces", denied);
                data.put("reason", "VECTOR_SPACE_NOT_ALLOWED_BY_POLICY");
                return OrchestrationResult.builder()
                    .type(OrchestrationResultType.CLARIFICATION_REQUIRED)
                    .success(false)
                    .message("That request requires retrieval from a vector space that is not allowed in this mode.")
                    .data(Collections.unmodifiableMap(data))
                    .nextSteps(extractNextSteps(intent))
                    .build();
            }
        }

        if (!fanoutAllowed && vectorSpaces.size() > 1) {
            metadata.put("fanoutSuppressed", true);
            metadata.put("fanoutSuppressedReason", "POLICY");
            metadata.put("fanoutSuppressedRequestedSpaces", vectorSpaces);
            vectorSpaces = List.of(vectorSpaces.getFirst());
            intent.setVectorSpace(vectorSpaces.getFirst());
        }

        metadata.put("retrievalStrategy", vectorSpaces.size() > 1 ? "FAN_OUT" : "SINGLE_SPACE");
        metadata.put("vectorSpacesSelected", vectorSpaces);
        metadata.put("vectorSpacesSelectionSource", vectorSpacesSelectionSource);
        if (ragBudgets != null && ragBudgets.fanoutEnabled() != null) {
            metadata.put("fanoutEnabledEffective", ragBudgets.fanoutEnabled());
        }
        if (ragBudgets != null && ragBudgets.maxSpaces() != null) {
            metadata.put("ragMaxSpacesEffective", ragBudgets.maxSpaces());
        }

        String retrievalQuery = applyRetrievalQueryHint(retrievalBaseQuery, pipelineContext, intent, metadata);

	        // Prefer the LLM-provided optimizedQuery (when present) as the base for the embedding query.
	        // The user query may be too short/ambiguous (e.g., "price?", "compare") while optimizedQuery carries
	        // the resolved intent semantics and identifiers.
	        String embeddingBaseQuery = retrievalBaseQuery;
	        embeddingBaseQuery = applyRetrievalQueryHint(embeddingBaseQuery, pipelineContext, intent, null);

	        boolean forcedTargetResolution = false;
	        if (deepRetrievalEnabled
	            && forceRetrievalWhenTargetsPresent
	            && pipelineContext != null
	            && pipelineContext.getResolvedTargets() != null
	            && !pipelineContext.getResolvedTargets().isEmpty()
	            && intent != null
	            && !Boolean.TRUE.equals(intent.getRequiresTargetResolution())) {
	            // Deep mode is specifically intended to handle implicit follow-ups like "negative reviews?"
	            // where the user relies on pinned targets. Make the embedding query eligible for target hints.
	            intent.setRequiresTargetResolution(true);
	            forcedTargetResolution = true;
	        }

	        EmbeddingQueryComposer.Result embedding = EmbeddingQueryComposer.compose(
	            embeddingBaseQuery,
	            intent,
	            pipelineContext,
	            orchestrationProperties
	        );

        if (StringUtils.hasText(processedQuery)) {
            metadata.put(EmbeddingQueryComposer.METADATA_KEY_USER_QUERY, processedQuery);
        }
        if (embedding != null && StringUtils.hasText(embedding.embeddingQuery())) {
            metadata.put(EmbeddingQueryComposer.METADATA_KEY_EMBEDDING_QUERY, embedding.embeddingQuery());
        }
	        if (embedding != null) {
	            metadata.put(EmbeddingQueryComposer.METADATA_KEY_TARGET_HINT_ENABLED, embedding.targetHintEnabled());
	            metadata.put(EmbeddingQueryComposer.METADATA_KEY_TARGET_HINT_APPLIED, embedding.targetHintApplied());
	            metadata.put(EmbeddingQueryComposer.METADATA_KEY_TARGET_HINT_TARGETS_USED, embedding.targetHintTargetsUsed());
	            metadata.put(EmbeddingQueryComposer.METADATA_KEY_TARGET_HINT_CHARS, embedding.targetHintChars());
	        }
	        if (forcedTargetResolution) {
	            metadata.put("requiresTargetResolutionForced", true);
	        }

        if (vectorSpaces.size() > 1) {
            return handleInformationFanOut(intent, context, pipelineContext, deterministic, needsGeneration, generationQuery, retrievalQuery, metadata, vectorSpaces, ragBudgets);
        }

        String advancedDecisionQuery = StringUtils.hasText(optimizedQuery)
            ? optimizedQuery
            : (pipelineContext != null && StringUtils.hasText(pipelineContext.getOriginalQuery())
                ? pipelineContext.getOriginalQuery()
                : generationQuery);

	        if (shouldUseAdvancedRag(intent, needsGeneration, advancedDecisionQuery, context, pipelineContext)) {
	            String advancedQuery = embedding != null && StringUtils.hasText(embedding.embeddingQuery())
	                ? embedding.embeddingQuery()
	                : retrievalQuery;
	            OrchestrationResult advanced = handleInformationAdvanced(intent, context, pipelineContext, needsGeneration, generationQuery, advancedQuery, metadata);
	            if (advanced != null) {
	                if (deepRetrievalEnabled
	                    && fanoutAllowed
	                    && isNoEvidenceRagResult(advanced)) {
	                    List<String> fallbackSpaces = resolveAllVectorSpaces();
	                    fallbackSpaces = capVectorSpacesToBudget(fallbackSpaces, ragBudgets);
	                    if (fallbackSpaces.size() > 1) {
	                        // Deep mode: when Advanced RAG returns no documents/confidence, broaden retrieval across
	                        // all configured spaces (e.g., reviews/policies) instead of returning a potentially
	                        // ungrounded generated answer.
	                        metadata.put("advancedRagFallback", true);
	                        metadata.put("advancedRagFallbackReason", "NO_RELEVANT_RESULTS");

	                        vectorSpaces = fallbackSpaces;
	                        intent.setVectorSpace(String.join(",", vectorSpaces));
	                        metadata.put("retrievalStrategy", "FAN_OUT");
	                        metadata.put("vectorSpacesSelected", vectorSpaces);
	                        metadata.put("vectorSpacesSelectionSource", "ADVANCED_FALLBACK_FAN_OUT");

	                        return handleInformationFanOut(intent, context, pipelineContext, deterministic, needsGeneration, generationQuery, retrievalQuery, metadata, vectorSpaces, ragBudgets);
	                    }
	                }
	                return advanced;
	            }
	        }

        return handleInformationBasic(intent, context, pipelineContext, needsGeneration, generationQuery, retrievalQuery, metadata, ragBudgets);
    }

    // Retrieval queries must always be derived from the user's actual query (PII-processed if enabled),
    // never from any carrier string that mixes history/attachments into the query.

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
                answer = generateRagAnswer(query, pinnedTargetsContext, pipelineContext);
            } else {
                Map<String, String> promptPreview = extractPromptPreview(pipelineContext);
                // Generation-only informational intent (no retrieval / no vectorSpace required).
                answer = aiCoreService.generateText(
                    hasManagedGenerationPromptOverride(promptPreview)
                        ? buildRagNoContextPrompt(query, promptPreview)
                        : query,
                    LlmPurpose.GENERATION
                );
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
                                                       Map<String, Object> metadata,
                                                       OrchestrationPolicy.RagBudgets ragBudgets) {
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

        int limit = DEFAULT_RAG_LIMIT;
        if (ragBudgets != null && ragBudgets.maxDocumentsReturnedToClient() != null && ragBudgets.maxDocumentsReturnedToClient() > 0) {
            limit = ragBudgets.maxDocumentsReturnedToClient();
        }

        RAGRequest ragRequest = RAGRequest.builder()
            .query(retrievalQuery)
            .entityType(intent.getVectorSpace())
            .limit(limit)
            .threshold(resolveSimilarityThreshold(ragBudgets, DEFAULT_RAG_THRESHOLD))
            .metadata(Collections.unmodifiableMap(metadata))
            .authContext(OrchestrationAuthContextResolver.from(context))
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
	                String baseContext = ragResponse.getContext();
	                if (ragBudgets != null && (ragBudgets.maxDocumentsUsedForContext() != null || ragBudgets.maxContextChars() != null)) {
	                    List<RAGResponse.RAGDocument> docs = ragResponse.getDocuments() != null ? ragResponse.getDocuments() : List.of();
	                    int docsForContext = DEFAULT_RAG_LIMIT;
	                    if (ragBudgets.maxDocumentsUsedForContext() != null && ragBudgets.maxDocumentsUsedForContext() > 0) {
	                        docsForContext = ragBudgets.maxDocumentsUsedForContext();
	                    }
	                    docsForContext = Math.min(docsForContext, docs.size());
	                    Integer maxChars = ragBudgets.maxContextChars();
	                    baseContext = buildContextFromDocuments(docs.subList(0, docsForContext), maxChars);
	                }

	                boolean hasRetrievedEvidence = ragResponse.getDocuments() != null && !ragResponse.getDocuments().isEmpty();
	                if (!hasRetrievedEvidence) {
	                    hasRetrievedEvidence = StringUtils.hasText(baseContext) && !RAG_NO_CONTEXT_MESSAGE.equals(baseContext);
	                }
	                String generationContext = hasRetrievedEvidence
	                    ? prependPinnedTargetsContext(baseContext, pipelineContext)
	                    : baseContext;
	                answer = generateRagAnswer(generationQuery, generationContext, pipelineContext);
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
                                                        List<String> vectorSpaces,
                                                        OrchestrationPolicy.RagBudgets ragBudgets) {
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

        int topKPerSpace = ragBudgets != null && ragBudgets.topKPerSpace() != null && ragBudgets.topKPerSpace() > 0
            ? ragBudgets.topKPerSpace()
            : (vectorSpaceRoutingProperties != null
                ? vectorSpaceRoutingProperties.getFanOutTopKPerSpace()
                : DEFAULT_RAG_LIMIT);

        double fanOutThreshold = vectorSpaceRoutingProperties != null
            ? vectorSpaceRoutingProperties.getFanOutRagThreshold()
            : DEFAULT_FAN_OUT_RAG_THRESHOLD;
        fanOutThreshold = resolveSimilarityThreshold(ragBudgets, fanOutThreshold);

        Map<String, List<RAGResponse.RAGDocument>> docsBySpace = new LinkedHashMap<>();
        for (String vectorSpace : vectorSpaces) {
            RAGRequest ragRequest = RAGRequest.builder()
                .query(retrievalQuery)
                .entityType(vectorSpace)
                .limit(topKPerSpace)
                .threshold(fanOutThreshold)
                .metadata(Collections.unmodifiableMap(new LinkedHashMap<>(metadata)))
                .authContext(OrchestrationAuthContextResolver.from(context))
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

        if (ragBudgets != null
            && ragBudgets.maxDocumentsReturnedToClient() != null
            && ragBudgets.maxDocumentsReturnedToClient() > 0
            && merged.size() > ragBudgets.maxDocumentsReturnedToClient()) {
            merged = merged.subList(0, ragBudgets.maxDocumentsReturnedToClient());
        }

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

        int docsForContext = DEFAULT_RAG_LIMIT;
        if (ragBudgets != null && ragBudgets.maxDocumentsUsedForContext() != null && ragBudgets.maxDocumentsUsedForContext() > 0) {
            docsForContext = ragBudgets.maxDocumentsUsedForContext();
        }
        docsForContext = Math.min(docsForContext, merged.size());
        Integer maxContextChars = ragBudgets != null ? ragBudgets.maxContextChars() : null;
        String mergedContext = buildContextFromDocuments(merged.subList(0, docsForContext), maxContextChars);
        RAGResponse mergedResponse = RAGResponse.builder()
            .documents(merged)
            .context(mergedContext)
            .originalQuery(generationQuery)
            .entityType(String.join(",", vectorSpaces))
            .metadata(metadata != null && !metadata.isEmpty()
                ? Collections.unmodifiableMap(new LinkedHashMap<>(metadata))
                : null)
            .success(true)
            .build();

	        String answer = null;
	        if (needsGeneration) {
	            try {
	                boolean hasRetrievedEvidence = merged != null && !merged.isEmpty();
	                if (!hasRetrievedEvidence) {
	                    hasRetrievedEvidence = StringUtils.hasText(mergedContext) && !RAG_NO_CONTEXT_MESSAGE.equals(mergedContext);
	                }
	                String generationContext = hasRetrievedEvidence
	                    ? prependPinnedTargetsContext(mergedContext, pipelineContext)
	                    : mergedContext;
	                answer = generateRagAnswer(generationQuery, generationContext, pipelineContext);
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
            && orchestrationProperties.getInformationMode() == OrchestrationProperties.InformationMode.DETERMINISTIC_RAG_GENERATE;
    }

    private List<String> resolveAllVectorSpaces() {
        KnowledgeBaseOverviewService overviewService = knowledgeBaseOverviewServiceProvider != null
            ? knowledgeBaseOverviewServiceProvider.getIfAvailable()
            : null;
        if (overviewService == null) {
            return resolveConfiguredVectorSpaces();
        }

        KnowledgeBaseOverview overview = overviewService.getOverview();
        if (overview == null) {
            return resolveConfiguredVectorSpaces();
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
            return resolveConfiguredVectorSpaces();
        }

        return entityTypes.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .distinct()
            .toList();
    }

    private List<String> resolveConfiguredVectorSpaces() {
        if (entityConfigurationLoader == null) {
            return List.of();
        }
        Set<String> supported = entityConfigurationLoader.getSupportedEntityTypes();
        if (supported == null || supported.isEmpty()) {
            return List.of();
        }
        // Stable order for deterministic fan-out behavior when KB stats are unavailable.
        return supported.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .map(s -> s.toLowerCase(java.util.Locale.ROOT))
            .distinct()
            .sorted()
            .toList();
    }

    private List<String> resolveDeterministicFallbackVectorSpaces(OrchestrationPolicy.RagBudgets ragBudgets) {
        List<String> spaces = resolveAllVectorSpaces();
        if (spaces.isEmpty()) {
            return spaces;
        }

        Integer maxOverride = ragBudgets != null ? ragBudgets.maxSpaces() : null;
        if (maxOverride != null && maxOverride > 0) {
            return spaces.size() > maxOverride ? spaces.subList(0, maxOverride) : spaces;
        }

        int maxDefault = vectorSpaceRoutingProperties != null ? vectorSpaceRoutingProperties.getFanOutMaxSpaces() : 3;
        if (maxDefault <= 0) {
            return spaces;
        }
        return spaces.size() > maxDefault ? spaces.subList(0, maxDefault) : spaces;
    }

    private List<String> capVectorSpacesToBudget(List<String> vectorSpaces, OrchestrationPolicy.RagBudgets ragBudgets) {
        if (vectorSpaces == null || vectorSpaces.isEmpty() || ragBudgets == null) {
            return vectorSpaces;
        }
        Integer maxSpaces = ragBudgets.maxSpaces();
        if (maxSpaces == null || maxSpaces <= 0) {
            return vectorSpaces;
        }
        return vectorSpaces.size() > maxSpaces ? vectorSpaces.subList(0, maxSpaces) : vectorSpaces;
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
	            String retrievedContext = ragResponse != null ? ragResponse.getContext() : null;
	            boolean hasRetrievedEvidence = documents != null && !documents.isEmpty();
	            if (!hasRetrievedEvidence) {
	                hasRetrievedEvidence = StringUtils.hasText(retrievedContext) && !RAG_NO_CONTEXT_MESSAGE.equals(retrievedContext);
	            }
	            boolean lowConfidence = advancedResponse.getConfidenceScore() == null || advancedResponse.getConfidenceScore() <= 0.0d;
	            boolean noEvidence = !hasRetrievedEvidence && lowConfidence;

	            String answer = null;
	            if (needsGeneration) {
	                if (StringUtils.hasText(advancedResponse.getResponse()) && !noEvidence) {
	                    answer = advancedResponse.getResponse();
	                } else {
	                    try {
	                        String generationContext = hasRetrievedEvidence
	                            ? prependPinnedTargetsContext(retrievedContext, pipelineContext)
	                            : retrievedContext;
	                        answer = generateRagAnswer(generationQuery, generationContext, pipelineContext);
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
            .similarityThreshold(resolveSimilarityThreshold(
                pipelineContext != null && pipelineContext.getOrchestrationPolicy() != null
                    ? pipelineContext.getOrchestrationPolicy().ragBudgets()
                    : null,
                DEFAULT_RAG_THRESHOLD
            ))
            .authContext(context != null ? OrchestrationAuthContextResolver.from(context) : null)
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

    private double resolveSimilarityThreshold(OrchestrationPolicy.RagBudgets ragBudgets, double defaultThreshold) {
        if (ragBudgets != null && ragBudgets.similarityThreshold() != null) {
            return ragBudgets.similarityThreshold();
        }
        return defaultThreshold;
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

    private String generateRagAnswer(String query, String context, PipelineContext pipelineContext) {
        if (!StringUtils.hasText(query)) {
            return null;
        }
        String safeQuery = query.trim();
        Map<String, String> promptPreview = extractPromptPreview(pipelineContext);

        if (!StringUtils.hasText(context) || RAG_NO_CONTEXT_MESSAGE.equals(context)) {
            if (aiServiceConfig != null
                && aiServiceConfig.getFeatures() != null
                && Boolean.TRUE.equals(aiServiceConfig.getFeatures().getEnableGeneration())) {
                try {
                    String prompt = buildRagNoContextPrompt(safeQuery, promptPreview);
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
        String prompt = buildRagAnswerPrompt(safeQuery, safeContext, promptPreview);
        return aiCoreService.generateText(prompt, LlmPurpose.GENERATION);
    }

    private Map<String, String> extractPromptPreview(PipelineContext pipelineContext) {
        if (pipelineContext == null || pipelineContext.getOrchestrationContext() == null) {
            return Map.of();
        }
        return PromptPreviewOverlaySupport.extract(pipelineContext.getOrchestrationContext().getMetadata());
    }

    private String buildRagNoContextPrompt(String query, Map<String, String> promptOverlay) {
        if (hasManagedGenerationPromptOverride(promptOverlay)) {
            return renderManagedGenerationPrompt(
                TEMPLATE_FAMILY_RAG_GENERATION,
                TEMPLATE_RAG_NO_CONTEXT_MANAGED,
                Map.of(PLACEHOLDER_QUERY, query),
                promptOverlay
            );
        }
        return promptRenderer.render(
            promptTemplateResolver.resolve(TEMPLATE_FAMILY_RAG_GENERATION, TEMPLATE_RAG_NO_CONTEXT).template(),
            Map.of(PLACEHOLDER_QUERY, query)
        );
    }

    private String buildRagAnswerPrompt(String query, String context, Map<String, String> promptOverlay) {
        if (hasManagedGenerationPromptOverride(promptOverlay)) {
            return renderManagedGenerationPrompt(
                TEMPLATE_FAMILY_RAG_GENERATION,
                TEMPLATE_RAG_ANSWER_MANAGED,
                Map.of(
                    PLACEHOLDER_QUERY, query,
                    PLACEHOLDER_CONTEXT, context
                ),
                promptOverlay
            );
        }
        return promptRenderer.render(
            promptTemplateResolver.resolve(TEMPLATE_FAMILY_RAG_GENERATION, TEMPLATE_RAG_ANSWER).template(),
            Map.of(
                PLACEHOLDER_QUERY, query,
                PLACEHOLDER_CONTEXT, context
            )
        );
    }

    private boolean hasManagedGenerationPromptOverride(Map<String, String> promptOverlay) {
        return PromptPreviewOverlaySupport.hasAny(
            promptOverlay,
            "retrievalPrompt",
            "answerGenerationPrompt",
            "assistantUiPrompt"
        );
    }

    private String renderManagedGenerationPrompt(String family,
                                                 String templateName,
                                                 Map<String, String> placeholders,
                                                 Map<String, String> promptOverlay) {
        Map<String, String> values = new LinkedHashMap<>();
        if (placeholders != null && !placeholders.isEmpty()) {
            values.putAll(placeholders);
        }
        values.put(
            PLACEHOLDER_MANAGED_ANSWER_GENERATION_PROMPT,
            PromptPreviewOverlaySupport.resolveOverlayValue(
                promptOverlay,
                "answerGenerationPrompt",
                ManagedPromptDefaults.ANSWER_GENERATION_PROMPT
            )
        );
        values.put(
            PLACEHOLDER_MANAGED_RETRIEVAL_PROMPT,
            PromptPreviewOverlaySupport.resolveOverlayValue(
                promptOverlay,
                "retrievalPrompt",
                ManagedPromptDefaults.RETRIEVAL_PROMPT
            )
        );
        values.put(
            PLACEHOLDER_MANAGED_ASSISTANT_UI_PROMPT,
            PromptPreviewOverlaySupport.resolveOverlayValue(
                promptOverlay,
                "assistantUiPrompt",
                ManagedPromptDefaults.ASSISTANT_UI_PROMPT
            )
        );
        return promptRenderer.render(
            promptTemplateResolver.resolve(family, templateName).template(),
            Collections.unmodifiableMap(values)
        );
    }

    private String prependPinnedTargetsContext(String ragContext, PipelineContext pipelineContext) {
        if (pipelineContext == null) {
            return ragContext;
        }
        List<ResolvedTarget> targets = pipelineContext.getResolvedTargets();
        if (targets == null || targets.isEmpty()) {
            return ragContext;
        }

        String block = null;
        String pinnedTargetsContext = pipelineContext.getPinnedTargetsContext();
        if (StringUtils.hasText(pinnedTargetsContext) && pinnedTargetsContext.trim().startsWith("PINNED TARGETS")) {
            // Reuse the enrichment-rendered pinned targets block (e.g., "previously pinned") when available.
            // Do NOT reuse the attachments prompt block here, since it has a different contract/format.
            block = pinnedTargetsContext.trim();
        }
        if (!StringUtils.hasText(block)) {
            block = buildPinnedTargetsBlock(targets);
        }
        if (!StringUtils.hasText(block)) {
            return ragContext;
        }
        if (!StringUtils.hasText(ragContext)) {
            return block;
        }
        return block + "\n\n" + ragContext;
    }

    private String buildPinnedTargetsBlock(List<ResolvedTarget> targets) {
        String header = resolvePinnedTargetsHeader(targets);
        return com.ai.infrastructure.intent.orchestration.targets.ResolvedTargetsContextRenderer.render(
            header,
            "target",
            targets
        );
    }

    private String resolvePinnedTargetsHeader(List<ResolvedTarget> targets) {
        if (targets == null || targets.isEmpty()) {
            return "PINNED TARGETS:";
        }

        com.ai.infrastructure.intent.orchestration.targets.ResolvedTargetSource uniform = null;
        for (ResolvedTarget target : targets) {
            if (target == null || target.getSource() == null) {
                return "PINNED TARGETS:";
            }
            if (uniform == null) {
                uniform = target.getSource();
                continue;
            }
            if (uniform != target.getSource()) {
                return "PINNED TARGETS:";
            }
        }

        if (uniform == com.ai.infrastructure.intent.orchestration.targets.ResolvedTargetSource.SESSION_METADATA) {
            return "PINNED TARGETS (previously pinned; not current UI selection):";
        }

        return "PINNED TARGETS (authoritative):";
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
        boolean hasRequestAttachments = orchContext != null
            && orchContext.getAttachmentsNormalized() != null
            && !orchContext.getAttachmentsNormalized().isEmpty();

        if (!requiresTargetResolution && !hasRequestAttachments) {
            return false;
        }

        String intentVectorSpace = intent.getVectorSpace();
        if (!StringUtils.hasText(intentVectorSpace)) {
            // vectorSpace is optional. If the model omitted it while still requiring retrieval,
            // do NOT skip retrieval here; allow routing/fan-out to fetch the missing knowledge.
            return false;
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

    private VectorSpaceValidation validateRequestedVectorSpaces(List<String> requestedSpaces) {
        if (requestedSpaces == null || requestedSpaces.isEmpty()) {
            return VectorSpaceValidation.empty();
        }

        Set<String> supportedRaw = entityConfigurationLoader != null ? entityConfigurationLoader.getSupportedEntityTypes() : null;
        if (supportedRaw == null || supportedRaw.isEmpty()) {
            // No configured spaces available; don't block routing, just normalize.
            java.util.LinkedHashSet<String> unique = new java.util.LinkedHashSet<>();
            boolean changed = false;
            for (String space : requestedSpaces) {
                if (!StringUtils.hasText(space)) {
                    changed = true;
                    continue;
                }
                String normalized = space.trim().toLowerCase(java.util.Locale.ROOT);
                changed = changed || !normalized.equals(space);
                if (!unique.add(normalized)) {
                    changed = true;
                }
            }
            return new VectorSpaceValidation(List.copyOf(unique), List.of(), changed);
        }

        Set<String> supported = supportedRaw.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .map(s -> s.toLowerCase(java.util.Locale.ROOT))
            .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));

        if (supported.isEmpty()) {
            return VectorSpaceValidation.empty();
        }

        List<String> valid = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        boolean changed = false;

        for (String space : requestedSpaces) {
            if (!StringUtils.hasText(space)) {
                changed = true;
                continue;
            }
            String normalized = space.trim().toLowerCase(java.util.Locale.ROOT);
            changed = changed || !normalized.equals(space);

            if (!seen.add(normalized)) {
                changed = true;
                continue;
            }

            if (supported.contains(normalized)) {
                valid.add(normalized);
            } else {
                invalid.add(normalized);
                changed = true;
            }
        }

        return new VectorSpaceValidation(List.copyOf(valid), List.copyOf(invalid), changed);
    }

    private record VectorSpaceValidation(List<String> valid, List<String> invalid, boolean normalizedOrFiltered) {
        static VectorSpaceValidation empty() {
            return new VectorSpaceValidation(List.of(), List.of(), false);
        }

        boolean hasInvalid() {
            return invalid != null && !invalid.isEmpty();
        }
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
        return buildContextFromDocuments(documents, null);
    }

    private String buildContextFromDocuments(List<RAGResponse.RAGDocument> documents, Integer maxChars) {
        if (documents == null || documents.isEmpty()) {
            return RAG_NO_CONTEXT_MESSAGE;
        }
        int effectiveMaxChars = maxChars != null && maxChars > 0 ? maxChars : Integer.MAX_VALUE;
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

            if (builder.length() >= effectiveMaxChars) {
                break;
            }
        }
        String out = builder.toString();
        if (out.length() <= effectiveMaxChars) {
            return out;
        }
        return out.substring(0, effectiveMaxChars);
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
    
    private OrchestrationResult handleCompoundIntents(MultiIntentResponse response, OrchestrationContext context, PipelineContext pipelineContext) {
        response = coalesceBatchActionIntents(response);
        if (response == null || response.getIntents() == null || response.getIntents().isEmpty()) {
            return OrchestrationResult.error("No intents to process.");
        }
        if (response.getIntents().size() == 1) {
            return handleSingleIntent(response.getIntents().getFirst(), context, pipelineContext);
        }
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

    /**
     * If the model emits multiple ACTION intents for the same action, but that action exposes a batch-capable
     * array parameter (marked with {@code [batchTargets]} in the paramsSchema), coalesce them into a single
     * ACTION intent by concatenating the batch parameter list.
     *
     * <p>This is schema-driven and domain-agnostic. It reduces multi-confirmation loops and aligns with the
     * "true batch schema" contract for actions like {@code add_to_cart(items=[...])}.</p>
     */
    private MultiIntentResponse coalesceBatchActionIntents(MultiIntentResponse response) {
        if (response == null || response.getIntents() == null || response.getIntents().size() < 2) {
            return response;
        }

        List<Intent> intents = response.getIntents();
        Map<String, List<Integer>> indicesByAction = new LinkedHashMap<>();
        Map<String, String> actionNameByKey = new LinkedHashMap<>();

        for (int i = 0; i < intents.size(); i++) {
            Intent intent = intents.get(i);
            if (intent == null || intent.getType() != com.ai.infrastructure.dto.IntentType.ACTION) {
                continue;
            }
            String actionName = resolveActionName(intent);
            if (!StringUtils.hasText(actionName)) {
                continue;
            }
            AIActionMetaData meta = getMetadataForAction(actionName);
            BatchParamSpec batchSpec = findBatchParamSpec(meta);
            if (batchSpec == null) {
                continue;
            }

            String key = actionName.trim().toLowerCase(java.util.Locale.ROOT);
            indicesByAction.computeIfAbsent(key, ignored -> new ArrayList<>()).add(i);
            actionNameByKey.putIfAbsent(key, actionName);
        }

        if (indicesByAction.isEmpty()) {
            return response;
        }

        Map<Integer, Intent> mergedAtIndex = new LinkedHashMap<>();
        java.util.Set<Integer> remove = new java.util.HashSet<>();

        for (Map.Entry<String, List<Integer>> entry : indicesByAction.entrySet()) {
            List<Integer> indices = entry.getValue();
            if (indices == null || indices.size() < 2) {
                continue;
            }
            String actionName = actionNameByKey.get(entry.getKey());
            AIActionMetaData meta = getMetadataForAction(actionName);
            BatchParamSpec batchSpec = findBatchParamSpec(meta);
            if (batchSpec == null) {
                continue;
            }

            int firstIndex = indices.getFirst();
            Intent first = intents.get(firstIndex);
            if (first == null) {
                continue;
            }

            List<Object> combined = new ArrayList<>();
            for (Integer idx : indices) {
                if (idx == null) {
                    continue;
                }
                Intent it = intents.get(idx);
                if (it == null) {
                    continue;
                }
                Map<String, Object> params = it.getActionParams() != null ? it.getActionParams() : Map.of();
                Object raw = params.get(batchSpec.paramName());
                List<Object> list = coerceToObjectList(raw);
                if (!list.isEmpty()) {
                    combined.addAll(list);
                }
            }

            List<Object> deduped = dedupeListElements(combined);
            if (deduped.isEmpty()) {
                continue;
            }

            Map<String, Object> mergedParams = new LinkedHashMap<>(first.getActionParams() != null ? first.getActionParams() : Map.of());
            mergedParams.put(batchSpec.paramName(), deduped);

            Intent merged = Intent.builder()
                .type(com.ai.infrastructure.dto.IntentType.ACTION)
                .intent(first.getIntent())
                .confidence(first.getConfidence())
                .action(actionName)
                .actionParams(Collections.unmodifiableMap(mergedParams))
                .vectorSpace(first.getVectorSpace())
                .requiresRetrieval(first.getRequiresRetrieval())
                .requiresGeneration(first.getRequiresGeneration())
                .requiresTargetResolution(first.getRequiresTargetResolution())
                .directAnswer(first.getDirectAnswer())
                .generationInstructions(first.getGenerationInstructions())
                .needsAdvancedRAG(first.getNeedsAdvancedRAG())
                .optimizedQuery(first.getOptimizedQuery())
                .nextStepRecommended(first.getNextStepRecommended())
                .build();

            mergedAtIndex.put(firstIndex, merged);
            for (Integer idx : indices) {
                if (idx != null && idx != firstIndex) {
                    remove.add(idx);
                }
            }
        }

        if (mergedAtIndex.isEmpty() || remove.isEmpty()) {
            return response;
        }

        List<Intent> out = new ArrayList<>();
        for (int i = 0; i < intents.size(); i++) {
            if (remove.contains(i)) {
                continue;
            }
            Intent replacement = mergedAtIndex.get(i);
            out.add(replacement != null ? replacement : intents.get(i));
        }

        return MultiIntentResponse.builder()
            .intents(out)
            .orchestrationStrategy(response.getOrchestrationStrategy())
            .metadata(response.getMetadata() != null ? response.getMetadata() : Map.of())
            .build();
    }

    private record BatchParamSpec(String paramName, com.ai.infrastructure.intent.action.AIActionParamSchema schema) {}

    private BatchParamSpec findBatchParamSpec(AIActionMetaData meta) {
        if (meta == null || meta.getParameterSchemas() == null || meta.getParameterSchemas().isEmpty()) {
            return null;
        }
        for (Map.Entry<String, com.ai.infrastructure.intent.action.AIActionParamSchema> entry : meta.getParameterSchemas().entrySet()) {
            if (entry == null || !StringUtils.hasText(entry.getKey()) || entry.getValue() == null) {
                continue;
            }
            com.ai.infrastructure.intent.action.AIActionParamSchema schema = entry.getValue();
            if (!Boolean.TRUE.equals(schema.getBatchTargets())) {
                continue;
            }
            if (schema.getType() != com.ai.infrastructure.intent.action.AIActionParamType.ARRAY) {
                continue;
            }
            if (schema.getItems() == null) {
                continue;
            }
            return new BatchParamSpec(entry.getKey(), schema);
        }
        return null;
    }

    private String resolveActionName(Intent intent) {
        if (intent == null) {
            return null;
        }
        if (StringUtils.hasText(intent.getAction())) {
            return intent.getAction().trim();
        }
        if (StringUtils.hasText(intent.getIntent())) {
            return intent.getIntent().trim();
        }
        return null;
    }

    private List<Object> coerceToObjectList(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            List<Object> out = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    out.add(item);
                }
            }
            return out;
        }
        if (raw instanceof Map<?, ?> map) {
            return List.of(map);
        }
        return List.of();
    }

    private List<Object> dedupeListElements(List<Object> combined) {
        if (combined == null || combined.isEmpty()) {
            return List.of();
        }
        List<Object> out = new ArrayList<>();
        java.util.Set<Object> seen = new java.util.HashSet<>();
        for (Object item : combined) {
            if (item == null) {
                continue;
            }
            if (seen.add(item)) {
                out.add(item);
            }
        }
        return out;
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

    private record EvidenceBundle(
        String userEvidenceLower,
        String pinnedEvidenceLower,
        Map<String, Object> sourcesUsed
    ) {}

    private record ActionParamValidation(
        List<String> missingRequired,
        List<String> provenanceMissing,
        Map<String, Object> debugMetadata
    ) {}

    private ActionParamValidation validateRequiredActionParams(AIActionMetaData meta,
                                                              Map<String, Object> params,
                                                              PipelineContext pipelineContext) {
        if (meta == null || meta.getRequiredParameters() == null || meta.getRequiredParameters().isEmpty()) {
            return null;
        }

        EvidenceBundle evidence = buildEvidenceBundle(pipelineContext);
        String userEvidenceLower = evidence != null && StringUtils.hasText(evidence.userEvidenceLower())
            ? evidence.userEvidenceLower()
            : "";
        String pinnedEvidenceLower = evidence != null && StringUtils.hasText(evidence.pinnedEvidenceLower())
            ? evidence.pinnedEvidenceLower()
            : "";

        String originalQuery = pipelineContext != null ? pipelineContext.getOriginalQuery() : null;
        String normalizedOriginalQuery = StringUtils.hasText(originalQuery) ? originalQuery.trim() : "";

        List<String> missing = new ArrayList<>();
        List<String> provenanceMissing = new ArrayList<>();

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

            // Guardrails: placeholders / instruction text / param-name echoes.
            if (isPlaceholderOrInstructionEcho(required, raw, meta, normalizedOriginalQuery)) {
                missing.add(required);
                continue;
            }

            // Provenance validation: for string params, value must appear in user text history OR pinned targets.
            if (value instanceof String) {
                String needle = raw.trim().toLowerCase(java.util.Locale.ROOT);
                if (StringUtils.hasText(needle)
                    && !userEvidenceLower.contains(needle)
                    && !pinnedEvidenceLower.contains(needle)) {
                    missing.add(required);
                    provenanceMissing.add(required);
                }
            }
        }

        Map<String, Object> debug = new LinkedHashMap<>();
        debug.put("missing", List.copyOf(missing));
        debug.put("provenanceMissing", List.copyOf(provenanceMissing));
        debug.put("sourcesUsed", evidence != null ? evidence.sourcesUsed() : Map.of());
        return new ActionParamValidation(
            List.copyOf(missing),
            List.copyOf(provenanceMissing),
            Collections.unmodifiableMap(debug)
        );
    }

    private EvidenceBundle buildEvidenceBundle(PipelineContext pipelineContext) {
        if (pipelineContext == null) {
            return new EvidenceBundle("", "", Map.of(
                "user", false,
                "history", false,
                "pinned", false
            ));
        }

        StringBuilder userEvidence = new StringBuilder(512);
        boolean hasUser = false;
        if (StringUtils.hasText(pipelineContext.getOriginalQuery())) {
            userEvidence.append(pipelineContext.getOriginalQuery().trim());
            hasUser = true;
        }

        boolean hasHistory = false;
        if (pipelineContext.getHistoryMessages() != null && !pipelineContext.getHistoryMessages().isEmpty()) {
            for (AIChatMessage msg : pipelineContext.getHistoryMessages()) {
                if (msg == null || msg.getRole() != com.ai.infrastructure.dto.AIChatRole.USER) {
                    continue;
                }
                if (!StringUtils.hasText(msg.getContent())) {
                    continue;
                }
                userEvidence.append("\n").append(msg.getContent().trim());
                hasHistory = true;
            }
        }

        StringBuilder pinnedEvidence = new StringBuilder(512);
        boolean hasPinned = false;

        OrchestrationContext orchContext = pipelineContext.getOrchestrationContext();
        List<NormalizedAttachment> attachments = orchContext != null ? orchContext.getAttachmentsNormalized() : null;
        if (attachments != null && !attachments.isEmpty()) {
            for (NormalizedAttachment attachment : attachments) {
                if (attachment == null) {
                    continue;
                }
                if (StringUtils.hasText(attachment.getId())) {
                    pinnedEvidence.append("\n").append(attachment.getId().trim());
                    hasPinned = true;
                }
                if (StringUtils.hasText(attachment.getVectorSpace())) {
                    pinnedEvidence.append("\n").append(attachment.getVectorSpace().trim());
                    hasPinned = true;
                }
                if (StringUtils.hasText(attachment.getContentText())) {
                    pinnedEvidence.append("\n").append(attachment.getContentText().trim());
                    hasPinned = true;
                }
                if (attachment.getMetadata() != null && !attachment.getMetadata().isEmpty()) {
                    for (String value : attachment.getMetadata().values()) {
                        if (!StringUtils.hasText(value)) {
                            continue;
                        }
                        pinnedEvidence.append("\n").append(value.trim());
                        hasPinned = true;
                    }
                }
            }
        }

        List<ResolvedTarget> targets = pipelineContext.getResolvedTargets();
        if (targets != null && !targets.isEmpty()) {
            for (ResolvedTarget target : targets) {
                if (target == null) {
                    continue;
                }
                if (StringUtils.hasText(target.getId())) {
                    pinnedEvidence.append("\n").append(target.getId().trim());
                    hasPinned = true;
                }
                if (StringUtils.hasText(target.getVectorSpace())) {
                    pinnedEvidence.append("\n").append(target.getVectorSpace().trim());
                    hasPinned = true;
                }
                if (StringUtils.hasText(target.getContentText())) {
                    pinnedEvidence.append("\n").append(target.getContentText().trim());
                    hasPinned = true;
                }
                if (target.getMetadata() != null && !target.getMetadata().isEmpty()) {
                    for (String value : target.getMetadata().values()) {
                        if (!StringUtils.hasText(value)) {
                            continue;
                        }
                        pinnedEvidence.append("\n").append(value.trim());
                        hasPinned = true;
                    }
                }
            }
        }

        Map<String, Object> sourcesUsed = Map.of(
            "user", hasUser,
            "history", hasHistory,
            "pinned", hasPinned
        );

        return new EvidenceBundle(
            userEvidence.toString().toLowerCase(java.util.Locale.ROOT),
            pinnedEvidence.toString().toLowerCase(java.util.Locale.ROOT),
            sourcesUsed
        );
    }

    private boolean isPlaceholderOrInstructionEcho(String requiredParamName,
                                                   String rawValue,
                                                   AIActionMetaData meta,
                                                   String normalizedOriginalQuery) {
        String raw = rawValue != null ? rawValue.trim() : "";
        if (!StringUtils.hasText(raw)) {
            return true;
        }

        String lowered = raw.toLowerCase(java.util.Locale.ROOT);
        if (lowered.contains("required") || lowered.contains("optional") || lowered.contains("example") || lowered.contains("e.g")) {
            return true;
        }

        Map<String, String> descriptions = meta != null ? meta.getParameters() : null;
        String description = descriptions != null ? descriptions.get(requiredParamName) : null;
        if (StringUtils.hasText(description) && raw.equalsIgnoreCase(description.trim())) {
            return true;
        }

        if (StringUtils.hasText(requiredParamName) && raw.equalsIgnoreCase(requiredParamName.trim())) {
            return true;
        }

        if (StringUtils.hasText(normalizedOriginalQuery) && raw.equalsIgnoreCase(normalizedOriginalQuery)) {
            String originalLower = normalizedOriginalQuery.toLowerCase(java.util.Locale.ROOT);
            boolean looksLikeInstruction = false;
            if (meta != null && StringUtils.hasText(meta.getName())
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
            return looksLikeInstruction;
        }

        return false;
    }
}
