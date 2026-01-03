package com.ai.infrastructure.relationship.action;

import com.ai.infrastructure.dto.RAGResponse;
import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.ai.infrastructure.intent.action.ActionHandler;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.relationship.model.QueryMode;
import com.ai.infrastructure.relationship.model.QueryOptions;
import com.ai.infrastructure.relationship.model.ReturnMode;
import com.ai.infrastructure.relationship.service.ReliableRelationshipQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * ActionHandler bridge that lets the orchestrator execute relationship queries.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(ReliableRelationshipQueryService.class)
@ConditionalOnProperty(
    prefix = "ai.infrastructure.relationship",
    name = "enable-orchestrator-integration",
    havingValue = "true",
    matchIfMissing = true
)
public class RelationshipQueryActionHandler implements ActionHandler {

    private static final String ACTION_NAME = "relationship_query";

    private final ReliableRelationshipQueryService queryService;

    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name(ACTION_NAME)
            .description("Execute natural language relationship queries across entities")
            .category("data_query")
            .parameters(Map.of(
                "query", "Natural language query (required)",
                "entityTypes", "List<String> entity types to target (required)",
                "limit", "Maximum number of results (optional, default 20)",
                "returnMode", "IDS or FULL (optional, default IDS)",
                "queryMode", "STANDALONE or ENHANCED (optional)",
                "similarityThreshold", "Vector similarity threshold 0-1 (optional)"
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
            if (allowedEntityTypes.isEmpty() && !autoDetect) {
                return ActionResult.builder()
                    .success(false)
                    .message("Access denied for requested entity types")
                    .errorCode("ACCESS_DENIED")
                    .data(Map.of("requestedEntityTypes", entityTypes))
                    .build();
            }

            QueryOptions options = buildQueryOptions(params);
            List<String> entityTypeHints = allowedEntityTypes.isEmpty() ? null : allowedEntityTypes;
            RAGResponse response = queryService.execute(query, entityTypeHints, options);

            return ActionResult.builder()
                .success(response.getSuccess() == null || response.getSuccess())
                .message(buildSuccessMessage(response))
                .data(buildResultData(response))
                .build();
        } catch (IllegalArgumentException ex) {
            return ActionResult.builder()
                .success(false)
                .message(ex.getMessage())
                .errorCode("INVALID_PARAMETERS")
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
            .errorCode("EXECUTION_FAILED")
            .data(Map.of("errorType", e.getClass().getSimpleName()))
            .build();
    }

    @Override
    public boolean validateActionAllowed(String userId) {
        return userId != null && !userId.isBlank();
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

        if (params.containsKey("limit")) {
            builder.limit(parseInteger(params.get("limit"), 20));
        }
        if (params.containsKey("returnMode")) {
            builder.returnMode(parseReturnMode(params.get("returnMode")));
        }
        if (params.containsKey("queryMode") || params.containsKey("forceMode")) {
            Object rawMode = params.getOrDefault("queryMode", params.get("forceMode"));
            builder.forceMode(parseQueryMode(rawMode));
        }
        if (params.containsKey("similarityThreshold")) {
            builder.similarityThreshold(parseDouble(params.get("similarityThreshold"), null));
        }

        return builder.build();
    }

    private String requireQuery(Map<String, Object> params) {
        Object value = params != null ? params.get("query") : null;
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("'query' parameter is required for relationship_query");
        }
        return value.toString();
    }

    private List<String> requireEntityTypes(Map<String, Object> params) {
        Object value = params != null ? params.get("entityTypes") : null;
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

        throw new IllegalArgumentException("'entityTypes' must be a String or List<String>");
    }

    private List<String> filterAllowedEntityTypes(String userId, List<String> requestedEntityTypes) {
        // Placeholder for downstream access control; currently allow all.
        if (requestedEntityTypes == null) {
            return List.of();
        }
        if (requestedEntityTypes.isEmpty()) {
            return List.of();
        }
        if (log.isDebugEnabled()) {
            log.debug("Allowing entity types {} for user {}", requestedEntityTypes, userId);
        }
        return requestedEntityTypes;
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

    private QueryMode parseQueryMode(Object raw) {
        if (raw == null) {
            return null;
        }
        return QueryMode.fromValue(raw.toString());
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
        data.put("documents", response.getDocuments());
        data.put("totalResults", response.getTotalResults());
        data.put("returnedResults", response.getReturnedResults());
        data.put("hybridSearchUsed", response.getHybridSearchUsed());
        data.put("processingTimeMs", response.getProcessingTimeMs());
        if (response.getMetadata() != null && !response.getMetadata().isEmpty()) {
            data.put("metadata", response.getMetadata());
        }
        if (response.getWarnings() != null && !response.getWarnings().isEmpty()) {
            data.put("warnings", response.getWarnings());
        }
        if (response.getConfidenceScore() != null) {
            data.put("confidenceScore", response.getConfidenceScore());
        }
        if (Objects.nonNull(response.getEntityType())) {
            data.put("entityType", response.getEntityType());
        }
        return data;
    }
}
