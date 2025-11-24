package com.ai.infrastructure.relationship.service;

import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.relationship.cache.QueryCache;
import com.ai.infrastructure.relationship.config.RelationshipQueryProperties;
import com.ai.infrastructure.relationship.dto.RelationshipQueryPlan;
import com.ai.infrastructure.relationship.dto.QueryStrategy;
import com.ai.infrastructure.relationship.validation.RelationshipQueryValidationException;
import com.ai.infrastructure.relationship.validation.RelationshipQueryValidator;
import com.ai.infrastructure.relationship.exception.QueryPlanningException;
import com.ai.infrastructure.relationship.exception.RelationshipQueryErrorContext;
import com.ai.infrastructure.relationship.metrics.QueryMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Uses the LLM via {@link AICoreService} to transform natural language queries into structured plans.
 */
@Slf4j
public class RelationshipQueryPlanner {

    private final AICoreService aiCoreService;
    private final RelationshipSchemaProvider schemaProvider;
    private final RelationshipQueryProperties properties;
    private final RelationshipQueryValidator validator;
    private final QueryCache queryCache;
    private final QueryMetrics queryMetrics;
    private final ObjectMapper objectMapper;

    private static final List<String> PLAN_EXAMPLES = List.of(
        """
        Example plan for query "Show me blue shoes under $100 from Nike":
        {
          "primaryEntityType": "product",
          "candidateEntityTypes": ["product"],
          "relationshipPaths": [
            {
              "fromEntityType": "product",
              "relationshipType": "brand",
              "toEntityType": "brand",
              "direction": "FORWARD",
              "optional": false,
              "conditions": [
                {"field": "name", "operator": "EQUALS", "value": "Nike", "entityType": "brand"}
              ]
            }
          ],
          "directFilters": {
            "product": [
              {"field": "color", "operator": "LIKE", "value": "%blue%", "entityType": "product"},
              {"field": "price", "operator": "LESS_THAN", "value": 100, "entityType": "product"}
            ]
          },
          "relationshipFilters": {}
        }
        """,
        """
        Example plan for query "Find suspicious wires over $25k routed through the same counterparty":
        {
          "primaryEntityType": "transaction",
          "candidateEntityTypes": ["transaction"],
          "relationshipPaths": [
            {
              "fromEntityType": "transaction",
              "relationshipType": "destinationAccount",
              "toEntityType": "destination-account",
              "direction": "FORWARD",
              "optional": false,
              "conditions": [
                {"field": "region", "operator": "ILIKE", "value": "%high-risk%", "entityType": "destination-account"}
              ]
            },
            {
              "fromEntityType": "transaction",
              "relationshipType": "sourceAccount",
              "toEntityType": "origin-account",
              "direction": "FORWARD",
              "optional": false,
              "conditions": [
                {"field": "riskScore", "operator": "GREATER_THAN_OR_EQUAL", "value": 0.7, "entityType": "origin-account"},
                {"field": "ownerName", "operator": "EQUALS", "value": "destination-account.ownerName", "entityType": "origin-account"}
              ]
            }
          ],
          "directFilters": {
            "transaction": [
              {"field": "amount", "operator": "GREATER_THAN", "value": 25000, "entityType": "transaction"}
            ]
          },
          "relationshipFilters": {}
        }
        """,
        """
        Example plan for query "Find all contracts related to John Smith in Q4 2023":
        {
          "primaryEntityType": "document",
          "candidateEntityTypes": ["document"],
          "relationshipPaths": [
            {
              "fromEntityType": "document",
              "relationshipType": "author",
              "toEntityType": "user",
              "direction": "FORWARD",
              "optional": false,
              "conditions": [
                {"field": "fullName", "operator": "EQUALS", "value": "John Smith", "entityType": "user"}
              ]
            }
          ],
          "directFilters": {
            "document": [
              {"field": "creationDate", "operator": "GREATER_THAN_OR_EQUAL", "value": "2023-10-01T00:00:00", "entityType": "document"},
              {"field": "creationDate", "operator": "LESS_THAN_OR_EQUAL", "value": "2023-12-31T23:59:59", "entityType": "document"}
            ]
          },
          "relationshipFilters": {}
        }
        """
    );

    public RelationshipQueryPlanner(AICoreService aiCoreService,
                                    RelationshipSchemaProvider schemaProvider,
                                    RelationshipQueryProperties properties,
                                    RelationshipQueryValidator validator,
                                    QueryCache queryCache,
                                    QueryMetrics queryMetrics,
                                    ObjectMapper objectMapper) {
        this.aiCoreService = aiCoreService;
        this.schemaProvider = schemaProvider;
        this.properties = properties;
        this.validator = validator;
        this.queryCache = queryCache;
        this.queryMetrics = queryMetrics;
        this.objectMapper = objectMapper;
    }

    public RelationshipQueryPlan planQuery(String query, List<String> entityTypes) {
        long start = System.nanoTime();
        String cacheKey = QueryCache.hash(query);
        if (queryCache.isEnabled()) {
            Optional<RelationshipQueryPlan> cachedPlan = queryCache.getPlan(cacheKey);
            if (cachedPlan.isPresent()) {
                recordPlanMetrics(start, true, true);
                return cachedPlan.get();
            }
        }

        RelationshipQueryPlan fallback = createFallbackPlan(query, entityTypes);
        int maxRetries = Math.max(0, properties.getPlanner().getMaxRetries());
        List<String> feedback = new ArrayList<>();
        Exception lastFailure = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                RelationshipQueryPlan plan = requestPlan(query, entityTypes, fallback, feedback);
                cachePlan(cacheKey, plan);
                recordPlanMetrics(start, false, true);
                return plan;
            } catch (RelationshipQueryValidationException ex) {
                lastFailure = ex;
                if (attempt < maxRetries) {
                    feedback = List.of(safeMessage(ex));
                    continue;
                }
                recordPlanMetrics(start, false, false);
                if (properties.getPlanner().isFailOnParseError()) {
                    throw new QueryPlanningException(
                        "Planner failed to produce a structured plan",
                        buildPlannerErrorContext(query, entityTypes, fallback, ex),
                        ex
                    );
                }
                return fallback;
            } catch (Exception ex) {
                lastFailure = ex;
                if (attempt < maxRetries) {
                    feedback = List.of(safeMessage(ex));
                    continue;
                }
                break;
            }
        }

        recordPlanMetrics(start, false, false);
        log.warn("Failed to obtain structured plan from LLM: {}", lastFailure != null ? lastFailure.getMessage() : "unknown error");
        if (properties.getPlanner().isFailOnParseError()) {
            throw new QueryPlanningException(
                "Planner failed to produce a structured plan",
                buildPlannerErrorContext(query, entityTypes, fallback, lastFailure),
                lastFailure
            );
        }
        return fallback;
    }

    private RelationshipQueryPlan requestPlan(String query,
                                              List<String> entityTypes,
                                              RelationshipQueryPlan fallback,
                                              List<String> feedback) throws Exception {
        String prompt = buildPrompt(query, entityTypes, feedback);
        AIGenerationResponse response = aiCoreService.generateContent(buildRequest(query, prompt));
        RelationshipQueryPlan plan = parsePlan(response.getContent());
        applyDefaults(plan, fallback);
        validator.validate(plan);
        return plan;
    }

    private AIGenerationRequest buildRequest(String query, String prompt) {
        RelationshipQueryProperties.LlmProperties llm = properties.getLlm();
        return AIGenerationRequest.builder()
            .entityId("relationship-query-" + UUID.randomUUID())
            .entityType("relationship-query")
            .generationType("planning")
            .prompt(prompt)
            .systemPrompt("You are an expert database planner. Return ONLY a JSON object.")
            .model(llm.getModel())
            .temperature(llm.getTemperature())
            .maxTokens(1200)
            .parameters(java.util.Map.of(
                "response_format", "json",
                "min_confidence", llm.getMinConfidence()
            ))
            .purpose("relationship-query-plan")
            .userId("relationship-module")
            .build();
    }

    private String buildPrompt(String query, List<String> entityTypes) {
        return buildPrompt(query, entityTypes, Collections.emptyList());
    }

    private String buildPrompt(String query, List<String> entityTypes, List<String> feedback) {
        String schemaDescription = schemaProvider.getSchemaDescription(entityTypes);
        StringBuilder builder = new StringBuilder("""
            Analyze the user's request using the provided entity schema. Produce a JSON payload with:
            - primaryEntityType (snake-case)
            - candidateEntityTypes (array)
            - relationshipPaths (array of {fromEntityType, relationshipType, toEntityType, direction, optional, conditions})
            - directFilters (map of entity -> array of filters)
            - relationshipFilters (map)
            - needsSemanticSearch (boolean)
            - queryStrategy ("RELATIONSHIP", "SEMANTIC", or "HYBRID")
            - confidence (0.0 - 1.0 decimal)
            - semanticQuery (string)

            Guidelines:
            - candidateEntityTypes MUST always include the primaryEntityType.
            - Each element inside directFilters/relationshipFilters MUST be an array of objects shaped like {"field":"entity.field","operator":"GREATER_THAN","value":123}. Valid operators: EQUALS, NOT_EQUALS, GREATER_THAN, GREATER_THAN_OR_EQUAL, LESS_THAN, LESS_THAN_OR_EQUAL, BETWEEN, IN, LIKE.
            - relationshipPaths[].conditions follows the exact same object structure (arrays of filter objects).
            - Use fully-qualified field names such as "transaction.amount" or "destinationAccount.region".
            - When a predicate needs to compare two entities (e.g., "same counterparty"), set the filter value to "<entity-slug>.<field>" (example: {"field":"ownerName","operator":"EQUALS","value":"destination-account.ownerName"}).
            - Use the exact field names shown in the schema (e.g., "creationDate", "author.fullName"); do not invent shorthand names like "date" or "author".
            - Do NOT emit raw strings, bare values, or shorthand expressions for any filter/condition.

            Schema:
            """);
        builder.append(schemaDescription)
            .append("\n\nUser Query: \"").append(query).append("\"\n");

        builder.append("\nExample plans:\n");
        PLAN_EXAMPLES.forEach(example -> builder.append(example).append("\n"));
        if (!CollectionUtils.isEmpty(feedback)) {
            builder.append("\nPrevious attempt issues:\n");
            feedback.forEach(issue -> builder.append("- ").append(issue).append("\n"));
            builder.append("Correct the issues above and return JSON only.\n");
        } else {
            builder.append("\nRespond with valid JSON only.\n");
        }
        return builder.toString();
    }

    private RelationshipQueryPlan parsePlan(String rawResponse) throws Exception {
        String jsonPayload = extractJson(rawResponse);
        if (!StringUtils.hasText(jsonPayload)) {
            throw new IllegalStateException("LLM did not return JSON payload");
        }
        String sanitizedPayload = sanitizePayload(jsonPayload);
        RelationshipQueryPlan plan = objectMapper.readValue(sanitizedPayload, RelationshipQueryPlan.class);
        plan.setConfidenceScore(normalizeConfidence(plan.getConfidenceScore()));
        return plan;
    }

    private double normalizeConfidence(Double confidence) {
        if (confidence == null) {
            return properties.getPlanner().getMinConfidenceToExecute();
        }
        if (confidence < 0) {
            return 0;
        }
        if (confidence > 1) {
            return 1;
        }
        return confidence;
    }

    private void applyDefaults(RelationshipQueryPlan plan, RelationshipQueryPlan fallback) {
        plan.setOriginalQuery(fallback.getOriginalQuery());
        if (!StringUtils.hasText(plan.getPrimaryEntityType())) {
            plan.setPrimaryEntityType(fallback.getPrimaryEntityType());
        }
        if (CollectionUtils.isEmpty(plan.getCandidateEntityTypes())) {
            plan.setCandidateEntityTypes(fallback.getCandidateEntityTypes());
        }
        if (!StringUtils.hasText(plan.getSemanticQuery())) {
            plan.setSemanticQuery(fallback.getSemanticQuery());
        }
        if (plan.getQueryStrategy() == null) {
            plan.setQueryStrategy(QueryStrategy.RELATIONSHIP);
        }
    }

    private RelationshipQueryPlan createFallbackPlan(String query, List<String> entityTypes) {
        List<String> available = new ArrayList<>();
        if (!CollectionUtils.isEmpty(entityTypes)) {
            available.addAll(entityTypes);
        } else {
            available.addAll(schemaProvider.getSchema().entities().keySet());
        }
        if (available.isEmpty()) {
            available.add("document");
        }
        String primary = available.get(0);
        return RelationshipQueryPlan.builder()
            .originalQuery(query)
            .semanticQuery(query)
            .primaryEntityType(primary)
            .candidateEntityTypes(available)
            .queryStrategy(QueryStrategy.RELATIONSHIP)
            .confidenceScore(0.25)
            .needsSemanticSearch(false)
            .build();
    }

    private RelationshipQueryErrorContext buildPlannerErrorContext(String query,
                                                                   List<String> entityTypes,
                                                                   RelationshipQueryPlan fallback,
                                                                   Exception ex) {
        List<String> candidates = !CollectionUtils.isEmpty(entityTypes)
            ? entityTypes
            : fallback.getCandidateEntityTypes();
        return RelationshipQueryErrorContext.builder()
            .originalQuery(query)
            .executionStage("PLAN_GENERATION")
            .primaryEntityType(fallback.getPrimaryEntityType())
            .candidateEntityTypes(candidates)
            .fallbackUsed(true)
            .attributes(Map.of(
                "reason", ex.getMessage() != null ? ex.getMessage() : "unknown",
                "plannerFallback", Boolean.TRUE
            ))
            .build();
    }

    private String extractJson(String response) throws Exception {
        if (!StringUtils.hasText(response)) {
            return null;
        }
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start == -1 || end <= start) {
            return null;
        }
        return response.substring(start, end + 1);
    }

    private void cachePlan(String cacheKey, RelationshipQueryPlan plan) {
        if (queryCache.isEnabled() && cacheKey != null && plan != null) {
            queryCache.putPlan(cacheKey, plan);
        }
    }

    private void recordPlanMetrics(long startNano, boolean fromCache, boolean success) {
        if (queryMetrics != null && queryMetrics.isEnabled()) {
            long latencyMs = Math.max(0, (System.nanoTime() - startNano) / 1_000_000);
            queryMetrics.recordPlan(latencyMs, fromCache, success);
        }
    }

    private String sanitizePayload(String jsonPayload) {
        return jsonPayload;
    }

    private String safeMessage(Exception ex) {
        if (ex == null) {
            return "Unknown planner failure";
        }
        String message = ex.getMessage();
        return StringUtils.hasText(message) ? message : ex.getClass().getSimpleName();
    }
}
