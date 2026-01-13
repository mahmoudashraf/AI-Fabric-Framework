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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Locale;
import java.util.stream.Collectors;

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
        Example plan for query "Find all brands":
        {
          "primaryEntityType": "brand",
          "candidateEntityTypes": ["brand"],
          "relationshipPaths": [],
          "directFilters": {},
          "relationshipFilters": {},
          "metadataFilters": {},
          "queryStrategy": "RELATIONSHIP",
          "needsSemanticSearch": false,
          "confidence": 0.9,
          "context": {}
        }
        """,
        """
        Example plan for query "Show me blue shoes under $100 from Nike":
        {
          "primaryEntityType": "product",
          "candidateEntityTypes": ["product", "brand"],
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
        """,
        """
        Example plan for query "Show active Nike or Adidas running shoes priced between $80 and $120 available in red or blue":
        {
          "primaryEntityType": "product",
          "candidateEntityTypes": ["product", "brand"],
          "relationshipPaths": [
            {
              "fromEntityType": "product",
              "relationshipType": "brand",
              "toEntityType": "brand",
              "direction": "FORWARD",
              "optional": false,
              "conditions": [
                {"field": "name", "operator": "IN", "value": ["Nike", "Adidas"], "entityType": "brand"}
              ]
            }
          ],
          "directFilters": {
            "product": [
              {"field": "status", "operator": "EQUALS", "value": "ACTIVE", "entityType": "product"},
              {"field": "price", "operator": "BETWEEN", "value": 80, "secondaryValue": 120, "entityType": "product"},
              {"field": "color", "operator": "IN", "value": ["red", "blue"], "entityType": "product"}
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
        String cacheKey = buildCacheKey(query, entityTypes);
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

    private String buildCacheKey(String query, List<String> entityTypes) {
        String normalizedQuery = query != null ? query.trim() : "";
        String normalizedEntityTypes = "";
        if (!CollectionUtils.isEmpty(entityTypes)) {
            normalizedEntityTypes = entityTypes.stream()
                .filter(StringUtils::hasText)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .sorted()
                .collect(Collectors.joining(","));
        }
        return QueryCache.hash(normalizedQuery + "|" + normalizedEntityTypes);
    }

    private RelationshipQueryPlan requestPlan(String query,
                                              List<String> entityTypes,
                                              RelationshipQueryPlan fallback,
                                              List<String> feedback) throws Exception {
        String prompt = buildPrompt(query, entityTypes, feedback);
        AIGenerationResponse response = aiCoreService.generateContent(buildRequest(query, prompt));
        RelationshipQueryPlan plan = parsePlan(response.getContent());
        applyDefaults(plan, fallback);
        sanitizeHallucinatedFilters(plan, query);
        normalizeCrossEntityValueReferences(plan);
        validateCrossEntityReferences(plan);
        validateFilterFields(plan);
        validator.validate(plan);
        return plan;
    }

    /**
     * Provider-agnostic normalization for cross-entity comparisons.
     *
     * <p>The planner prompt asks models to express cross-entity comparisons using
     * "<entity-slug>.<field>" (e.g., "destination-account.ownerName"). Some providers instead emit
     * the relationship field name (e.g., "destinationAccount.ownerName"). We normalize those cases
     * to the entity slug when the mapping is available from relationshipPaths.</p>
     *
     * <p>This improves stability without introducing provider-specific behavior.</p>
     */
    private void normalizeCrossEntityValueReferences(RelationshipQueryPlan plan) {
        if (plan == null || CollectionUtils.isEmpty(plan.getRelationshipPaths())) {
            return;
        }

        Map<String, String> relationshipFieldToEntityType = new java.util.LinkedHashMap<>();
        plan.getRelationshipPaths().forEach(path -> {
            if (path == null) {
                return;
            }
            if (StringUtils.hasText(path.getRelationshipType()) && StringUtils.hasText(path.getToEntityType())) {
                relationshipFieldToEntityType.put(path.getRelationshipType().trim(), path.getToEntityType().trim());
            }
        });
        if (relationshipFieldToEntityType.isEmpty()) {
            return;
        }

        if (plan.getDirectFilters() != null && !plan.getDirectFilters().isEmpty()) {
            plan.getDirectFilters().values().forEach(filters ->
                normalizeCrossEntityValueReferences(filters, relationshipFieldToEntityType));
        }
        if (plan.getRelationshipFilters() != null && !plan.getRelationshipFilters().isEmpty()) {
            plan.getRelationshipFilters().values().forEach(filters ->
                normalizeCrossEntityValueReferences(filters, relationshipFieldToEntityType));
        }
        if (!CollectionUtils.isEmpty(plan.getRelationshipPaths())) {
            plan.getRelationshipPaths().forEach(path -> {
                if (path != null) {
                    normalizeCrossEntityValueReferences(path.getConditions(), relationshipFieldToEntityType);
                }
            });
        }
    }

    private void normalizeCrossEntityValueReferences(List<com.ai.infrastructure.relationship.dto.FilterCondition> filters,
                                                     Map<String, String> relationshipFieldToEntityType) {
        if (CollectionUtils.isEmpty(filters) || relationshipFieldToEntityType == null || relationshipFieldToEntityType.isEmpty()) {
            return;
        }
        for (var filter : filters) {
            if (filter == null) {
                continue;
            }
            filter.setValue(normalizeReferenceValue(filter.getValue(), relationshipFieldToEntityType));
            filter.setSecondaryValue(normalizeReferenceValue(filter.getSecondaryValue(), relationshipFieldToEntityType));
        }
    }

    private Object normalizeReferenceValue(Object raw, Map<String, String> relationshipFieldToEntityType) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof List<?> list) {
            List<Object> normalized = new ArrayList<>(list.size());
            for (Object item : list) {
                normalized.add(normalizeReferenceValue(item, relationshipFieldToEntityType));
            }
            return normalized;
        }
        if (!(raw instanceof String text)) {
            return raw;
        }
        String trimmed = text.trim();
        if (!trimmed.contains(".")) {
            return raw;
        }
        String[] parts = trimmed.split("\\.", 2);
        if (parts.length != 2) {
            return raw;
        }

        String head = parts[0].trim();
        String tail = parts[1].trim();
        if (!StringUtils.hasText(head) || !StringUtils.hasText(tail)) {
            return raw;
        }

        // If it is already a known entity type, leave it untouched.
        if (schemaProvider.getEntitySchema(head).isPresent()) {
            return raw;
        }

        // If it's a relationship field name, rewrite to the target entity type slug.
        String mapped = relationshipFieldToEntityType.get(head);
        if (StringUtils.hasText(mapped)) {
            return mapped + "." + tail;
        }

        return raw;
    }

    /**
     * Validate that any cross-entity reference "<entity-type>.<field>" points to an entity type that is actually
     * present in the plan (primary/candidates/relationshipPaths). If not, fail planning deterministically so the
     * retry loop can ask the model to include the required relationship path(s).
     */
    private void validateCrossEntityReferences(RelationshipQueryPlan plan) {
        if (plan == null) {
            return;
        }

        List<String> errors = new ArrayList<>();
        Set<String> presentEntityTypes = new HashSet<>();
        if (StringUtils.hasText(plan.getPrimaryEntityType())) {
            presentEntityTypes.add(plan.getPrimaryEntityType().trim());
        }
        if (!CollectionUtils.isEmpty(plan.getCandidateEntityTypes())) {
            presentEntityTypes.addAll(plan.getCandidateEntityTypes().stream().filter(StringUtils::hasText).map(String::trim).toList());
        }
        if (!CollectionUtils.isEmpty(plan.getRelationshipPaths())) {
            plan.getRelationshipPaths().forEach(path -> {
                if (path == null) {
                    return;
                }
                if (StringUtils.hasText(path.getFromEntityType())) {
                    presentEntityTypes.add(path.getFromEntityType().trim());
                }
                if (StringUtils.hasText(path.getToEntityType())) {
                    presentEntityTypes.add(path.getToEntityType().trim());
                }
            });
        }

        if (plan.getDirectFilters() != null && !plan.getDirectFilters().isEmpty()) {
            plan.getDirectFilters().values().forEach(filters -> collectCrossEntityReferenceErrors(filters, presentEntityTypes, errors));
        }
        if (plan.getRelationshipFilters() != null && !plan.getRelationshipFilters().isEmpty()) {
            plan.getRelationshipFilters().values().forEach(filters -> collectCrossEntityReferenceErrors(filters, presentEntityTypes, errors));
        }
        if (!CollectionUtils.isEmpty(plan.getRelationshipPaths())) {
            plan.getRelationshipPaths().forEach(path -> {
                if (path != null) {
                    collectCrossEntityReferenceErrors(path.getConditions(), presentEntityTypes, errors);
                }
            });
        }

        if (!errors.isEmpty()) {
            throw new RelationshipQueryValidationException("Planner emitted invalid cross-entity reference(s): " + String.join("; ", errors));
        }
    }

    private void collectCrossEntityReferenceErrors(List<com.ai.infrastructure.relationship.dto.FilterCondition> filters,
                                                   Set<String> presentEntityTypes,
                                                   List<String> errors) {
        if (CollectionUtils.isEmpty(filters)) {
            return;
        }
        for (var filter : filters) {
            if (filter == null) {
                continue;
            }
            collectCrossEntityReferenceErrors(filter.getValue(), presentEntityTypes, errors);
            collectCrossEntityReferenceErrors(filter.getSecondaryValue(), presentEntityTypes, errors);
        }
    }

    private void collectCrossEntityReferenceErrors(Object raw, Set<String> presentEntityTypes, List<String> errors) {
        if (raw == null) {
            return;
        }
        if (raw instanceof List<?> list) {
            list.forEach(item -> collectCrossEntityReferenceErrors(item, presentEntityTypes, errors));
            return;
        }
        if (!(raw instanceof String text)) {
            return;
        }
        String trimmed = text.trim();
        if (!trimmed.contains(".")) {
            return;
        }
        String[] parts = trimmed.split("\\.", 2);
        if (parts.length != 2) {
            return;
        }
        String head = parts[0].trim();
        if (!StringUtils.hasText(head)) {
            return;
        }
        if (schemaProvider.getEntitySchema(head).isEmpty()) {
            return; // not an entity type reference
        }
        if (presentEntityTypes == null || !presentEntityTypes.contains(head)) {
            errors.add("referencedEntityType='%s' value='%s'".formatted(head, trimmed));
        }
    }

    /**
     * Validates that every filter field emitted by the planner exists in the discovered schema.
     * If unknown fields are present (e.g. "product.type" when Product has no such field), we fail planning
     * deterministically so the retry/feedback loop can ask the LLM to correct the structure.
     */
    private void validateFilterFields(RelationshipQueryPlan plan) {
        if (plan == null) {
            return;
        }

        List<String> errors = new ArrayList<>();

        if (plan.getDirectFilters() != null && !plan.getDirectFilters().isEmpty()) {
            plan.getDirectFilters().forEach((entityKey, filters) -> {
                if (filters == null || filters.isEmpty()) {
                    return;
                }
                for (var filter : filters) {
                    if (filter == null) {
                        continue;
                    }
                    String entityType = filter.getEntityType() != null ? filter.getEntityType() : entityKey;
                    validateFieldReference(entityType, filter.getField(), errors);
                }
            });
        }

        if (plan.getRelationshipPaths() != null && !plan.getRelationshipPaths().isEmpty()) {
            plan.getRelationshipPaths().forEach(path -> {
                if (path == null || path.getConditions() == null || path.getConditions().isEmpty()) {
                    return;
                }
                for (var condition : path.getConditions()) {
                    if (condition == null) {
                        continue;
                    }
                    String entityType = condition.getEntityType() != null
                        ? condition.getEntityType()
                        : (path.getToEntityType() != null ? path.getToEntityType() : path.getFromEntityType());
                    validateFieldReference(entityType, condition.getField(), errors);
                }
            });
        }

        if (!errors.isEmpty()) {
            throw new RelationshipQueryValidationException(
                "Planner emitted unknown field(s): " + String.join("; ", errors)
            );
        }
    }

    private void validateFieldReference(String entityType, String field, List<String> errors) {
        if (!StringUtils.hasText(entityType) || !StringUtils.hasText(field)) {
            return;
        }
        Set<String> allowed = allowedFields(entityType);
        if (allowed.isEmpty()) {
            // Schema fields may be unavailable for some entity types; skip strict validation in that case.
            return;
        }

        String raw = field.trim();
        String fieldName = raw.contains(".") ? raw.substring(raw.lastIndexOf('.') + 1) : raw;
        if (fieldName.isBlank()) {
            return;
        }

        if (!allowed.contains(fieldName)) {
            errors.add("entityType='" + entityType + "' field='" + fieldName + "' allowed=" + allowed);
        }
    }

    private Set<String> allowedFields(String entityType) {
        if (!StringUtils.hasText(entityType)) {
            return Set.of();
        }
        return schemaProvider.getEntitySchema(entityType)
            .map(schema -> {
                if (schema.getFields() == null || schema.getFields().isEmpty()) {
                    return Set.<String>of();
                }
                Set<String> allowed = new HashSet<>();
                schema.getFields().forEach(field -> {
                    if (field != null && StringUtils.hasText(field.getName())) {
                        allowed.add(field.getName());
                    }
                });
                return allowed;
            })
            .orElse(Set.of());
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
                "response_format", java.util.Map.of("type", "json_object"),
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
            - relationshipPaths[].relationshipType MUST be the relationship field name exactly as shown under "Relationships" in the schema (e.g., "brand", "destinationAccount", "sourceAccount", "author").
            - relationshipPaths[].conditions follows the exact same object structure (arrays of filter objects).
            - Use fully-qualified field names such as "transaction.amount" or "destinationAccount.region".
            - When a predicate needs to compare two entities (e.g., "same counterparty"), set the filter value to "<entity-slug>.<field>" (example: {"field":"ownerName","operator":"EQUALS","value":"destination-account.ownerName"}).
            - When the request lists multiple acceptable values for the same field (e.g., "Nike or Adidas"), prefer the IN operator with an array of values.
            - Use the exact field names shown in the schema (e.g., "creationDate", "author.fullName"); do not invent shorthand names like "date" or "author".
            - NEVER copy literal values from the example plans. Examples are illustrative only.
            - Only include literal filter values that are explicitly stated in the user's query (except for enumerated constants defined in the schema, e.g., statuses).
            - For broad list queries like "find all <entity>" or "list all <entity>", return empty filters unless the user explicitly requests constraints.
            - Do NOT emit raw strings, bare values, or shorthand expressions for any filter/condition.
            - If the user mentions a concept that is not represented as a schema field, do NOT invent a new field. Either omit that constraint or map it to an existing field (commonly "name") if appropriate.

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

    /**
     * Provider-agnostic guardrails: remove filters that are likely copied from examples or hallucinated
     * when the user's query does not mention them.
     *
     * <p>This is intentionally conservative and only targets a few high-impact patterns that cause
     * false negatives (empty results) in real API runs across providers.</p>
     */
    private void sanitizeHallucinatedFilters(RelationshipQueryPlan plan, String query) {
        if (plan == null || query == null) {
            return;
        }

        String normalizedQuery = query.toLowerCase(Locale.ROOT);

        boolean mentionsTime = mentionsTimeConstraint(normalizedQuery);
        boolean mentionsExplicitRiskScore = normalizedQuery.contains("risk score") || normalizedQuery.contains("riskscore");
        boolean mentionsCrossEntityMatch = normalizedQuery.contains("same ")
            || normalizedQuery.contains("same-")
            || normalizedQuery.contains("same counterparty")
            || normalizedQuery.contains("counterparty")
            || normalizedQuery.contains("matches")
            || normalizedQuery.contains("match ")
            || normalizedQuery.contains("equal ")
            || normalizedQuery.contains("equals ")
            || normalizedQuery.contains("matching");

        // 1) Date/time filters: keep only if query mentions a timeframe.
        if (!mentionsTime && plan.getDirectFilters() != null && !plan.getDirectFilters().isEmpty()) {
            plan.getDirectFilters().forEach((entity, filters) -> {
                if (filters == null || filters.isEmpty()) {
                    return;
                }
                filters.removeIf(filter ->
                    filter != null
                        && isTemporalField(filter.getField())
                        && isTemporalOperator(filter.getOperator())
                        && looksLikeIsoTimestamp(filter.getValue())
                );
            });
        }

        // 2) Relationship path conditions guardrails.
        if (plan.getRelationshipPaths() != null && !plan.getRelationshipPaths().isEmpty()) {
            plan.getRelationshipPaths().forEach(path -> {
                if (path == null || path.getConditions() == null || path.getConditions().isEmpty()) {
                    return;
                }
                path.getConditions().removeIf(condition -> {
                    if (condition == null) {
                        return false;
                    }
                    String field = condition.getField() != null ? condition.getField().toLowerCase(Locale.ROOT) : "";
                    Object valueObj = condition.getValue();
                    String value = valueObj != null ? valueObj.toString() : "";

                    // Remove hallucinated riskScore constraints unless query explicitly asks for it.
                    if (!mentionsExplicitRiskScore && field.contains("riskscore")) {
                        return true;
                    }

                    // Remove cross-entity comparisons (value like "destination-account.ownerName") unless query asks for "same/matching".
                    if (!mentionsCrossEntityMatch && value.contains(".")) {
                        return true;
                    }

                    return false;
                });
            });
        }

        // 3) Generic literal-value leakage guardrail:
        // Remove string literal filter values that are NOT explicitly mentioned in the user's query.
        // This is provider-agnostic and prevents common example leakage like "Nike" -> "Nike, Adidas".
        sanitizeUnmentionedStringLiterals(plan, normalizedQuery);
    }

    private void sanitizeUnmentionedStringLiterals(RelationshipQueryPlan plan, String normalizedQuery) {
        if (plan == null || !StringUtils.hasText(normalizedQuery)) {
            return;
        }

        if (plan.getDirectFilters() != null && !plan.getDirectFilters().isEmpty()) {
            plan.getDirectFilters().forEach((entity, filters) -> {
                if (filters == null || filters.isEmpty()) {
                    return;
                }
                filters.removeIf(filter -> shouldRemoveFilterForUnmentionedStringValue(filter, normalizedQuery));
            });
        }

        if (plan.getRelationshipPaths() != null && !plan.getRelationshipPaths().isEmpty()) {
            plan.getRelationshipPaths().forEach(path -> {
                if (path == null || path.getConditions() == null || path.getConditions().isEmpty()) {
                    return;
                }
                path.getConditions().removeIf(condition -> shouldRemoveFilterForUnmentionedStringValue(condition, normalizedQuery));
            });
        }
    }

    private boolean shouldRemoveFilterForUnmentionedStringValue(com.ai.infrastructure.relationship.dto.FilterCondition condition,
                                                                String normalizedQuery) {
        if (condition == null || condition.getOperator() == null) {
            return false;
        }
        Object value = condition.getValue();
        if (value == null) {
            return false;
        }

        // Skip cross-entity comparisons like "destination-account.ownerName"
        if (value instanceof String s && s.contains(".")) {
            return false;
        }

        return switch (condition.getOperator()) {
            case EQUALS, NOT_EQUALS, LIKE, IN, ILIKE -> {
                boolean changedOrEmpty = sanitizeConditionValueInPlace(condition, normalizedQuery);
                yield changedOrEmpty;
            }
            default -> false;
        };
    }

    /**
     * Sanitizes condition.value for string literals:
     * - For String values: remove filter if value not mentioned.
     * - For List<String> values: drop unmentioned elements; remove filter if list becomes empty.
     *
     * @return true if the filter should be removed from the plan.
     */
    @SuppressWarnings("unchecked")
    private boolean sanitizeConditionValueInPlace(com.ai.infrastructure.relationship.dto.FilterCondition condition,
                                                  String normalizedQuery) {
        Object value = condition.getValue();
        if (value instanceof String text) {
            String normalizedValue = normalizeLiteralForQueryMatch(text);
            if (!StringUtils.hasText(normalizedValue)) {
                return false;
            }
            return !normalizedQuery.contains(normalizedValue);
        }

        if (value instanceof List<?> list) {
            List<Object> mutable = new ArrayList<>(list);
            mutable.removeIf(item -> {
                if (!(item instanceof String s)) {
                    return false;
                }
                String normalizedValue = normalizeLiteralForQueryMatch(s);
                if (!StringUtils.hasText(normalizedValue)) {
                    return false;
                }
                return !normalizedQuery.contains(normalizedValue);
            });

            if (mutable.isEmpty()) {
                return true;
            }
            condition.setValue(mutable);
            return false;
        }

        return false;
    }

    private String normalizeLiteralForQueryMatch(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        // remove common quoting / wildcard wrappers
        value = value.replace("\"", "").replace("'", "");
        value = value.replace("%", "").replace("*", "").trim();
        // collapse whitespace
        value = value.replaceAll("\\s+", " ").trim();
        return value;
    }

    private boolean mentionsTimeConstraint(String normalizedQuery) {
        // Obvious time/date signals. Keep this broad but simple.
        if (normalizedQuery.contains(" q1") || normalizedQuery.contains(" q2") || normalizedQuery.contains(" q3") || normalizedQuery.contains(" q4")) {
            return true;
        }
        if (normalizedQuery.matches(".*\\b(19|20)\\d{2}\\b.*")) {
            return true;
        }
        return normalizedQuery.contains("last ")
            || normalizedQuery.contains("this ")
            || normalizedQuery.contains("yesterday")
            || normalizedQuery.contains("today")
            || normalizedQuery.contains("tomorrow")
            || normalizedQuery.contains("before ")
            || normalizedQuery.contains("after ")
            || normalizedQuery.contains("since ")
            || normalizedQuery.contains("between ");
    }

    private boolean isTemporalField(String field) {
        if (field == null) {
            return false;
        }
        String normalized = field.toLowerCase(Locale.ROOT);
        return normalized.contains("creationdate")
            || normalized.contains("createdat")
            || normalized.contains("occurredat")
            || normalized.endsWith(".date")
            || normalized.endsWith("date");
    }

    private boolean looksLikeIsoTimestamp(Object value) {
        if (value == null) {
            return false;
        }
        String text = value.toString();
        // Very lightweight ISO-ish check: "2023-10-01" or "2023-10-01T00:00:00"
        return text.matches("\\d{4}-\\d{2}-\\d{2}.*");
    }

    private boolean isTemporalOperator(com.ai.infrastructure.relationship.dto.FilterOperator operator) {
        if (operator == null) {
            return false;
        }
        return switch (operator) {
            case GREATER_THAN, GREATER_THAN_OR_EQUAL, LESS_THAN, LESS_THAN_OR_EQUAL, BETWEEN -> true;
            default -> false;
        };
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
        String trimmed = response.trim();

        // Strip common markdown fences before extracting JSON.
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }

        // Some providers wrap the JSON in explanatory text; extract the outermost JSON object.
        response = trimmed;
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
