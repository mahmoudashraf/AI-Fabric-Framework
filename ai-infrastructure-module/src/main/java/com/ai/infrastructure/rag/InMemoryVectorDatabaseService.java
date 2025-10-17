package com.ai.infrastructure.rag;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.exception.AIServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * In-Memory Vector Database Service
 * 
 * This service provides vector database operations using an in-memory store.
 * It's designed for testing and development environments where persistence
 * is not required.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.vector-db.type", havingValue = "memory", matchIfMissing = false)
public class InMemoryVectorDatabaseService implements VectorDatabaseService {
    
    private final AIProviderConfig config;
    
    // In-memory vector store
    private final Map<String, List<Map<String, Object>>> vectorStore = new HashMap<>();
    
    @Override
    public void storeVector(String entityType, String entityId, String content, 
                           List<Double> embedding, Map<String, Object> metadata) {
        try {
            log.debug("Storing vector in memory for entity {} of type {}", entityId, entityType);
            
            Map<String, Object> vector = new HashMap<>();
            vector.put("id", entityId);
            vector.put("content", content);
            vector.put("embedding", embedding);
            vector.put("entityType", entityType);
            vector.put("metadata", metadata != null ? metadata : new HashMap<>());
            vector.put("storedAt", System.currentTimeMillis());
            
            vectorStore.computeIfAbsent(entityType, k -> new ArrayList<>()).add(vector);
            
            log.debug("Successfully stored vector in memory for entity {} of type {}", entityId, entityType);
            
        } catch (Exception e) {
            log.error("Error storing vector in memory", e);
            throw new AIServiceException("Failed to store vector in memory", e);
        }
    }
    
    @Override
    public AISearchResponse search(List<Double> queryVector, AISearchRequest request) {
        try {
            log.debug("Searching vectors in memory for query: {}", request.getQuery());
            
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
            
            log.debug("Found {} results in memory in {}ms", scoredEntities.size(), processingTime);
            
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
            log.error("Error searching vectors in memory", e);
            throw new AIServiceException("Failed to search vectors in memory", e);
        }
    }
    
    @Override
    public void removeVector(String entityType, String entityId) {
        try {
            log.debug("Removing vector from memory for entity {} of type {}", entityId, entityType);
            
            List<Map<String, Object>> entities = vectorStore.get(entityType);
            if (entities != null) {
                entities.removeIf(entity -> entityId.equals(entity.get("id")));
            }
            
            log.debug("Successfully removed vector from memory for entity {} of type {}", entityId, entityType);
            
        } catch (Exception e) {
            log.error("Error removing vector from memory", e);
            throw new AIServiceException("Failed to remove vector from memory", e);
        }
    }
    
    @Override
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("type", "memory");
        stats.put("totalVectors", vectorStore.values().stream().mapToInt(List::size).sum());
        stats.put("entityTypes", vectorStore.keySet());
        stats.put("entityTypeCounts", vectorStore.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().size())));
        return stats;
    }
    
    @Override
    public void clearVectors() {
        log.debug("Clearing vector store from memory");
        vectorStore.clear();
    }
    
    /**
     * Calculate cosine similarity between two vectors
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
}