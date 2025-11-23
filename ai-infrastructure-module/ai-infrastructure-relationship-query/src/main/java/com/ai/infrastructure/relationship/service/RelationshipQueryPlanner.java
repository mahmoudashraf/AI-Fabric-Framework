package com.ai.infrastructure.relationship.service;

import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.relationship.cache.QueryCache;
import com.ai.infrastructure.relationship.config.RelationshipQueryProperties;
import com.ai.infrastructure.relationship.dto.RelationshipQueryPlan;
import com.ai.infrastructure.relationship.dto.QueryStrategy;
import com.ai.infrastructure.relationship.validation.RelationshipQueryValidator;
import com.ai.infrastructure.relationship.metrics.QueryMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
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
        RelationshipQueryPlan plan = objectMapper.readValue(jsonPayload, RelationshipQueryPlan.class);
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
}
