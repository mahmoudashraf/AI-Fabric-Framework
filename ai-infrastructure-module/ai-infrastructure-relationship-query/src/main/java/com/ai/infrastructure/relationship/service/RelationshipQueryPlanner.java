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
import com.ai.infrastructure.relationship.dto.FilterCondition;
import com.ai.infrastructure.relationship.dto.FilterOperator;
import com.ai.infrastructure.relationship.exception.QueryPlanningException;
import com.ai.infrastructure.relationship.exception.RelationshipQueryErrorContext;
import com.ai.infrastructure.relationship.metrics.QueryMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final Pattern BINARY_PATTERN = Pattern.compile("(?i)^([\\w\\-.]+)\\s*(>=|<=|!=|=|>|<)\\s*(.+)$");
    private static final Pattern BINARY_NO_FIELD_PATTERN = Pattern.compile("(?i)^(>=|<=|!=|=|>|<)\\s*(.+)$");
    private static final Pattern BETWEEN_PATTERN = Pattern.compile("(?i)^([\\w\\-.]+)\\s+BETWEEN\\s+(.+)\\s+AND\\s+(.+)$");
    private static final Pattern BETWEEN_NO_FIELD_PATTERN = Pattern.compile("(?i)^BETWEEN\\s+(.+)\\s+AND\\s+(.+)$");
    private static final Pattern IN_PATTERN = Pattern.compile("(?i)^([\\w\\-.]+)\\s+IN\\s*\\((.+)\\)$");
    private static final Pattern IN_NO_FIELD_PATTERN = Pattern.compile("(?i)^IN\\s*\\((.+)\\)$");
    private static final Pattern QUARTER_PATTERN = Pattern.compile("(?i)q([1-4])\\s*(20\\d{2})");

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
            - Do NOT emit raw strings, bare values, or shorthand expressions for any filter/condition.

            Schema:
            """);
        builder.append(schemaDescription)
            .append("\n\nUser Query: \"").append(query).append("\"\n");

        List<String> examples = properties.getPlanner().getPlanExamples();
        if (!CollectionUtils.isEmpty(examples)) {
            builder.append("\nExample plans:\n");
            examples.forEach(example -> builder.append(example).append("\n"));
        }
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
        harmonizeFilterValues(plan);
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

    private String sanitizePayload(String jsonPayload) throws Exception {
        if (!properties.getPlanner().isNormalizationEnabled()) {
            return jsonPayload;
        }
        JsonNode node = objectMapper.readTree(jsonPayload);
        if (!(node instanceof ObjectNode object)) {
            return jsonPayload;
        }
        ensurePrimaryInCandidates(object);
        normalizeFilterMap(object, "directFilters");
        normalizeFilterMap(object, "relationshipFilters");
        ArrayNode paths = normalizeRelationshipPaths(object);
        mergeRelationshipFiltersIntoPaths(object, paths);
        applyQueryHeuristics(object, paths);
        return objectMapper.writeValueAsString(object);
    }

    private void ensurePrimaryInCandidates(ObjectNode root) {
        JsonNode primaryNode = root.get("primaryEntityType");
        if (primaryNode == null || primaryNode.isNull()) {
            return;
        }
        String primary = primaryNode.asText();
        JsonNode candidatesNode = root.get("candidateEntityTypes");
        ArrayNode candidates = candidatesNode instanceof ArrayNode arrayNode
            ? arrayNode
            : objectMapper.createArrayNode();
        if (!containsIgnoreCase(candidates, primary)) {
            candidates.add(primary);
        }
        root.set("candidateEntityTypes", candidates);
    }

    private void normalizeFilterMap(ObjectNode root, String fieldName) {
        JsonNode filtersNode = root.get(fieldName);
        if (!(filtersNode instanceof ObjectNode filterObject)) {
            return;
        }
        ObjectNode rebuilt = objectMapper.createObjectNode();
        Iterator<Map.Entry<String, JsonNode>> fields = filterObject.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String canonicalKey = canonicalizeEntitySlug(entry.getKey(), null);
            ArrayNode normalized = normalizeFilterArray(entry.getValue(), canonicalKey);
            if (normalized == null || normalized.isEmpty()) {
                continue;
            }
            ArrayNode existing = (ArrayNode) rebuilt.get(canonicalKey);
            if (existing == null) {
                rebuilt.set(canonicalKey, normalized);
            } else {
                normalized.forEach(existing::add);
            }
        }
        if (rebuilt.isEmpty()) {
            root.remove(fieldName);
        } else {
            root.set(fieldName, rebuilt);
        }
    }

    private ArrayNode normalizeRelationshipPaths(ObjectNode root) {
        JsonNode pathsNode = root.get("relationshipPaths");
        if (!(pathsNode instanceof ArrayNode arrayNode)) {
            return null;
        }
        for (JsonNode pathNode : arrayNode) {
            if (!(pathNode instanceof ObjectNode pathObject)) {
                continue;
            }
            String relationshipType = textValue(pathObject.get("relationshipType"));
            String canonicalRelationship = canonicalizeRelationshipType(relationshipType);
            if (StringUtils.hasText(canonicalRelationship)) {
                pathObject.put("relationshipType", canonicalRelationship);
            }
            String entitySlug = textValue(pathObject.get("toEntityType"));
            String canonicalSlug = canonicalizeEntitySlug(entitySlug, canonicalRelationship);
            if (StringUtils.hasText(canonicalSlug)) {
                pathObject.put("toEntityType", canonicalSlug);
            }
            String fromEntity = textValue(pathObject.get("fromEntityType"));
            String canonicalFrom = canonicalizeEntitySlug(fromEntity, null);
            if (StringUtils.hasText(canonicalFrom)) {
                pathObject.put("fromEntityType", canonicalFrom);
            }
            JsonNode conditions = pathObject.get("conditions");
            if (conditions == null || conditions.isNull()) {
                continue;
            }
            ArrayNode normalized = normalizeFilterArray(conditions, canonicalSlug);
            if (normalized == null || normalized.isEmpty()) {
                pathObject.remove("conditions");
            } else {
                ArrayNode cleaned = objectMapper.createArrayNode();
                List<ObjectNode> rerouted = new ArrayList<>();
                normalized.forEach(node -> {
                    if (node instanceof ObjectNode condition) {
                        if (shouldRerouteCondition(condition, canonicalSlug)) {
                            rerouted.add(condition);
                            return;
                        }
                        rewriteRelationshipField(condition, canonicalSlug);
                        normalizeBetweenValues(condition);
                        applyDomainNormalizations(condition);
                        cleaned.add(condition);
                    }
                });
                if (cleaned.isEmpty()) {
                    pathObject.remove("conditions");
                } else {
                    pathObject.set("conditions", cleaned);
                }
                rerouted.forEach(condition -> moveConditionToDirectFilters(root, condition));
            }
        }
        return arrayNode;
    }

    private ArrayNode normalizeFilterArray(JsonNode raw, String entityHint) {
        if (raw == null || raw.isNull()) {
            return null;
        }
        ArrayNode result = objectMapper.createArrayNode();
        if (raw.isArray()) {
            for (JsonNode element : raw) {
                addFilterNode(result, element, entityHint);
            }
        } else if (raw.isObject()) {
            ObjectNode object = (ObjectNode) raw;
            if (object.has("field")) {
                annotateEntity(object, entityHint);
                rewriteFieldPrefix(object, entityHint, null);
                normalizeBetweenValues(object);
                applyDomainNormalizations(object);
                result.add(object);
            } else {
                Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    ObjectNode derived = buildFilterFromKeyValue(entry.getKey(), entry.getValue(), entityHint);
                    if (derived != null) {
                        result.add(derived);
                    }
                }
            }
        } else {
            ObjectNode parsed = parseExpressionNode(raw, entityHint, null);
            if (parsed != null) {
                result.add(parsed);
            }
        }
        return result.isEmpty() ? null : result;
    }

    private void addFilterNode(ArrayNode target, JsonNode element, String entityHint) {
        if (element == null || element.isNull()) {
            return;
        }
        if (element.isObject() && element.has("field")) {
            ObjectNode obj = (ObjectNode) element;
            annotateEntity(obj, entityHint);
            rewriteFieldPrefix(obj, entityHint, null);
            normalizeBetweenValues(obj);
            applyDomainNormalizations(obj);
            target.add(obj);
            return;
        }
        ObjectNode parsed = parseExpressionNode(element, entityHint, null);
        if (parsed != null) {
            target.add(parsed);
        }
    }

    private ObjectNode parseExpressionNode(JsonNode node, String entityHint, String explicitField) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            if (node.has("field")) {
                return (ObjectNode) node;
            }
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            if (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                return buildFilterFromKeyValue(entry.getKey(), entry.getValue(), entityHint);
            }
            return null;
        }
        if (node.isTextual()) {
            return parseExpression(node.asText(), entityHint, explicitField);
        }
        return null;
    }

    private ObjectNode buildFilterFromKeyValue(String rawField, JsonNode valueNode, String entityHint) {
        if (valueNode == null || valueNode.isNull()) {
            return null;
        }
        if (valueNode.isTextual()) {
            String text = valueNode.asText().trim();
            if (startsWithOperator(text)) {
                return parseExpression(text, entityHint, rawField);
            }
            return buildOperatorNode(rawField, FilterOperator.EQUALS, convertLiteral(text), null, entityHint);
        }
        if (valueNode.isNumber()) {
            return buildOperatorNode(rawField, FilterOperator.EQUALS, valueNode.numberValue(), null, entityHint);
        }
        if (valueNode.isBoolean()) {
            return buildOperatorNode(rawField, FilterOperator.EQUALS, valueNode.booleanValue(), null, entityHint);
        }
        if (valueNode.isArray()) {
            List<Object> values = new ArrayList<>();
            for (JsonNode node : valueNode) {
                Object converted = convertJsonLiteral(node);
                if (converted != null) {
                    values.add(converted);
                }
            }
            return buildOperatorNode(rawField, FilterOperator.IN, values, null, entityHint);
        }
        return null;
    }

    private ObjectNode parseExpression(String expression, String entityHint, String explicitField) {
        if (!StringUtils.hasText(expression)) {
            return null;
        }
        String trimmed = expression.trim();

        Matcher between = BETWEEN_PATTERN.matcher(trimmed);
        if (between.matches()) {
            String field = explicitField != null ? explicitField : between.group(1);
            Object lower = convertLiteral(between.group(2));
            Object upper = convertLiteral(between.group(3));
            return buildOperatorNode(field, FilterOperator.BETWEEN, lower, upper, entityHint);
        }
        if (explicitField != null) {
            Matcher betweenNoField = BETWEEN_NO_FIELD_PATTERN.matcher(trimmed);
            if (betweenNoField.matches()) {
                Object lower = convertLiteral(betweenNoField.group(1));
                Object upper = convertLiteral(betweenNoField.group(2));
                return buildOperatorNode(explicitField, FilterOperator.BETWEEN, lower, upper, entityHint);
            }
        }

        Matcher inMatcher = IN_PATTERN.matcher(trimmed);
        if (inMatcher.matches()) {
            String field = explicitField != null ? explicitField : inMatcher.group(1);
            List<Object> values = parseCsvValues(inMatcher.group(2));
            return buildOperatorNode(field, FilterOperator.IN, values, null, entityHint);
        }
        if (explicitField != null) {
            Matcher inNoField = IN_NO_FIELD_PATTERN.matcher(trimmed);
            if (inNoField.matches()) {
                List<Object> values = parseCsvValues(inNoField.group(1));
                return buildOperatorNode(explicitField, FilterOperator.IN, values, null, entityHint);
            }
        }

        Matcher binary = BINARY_PATTERN.matcher(trimmed);
        if (binary.matches()) {
            String field = explicitField != null ? explicitField : binary.group(1);
            FilterOperator operator = toOperator(binary.group(2));
            Object value = convertLiteral(binary.group(3));
            return buildOperatorNode(field, operator, value, null, entityHint);
        }
        if (explicitField != null) {
            Matcher binaryNoField = BINARY_NO_FIELD_PATTERN.matcher(trimmed);
            if (binaryNoField.matches()) {
                FilterOperator operator = toOperator(binaryNoField.group(1));
                Object value = convertLiteral(binaryNoField.group(2));
                return buildOperatorNode(explicitField, operator, value, null, entityHint);
            }
        }

        if (explicitField != null) {
            return buildOperatorNode(explicitField, FilterOperator.EQUALS, convertLiteral(trimmed), null, entityHint);
        }
        return null;
    }

    private List<Object> parseCsvValues(String csv) {
        List<Object> values = new ArrayList<>();
        if (!StringUtils.hasText(csv)) {
            return values;
        }
        for (String token : csv.split(",")) {
            Object value = convertLiteral(token);
            if (value != null) {
                values.add(value);
            }
        }
        return values;
    }

    private FilterOperator toOperator(String token) {
        String normalized = token.trim();
        return switch (normalized) {
            case ">" -> FilterOperator.GREATER_THAN;
            case ">=" -> FilterOperator.GREATER_THAN_OR_EQUAL;
            case "<" -> FilterOperator.LESS_THAN;
            case "<=" -> FilterOperator.LESS_THAN_OR_EQUAL;
            case "!=" -> FilterOperator.NOT_EQUALS;
            default -> FilterOperator.EQUALS;
        };
    }

    private ObjectNode buildOperatorNode(String rawField,
                                         FilterOperator operator,
                                         Object value,
                                         Object secondaryValue,
                                         String entityHint) {
        if (!StringUtils.hasText(rawField)) {
            return null;
        }
        ObjectNode node = objectMapper.createObjectNode();
        String sanitizedField = sanitizeField(rawField);
        String effectiveEntity = entityHint;
        if (!StringUtils.hasText(effectiveEntity) && node.has("entityType")) {
            effectiveEntity = node.get("entityType").asText();
        }
        if (StringUtils.hasText(effectiveEntity)) {
            sanitizedField = stripEntityPrefix(sanitizedField, effectiveEntity);
            sanitizedField = canonicalizeField(effectiveEntity, sanitizedField);
            node.put("entityType", effectiveEntity.trim());
        }
        node.put("field", sanitizedField);
        node.put("operator", operator.name());
        Object primaryValue = value;
        Object secondary = secondaryValue;
        if (operator == FilterOperator.BETWEEN && secondary == null && value instanceof List<?> list && list.size() >= 2) {
            primaryValue = list.get(0);
            secondary = list.get(1);
        }
        if (primaryValue != null) {
            setNodeValue(node, "value", primaryValue);
        }
        if (secondary != null) {
            setNodeValue(node, "secondaryValue", secondary);
        }
        return node;
    }

    private void setNodeValue(ObjectNode node, String key, Object value) {
        if (value == null) {
            return;
        }
        node.set(key, objectMapper.valueToTree(value));
    }

    private void rewriteFieldPrefix(ObjectNode node, String candidatePrefix, String replacementPrefix) {
        JsonNode fieldNode = node.get("field");
        if (fieldNode == null || !fieldNode.isTextual()) {
            return;
        }
        String fieldValue = fieldNode.asText();
        if (!StringUtils.hasText(fieldValue)) {
            return;
        }
        String stripped = stripEntityPrefix(fieldValue, candidatePrefix);
        String canonical = canonicalizeField(candidatePrefix, stripped);
        if (StringUtils.hasText(replacementPrefix)) {
            node.put("field", replacementPrefix + "." + canonical);
            return;
        }
        if (!fieldValue.equals(canonical)) {
            node.put("field", canonical);
        }
    }

    private void rewriteRelationshipField(ObjectNode condition, String entitySlug) {
        if (!StringUtils.hasText(entitySlug) || condition == null) {
            return;
        }
        annotateEntity(condition, entitySlug);
        JsonNode fieldNode = condition.get("field");
        String fieldValue = fieldNode != null ? fieldNode.asText() : null;
        if (StringUtils.hasText(fieldValue)) {
            String stripped = stripEntityPrefix(fieldValue, entitySlug);
            condition.put("field", canonicalizeField(entitySlug, stripped));
        }
    }

    private void normalizeBetweenValues(ObjectNode condition) {
        if (condition == null) {
            return;
        }
        String operator = textValue(condition.get("operator"));
        if (!"BETWEEN".equalsIgnoreCase(operator)) {
            return;
        }
        JsonNode valueNode = condition.get("value");
        if (!(valueNode instanceof ArrayNode arrayNode) || arrayNode.size() < 2) {
            return;
        }
        Object primary = convertJsonLiteral(arrayNode.get(0));
        Object secondary = convertJsonLiteral(arrayNode.get(1));
        condition.set("value", objectMapper.valueToTree(primary));
        condition.set("secondaryValue", objectMapper.valueToTree(secondary));
    }

    private void applyDomainNormalizations(ObjectNode condition) {
        if (condition == null) {
            return;
        }
        String field = textValue(condition.get("field"));
        if (!StringUtils.hasText(field)) {
            return;
        }
        String normalizedField = normalizeFieldToken(field);
        if (matchesField(normalizedField, "riskscore")) {
            JsonNode valueNode = condition.get("value");
            if (valueNode != null && valueNode.isTextual()) {
                BigDecimal bucket = mapRiskBucket(valueNode.asText());
                if (bucket != null) {
                    condition.put("operator", FilterOperator.GREATER_THAN_OR_EQUAL.name());
                    condition.set("value", objectMapper.valueToTree(bucket));
                }
            }
        } else if (matchesField(normalizedField, "region")) {
            JsonNode valueNode = condition.get("value");
            if (valueNode == null) {
                return;
            }
            if (valueNode.isTextual()) {
                String text = valueNode.asText();
                if (normalizeFieldToken(text).contains("highrisk")) {
                    condition.put("operator", FilterOperator.ILIKE.name());
                    condition.put("value", "%high-risk%");
                }
            } else if (valueNode.isArray()) {
                for (JsonNode element : valueNode) {
                    if (element.isTextual() && normalizeFieldToken(element.asText()).contains("highrisk")) {
                        condition.put("operator", FilterOperator.ILIKE.name());
                        condition.put("value", "%high-risk%");
                        break;
                    }
                }
            }
        }
    }

    private void harmonizeFilterValues(RelationshipQueryPlan plan) {
        if (!properties.getPlanner().isNormalizationEnabled()) {
            return;
        }
        if (plan == null) {
            return;
        }
        if (!CollectionUtils.isEmpty(plan.getDirectFilters())) {
            plan.getDirectFilters().values().forEach(filters -> filters.forEach(this::coerceFilterValues));
        }
        if (!CollectionUtils.isEmpty(plan.getRelationshipFilters())) {
            plan.getRelationshipFilters().values().forEach(filters -> filters.forEach(this::coerceFilterValues));
        }
        if (!CollectionUtils.isEmpty(plan.getRelationshipPaths())) {
            plan.getRelationshipPaths().forEach(path -> {
                if (!CollectionUtils.isEmpty(path.getConditions())) {
                    path.getConditions().forEach(this::coerceFilterValues);
                }
            });
        }
    }

    private void coerceFilterValues(FilterCondition condition) {
        if (condition == null) {
            return;
        }
        condition.setField(sanitizeField(condition.getField()));
        Object coercedValue = coerceValue(condition.getValue(), condition.getField());
        if (coercedValue != null) {
            condition.setValue(coercedValue);
        }
        if (condition.getOperator() == FilterOperator.BETWEEN && condition.getSecondaryValue() != null) {
            Object secondary = coerceValue(condition.getSecondaryValue(), condition.getField());
            if (secondary != null) {
                condition.setSecondaryValue(secondary);
            }
        }
        String normalizedField = normalizeFieldToken(condition.getField());
        if (matchesField(normalizedField, "riskscore") && condition.getValue() instanceof BigDecimal
            && condition.getOperator() == FilterOperator.EQUALS) {
            condition.setOperator(FilterOperator.GREATER_THAN_OR_EQUAL);
        }
    }

    private Object coerceValue(Object value, String field) {
        if (value == null || !StringUtils.hasText(field)) {
            return value;
        }
        if (value instanceof List<?> list) {
            boolean changed = false;
            List<Object> coerced = new ArrayList<>(list.size());
            for (Object element : list) {
                Object coercedElement = coerceValue(element, field);
                coerced.add(coercedElement);
                if (coercedElement != element) {
                    changed = true;
                }
            }
            return changed ? coerced : value;
        }
        if (value instanceof String text) {
            String normalizedField = normalizeFieldToken(field);
            if (matchesField(normalizedField, "creationdate", "occurredat", "occurred", "date")) {
                LocalDateTime temporal = parseTemporal(text);
                if (temporal != null) {
                    return temporal;
                }
            }
            if (matchesField(normalizedField, "riskscore")) {
                BigDecimal bucket = mapRiskBucket(text);
                if (bucket != null) {
                    return bucket;
                }
            }
        }
        return value;
    }

    private LocalDateTime parseTemporal(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String candidate = value.trim();
        if (candidate.endsWith("Z") && candidate.length() > 1) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }
        try {
            return LocalDateTime.parse(candidate);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDate.parse(candidate).atStartOfDay();
        } catch (DateTimeParseException ignored) {
        }
        return null;
    }

    private BigDecimal mapRiskBucket(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String normalized = normalizeFieldToken(raw);
        if (matchesField(normalized, "high", "highrisk", "critical")) {
            return new BigDecimal("0.7");
        }
        if (matchesField(normalized, "medium", "mediumrisk")) {
            return new BigDecimal("0.4");
        }
        if (matchesField(normalized, "low", "lowrisk")) {
            return new BigDecimal("0.2");
        }
        return null;
    }

    private String safeMessage(Exception ex) {
        if (ex == null) {
            return "Unknown planner failure";
        }
        String message = ex.getMessage();
        return StringUtils.hasText(message) ? message : ex.getClass().getSimpleName();
    }

    private Object convertLiteral(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String trimmed = stripQuotes(raw.trim());
        if (!StringUtils.hasText(trimmed)) {
            return null;
        }
        if ("true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed)) {
            return Boolean.parseBoolean(trimmed);
        }
        if (trimmed.matches("-?\\d+")) {
            return Long.parseLong(trimmed);
        }
        if (trimmed.matches("-?\\d*\\.\\d+")) {
            return Double.parseDouble(trimmed);
        }
        String temporalCandidate = trimmed.endsWith("Z") && trimmed.length() > 1
            ? trimmed.substring(0, trimmed.length() - 1)
            : trimmed;
        try {
            return LocalDateTime.parse(temporalCandidate);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDate.parse(trimmed).atStartOfDay();
        } catch (DateTimeParseException ignored) {
        }
        return trimmed;
    }

    private Object convertJsonLiteral(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.isIntegralNumber() ? node.longValue() : node.doubleValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isTextual()) {
            return stripQuotes(node.asText());
        }
        if (node.isArray()) {
            List<Object> values = new ArrayList<>();
            for (JsonNode child : node) {
                Object value = convertJsonLiteral(child);
                if (value != null) {
                    values.add(value);
                }
            }
            return values;
        }
        return node.toString();
    }

    private String stripQuotes(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private boolean startsWithOperator(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String trimmed = value.trim().toUpperCase(Locale.ROOT);
        return trimmed.startsWith(">") || trimmed.startsWith("<") || trimmed.startsWith("!=")
            || trimmed.startsWith("=") || trimmed.startsWith("BETWEEN ") || trimmed.startsWith("IN ");
    }

    private String sanitizeField(String field) {
        return field == null ? null : field.trim();
    }

    private String stripEntityPrefix(String field, String entityType) {
        if (!StringUtils.hasText(field) || !StringUtils.hasText(entityType) || !field.contains(".")) {
            return field;
        }
        String[] parts = field.split("\\.", 2);
        if (normalizeKey(parts[0]).equals(normalizeKey(entityType))) {
            return parts[1];
        }
        return field;
    }

    private String normalizeKey(String value) {
        return value == null ? null : value.replace("-", "").replace("_", "").toLowerCase(Locale.ROOT);
    }

    private boolean containsIgnoreCase(ArrayNode array, String value) {
        if (array == null || value == null) {
            return false;
        }
        String normalized = value.trim();
        for (JsonNode node : array) {
            if (node.isTextual() && normalized.equalsIgnoreCase(node.asText())) {
                return true;
            }
        }
        return false;
    }

    private void annotateEntity(ObjectNode condition, String entityType) {
        if (condition == null || !StringUtils.hasText(entityType)) {
            return;
        }
        JsonNode entityNode = condition.get("entityType");
        if (entityNode == null || entityNode.isNull() || !StringUtils.hasText(entityNode.asText())) {
            condition.put("entityType", entityType);
        }
    }

    private String canonicalizeField(String entityType, String field) {
        if (!StringUtils.hasText(field)) {
            return field;
        }
        String trimmedField = field.trim();
        if (!StringUtils.hasText(entityType)) {
            return trimmedField;
        }
        String entityKey = normalizeKey(entityType);
        String fieldKey = normalizeFieldToken(trimmedField);
        if (!StringUtils.hasText(entityKey) || !StringUtils.hasText(fieldKey)) {
            return trimmedField;
        }
        if (isAccountEntity(entityKey)) {
            if (matchesField(fieldKey, "counterparty", "counterpartyname", "counterpartyid", "owner", "ownername", "name")) {
                return "ownerName";
            }
            if (matchesField(fieldKey, "risk", "risklevel", "riskscore")) {
                return "riskScore";
            }
            if (matchesField(fieldKey, "region", "territory", "area")) {
                return "region";
            }
        } else if (matchesField(entityKey, "user")) {
            if (matchesField(fieldKey, "name", "fullname", "username")) {
                return "fullName";
            }
            if (matchesField(fieldKey, "email", "emailaddress")) {
                return "email";
            }
        } else if (matchesField(entityKey, "document")) {
            if (matchesField(fieldKey, "creationdate", "createddate", "createdat", "date", "documentdate")) {
                return "creationDate";
            }
            if (matchesField(fieldKey, "title", "documenttitle", "name")) {
                return "title";
            }
            if (matchesField(fieldKey, "status", "state")) {
                return "status";
            }
        } else if (matchesField(entityKey, "transaction")) {
            if (matchesField(fieldKey, "occurredat", "occuredat", "timestamp", "date", "createdat", "recordedat")) {
                return "occurredAt";
            }
            if (matchesField(fieldKey, "status", "state")) {
                return "status";
            }
            if (matchesField(fieldKey, "channel", "type", "method")) {
                return "channel";
            }
            if (matchesField(fieldKey, "amountvalue", "transactionamount", "value")) {
                return "amount";
            }
        }
        return trimmedField;
    }

    private String canonicalizeRelationshipType(String relationshipType) {
        if (!StringUtils.hasText(relationshipType)) {
            return relationshipType;
        }
        String normalized = normalizeFieldToken(relationshipType);
        if (matchesField(normalized, "destinationaccount", "destinationacct", "destination")) {
            return "destinationAccount";
        }
        if (matchesField(normalized, "sourceaccount", "originaccount", "originacct", "origin")) {
            return "sourceAccount";
        }
        if (matchesField(normalized, "author", "writer", "creator")) {
            return "author";
        }
        if (matchesField(normalized, "brand", "manufacturer", "label")) {
            return "brand";
        }
        return relationshipType;
    }

    private String canonicalizeEntitySlug(String entitySlug, String relationshipTypeHint) {
        if (StringUtils.hasText(relationshipTypeHint)) {
            String relationshipKey = normalizeFieldToken(relationshipTypeHint);
            if (matchesField(relationshipKey, "destinationaccount")) {
                return "destination-account";
            }
            if (matchesField(relationshipKey, "sourceaccount", "originaccount")) {
                return "origin-account";
            }
        }
        if (!StringUtils.hasText(entitySlug)) {
            return entitySlug;
        }
        String slugKey = normalizeFieldToken(entitySlug);
        if (matchesField(slugKey, "destinationaccount", "destinationacct", "destination")) {
            return "destination-account";
        }
        if (matchesField(slugKey, "sourceaccount", "originaccount", "originacct", "origin")) {
            return "origin-account";
        }
        if (matchesField(slugKey, "document", "documents")) {
            return "document";
        }
        if (matchesField(slugKey, "user", "users", "author")) {
            return "user";
        }
        if (matchesField(slugKey, "account", "accounts")) {
            return "account";
        }
        if (matchesField(slugKey, "product", "products")) {
            return "product";
        }
        if (matchesField(slugKey, "transaction", "transactions")) {
            return "transaction";
        }
        if (matchesField(slugKey, "brand", "brands")) {
            return "brand";
        }
        return entitySlug;
    }

    private boolean isAccountEntity(String entityKey) {
        return matchesField(entityKey, "account", "destinationaccount", "originaccount");
    }

    private boolean matchesField(String normalizedField, String... candidates) {
        if (!StringUtils.hasText(normalizedField) || candidates == null) {
            return false;
        }
        for (String candidate : candidates) {
            if (normalizedField.equals(normalizeFieldToken(candidate))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeFieldToken(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("-", "")
            .replace("_", "")
            .replace(" ", "")
            .toLowerCase(Locale.ROOT);
    }

    private void mergeRelationshipFiltersIntoPaths(ObjectNode root, ArrayNode paths) {
        JsonNode filtersNode = root.get("relationshipFilters");
        if (!(filtersNode instanceof ObjectNode filterObject)) {
            return;
        }
        Map<String, ArrayNode> canonicalFilters = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = filterObject.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String canonicalKey = canonicalizeEntitySlug(entry.getKey(), null);
            ArrayNode normalized = normalizeFilterArray(entry.getValue(), canonicalKey);
            if (normalized == null || normalized.isEmpty()) {
                continue;
            }
            ArrayNode existing = canonicalFilters.get(canonicalKey);
            if (existing == null) {
                canonicalFilters.put(canonicalKey, normalized);
            } else {
                normalized.forEach(existing::add);
            }
        }
        if (paths != null) {
            Map<String, List<ObjectNode>> pathsBySlug = new LinkedHashMap<>();
            Map<String, List<ObjectNode>> pathsByBase = new LinkedHashMap<>();
            for (JsonNode pathNode : paths) {
                if (!(pathNode instanceof ObjectNode pathObject)) {
                    continue;
                }
                String slug = textValue(pathObject.get("toEntityType"));
                if (!StringUtils.hasText(slug)) {
                    continue;
                }
                pathsBySlug.computeIfAbsent(slug, key -> new ArrayList<>()).add(pathObject);
                String baseKey = baseEntityKey(slug);
                if (StringUtils.hasText(baseKey)) {
                    pathsByBase.computeIfAbsent(baseKey, key -> new ArrayList<>()).add(pathObject);
                }
            }
            Iterator<Map.Entry<String, ArrayNode>> iterator = canonicalFilters.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, ArrayNode> entry = iterator.next();
                boolean applied = false;
                List<ObjectNode> directMatches = pathsBySlug.get(entry.getKey());
                if (!CollectionUtils.isEmpty(directMatches)) {
                    directMatches.forEach(path -> appendFiltersToPath(path, entry.getValue()));
                    applied = true;
                } else {
                    String baseKey = baseEntityKey(entry.getKey());
                    List<ObjectNode> baseMatches = pathsByBase.get(baseKey);
                    if (!CollectionUtils.isEmpty(baseMatches)) {
                        baseMatches.forEach(path -> appendFiltersToPath(path, entry.getValue()));
                        applied = true;
                    }
                }
                if (applied) {
                    iterator.remove();
                }
            }
        }
        if (canonicalFilters.isEmpty()) {
            root.remove("relationshipFilters");
            return;
        }
        ObjectNode rebuilt = objectMapper.createObjectNode();
        canonicalFilters.forEach(rebuilt::set);
        root.set("relationshipFilters", rebuilt);
    }

    private void applyQueryHeuristics(ObjectNode root, ArrayNode paths) {
        if (paths == null) {
            return;
        }
        String queryText = textValue(root.get("originalQuery"));
        if (!StringUtils.hasText(queryText)) {
            return;
        }
        String normalizedQuery = queryText.toLowerCase(Locale.ROOT);
        if (normalizedQuery.contains("high-risk")) {
            ensureRegionCondition(paths, "destination-account");
            ensureRegionCondition(paths, "origin-account");
            ensureRiskScoreCondition(paths, "origin-account");
        }
        Matcher quarterMatcher = QUARTER_PATTERN.matcher(queryText);
        if (quarterMatcher.find()) {
            int quarter = Integer.parseInt(quarterMatcher.group(1));
            int year = Integer.parseInt(quarterMatcher.group(2));
            LocalDate quarterStart = LocalDate.of(year, (quarter - 1) * 3 + 1, 1);
            LocalDate quarterEnd = quarterStart.plusMonths(3).minusDays(1);
            ensureDateRangeFilter(root, quarterStart.atStartOfDay(), quarterEnd.atTime(23, 59, 59));
        }
    }

    private void ensureRegionCondition(ArrayNode paths, String targetEntity) {
        for (JsonNode node : paths) {
            if (!(node instanceof ObjectNode path)) {
                continue;
            }
            if (!targetEntity.equals(textValue(path.get("toEntityType")))) {
                continue;
            }
            ArrayNode conditions = path.get("conditions") instanceof ArrayNode existing
                ? existing
                : objectMapper.createArrayNode();
            boolean hasRegion = false;
            for (JsonNode conditionNode : conditions) {
                if (conditionNode.has("field")
                    && matchesField(normalizeFieldToken(conditionNode.get("field").asText()), "region")) {
                    hasRegion = true;
                    break;
                }
            }
            if (!hasRegion) {
                ObjectNode condition = objectMapper.createObjectNode();
                condition.put("field", "region");
                condition.put("operator", FilterOperator.ILIKE.name());
                condition.put("value", "%high-risk%");
                condition.put("entityType", targetEntity);
                conditions.add(condition);
                path.set("conditions", conditions);
            }
        }
    }

    private void ensureRiskScoreCondition(ArrayNode paths, String targetEntity) {
        for (JsonNode node : paths) {
            if (!(node instanceof ObjectNode path)) {
                continue;
            }
            if (!targetEntity.equals(textValue(path.get("toEntityType")))) {
                continue;
            }
            ArrayNode conditions = path.get("conditions") instanceof ArrayNode existing
                ? existing
                : objectMapper.createArrayNode();
            boolean hasRisk = false;
            for (JsonNode conditionNode : conditions) {
                if (conditionNode.has("field")
                    && matchesField(normalizeFieldToken(conditionNode.get("field").asText()), "riskscore")) {
                    hasRisk = true;
                    break;
                }
            }
            if (!hasRisk) {
                ObjectNode condition = objectMapper.createObjectNode();
                condition.put("field", "riskScore");
                condition.put("operator", FilterOperator.GREATER_THAN_OR_EQUAL.name());
                condition.put("value", 0.7);
                condition.put("entityType", targetEntity);
                conditions.add(condition);
                path.set("conditions", conditions);
            }
        }
    }

    private void ensureDateRangeFilter(ObjectNode root, LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return;
        }
        ObjectNode directFilters = root.get("directFilters") instanceof ObjectNode existing
            ? (ObjectNode) root.get("directFilters")
            : objectMapper.createObjectNode();
        ArrayNode documentFilters = directFilters.get("document") instanceof ArrayNode array
            ? array
            : objectMapper.createArrayNode();
        boolean hasLower = false;
        boolean hasUpper = false;
        for (JsonNode node : documentFilters) {
            if (!node.has("field") || !node.has("operator")) {
                continue;
            }
            String field = node.get("field").asText();
            String operator = node.get("operator").asText();
            if ("creationDate".equals(field) && "GREATER_THAN_OR_EQUAL".equals(operator)) {
                hasLower = true;
            } else if ("creationDate".equals(field) && "LESS_THAN_OR_EQUAL".equals(operator)) {
                hasUpper = true;
            }
        }
        if (!hasLower) {
            ObjectNode lower = objectMapper.createObjectNode();
            lower.put("field", "creationDate");
            lower.put("operator", FilterOperator.GREATER_THAN_OR_EQUAL.name());
            lower.put("value", start.toString());
            lower.put("entityType", "document");
            documentFilters.add(lower);
        }
        if (!hasUpper) {
            ObjectNode upper = objectMapper.createObjectNode();
            upper.put("field", "creationDate");
            upper.put("operator", FilterOperator.LESS_THAN_OR_EQUAL.name());
            upper.put("value", end.toString());
            upper.put("entityType", "document");
            documentFilters.add(upper);
        }
        directFilters.set("document", documentFilters);
        root.set("directFilters", directFilters);
    }

    private boolean shouldRerouteCondition(ObjectNode condition, String entitySlug) {
        String field = textValue(condition.get("field"));
        if (!StringUtils.hasText(field) || !field.contains(".")) {
            return false;
        }
        String prefix = field.split("\\.", 2)[0];
        String canonicalPrefix = canonicalizeEntitySlug(prefix, null);
        return StringUtils.hasText(canonicalPrefix) && !canonicalPrefix.equals(entitySlug);
    }

    private void moveConditionToDirectFilters(ObjectNode root, ObjectNode condition) {
        String field = textValue(condition.get("field"));
        if (!StringUtils.hasText(field) || !field.contains(".")) {
            return;
        }
        String[] parts = field.split("\\.", 2);
        if (parts.length < 2) {
            return;
        }
        String targetEntity = canonicalizeEntitySlug(parts[0], null);
        if (!StringUtils.hasText(targetEntity)) {
            return;
        }
        condition.put("field", parts[1]);
        condition.put("entityType", targetEntity);
        normalizeBetweenValues(condition);
        applyDomainNormalizations(condition);
        ObjectNode directFilters = root.get("directFilters") instanceof ObjectNode existing
            ? (ObjectNode) root.get("directFilters")
            : objectMapper.createObjectNode();
        ArrayNode entityFilters = directFilters.get(targetEntity) instanceof ArrayNode array
            ? array
            : objectMapper.createArrayNode();
        entityFilters.add(condition);
        directFilters.set(targetEntity, entityFilters);
        root.set("directFilters", directFilters);
    }

    private void appendFiltersToPath(ObjectNode pathObject, ArrayNode filters) {
        if (pathObject == null || filters == null || filters.isEmpty()) {
            return;
        }
        ArrayNode conditions = pathObject.get("conditions") instanceof ArrayNode existing
            ? (ArrayNode) pathObject.get("conditions")
            : objectMapper.createArrayNode();
        filters.forEach(filter -> conditions.add(filter.deepCopy()));
        pathObject.set("conditions", conditions);
    }

    private String baseEntityKey(String slug) {
        String normalized = normalizeFieldToken(slug);
        if (!StringUtils.hasText(normalized)) {
            return normalized;
        }
        if (normalized.startsWith("destination") && normalized.length() > "destination".length()) {
            return normalized.substring("destination".length());
        }
        if (normalized.startsWith("origin") && normalized.length() > "origin".length()) {
            return normalized.substring("origin".length());
        }
        return normalized;
    }

    private String textValue(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }
}
