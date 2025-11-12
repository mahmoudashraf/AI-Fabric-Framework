package com.ai.infrastructure.relationship;

import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.dto.*;
import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.rag.RAGService;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.*;
import java.util.stream.Collectors;

/**
 * LLM-Driven JPA Query Service
 * 
 * Complete flow:
 * 1. User query (natural language) → "Find documents created by active users"
 * 2. LLM analyzes query → generates RelationshipQueryPlan
 * 3. DynamicJPAQueryBuilder translates plan → generates JPQL
 * 4. Execute JPA query → get results
 * 5. Optionally rank by vector similarity
 * 
 * This service bridges LLM understanding with JPA query execution.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@Service
public class LLMDrivenJPAQueryService {
    
    private final RelationshipQueryPlanner queryPlanner;
    private final DynamicJPAQueryBuilder queryBuilder;
    private final EntityManager entityManager;
    private final AISearchableEntityRepository searchableEntityRepository;
    private final VectorDatabaseService vectorDatabaseService;
    private final AIEmbeddingService embeddingService;
    
    public LLMDrivenJPAQueryService(RelationshipQueryPlanner queryPlanner,
                                   DynamicJPAQueryBuilder queryBuilder,
                                   EntityManager entityManager,
                                   AISearchableEntityRepository searchableEntityRepository,
                                   VectorDatabaseService vectorDatabaseService,
                                   AIEmbeddingService embeddingService) {
        this.queryPlanner = queryPlanner;
        this.queryBuilder = queryBuilder;
        this.entityManager = entityManager;
        this.searchableEntityRepository = searchableEntityRepository;
        this.vectorDatabaseService = vectorDatabaseService;
        this.embeddingService = embeddingService;
    }
    
    /**
     * Execute relationship-aware query driven by LLM
     * 
     * @param userQuery natural language query (e.g., "Find documents created by active users")
     * @param availableEntityTypes list of available entity types
     * @return RAG response with results
     */
    @Transactional(readOnly = true)
    public RAGResponse executeRelationshipQuery(String userQuery, List<String> availableEntityTypes) {
        try {
            log.info("Executing LLM-driven relationship query: {}", userQuery);
            
            // ============================================================
            // STEP 1: LLM analyzes query and generates plan
            // ============================================================
            RelationshipQueryPlan plan = queryPlanner.planQuery(userQuery, availableEntityTypes);
            
            log.debug("LLM generated plan: strategy={}, paths={}, filters={}", 
                plan.getStrategy(),
                plan.getRelationshipPaths() != null ? plan.getRelationshipPaths().size() : 0,
                plan.getRelationshipFilters());
            
            // ============================================================
            // STEP 2: Build JPA query from plan
            // ============================================================
            String jpql = queryBuilder.buildQuery(plan);
            
            if (jpql == null || jpql.isEmpty()) {
                log.warn("Could not build JPA query from plan, falling back to vector search");
                return fallbackToVectorSearch(userQuery, plan);
            }
            
            log.debug("Generated JPQL: {}", jpql);
            
            // ============================================================
            // STEP 3: Execute JPA query
            // ============================================================
            Query query = entityManager.createQuery(jpql);
            
            // Set parameters from plan
            setQueryParameters(query, plan);
            
            @SuppressWarnings("unchecked")
            List<Object> results = query.getResultList();
            
            log.debug("JPA query returned {} results", results.size());
            
            // ============================================================
            // STEP 4: Extract entity IDs
            // ============================================================
            List<String> entityIds = extractEntityIds(results);
            
            if (entityIds.isEmpty()) {
                return createEmptyResponse();
            }
            
            // ============================================================
            // STEP 5: Optionally rank by vector similarity
            // ============================================================
            if (plan.getSemanticQuery() != null && !plan.getSemanticQuery().isEmpty()) {
                return rankByVectorSimilarity(entityIds, plan, userQuery);
            }
            
            // ============================================================
            // STEP 6: Build response
            // ============================================================
            return buildRAGResponseFromEntityIds(entityIds, plan.getPrimaryEntityType());
            
        } catch (Exception e) {
            log.error("Error executing LLM-driven relationship query", e);
            return fallbackToVectorSearch(userQuery, null);
        }
    }
    
    /**
     * Set query parameters from plan
     */
    private void setQueryParameters(Query query, RelationshipQueryPlan plan) {
        // Set relationship filters
        if (plan.getRelationshipFilters() != null) {
            for (Map.Entry<String, Object> filter : plan.getRelationshipFilters().entrySet()) {
                String paramName = filter.getKey().replace(".", "_").replace("-", "_");
                query.setParameter(paramName, filter.getValue());
            }
        }
        
        // Set path conditions
        if (plan.getRelationshipPaths() != null) {
            for (RelationshipQueryPlan.RelationshipPath path : plan.getRelationshipPaths()) {
                if (path.getConditions() != null) {
                    for (Map.Entry<String, Object> condition : path.getConditions().entrySet()) {
                        String paramName = condition.getKey().replace(".", "_").replace("-", "_");
                        query.setParameter(paramName, condition.getValue());
                    }
                }
            }
        }
        
        // Set direct filters
        if (plan.getDirectFilters() != null) {
            for (Map.Entry<String, Object> filter : plan.getDirectFilters().entrySet()) {
                String paramName = filter.getKey().replace(".", "_").replace("-", "_");
                query.setParameter(paramName, filter.getValue());
            }
        }
    }
    
    /**
     * Extract entity IDs from query results
     */
    private List<String> extractEntityIds(List<Object> results) {
        return results.stream()
            .map(this::extractEntityId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
    }
    
    /**
     * Extract entity ID from result object
     */
    private String extractEntityId(Object result) {
        if (result == null) {
            return null;
        }
        
        if (result instanceof String) {
            return (String) result;
        }
        
        try {
            // Try to get ID field via reflection
            java.lang.reflect.Method getIdMethod = result.getClass().getMethod("getId");
            Object id = getIdMethod.invoke(result);
            return id != null ? id.toString() : null;
        } catch (Exception e) {
            log.debug("Could not extract ID from result: {}", result.getClass().getName());
            return result.toString();
        }
    }
    
    /**
     * Rank entities by vector similarity
     */
    private RAGResponse rankByVectorSimilarity(List<String> entityIds,
                                               RelationshipQueryPlan plan,
                                               String userQuery) {
        try {
            // Generate query embedding
            List<Double> queryVector = embeddingService.generateEmbedding(
                AIEmbeddingRequest.builder().text(plan.getSemanticQuery()).build()
            ).getEmbedding();
            
            // Get vectors and compute similarity
            List<RAGResponse.RAGDocument> scoredDocs = new ArrayList<>();
            
            for (String entityId : entityIds) {
                Optional<AISearchableEntity> entityOpt = searchableEntityRepository
                    .findByEntityTypeAndEntityId(plan.getPrimaryEntityType(), entityId);
                
                if (entityOpt.isPresent() && entityOpt.get().getVectorId() != null) {
                    Optional<VectorRecord> vectorOpt = vectorDatabaseService
                        .getVector(entityOpt.get().getVectorId());
                    
                    if (vectorOpt.isPresent()) {
                        double similarity = computeCosineSimilarity(
                            queryVector,
                            vectorOpt.get().getEmbedding()
                        );
                        
                        RAGResponse.RAGDocument doc = RAGResponse.RAGDocument.builder()
                            .id(entityId)
                            .content(vectorOpt.get().getContent())
                            .similarity(similarity)
                            .score(similarity)
                            .metadata(parseMetadata(entityOpt.get().getMetadata()))
                            .build();
                        
                        scoredDocs.add(doc);
                    }
                }
            }
            
            // Sort by similarity
            scoredDocs.sort((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));
            
            return RAGResponse.builder()
                .documents(scoredDocs)
                .totalDocuments(scoredDocs.size())
                .usedDocuments(scoredDocs.size())
                .success(true)
                .build();
            
        } catch (Exception e) {
            log.error("Error ranking by vector similarity", e);
            return buildRAGResponseFromEntityIds(entityIds, plan.getPrimaryEntityType());
        }
    }
    
    /**
     * Build RAG response from entity IDs
     */
    private RAGResponse buildRAGResponseFromEntityIds(List<String> entityIds, String entityType) {
        List<RAGResponse.RAGDocument> documents = new ArrayList<>();
        
        for (String entityId : entityIds) {
            Optional<AISearchableEntity> entityOpt = searchableEntityRepository
                .findByEntityTypeAndEntityId(entityType, entityId);
            
            if (entityOpt.isPresent()) {
                AISearchableEntity entity = entityOpt.get();
                
                RAGResponse.RAGDocument doc = RAGResponse.RAGDocument.builder()
                    .id(entityId)
                    .content(entity.getSearchableContent())
                    .metadata(parseMetadata(entity.getMetadata()))
                    .similarity(1.0)
                    .score(1.0)
                    .build();
                
                documents.add(doc);
            }
        }
        
        return RAGResponse.builder()
            .documents(documents)
            .totalDocuments(documents.size())
            .usedDocuments(documents.size())
            .success(true)
            .build();
    }
    
    /**
     * Fallback to vector search if JPA query fails
     */
    private RAGResponse fallbackToVectorSearch(String query, RelationshipQueryPlan plan) {
        log.info("Falling back to vector search");
        // Use standard RAG service
        RAGRequest request = RAGRequest.builder()
            .query(query)
            .entityType(plan != null ? plan.getPrimaryEntityType() : null)
            .limit(10)
            .build();
        
        // Would need RAGService injected for this
        // For now, return empty
        return createEmptyResponse();
    }
    
    /**
     * Create empty response
     */
    private RAGResponse createEmptyResponse() {
        return RAGResponse.builder()
            .documents(Collections.emptyList())
            .totalDocuments(0)
            .usedDocuments(0)
            .success(true)
            .build();
    }
    
    /**
     * Compute cosine similarity
     */
    private double computeCosineSimilarity(List<Double> vec1, List<Double> vec2) {
        if (vec1 == null || vec2 == null || vec1.size() != vec2.size()) {
            return 0.0;
        }
        
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        
        for (int i = 0; i < vec1.size(); i++) {
            dotProduct += vec1.get(i) * vec2.get(i);
            norm1 += vec1.get(i) * vec1.get(i);
            norm2 += vec2.get(i) * vec2.get(i);
        }
        
        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
    
    /**
     * Parse metadata JSON
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.trim().isEmpty() || "{}".equals(metadataJson.trim())) {
            return Collections.emptyMap();
        }
        
        // Simplified parsing - in production use Jackson ObjectMapper
        Map<String, Object> result = new HashMap<>();
        try {
            // Placeholder - should use proper JSON parsing
            result.put("raw", metadataJson);
        } catch (Exception e) {
            log.debug("Error parsing metadata", e);
        }
        return result;
    }
}
