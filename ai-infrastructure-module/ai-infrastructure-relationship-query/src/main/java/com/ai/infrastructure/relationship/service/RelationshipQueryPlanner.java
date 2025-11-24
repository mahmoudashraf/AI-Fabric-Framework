package com.ai.infrastructure.relationship.service;

import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.relationship.cache.QueryCache;
import com.ai.infrastructure.relationship.config.RelationshipQueryProperties;
import com.ai.infrastructure.relationship.dto.RelationshipQueryPlan;
import com.ai.infrastructure.relationship.dto.QueryStrategy;
import com.ai.infrastructure.relationship.validation.RelationshipQueryValidator;
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

import java.util.ArrayList;
import java.util.Iterator;
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
        try {
            String prompt = buildPrompt(query, entityTypes);
            AIGenerationResponse response = aiCoreService.generateContent(buildRequest(query, prompt));
            RelationshipQueryPlan plan = parsePlan(response.getContent());
            applyDefaults(plan, fallback);
            validator.validate(plan);
            cachePlan(cacheKey, plan);
            recordPlanMetrics(start, false, true);
            return plan;
        } catch (Exception ex) {
            recordPlanMetrics(start, false, false);
            log.warn("Failed to obtain structured plan from LLM: {}", ex.getMessage());
            if (properties.getPlanner().isFailOnParseError()) {
                throw new QueryPlanningException(
                    "Planner failed to produce a structured plan",
                    buildPlannerErrorContext(query, entityTypes, fallback, ex),
                    ex
                );
            }
            return fallback;
        }
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
        String schemaDescription = schemaProvider.getSchemaDescription(entityTypes);
        return """
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
            %s

            User Query: "%s"

            Respond with valid JSON only.
            """.formatted(schemaDescription, query);
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

    private String sanitizePayload(String jsonPayload) throws Exception {
        JsonNode node = objectMapper.readTree(jsonPayload);
        if (!(node instanceof ObjectNode object)) {
            return jsonPayload;
        }
        ensurePrimaryInCandidates(object);
        normalizeFilterMap(object, "directFilters");
        normalizeFilterMap(object, "relationshipFilters");
        normalizeRelationshipPaths(object);
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
        boolean exists = false;
        for (JsonNode candidate : candidates) {
            if (primary.equalsIgnoreCase(candidate.asText())) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            candidates.add(primary);
        }
        root.set("candidateEntityTypes", candidates);
    }

    private void normalizeFilterMap(ObjectNode root, String fieldName) {
        JsonNode filtersNode = root.get(fieldName);
        if (!(filtersNode instanceof ObjectNode filterObject)) {
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = filterObject.fields();
        List<String> toRemove = new ArrayList<>();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            ArrayNode normalized = normalizeFilterArray(entry.getValue(), null);
            if (normalized == null || normalized.isEmpty()) {
                toRemove.add(entry.getKey());
            } else {
                normalized.forEach(node -> {
                    if (node instanceof ObjectNode condition) {
                        rewriteFieldPrefix(condition, entry.getKey(), null);
                    }
                });
                filterObject.set(entry.getKey(), normalized);
            }
        }
        toRemove.forEach(filterObject::remove);
    }

    private void normalizeRelationshipPaths(ObjectNode root) {
        JsonNode pathsNode = root.get("relationshipPaths");
        if (!(pathsNode instanceof ArrayNode arrayNode)) {
            return;
        }
        for (JsonNode pathNode : arrayNode) {
            if (!(pathNode instanceof ObjectNode pathObject)) {
                continue;
            }
            JsonNode conditions = pathObject.get("conditions");
            if (conditions == null || conditions.isNull()) {
                continue;
            }
            String relationshipType = textValue(pathObject.get("relationshipType"));
            String entitySlug = textValue(pathObject.get("toEntityType"));
            ArrayNode normalized = normalizeFilterArray(conditions, relationshipType);
            if (normalized == null || normalized.isEmpty()) {
                pathObject.remove("conditions");
            } else {
                normalized.forEach(node -> {
                    if (node instanceof ObjectNode condition) {
                        rewriteRelationshipField(condition, entitySlug, relationshipType);
                    }
                });
                pathObject.set("conditions", normalized);
            }
        }
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
            target.add(element);
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
        node.put("field", sanitizeField(rawField));
        if (StringUtils.hasText(entityHint)) {
            node.put("entityType", entityHint.trim());
        }
        node.put("operator", operator.name());
        if (value != null) {
            setNodeValue(node, "value", value);
        }
        if (secondaryValue != null) {
            setNodeValue(node, "secondaryValue", secondaryValue);
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
        String[] parts = fieldValue.split("\\.", 2);
        if (parts.length < 2) {
            if (StringUtils.hasText(replacementPrefix)) {
                node.put("field", replacementPrefix + "." + sanitizeField(fieldValue));
            }
            return;
        }
        String prefix = parts[0];
        String remainder = parts[1];
        if (candidatePrefix != null && prefix.equalsIgnoreCase(candidatePrefix)) {
            if (StringUtils.hasText(replacementPrefix)) {
                node.put("field", replacementPrefix + "." + remainder);
            } else {
                node.put("field", remainder);
            }
        }
    }

    private void rewriteRelationshipField(ObjectNode condition,
                                          String entitySlug,
                                          String relationshipType) {
        JsonNode fieldNode = condition.get("field");
        String fieldValue = fieldNode != null ? fieldNode.asText() : null;
        if (!StringUtils.hasText(fieldValue)) {
            if (StringUtils.hasText(relationshipType)) {
                condition.put("field", relationshipType + ".id");
            }
            return;
        }
        if (fieldValue.contains(".")) {
            String[] parts = fieldValue.split("\\.", 2);
            String prefix = parts[0];
            String remainder = parts[1];
            if (StringUtils.hasText(entitySlug) && prefix.equalsIgnoreCase(entitySlug)) {
                condition.put("field", (StringUtils.hasText(relationshipType) ? relationshipType : prefix) + "." + remainder);
            }
        } else if (StringUtils.hasText(relationshipType)) {
            condition.put("field", relationshipType + "." + fieldValue);
        }
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

    private String textValue(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }
}
