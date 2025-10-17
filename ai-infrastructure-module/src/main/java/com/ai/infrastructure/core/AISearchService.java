package com.ai.infrastructure.core;

import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.exception.AIServiceException;
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
@Service
@RequiredArgsConstructor
public class AISearchService {
    
    private final AIProviderConfig config;
    
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
            
            long startTime = System.currentTimeMillis();
            
            // Get entities to search
            String entityType = request.getEntityType();
            List<Map<String, Object>> entities = vectorStore.getOrDefault(entityType, new ArrayList<>());
            
            if (entities.isEmpty()) {
                log.debug("No entities found for type: {}", entityType);
                return AISearchResponse.builder()
                    .results(new ArrayList<>())
                    .totalResults(0)
                    .maxScore(0.0)
                    .processingTimeMs(System.currentTimeMillis() - startTime)
                    .requestId(UUID.randomUUID().toString())
                    .query(request.getQuery())
                    .model(config.getOpenaiEmbeddingModel())
                    .build();
            }
            
            // Calculate similarity scores
            List<Map<String, Object>> scoredEntities = entities.stream()
                .map(entity -> {
                    List<Double> entityVector = (List<Double>) entity.get("embedding");
                    double similarity = calculateCosineSimilarity(queryVector, entityVector);
                    
                    Map<String, Object> scoredEntity = new HashMap<>(entity);
                    scoredEntity.put("similarity", similarity);
                    scoredEntity.put("score", similarity);
                    return scoredEntity;
                })
                .filter(entity -> (Double) entity.get("similarity") >= request.getThreshold())
                .sorted((a, b) -> Double.compare((Double) b.get("similarity"), (Double) a.get("similarity")))
                .limit(request.getLimit())
                .collect(Collectors.toList());
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            log.debug("Found {} results in {}ms", scoredEntities.size(), processingTime);
            
            return AISearchResponse.builder()
                .results(scoredEntities)
                .totalResults(scoredEntities.size())
                .maxScore(scoredEntities.isEmpty() ? 0.0 : (Double) scoredEntities.get(0).get("similarity"))
                .processingTimeMs(processingTime)
                .requestId(UUID.randomUUID().toString())
                .query(request.getQuery())
                .model(config.getOpenaiEmbeddingModel())
                .build();
                
        } catch (Exception e) {
            log.error("Error performing semantic search", e);
            throw new AIServiceException("Failed to perform semantic search", e);
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
}
