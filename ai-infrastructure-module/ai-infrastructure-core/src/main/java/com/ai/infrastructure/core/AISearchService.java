package com.ai.infrastructure.core;

import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.exception.AIServiceException;
import com.ai.infrastructure.search.VectorSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for AI semantic search
 * 
 * This service provides semantic search capabilities using vector similarity.
 * It can search across different entity types and provides ranking and filtering.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
// @Service // Removed - already defined as @Bean in AIInfrastructureAutoConfiguration
public class AISearchService {
    
    private final AIProviderConfig config;
    private final VectorSearchService vectorSearchService;
    
    public AISearchService(AIProviderConfig config, VectorSearchService vectorSearchService) {
        this.config = config;
        this.vectorSearchService = vectorSearchService;
    }
    
    // In-memory storage for demo purposes
    // In production, this would be replaced with a proper vector database
    private final Map<String, List<Map<String, Object>>> vectorStore = new HashMap<>();
    
    /**
     * Perform semantic search using vector similarity
     * 
     * @param queryVector the query vector for search
     * @param request the search request
     * @return search results with relevance scores
     */
    public AISearchResponse search(List<Double> queryVector, AISearchRequest request) {
        try {
            log.debug("Performing semantic search for query: {}", request.getQuery());
            
            // Use the advanced VectorSearchService for better performance
            return vectorSearchService.search(queryVector, request);
                
        } catch (Exception e) {
            log.error("Error performing semantic search", e);
            throw new AIServiceException("Failed to perform semantic search", e);
        }
    }
    
    /**
     * Perform hybrid search combining vector and text similarity
     * 
     * @param queryVector the query vector for search
     * @param queryText the original query text
     * @param request the search request
     * @return hybrid search results
     */
    public AISearchResponse hybridSearch(List<Double> queryVector, String queryText, AISearchRequest request) {
        try {
            log.debug("Performing hybrid search for query: {}", queryText);
            
            return vectorSearchService.hybridSearch(queryVector, queryText, request);
                
        } catch (Exception e) {
            log.error("Error performing hybrid search", e);
            throw new AIServiceException("Failed to perform hybrid search", e);
        }
    }
    
    /**
     * Perform contextual search with additional context
     * 
     * @param queryVector the query vector for search
     * @param context the search context
     * @param request the search request
     * @return context-aware search results
     */
    public AISearchResponse contextualSearch(List<Double> queryVector, String context, AISearchRequest request) {
        try {
            log.debug("Performing contextual search with context: {}", context);
            
            return vectorSearchService.contextualSearch(queryVector, context, request);
                
        } catch (Exception e) {
            log.error("Error performing contextual search", e);
            throw new AIServiceException("Failed to perform contextual search", e);
        }
    }
    
    /**
     * Index an entity with its embedding
     * 
     * @param entityType the type of entity
     * @param entityId the unique identifier
     * @param content the text content
     * @param embedding the vector embedding
     * @param metadata additional metadata
     */
    public void indexEntity(String entityType, String entityId, String content, 
                           List<Double> embedding, Map<String, Object> metadata) {
        try {
            log.debug("Indexing entity {} of type {}", entityId, entityType);
            
            // Use VectorSearchService for advanced indexing
            vectorSearchService.storeVector(entityType, entityId, content, embedding, metadata);
            
            // Also maintain backward compatibility with simple store
            Map<String, Object> entity = new HashMap<>();
            entity.put("id", entityId);
            entity.put("content", content);
            entity.put("embedding", embedding);
            entity.put("entityType", entityType);
            entity.put("metadata", metadata != null ? metadata : new HashMap<>());
            entity.put("indexedAt", System.currentTimeMillis());
            
            vectorStore.computeIfAbsent(entityType, k -> new ArrayList<>()).add(entity);
            
            log.debug("Successfully indexed entity {} of type {}", entityId, entityType);
            
        } catch (Exception e) {
            log.error("Error indexing entity", e);
            throw new AIServiceException("Failed to index entity", e);
        }
    }
    
    /**
     * Remove an entity from the index
     * 
     * @param entityType the type of entity
     * @param entityId the unique identifier
     */
    public void removeEntity(String entityType, String entityId) {
        try {
            log.debug("Removing entity {} of type {}", entityId, entityType);
            
            List<Map<String, Object>> entities = vectorStore.get(entityType);
            if (entities != null) {
                entities.removeIf(entity -> entityId.equals(entity.get("id")));
            }
            
            log.debug("Successfully removed entity {} of type {}", entityId, entityType);
            
        } catch (Exception e) {
            log.error("Error removing entity", e);
            throw new AIServiceException("Failed to remove entity", e);
        }
    }
    
    /**
     * Calculate cosine similarity between two vectors
     * 
     * @param vectorA first vector
     * @param vectorB second vector
     * @return cosine similarity score
     */
    private double calculateCosineSimilarity(List<Double> vectorA, List<Double> vectorB) {
        if (vectorA == null || vectorB == null || vectorA.size() != vectorB.size()) {
            return 0.0;
        }
        
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        
        for (int i = 0; i < vectorA.size(); i++) {
            double a = vectorA.get(i);
            double b = vectorB.get(i);
            dotProduct += a * b;
            normA += a * a;
            normB += b * b;
        }
        
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
    
    /**
     * Get statistics about the vector store
     * 
     * @return map of statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalEntities", vectorStore.values().stream().mapToInt(List::size).sum());
        stats.put("entityTypes", vectorStore.keySet());
        stats.put("entityTypeCounts", vectorStore.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().size())));
        return stats;
    }
    
    /**
     * Clear all entities from the index
     */
    public void clearIndex() {
        log.debug("Clearing vector store index");
        vectorStore.clear();
    }
    
    /**
     * Get search statistics and performance metrics
     * 
     * @return map of search statistics
     */
    public Map<String, Object> getSearchStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalEntities", vectorStore.values().stream().mapToInt(List::size).sum());
        stats.put("entityTypes", vectorStore.keySet());
        stats.put("entityTypeCounts", vectorStore.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().size())));
        
        // Add advanced search statistics
        Map<String, Object> advancedStats = vectorSearchService.getSearchStatistics();
        stats.putAll(advancedStats);
        
        return stats;
    }
}
