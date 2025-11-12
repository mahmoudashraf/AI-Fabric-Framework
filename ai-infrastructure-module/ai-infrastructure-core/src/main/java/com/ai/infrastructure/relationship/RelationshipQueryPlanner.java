package com.ai.infrastructure.relationship;

import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.dto.RelationshipQueryPlan;
import com.ai.infrastructure.exception.AIServiceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * Relationship Query Planner
 * 
 * Uses LLM to analyze user queries and extract relationship patterns.
 * Generates structured query plans that combine semantic search with relational database traversal.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@Service
public class RelationshipQueryPlanner {
    
    private final AICoreService aiCoreService;
    private final ObjectMapper objectMapper;
    private final RelationshipSchemaProvider schemaProvider;
    
    public RelationshipQueryPlanner(AICoreService aiCoreService,
                                   ObjectMapper objectMapper,
                                   RelationshipSchemaProvider schemaProvider) {
        this.aiCoreService = aiCoreService;
        this.objectMapper = objectMapper.copy()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, true)
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        this.schemaProvider = schemaProvider;
    }
    
    /**
     * Analyze user query and generate relationship query plan
     * 
     * @param query the user query
     * @param availableEntityTypes list of available entity types in the system
     * @return relationship query plan
     */
    public RelationshipQueryPlan planQuery(String query, List<String> availableEntityTypes) {
        try {
            log.debug("Planning relationship query for: {}", query);
            
            // Get relationship schema information
            String schemaInfo = schemaProvider.getSchemaDescription(availableEntityTypes);
            
            // Build LLM prompt
            String systemPrompt = buildSystemPrompt(schemaInfo);
            String userPrompt = buildUserPrompt(query, availableEntityTypes);
            
            // Call LLM to generate query plan
            AIGenerationRequest request = AIGenerationRequest.builder()
                .entityId("query-plan-" + UUID.randomUUID())
                .entityType("relationship_query_planning")
                .generationType("relationship_planning")
                .systemPrompt(systemPrompt)
                .prompt(userPrompt)
                .temperature(0.1) // Low temperature for deterministic planning
                .build();
            
            AIGenerationResponse response = aiCoreService.generateContent(request);
            String content = response != null ? response.getContent() : null;
            
            if (!StringUtils.hasText(content)) {
                log.warn("LLM returned empty response, falling back to vector-only strategy");
                return createFallbackPlan(query);
            }
            
            // Parse LLM response
            RelationshipQueryPlan plan = parsePlan(content);
            
            // Validate and enhance plan
            validatePlan(plan, availableEntityTypes);
            
            log.debug("Generated query plan with strategy: {}, paths: {}", 
                plan.getStrategy(), plan.getRelationshipPaths() != null ? plan.getRelationshipPaths().size() : 0);
            
            return plan;
            
        } catch (Exception e) {
            log.error("Error planning relationship query", e);
            return createFallbackPlan(query);
        }
    }
    
    private String buildSystemPrompt(String schemaInfo) {
        return String.format("""
            You are a database query planner that understands user intent and maps it to database relationships.
            
            Your task is to analyze user queries and generate structured query plans that combine:
            1. Semantic vector search (for finding similar content)
            2. Relational database queries (for traversing relationships)
            
            Available Entity Types and Relationships:
            %s
            
            Generate a JSON query plan with:
            - originalQuery: the user's original query
            - semanticQuery: cleaned query for vector search
            - primaryEntityType: main entity type to search
            - relationshipPaths: array of relationship traversals needed
            - directFilters: direct filters from query (e.g., {"status": "active"})
            - relationshipFilters: filters on related entities (e.g., {"user.status": "active"})
            - strategy: VECTOR_ONLY, RELATIONAL_ONLY, HYBRID, RELATIONSHIP_TRAVERSAL, VECTOR_THEN_RELATIONSHIP, RELATIONSHIP_THEN_VECTOR
            - confidence: 0.0 to 1.0
            - includeRelatedEntities: boolean
            - maxTraversalDepth: integer (1-3)
            
            Relationship Path format:
            {
              "fromEntityType": "user",
              "relationshipType": "hasDocuments",
              "toEntityType": "document",
              "direction": "FORWARD",
              "conditions": {},
              "required": true
            }
            
            Examples:
            
            Query: "Find documents created by active users"
            Plan: {
              "semanticQuery": "documents",
              "primaryEntityType": "document",
              "relationshipPaths": [{
                "fromEntityType": "document",
                "relationshipType": "createdBy",
                "toEntityType": "user",
                "direction": "REVERSE",
                "conditions": {"status": "active"}
              }],
              "strategy": "RELATIONSHIP_TRAVERSAL",
              "relationshipFilters": {"user.status": "active"}
            }
            
            Query: "Show me AI-related projects"
            Plan: {
              "semanticQuery": "AI-related projects",
              "primaryEntityType": "project",
              "directFilters": {"category": "ai"},
              "strategy": "HYBRID"
            }
            
            Query: "Find similar documents to this one"
            Plan: {
              "semanticQuery": "similar documents",
              "primaryEntityType": "document",
              "strategy": "VECTOR_ONLY"
            }
            
            Respond with valid JSON only, no markdown formatting.
            """, schemaInfo);
    }
    
    private String buildUserPrompt(String query, List<String> availableEntityTypes) {
        return String.format("""
            Analyze this user query and generate a relationship query plan:
            
            User Query: "%s"
            
            Available Entity Types: %s
            
            Generate a query plan that best matches the user's intent.
            """, query, String.join(", ", availableEntityTypes));
    }
    
    private RelationshipQueryPlan parsePlan(String content) {
        try {
            // Extract JSON from response
            String jsonContent = extractJson(content);
            
            JsonNode root = objectMapper.readTree(jsonContent);
            return objectMapper.treeToValue(root, RelationshipQueryPlan.class);
            
        } catch (JsonProcessingException e) {
            log.error("Failed to parse query plan JSON: {}", content, e);
            throw new AIServiceException("Failed to parse query plan", e);
        }
    }
    
    private String extractJson(String text) {
        // Try to find JSON object {...} in the text
        int startIdx = text.indexOf('{');
        if (startIdx >= 0) {
            int endIdx = text.lastIndexOf('}');
            if (endIdx > startIdx) {
                return text.substring(startIdx, endIdx + 1);
            }
        }
        return text;
    }
    
    private void validatePlan(RelationshipQueryPlan plan, List<String> availableEntityTypes) {
        if (plan == null) {
            throw new AIServiceException("Query plan cannot be null");
        }
        
        // Validate primary entity type
        if (plan.getPrimaryEntityType() != null && 
            !availableEntityTypes.contains(plan.getPrimaryEntityType())) {
            log.warn("Primary entity type {} not in available types, using first available", 
                plan.getPrimaryEntityType());
            plan.setPrimaryEntityType(availableEntityTypes.isEmpty() ? null : availableEntityTypes.get(0));
        }
        
        // Validate relationship paths
        if (plan.getRelationshipPaths() != null) {
            for (RelationshipQueryPlan.RelationshipPath path : plan.getRelationshipPaths()) {
                if (path.getFromEntityType() != null && 
                    !availableEntityTypes.contains(path.getFromEntityType())) {
                    log.warn("Invalid fromEntityType: {}", path.getFromEntityType());
                }
                if (path.getToEntityType() != null && 
                    !availableEntityTypes.contains(path.getToEntityType())) {
                    log.warn("Invalid toEntityType: {}", path.getToEntityType());
                }
            }
        }
        
        // Set defaults
        if (plan.getStrategy() == null) {
            plan.setStrategy(RelationshipQueryPlan.QueryStrategy.HYBRID);
        }
        if (plan.getConfidence() == null) {
            plan.setConfidence(0.7);
        }
        if (plan.getMaxTraversalDepth() == null || plan.getMaxTraversalDepth() < 1) {
            plan.setMaxTraversalDepth(2);
        }
    }
    
    private RelationshipQueryPlan createFallbackPlan(String query) {
        return RelationshipQueryPlan.builder()
            .originalQuery(query)
            .semanticQuery(query)
            .strategy(RelationshipQueryPlan.QueryStrategy.VECTOR_ONLY)
            .confidence(0.5)
            .relationshipPaths(Collections.emptyList())
            .directFilters(Collections.emptyMap())
            .relationshipFilters(Collections.emptyMap())
            .build();
    }
}
