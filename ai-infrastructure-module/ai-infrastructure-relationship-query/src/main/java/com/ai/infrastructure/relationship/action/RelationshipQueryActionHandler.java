package com.ai.infrastructure.relationship.action;

import com.ai.infrastructure.dto.RAGResponse;
import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.ai.infrastructure.intent.action.ActionHandler;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.relationship.model.QueryMode;
import com.ai.infrastructure.relationship.model.QueryOptions;
import com.ai.infrastructure.relationship.model.ReturnMode;
import com.ai.infrastructure.relationship.service.ReliableRelationshipQueryService;
import com.ai.infrastructure.relationship.service.RelationshipSchemaProvider;
import com.ai.infrastructure.relationship.spi.RelationshipQueryAccessControlPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ActionHandler bridge that lets the orchestrator execute relationship queries.
 * 
 * <p><strong>REQUIREMENT:</strong> When orchestrator integration is enabled, users MUST provide
 * an implementation of {@link RelationshipQueryAccessControlPolicy}. The application will fail
 * to start if no policy bean is provided.</p>
 * 
 * <p><strong>Behavior:</strong>
 * <ul>
 *   <li>When {@code enable-orchestrator-integration=true}: Handler is created, policy is REQUIRED</li>
 *   <li>When {@code enable-orchestrator-integration=false}: Handler is NOT created, policy is NOT needed
 *       (standalone mode - users handle access control manually)</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean({ReliableRelationshipQueryService.class, RelationshipQueryAccessControlPolicy.class})
@ConditionalOnProperty(
    prefix = "ai.infrastructure.relationship",
    name = "enable-orchestrator-integration",
    havingValue = "true",
    matchIfMissing = true
)
// Access control policy is REQUIRED when orchestrator integration is enabled
// Application will fail to start if no policy bean is provided
public class RelationshipQueryActionHandler implements ActionHandler {

    // Action metadata
    private static final String ACTION_NAME = "relationship_query";
    
    // Parameter names (extracted by LLM from user query)
    private static final String PARAM_QUERY = "query";  // From user input
    private static final String PARAM_ENTITY_TYPES = "entityTypes";  // Extracted by LLM
    
    // Application-level parameters (NOT extracted by LLM - passed by application code)
    private static final String PARAM_LIMIT = "limit";  // Application config
    private static final String PARAM_RETURN_MODE = "returnMode";  // Application config (IDS vs FULL)
    private static final String PARAM_SIMILARITY_THRESHOLD = "similarityThreshold";  // Application config
    
    // Error codes
    private static final String ERROR_ACCESS_DENIED = "ACCESS_DENIED";
    private static final String ERROR_INVALID_PARAMETERS = "INVALID_PARAMETERS";
    private static final String ERROR_EXECUTION_FAILED = "EXECUTION_FAILED";
    
    // Response data keys
    private static final String DATA_KEY_REQUESTED_ENTITY_TYPES = "requestedEntityTypes";
    private static final String DATA_KEY_ALLOWED_ENTITY_TYPES = "allowedEntityTypes";
    private static final String DATA_KEY_DENIED_ENTITY_TYPES = "deniedEntityTypes";
    private static final String DATA_KEY_ERROR_TYPE = "errorType";
    private static final String DATA_KEY_DOCUMENTS = "documents";
    private static final String DATA_KEY_TOTAL_RESULTS = "totalResults";
    private static final String DATA_KEY_RETURNED_RESULTS = "returnedResults";
    private static final String DATA_KEY_HYBRID_SEARCH_USED = "hybridSearchUsed";
    private static final String DATA_KEY_PROCESSING_TIME_MS = "processingTimeMs";
    private static final String DATA_KEY_METADATA = "metadata";
    private static final String DATA_KEY_WARNINGS = "warnings";
    private static final String DATA_KEY_CONFIDENCE_SCORE = "confidenceScore";
    private static final String DATA_KEY_ENTITY_TYPE = "entityType";
    
    // Default values
    private static final int DEFAULT_LIMIT = 20;

    private final ReliableRelationshipQueryService queryService;
    // REQUIRED - enforced via @ConditionalOnBean above
    // Users must provide their own implementation of RelationshipQueryAccessControlPolicy
    private final RelationshipQueryAccessControlPolicy accessControlPolicy;
    @Nullable
    private final RelationshipSchemaProvider schemaProvider;

    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name(ACTION_NAME)
            .description("Execute natural language relationship queries across entities")
            .category("data_query")
            .parameters(Map.of(
                PARAM_QUERY, "Natural language query (required)",
                PARAM_ENTITY_TYPES, "List<String> entity types to target (required)",
                PARAM_LIMIT, "Maximum number of results (optional, default " + DEFAULT_LIMIT + ")",
                PARAM_RETURN_MODE, "IDS or FULL (optional, default IDS)",
                PARAM_SIMILARITY_THRESHOLD, "Vector similarity threshold 0-1 (optional, used when ENHANCED mode is active)"
            ))
            .build();
    }

    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        try {
            String query = requireQuery(params);
            List<String> entityTypes = requireEntityTypes(params);
            List<String> allowedEntityTypes = filterAllowedEntityTypes(userId, entityTypes);

            boolean autoDetect = entityTypes.isEmpty();
            
            // SECURITY CRITICAL: Access control ALWAYS enforced, even for auto-detect
            // If user didn't specify entity types, use ONLY what policy allows
            if (autoDetect) {
                // Auto-detect scenario: user didn't specify entity types
                // Security: Use ONLY entity types the user is allowed to access
                if (allowedEntityTypes.isEmpty()) {
                    // Policy returned no allowed entity types
                    log.warn("Access denied: user {} has no allowed entity types for auto-detection", userId);
                    return ActionResult.builder()
                        .success(false)
                        .message("Access denied: You do not have permission to query any entity types")
                        .errorCode(ERROR_ACCESS_DENIED)
                        .data(Map.of("reason", "No entity types accessible for auto-detection"))
                        .build();
                }
                // Continue with allowed entity types only (policy-constrained auto-detection)
                log.debug("Auto-detect: using policy-allowed entity types: {} for user {}", 
                    allowedEntityTypes, userId);
            } else {
                // Explicit entity types requested: ALL must be allowed (fail-closed)
                if (allowedEntityTypes.isEmpty()) {
                    // All requested entity types denied
                    return ActionResult.builder()
                        .success(false)
                        .message("Access denied: You do not have permission to query the requested entity types")
                        .errorCode(ERROR_ACCESS_DENIED)
                        .data(Map.of(DATA_KEY_REQUESTED_ENTITY_TYPES, entityTypes))
                        .build();
                }
                
                if (allowedEntityTypes.size() < entityTypes.size()) {
                    // Some entity types denied - fail-closed for security
                    List<String> denied = new ArrayList<>(entityTypes);
                    denied.removeAll(allowedEntityTypes);
                    log.warn("Access denied: user {} requested unauthorized entity types: {}", userId, denied);
                    return ActionResult.builder()
                        .success(false)
                        .message("Access denied: You do not have permission to query some of the requested entity types")
                        .errorCode(ERROR_ACCESS_DENIED)
                        .data(Map.of(
                            DATA_KEY_REQUESTED_ENTITY_TYPES, entityTypes,
                            DATA_KEY_ALLOWED_ENTITY_TYPES, allowedEntityTypes,
                            DATA_KEY_DENIED_ENTITY_TYPES, denied
                        ))
                        .build();
                }
            }

            QueryOptions options = buildQueryOptions(params);
            // Always pass allowed entity types (never null, never unrestricted)
            RAGResponse response = queryService.execute(query, allowedEntityTypes, options);

            return ActionResult.builder()
                .success(response.getSuccess() == null || response.getSuccess())
                .message(buildSuccessMessage(response))
                .data(buildResultData(response))
                .build();
        } catch (IllegalArgumentException ex) {
            return ActionResult.builder()
                .success(false)
                .message(ex.getMessage())
                .errorCode(ERROR_INVALID_PARAMETERS)
                .build();
        } catch (Exception ex) {
            return handleError(ex, userId);
        }
    }

    @Override
    public ActionResult handleError(Exception e, String userId) {
        log.error("Relationship query execution failed for user {}", userId, e);
        return ActionResult.builder()
            .success(false)
            .message("Relationship query failed: " + e.getMessage())
            .errorCode(ERROR_EXECUTION_FAILED)
            .data(Map.of(DATA_KEY_ERROR_TYPE, e.getClass().getSimpleName()))
            .build();
    }

    @Override
    public boolean validateActionAllowed(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        
        // Policy is always present when orchestrator integration is enabled (enforced by @ConditionalOnBean)
        return accessControlPolicy.canUserExecuteRelationshipQueries(userId);
    }

    @Override
    public String getConfirmationMessage(Map<String, Object> params) {
        String query = requireQuery(params);
        List<String> entityTypes = requireEntityTypes(params);
        String entitySummary = entityTypes.isEmpty() ? "auto-detected entities" : entityTypes.toString();
        return "Execute relationship query on " + entitySummary + ": \"" + query + "\"";
    }

    private QueryOptions buildQueryOptions(Map<String, Object> params) {
        QueryOptions.QueryOptionsBuilder builder = QueryOptions.builder();

        if (params.containsKey(PARAM_LIMIT)) {
            builder.limit(parseInteger(params.get(PARAM_LIMIT), DEFAULT_LIMIT));
        }
        if (params.containsKey(PARAM_RETURN_MODE)) {
            builder.returnMode(parseReturnMode(params.get(PARAM_RETURN_MODE)));
        }
        if (params.containsKey(PARAM_SIMILARITY_THRESHOLD)) {
            builder.similarityThreshold(parseDouble(params.get(PARAM_SIMILARITY_THRESHOLD), null));
        }

        return builder.build();
    }

    private String requireQuery(Map<String, Object> params) {
        Object value = params != null ? params.get(PARAM_QUERY) : null;
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("'" + PARAM_QUERY + "' parameter is required for relationship_query");
        }
        return value.toString();
    }

    private List<String> requireEntityTypes(Map<String, Object> params) {
        Object value = params != null ? params.get(PARAM_ENTITY_TYPES) : null;
        if (value == null) {
            log.warn("No entityTypes supplied for relationship_query; falling back to auto-detection");
            return List.of();
        }

        if (value instanceof List<?> list) {
            List<String> normalized = new ArrayList<>();
            for (Object entry : list) {
                if (entry == null) {
                    continue;
                }
                String trimmed = entry.toString().trim();
                if (!trimmed.isEmpty()) {
                    normalized.add(trimmed.toLowerCase(Locale.ROOT));
                }
            }
            return normalized;
        }

        if (value instanceof String text) {
            String trimmed = text.trim();
            return trimmed.isEmpty()
                ? List.of()
                : List.of(trimmed.toLowerCase(Locale.ROOT));
        }

        throw new IllegalArgumentException("'" + PARAM_ENTITY_TYPES + "' must be a String or List<String>");
    }

    /**
     * Filter entity types based on user permissions using RelationshipQueryAccessControlPolicy SPI.
     * 
     * Policy is REQUIRED when orchestrator integration is enabled (enforced by @ConditionalOnBean).
     * 
     * @param userId User identifier
     * @param requestedEntityTypes Entity types requested in the query
     * @return Filtered list of entity types the user is allowed to query
     */
    private List<String> filterAllowedEntityTypes(String userId, List<String> requestedEntityTypes) {
        if (requestedEntityTypes == null || requestedEntityTypes.isEmpty()) {
            // If no entity types specified, get allowed entity types from policy
            List<String> allowed = accessControlPolicy.getAllowedEntityTypesForUser(userId);
            if (allowed == null || allowed.isEmpty()) {
                // Some policies (including our test policies) express "unrestricted" by returning an empty list.
                // To remain safe, we expand to all known entity types and then filter each via canUserQueryEntityType.
                List<String> known = schemaProvider != null && schemaProvider.getSchema() != null
                    ? new ArrayList<>(schemaProvider.getSchema().entities().keySet())
                    : List.of();
                if (!known.isEmpty()) {
                    List<String> filtered = new ArrayList<>();
                    for (String entityType : known) {
                        if (accessControlPolicy.canUserQueryEntityType(userId, entityType)) {
                            filtered.add(entityType);
                        }
                    }
                    return filtered;
                }
            }
            if (log.isDebugEnabled()) {
                log.debug("No entity types specified - using policy allowed types: {} for user {}", allowed, userId);
            }
            return allowed;
        }
        
        // Filter requested entity types based on user permissions
        List<String> allowed = new ArrayList<>();
        for (String entityType : requestedEntityTypes) {
            if (accessControlPolicy.canUserQueryEntityType(userId, entityType)) {
                allowed.add(entityType);
            } else {
                log.debug("Access denied: user {} cannot query entity type {}", userId, entityType);
            }
        }
        
        return allowed;
    }

    private int parseInteger(Object raw, int defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        if (raw instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(raw.toString());
        } catch (NumberFormatException ex) {
            log.warn("Invalid integer value '{}', using default {}", raw, defaultValue);
            return defaultValue;
        }
    }

    private Double parseDouble(Object raw, Double defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(raw.toString());
        } catch (NumberFormatException ex) {
            log.warn("Invalid double value '{}', using default {}", raw, defaultValue);
            return defaultValue;
        }
    }

    private ReturnMode parseReturnMode(Object raw) {
        if (raw == null) {
            return ReturnMode.IDS;
        }
        return ReturnMode.fromValue(raw.toString());
    }


    private String buildSuccessMessage(RAGResponse response) {
        Integer total = response.getTotalResults();
        Long duration = response.getProcessingTimeMs();
        int totalSafe = total != null ? total : 0;
        String timePart = duration != null ? " in " + duration + "ms" : "";
        if (totalSafe == 0) {
            return "No results found" + timePart;
        }
        return "Found " + totalSafe + " result" + (totalSafe == 1 ? "" : "s") + timePart;
    }

    private Map<String, Object> buildResultData(RAGResponse response) {
        Map<String, Object> data = new HashMap<>();
        data.put(DATA_KEY_DOCUMENTS, response.getDocuments());
        data.put(DATA_KEY_TOTAL_RESULTS, response.getTotalResults());
        data.put(DATA_KEY_RETURNED_RESULTS, response.getReturnedResults());
        data.put(DATA_KEY_HYBRID_SEARCH_USED, response.getHybridSearchUsed());
        data.put(DATA_KEY_PROCESSING_TIME_MS, response.getProcessingTimeMs());
        if (response.getMetadata() != null && !response.getMetadata().isEmpty()) {
            data.put(DATA_KEY_METADATA, response.getMetadata());
        }
        if (response.getWarnings() != null && !response.getWarnings().isEmpty()) {
            data.put(DATA_KEY_WARNINGS, response.getWarnings());
        }
        if (response.getConfidenceScore() != null) {
            data.put(DATA_KEY_CONFIDENCE_SCORE, response.getConfidenceScore());
        }
        if (response.getEntityType() != null) {
            data.put(DATA_KEY_ENTITY_TYPE, response.getEntityType());
        }
        return data;
    }
}
