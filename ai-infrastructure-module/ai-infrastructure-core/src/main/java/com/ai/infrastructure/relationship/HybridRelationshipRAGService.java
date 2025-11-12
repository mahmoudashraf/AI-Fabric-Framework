package com.ai.infrastructure.relationship;

import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.dto.*;
import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.rag.RAGService;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.ai.infrastructure.dto.VectorRecord;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Hybrid Relationship-Aware RAG Service
 * 
 * Combines LLM intent understanding, vector similarity search, and relational database traversal
 * to provide intelligent relationship-aware search capabilities.
 * 
 * This service bridges the gap between:
 * - Semantic vector search (for content similarity)
 * - Relational database queries (for relationship traversal)
 * - LLM understanding (for query intent extraction)
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@Service
public class HybridRelationshipRAGService {
    
    private final RelationshipQueryPlanner queryPlanner;
    private final RelationshipTraversalService traversalService;
    private final RAGService ragService;
    private final VectorDatabaseService vectorDatabaseService;
    private final AIEmbeddingService embeddingService;
    private final AISearchableEntityRepository searchableEntityRepository;
    
    public HybridRelationshipRAGService(RelationshipQueryPlanner queryPlanner,
                                       RelationshipTraversalService traversalService,
                                       RAGService ragService,
                                       VectorDatabaseService vectorDatabaseService,
                                       AIEmbeddingService embeddingService,
                                       AISearchableEntityRepository searchableEntityRepository) {
        this.queryPlanner = queryPlanner;
        this.traversalService = traversalService;
        this.ragService = ragService;
        this.vectorDatabaseService = vectorDatabaseService;
        this.embeddingService = embeddingService;
        this.searchableEntityRepository = searchableEntityRepository;
    }
    
    /**
     * Perform relationship-aware RAG query
     * 
     * @param request the RAG request
     * @return enhanced RAG response with relationship context
     */
    public RAGResponse performRelationshipAwareRAG(RAGRequest request) {
        try {
            log.debug("Performing relationship-aware RAG for query: {}", request.getQuery());
            
            // Step 1: Use LLM to plan the query
            List<String> availableEntityTypes = getAvailableEntityTypes();
            RelationshipQueryPlan plan = queryPlanner.planQuery(request.getQuery(), availableEntityTypes);
            
            log.debug("Query plan: strategy={}, paths={}, confidence={}", 
                plan.getStrategy(), 
                plan.getRelationshipPaths() != null ? plan.getRelationshipPaths().size() : 0,
                plan.getConfidence());
            
            // Step 2: Execute query based on strategy
            RAGResponse response;
            
            switch (plan.getStrategy()) {
                case VECTOR_ONLY:
                    response = performVectorOnlySearch(request, plan);
                    break;
                    
                case RELATIONAL_ONLY:
                    response = performRelationalOnlySearch(request, plan);
                    break;
                    
                case HYBRID:
                    response = performHybridSearch(request, plan);
                    break;
                    
                case RELATIONSHIP_TRAVERSAL:
                    response = performRelationshipTraversalSearch(request, plan);
                    break;
                    
                case VECTOR_THEN_RELATIONSHIP:
                    response = performVectorThenRelationship(request, plan);
                    break;
                    
                case RELATIONSHIP_THEN_VECTOR:
                    response = performRelationshipThenVector(request, plan);
                    break;
                    
                default:
                    response = performHybridSearch(request, plan);
            }
            
            // Step 3: Enrich results with relationship context
            if (plan.getIncludeRelatedEntities() != null && plan.getIncludeRelatedEntities()) {
                response = enrichWithRelatedEntities(response, plan);
            }
            
            return response;
            
        } catch (Exception e) {
            log.error("Error performing relationship-aware RAG", e);
            // Fallback to standard RAG
            return ragService.performRag(request);
        }
    }
    
    /**
     * Vector-only search (semantic similarity)
     */
    private RAGResponse performVectorOnlySearch(RAGRequest request, RelationshipQueryPlan plan) {
        log.debug("Performing vector-only search");
        
        // Use standard RAG service
        RAGRequest vectorRequest = RAGRequest.builder()
            .query(plan.getSemanticQuery() != null ? plan.getSemanticQuery() : request.getQuery())
            .entityType(plan.getPrimaryEntityType() != null ? plan.getPrimaryEntityType() : request.getEntityType())
            .limit(request.getLimit())
            .threshold(request.getThreshold())
            .filters(plan.getDirectFilters())
            .build();
        
        return ragService.performRag(vectorRequest);
    }
    
    /**
     * Relational-only search (database queries)
     */
    private RAGResponse performRelationalOnlySearch(RAGRequest request, RelationshipQueryPlan plan) {
        log.debug("Performing relational-only search");
        
        // Traverse relationships
        List<String> entityIds = traversalService.traverseRelationships(plan);
        
        if (entityIds.isEmpty()) {
            return createEmptyResponse(request);
        }
        
        // Fetch entities and convert to RAG response
        return buildRAGResponseFromEntityIds(entityIds, plan.getPrimaryEntityType(), request);
    }
    
    /**
     * Hybrid search (combine vector + relational)
     */
    private RAGResponse performHybridSearch(RAGRequest request, RelationshipQueryPlan plan) {
        log.debug("Performing hybrid search");
        
        // Step 1: Vector search
        RAGRequest vectorRequest = RAGRequest.builder()
            .query(plan.getSemanticQuery() != null ? plan.getSemanticQuery() : request.getQuery())
            .entityType(plan.getPrimaryEntityType() != null ? plan.getPrimaryEntityType() : request.getEntityType())
            .limit(request.getLimit() * 2) // Get more results for filtering
            .threshold(request.getThreshold())
            .build();
        
        RAGResponse vectorResponse = ragService.performRag(vectorRequest);
        
        // Step 2: Apply relational filters
        if (plan.getRelationshipFilters() != null && !plan.getRelationshipFilters().isEmpty()) {
            vectorResponse = filterByRelationships(vectorResponse, plan);
        }
        
        // Step 3: Limit results
        return limitResults(vectorResponse, request.getLimit());
    }
    
    /**
     * Relationship traversal search
     */
    private RAGResponse performRelationshipTraversalSearch(RAGRequest request, RelationshipQueryPlan plan) {
        log.debug("Performing relationship traversal search");
        
        // Traverse relationships to get entity IDs
        List<String> entityIds = traversalService.traverseRelationships(plan);
        
        if (entityIds.isEmpty()) {
            return createEmptyResponse(request);
        }
        
        // Optionally: Re-rank by vector similarity
        if (plan.getSemanticQuery() != null && !plan.getSemanticQuery().isEmpty()) {
            return reRankByVectorSimilarity(entityIds, plan, request);
        }
        
        // Return relational results
        return buildRAGResponseFromEntityIds(entityIds, plan.getPrimaryEntityType(), request);
    }
    
    /**
     * Vector search first, then enrich with relationships
     */
    private RAGResponse performVectorThenRelationship(RAGRequest request, RelationshipQueryPlan plan) {
        log.debug("Performing vector-then-relationship search");
        
        // Step 1: Vector search
        RAGResponse vectorResponse = performVectorOnlySearch(request, plan);
        
        // Step 2: Find related entities for each result
        return enrichWithRelatedEntities(vectorResponse, plan);
    }
    
    /**
     * Relationship query first, then vector search on results
     */
    private RAGResponse performRelationshipThenVector(RAGRequest request, RelationshipQueryPlan plan) {
        log.debug("Performing relationship-then-vector search");
        
        // Step 1: Get entity IDs from relationship traversal
        List<String> entityIds = traversalService.traverseRelationships(plan);
        
        if (entityIds.isEmpty()) {
            return createEmptyResponse(request);
        }
        
        // Step 2: Re-rank by vector similarity
        return reRankByVectorSimilarity(entityIds, plan, request);
    }
    
    /**
     * Re-rank entities by vector similarity
     */
    private RAGResponse reRankByVectorSimilarity(List<String> entityIds, 
                                                  RelationshipQueryPlan plan,
                                                  RAGRequest request) {
        try {
            if (entityIds.isEmpty() || plan.getSemanticQuery() == null) {
                return buildRAGResponseFromEntityIds(entityIds, plan.getPrimaryEntityType(), request);
            }
            
            // Generate query embedding
            AIEmbeddingRequest embeddingRequest = AIEmbeddingRequest.builder()
                .text(plan.getSemanticQuery())
                .build();
            
            List<Double> queryVector = embeddingService.generateEmbedding(embeddingRequest).getEmbedding();
            
            // Get vectors for entity IDs and compute similarity
            List<RAGResponse.RAGDocument> rankedDocs = new ArrayList<>();
            
            for (String entityId : entityIds) {
                Optional<AISearchableEntity> entityOpt = searchableEntityRepository
                    .findByEntityTypeAndEntityId(plan.getPrimaryEntityType(), entityId);
                
                if (entityOpt.isPresent() && entityOpt.get().getVectorId() != null) {
                    Optional<VectorRecord> vectorOpt = vectorDatabaseService
                        .getVector(entityOpt.get().getVectorId());
                    
                    if (vectorOpt.isPresent()) {
                        double similarity = computeCosineSimilarity(queryVector, vectorOpt.get().getEmbedding());
                        
                        RAGResponse.RAGDocument doc = RAGResponse.RAGDocument.builder()
                            .id(entityId)
                            .content(vectorOpt.get().getContent())
                            .similarity(similarity)
                            .score(similarity)
                            .metadata(parseMetadata(entityOpt.get().getMetadata()))
                            .build();
                        
                        rankedDocs.add(doc);
                    }
                }
            }
            
            // Sort by similarity
            rankedDocs.sort((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));
            
            // Limit results
            List<RAGResponse.RAGDocument> limitedDocs = rankedDocs.stream()
                .limit(request.getLimit())
                .collect(Collectors.toList());
            
            return RAGResponse.builder()
                .documents(limitedDocs)
                .totalDocuments(rankedDocs.size())
                .usedDocuments(limitedDocs.size())
                .success(true)
                .build();
            
        } catch (Exception e) {
            log.error("Error re-ranking by vector similarity", e);
            return buildRAGResponseFromEntityIds(entityIds, plan.getPrimaryEntityType(), request);
        }
    }
    
    /**
     * Build RAG response from entity IDs
     */
    private RAGResponse buildRAGResponseFromEntityIds(List<String> entityIds, 
                                                      String entityType,
                                                      RAGRequest request) {
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
                    .similarity(1.0) // Default similarity for relational results
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
     * Enrich RAG response with related entities
     */
    private RAGResponse enrichWithRelatedEntities(RAGResponse response, RelationshipQueryPlan plan) {
        if (response.getDocuments() == null || response.getDocuments().isEmpty()) {
            return response;
        }
        
        List<RAGResponse.RAGDocument> enrichedDocs = new ArrayList<>();
        
        for (RAGResponse.RAGDocument doc : response.getDocuments()) {
            // Find related entities via metadata
            Map<String, Object> relatedEntities = findRelatedEntities(doc, plan);
            
            // Add to document metadata
            Map<String, Object> enrichedMetadata = new HashMap<>(doc.getMetadata());
            enrichedMetadata.put("relatedEntities", relatedEntities);
            
            RAGResponse.RAGDocument enrichedDoc = RAGResponse.RAGDocument.builder()
                .id(doc.getId())
                .content(doc.getContent())
                .title(doc.getTitle())
                .type(doc.getType())
                .score(doc.getScore())
                .similarity(doc.getSimilarity())
                .metadata(enrichedMetadata)
                .build();
            
            enrichedDocs.add(enrichedDoc);
        }
        
        return RAGResponse.builder()
            .documents(enrichedDocs)
            .totalDocuments(response.getTotalDocuments())
            .usedDocuments(response.getUsedDocuments())
            .success(response.getSuccess())
            .build();
    }
    
    /**
     * Find related entities for a document
     */
    private Map<String, Object> findRelatedEntities(RAGResponse.RAGDocument doc, RelationshipQueryPlan plan) {
        Map<String, Object> relatedEntities = new HashMap<>();
        
        if (plan.getRelationshipPaths() == null || doc.getMetadata() == null) {
            return relatedEntities;
        }
        
        for (RelationshipQueryPlan.RelationshipPath path : plan.getRelationshipPaths()) {
            Object relatedId = doc.getMetadata().get(path.getRelationshipType());
            if (relatedId != null) {
                relatedEntities.put(path.getToEntityType(), relatedId);
            }
        }
        
        return relatedEntities;
    }
    
    /**
     * Filter RAG response by relationships
     */
    private RAGResponse filterByRelationships(RAGResponse response, RelationshipQueryPlan plan) {
        if (response.getDocuments() == null || plan.getRelationshipFilters() == null) {
            return response;
        }
        
        List<RAGResponse.RAGDocument> filteredDocs = response.getDocuments().stream()
            .filter(doc -> matchesRelationshipFilters(doc, plan.getRelationshipFilters()))
            .collect(Collectors.toList());
        
        return RAGResponse.builder()
            .documents(filteredDocs)
            .totalDocuments(filteredDocs.size())
            .usedDocuments(filteredDocs.size())
            .success(response.getSuccess())
            .build();
    }
    
    /**
     * Check if document matches relationship filters
     */
    private boolean matchesRelationshipFilters(RAGResponse.RAGDocument doc, Map<String, Object> filters) {
        if (doc.getMetadata() == null) {
            return false;
        }
        
        for (Map.Entry<String, Object> filter : filters.entrySet()) {
            Object docValue = doc.getMetadata().get(filter.getKey());
            if (!Objects.equals(docValue, filter.getValue())) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Limit results to requested limit
     */
    private RAGResponse limitResults(RAGResponse response, Integer limit) {
        if (limit == null || response.getDocuments() == null || response.getDocuments().size() <= limit) {
            return response;
        }
        
        List<RAGResponse.RAGDocument> limitedDocs = response.getDocuments().stream()
            .limit(limit)
            .collect(Collectors.toList());
        
        return RAGResponse.builder()
            .documents(limitedDocs)
            .totalDocuments(response.getTotalDocuments())
            .usedDocuments(limitedDocs.size())
            .success(response.getSuccess())
            .build();
    }
    
    /**
     * Create empty response
     */
    private RAGResponse createEmptyResponse(RAGRequest request) {
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
        
        // Simplified parsing - in production, use proper JSON library
        Map<String, Object> result = new HashMap<>();
        try {
            // This is a placeholder - should use Jackson ObjectMapper
            result.put("raw", metadataJson);
        } catch (Exception e) {
            log.debug("Error parsing metadata", e);
        }
        return result;
    }
    
    /**
     * Get available entity types
     */
    private List<String> getAvailableEntityTypes() {
        // Get distinct entity types from searchable entities
        return searchableEntityRepository.findAll().stream()
            .map(AISearchableEntity::getEntityType)
            .distinct()
            .collect(Collectors.toList());
    }
}
