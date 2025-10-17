package com.ai.infrastructure.rag;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.exception.AIServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Vector Database Service
 * 
 * This service provides vector database operations for storing and searching
 * vector embeddings. It currently uses an in-memory implementation but can
 * be extended to support various vector databases like Pinecone, Chroma, etc.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorDatabaseService {
    
    private final AIProviderConfig config;
    
    // In-memory vector store for demo purposes
    // In production, this would be replaced with a proper vector database
    private final Map<String, List<Map<String, Object>>> vectorStore = new HashMap<>();
    
    /**
     * Store a vector in the database
     * 
     * @param entityType the type of entity
     * @param entityId the unique identifier
     * @param content the text content
     * @param embedding the vector embedding
     * @param metadata additional metadata
     */
    public void storeVector(String entityType, String entityId, String content, 
                           List<Double> embedding, Map<String, Object> metadata) {
        try {
            log.debug("Storing vector for entity {} of type {}", entityId, entityType);
            
            Map<String, Object> vector = new HashMap<>();
            vector.put("id", entityId);
            vector.put("content", content);
            vector.put("embedding", embedding);
            vector.put("entityType", entityType);
            vector.put("metadata", metadata != null ? metadata : new HashMap<>());
            vector.put("storedAt", System.currentTimeMillis());
            
            vectorStore.computeIfAbsent(entityType, k -> new ArrayList<>()).add(vector);
            
            log.debug("Successfully stored vector for entity {} of type {}", entityId, entityType);
            
        } catch (Exception e) {
            log.error("Error storing vector", e);
            throw new AIServiceException("Failed to store vector", e);
        }
    }
    
    /**
     * Search for similar vectors
     * 
     * @param queryVector the query vector
     * @param request the search request
     * @return search results with similarity scores
     */
    public AISearchResponse search(List<Double> queryVector, AISearchRequest request) {
        try {
            log.debug("Searching vectors for query: {}", request.getQuery());
            
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
            log.error("Error searching vectors", e);
            throw new AIServiceException("Failed to search vectors", e);
        }
    }
    
    /**
     * Remove a vector from the database
     * 
     * @param entityType the type of entity
     * @param entityId the unique identifier
     */
    public void removeVector(String entityType, String entityId) {
        try {
            log.debug("Removing vector for entity {} of type {}", entityId, entityType);
            
            List<Map<String, Object>> entities = vectorStore.get(entityType);
            if (entities != null) {
                entities.removeIf(entity -> entityId.equals(entity.get("id")));
            }
            
            log.debug("Successfully removed vector for entity {} of type {}", entityId, entityType);
            
        } catch (Exception e) {
            log.error("Error removing vector", e);
            throw new AIServiceException("Failed to remove vector", e);
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
        stats.put("totalVectors", vectorStore.values().stream().mapToInt(List::size).sum());
        stats.put("entityTypes", vectorStore.keySet());
        stats.put("entityTypeCounts", vectorStore.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().size())));
        return stats;
    }
    
    /**
     * Clear all vectors from the database
     */
    public void clearVectors() {
        log.debug("Clearing vector store");
        vectorStore.clear();
    }
}
