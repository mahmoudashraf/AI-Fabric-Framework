package com.ai.infrastructure.intent.orchestration.pipeline.steps;

import com.ai.infrastructure.config.AIServiceConfig;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AdvancedRAGRequest;
import com.ai.infrastructure.dto.AdvancedRAGResponse;
import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.dto.NextStepRecommendation;
import com.ai.infrastructure.dto.RAGRequest;
import com.ai.infrastructure.dto.RAGResponse;
import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.ai.infrastructure.intent.action.ActionHandler;
import com.ai.infrastructure.intent.action.ActionHandlerRegistry;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationResultType;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineStep;
import com.ai.infrastructure.spi.AdvancedRAGProvider;
import com.ai.infrastructure.spi.RAGProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
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
 *   <li>{@code INFORMATION} - Performs RAG search/generation</li>
 *   <li>{@code OUT_OF_SCOPE} - Returns guidance message</li>
 *   <li>{@code COMPOUND} - Processes multiple intents</li>
 * </ul>
 * 
 * <p><strong>Order:</strong> 60 (after intent extraction)</p>
 * 
 * <p><strong>Security:</strong> Actions are blocked for anonymous users.
 * Action handlers must implement {@link ActionHandler#validateActionAllowed}.</p>
 * 
 * @see ActionHandlerRegistry
 * @see RAGProvider
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
    
    // RAG defaults
    private static final double DEFAULT_RAG_THRESHOLD = 0.6;
    private static final int DEFAULT_RAG_LIMIT = 5;
    
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
    private static final String MSG_OUT_OF_SCOPE = "I'm not able to help with that request. Please contact support for further assistance.";
    private static final String MSG_ALL_PROCESSED = "All intents processed successfully.";
    private static final String MSG_SOME_FAILED = "Some intents failed. See results for details.";

    private static final String ERROR_MSG_RAG_NULL_RESPONSE = "RAG retrieval returned null response.";

    private static final String RAG_NO_CONTEXT_MESSAGE = "No relevant context found.";
    private static final String RAG_NO_INFO_MESSAGE_PREFIX = "I don't have enough information to answer your question: ";
    private static final String RAG_PROMPT_TEMPLATE =
        "Based on the following context, answer the question: %s\n\n" +
            "Context:\n%s\n\n" +
            "Provide a comprehensive, accurate answer based on the context provided. " +
            "If the context doesn't contain enough information, say so.";

    private static final String DATA_KEY_GENERATION_ERROR = "generationError";

    private static final String CONTEXT_METADATA_KEY_USE_ADVANCED_RAG = "useAdvancedRAG";
    private static final String CONTEXT_METADATA_KEY_ADVANCED_EXPANSION_LEVEL = "advancedRagExpansionLevel";
    private static final String CONTEXT_METADATA_KEY_ADVANCED_RERANKING_STRATEGY = "advancedRagRerankingStrategy";
    private static final String CONTEXT_METADATA_KEY_ADVANCED_CONTEXT_OPTIMIZATION_LEVEL = "advancedRagContextOptimizationLevel";

    private static final int ADVANCED_QUERY_LENGTH_THRESHOLD = 50;
    private static final int ADVANCED_QUERY_WORD_THRESHOLD = 8;
    
    // Intent params key
    private static final String PARAM_KEY_INTENTS = "intents";
    
    // =========================================================================
    // Dependencies
    // =========================================================================
    
    private final ActionHandlerRegistry actionHandlerRegistry;
    private final RAGProvider ragProvider;
    private final AICoreService aiCoreService;
    private final AIServiceConfig aiServiceConfig;
    private final ObjectProvider<AdvancedRAGProvider> advancedRagProvider;
    
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
            case ACTION -> handleAction(intent, context);
            case INFORMATION -> handleInformation(intent, context, pipelineContext);
            case OUT_OF_SCOPE -> handleOutOfScope(intent);
            case COMPOUND -> handleSyntheticCompound(intent, context, pipelineContext);
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
    
    private OrchestrationResult handleInformation(Intent intent, OrchestrationContext context, PipelineContext pipelineContext) {
        boolean needsGeneration = intent.requiresGenerationOrDefault(false);
        String optimizedQuery = StringUtils.hasText(intent.getOptimizedQuery()) ? intent.getOptimizedQuery() : null;
        String processedQuery = pipelineContext != null ? pipelineContext.getEffectiveQuery() : null;
        String query = StringUtils.hasText(optimizedQuery)
            ? optimizedQuery
            : (StringUtils.hasText(processedQuery) ? processedQuery : intent.getIntentOrAction());
        
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(METADATA_KEY_SOURCE, METADATA_VALUE_ORCHESTRATOR);
        metadata.put(METADATA_KEY_USER_ID, context.getIdentifier());
        metadata.put(METADATA_KEY_SESSION_ID, context.getSessionId());
        metadata.put(METADATA_KEY_AUTHENTICATED, context.isAuthenticated());
        metadata.put(DATA_KEY_REQUIRES_GENERATION, needsGeneration);
        if (optimizedQuery != null) {
            metadata.put(METADATA_KEY_OPTIMIZED_QUERY, optimizedQuery);
        }
        if (pipelineContext != null && !pipelineContext.getDetectedPiiTypesView().isEmpty()) {
            metadata.put("piiProcessed", true);
            metadata.put("piiDetectedTypes", pipelineContext.getDetectedPiiTypesView());
        }

        if (shouldUseAdvancedRag(needsGeneration, query, context)) {
            OrchestrationResult advanced = handleInformationAdvanced(intent, context, pipelineContext, query, metadata);
            if (advanced != null) {
                return advanced;
            }
        }

        return handleInformationBasic(intent, context, pipelineContext, needsGeneration, query, metadata);
    }

    private OrchestrationResult handleInformationBasic(Intent intent,
                                                       OrchestrationContext context,
                                                       PipelineContext pipelineContext,
                                                       boolean needsGeneration,
                                                       String query,
                                                       Map<String, Object> metadata) {
        RAGRequest ragRequest = RAGRequest.builder()
            .query(query)
            .entityType(intent.getVectorSpace())
            .limit(DEFAULT_RAG_LIMIT)
            .threshold(DEFAULT_RAG_THRESHOLD)
            .metadata(Collections.unmodifiableMap(metadata))
            .userId(context.getIdentifier())
            .build();

        RAGResponse ragResponse = ragProvider.performRag(ragRequest);
        if (ragResponse == null) {
            return OrchestrationResult.error(ERROR_MSG_RAG_NULL_RESPONSE);
        }

        String answer = null;
        if (needsGeneration) {
            try {
                answer = generateRagAnswer(query, ragResponse.getContext());
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

    private OrchestrationResult handleInformationAdvanced(Intent intent,
                                                          OrchestrationContext context,
                                                          PipelineContext pipelineContext,
                                                          String query,
                                                          Map<String, Object> metadata) {
        AdvancedRAGProvider provider = advancedRagProvider.getIfAvailable();
        if (provider == null) {
            return null;
        }

        try {
            AdvancedRAGRequest request = buildAdvancedRagRequest(intent, context, query, metadata);
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
            RAGResponse ragResponse = convertToRagResponse(advancedResponse, documents, query, intent.getVectorSpace());

            Map<String, Object> data = new LinkedHashMap<>();
            data.put(DATA_KEY_ANSWER, advancedResponse.getResponse());
            data.put(DATA_KEY_DOCUMENTS, documents);
            data.put(DATA_KEY_RAG_RESPONSE, ragResponse);
            data.put(DATA_KEY_REQUIRES_GENERATION, true);
            data.put(DATA_KEY_EXPANDED_QUERIES, advancedResponse.getExpandedQueries());
            data.put(DATA_KEY_CONFIDENCE_SCORE, advancedResponse.getConfidenceScore());
            data.put(DATA_KEY_RERANKING_STRATEGY, advancedResponse.getRerankingStrategy());
            data.put(DATA_KEY_CONTEXT_OPTIMIZATION_LEVEL, advancedResponse.getContextOptimizationLevel());

            String message = StringUtils.hasText(advancedResponse.getResponse())
                ? advancedResponse.getResponse()
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
                                                       Map<String, Object> metadata) {
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

    private boolean shouldUseAdvancedRag(boolean needsGeneration, String query, OrchestrationContext context) {
        AdvancedRAGProvider provider = advancedRagProvider.getIfAvailable();
        if (provider == null) {
            return false;
        }

        AIServiceConfig.FeatureFlags features = aiServiceConfig != null ? aiServiceConfig.getFeatures() : null;
        if (features != null && Boolean.FALSE.equals(features.getEnableAdvancedRAG())) {
            return false;
        }

        Map<String, Object> ctxMetadata = context != null ? context.getMetadata() : null;
        if (isTrue(ctxMetadata != null ? ctxMetadata.get(CONTEXT_METADATA_KEY_USE_ADVANCED_RAG) : null)) {
            return true;
        }

        if (!needsGeneration) {
            return false;
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

        if (!StringUtils.hasText(context) || RAG_NO_CONTEXT_MESSAGE.equals(context)) {
            return RAG_NO_INFO_MESSAGE_PREFIX + query;
        }

        String prompt = String.format(RAG_PROMPT_TEMPLATE, query, context);
        return aiCoreService.generateText(prompt);
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
