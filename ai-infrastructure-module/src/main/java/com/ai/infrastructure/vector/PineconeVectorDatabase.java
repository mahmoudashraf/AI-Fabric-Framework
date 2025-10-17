package com.ai.infrastructure.vector;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.exception.AIServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Pinecone vector database implementation
 * 
 * This implementation provides Pinecone integration for vector storage
 * and search operations. For now, it uses an in-memory store as a fallback.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PineconeVectorDatabase implements VectorDatabase {
    
    private final AIProviderConfig config;
    
    // In-memory store for demo purposes
    // In production, this would be replaced with actual Pinecone client
    private final Map<String, List<Map<String, Object>>> vectorStore = new ConcurrentHashMap<>();
    
    // Performance metrics
    private final Map<String, Long> operationCounts = new ConcurrentHashMap<>();
    private final Map<String, Long> operationTimes = new ConcurrentHashMap<>();
    
    @Override
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
            vector.put("dimensions", embedding.size());
            
            vectorStore.computeIfAbsent(entityType, k -> new ArrayList<>()).add(vector);
            
            updateMetrics("store", System.currentTimeMillis());
            log.debug("Successfully stored vector for entity {} of type {}", entityId, entityType);
            
        } catch (Exception e) {
            log.error("Error storing vector", e);
            throw new AIServiceException("Failed to store vector", e);
        }
    }
    
    @Override
    public AISearchResponse search(List<Double> queryVector, AISearchRequest request) {
        try {
            log.debug("Searching vectors for query: {}", request.getQuery());
            
            long startTime = System.currentTimeMillis();
            
            String entityType = request.getEntityType();
            List<Map<String, Object>> entities = vectorStore.getOrDefault(entityType, new ArrayList<>());
            
            if (entities.isEmpty()) {
                log.debug("No entities found for type: {}", entityType);
                return createEmptyResponse(request, startTime);
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
            updateMetrics("search", processingTime);
            
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
    
    @Override
    public void removeVector(String entityType, String entityId) {
        try {
            log.debug("Removing vector for entity {} of type {}", entityId, entityType);
            
            List<Map<String, Object>> entities = vectorStore.get(entityType);
            if (entities != null) {
                entities.removeIf(entity -> entityId.equals(entity.get("id")));
            }
            
            updateMetrics("remove", System.currentTimeMillis());
            log.debug("Successfully removed vector for entity {} of type {}", entityId, entityType);
            
        } catch (Exception e) {
            log.error("Error removing vector", e);
            throw new AIServiceException("Failed to remove vector", e);
        }
    }
    
    @Override
    public void updateVector(String entityType, String entityId, String content, 
                            List<Double> embedding, Map<String, Object> metadata) {
        try {
            log.debug("Updating vector for entity {} of type {}", entityId, entityType);
            
            // Remove existing vector
            removeVector(entityType, entityId);
            
            // Store updated vector
            storeVector(entityType, entityId, content, embedding, metadata);
            
            updateMetrics("update", System.currentTimeMillis());
            log.debug("Successfully updated vector for entity {} of type {}", entityId, entityType);
            
        } catch (Exception e) {
            log.error("Error updating vector", e);
            throw new AIServiceException("Failed to update vector", e);
        }
    }
    
    @Override
    public Map<String, Object> getVector(String entityType, String entityId) {
        try {
            log.debug("Getting vector for entity {} of type {}", entityId, entityType);
            
            List<Map<String, Object>> entities = vectorStore.get(entityType);
            if (entities != null) {
                return entities.stream()
                    .filter(entity -> entityId.equals(entity.get("id")))
                    .findFirst()
                    .orElse(null);
            }
            
            return null;
            
        } catch (Exception e) {
            log.error("Error getting vector", e);
            throw new AIServiceException("Failed to get vector", e);
        }
    }
    
    @Override
    public boolean vectorExists(String entityType, String entityId) {
        try {
            return getVector(entityType, entityId) != null;
        } catch (Exception e) {
            log.error("Error checking vector existence", e);
            return false;
        }
    }
    
    @Override
    public List<Map<String, Object>> getAllVectors(String entityType) {
        try {
            log.debug("Getting all vectors for entity type: {}", entityType);
            
            return vectorStore.getOrDefault(entityType, new ArrayList<>());
            
        } catch (Exception e) {
            log.error("Error getting all vectors", e);
            throw new AIServiceException("Failed to get all vectors", e);
        }
    }
    
    @Override
    public void clearVectors(String entityType) {
        try {
            log.debug("Clearing all vectors for entity type: {}", entityType);
            
            vectorStore.remove(entityType);
            
            updateMetrics("clear", System.currentTimeMillis());
            log.debug("Successfully cleared vectors for entity type: {}", entityType);
            
        } catch (Exception e) {
            log.error("Error clearing vectors", e);
            throw new AIServiceException("Failed to clear vectors", e);
        }
    }
    
    @Override
    public void clearAllVectors() {
        try {
            log.debug("Clearing all vectors");
            
            vectorStore.clear();
            
            updateMetrics("clearAll", System.currentTimeMillis());
            log.debug("Successfully cleared all vectors");
            
        } catch (Exception e) {
            log.error("Error clearing all vectors", e);
            throw new AIServiceException("Failed to clear all vectors", e);
        }
    }
    
    @Override
    public Map<String, Object> getStatistics() {
        try {
            Map<String, Object> stats = new HashMap<>();
            
            // Basic statistics
            stats.put("totalVectors", vectorStore.values().stream().mapToInt(List::size).sum());
            stats.put("entityTypes", vectorStore.keySet());
            stats.put("entityTypeCounts", vectorStore.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().size())));
            
            // Operation statistics
            stats.put("operationCounts", new HashMap<>(operationCounts));
            stats.put("operationTimes", new HashMap<>(operationTimes));
            
            // Calculate average times
            Map<String, Double> avgTimes = new HashMap<>();
            for (Map.Entry<String, Long> entry : operationTimes.entrySet()) {
                String operation = entry.getKey();
                Long totalTime = entry.getValue();
                Long count = operationCounts.getOrDefault(operation, 1L);
                avgTimes.put(operation, (double) totalTime / count);
            }
            stats.put("averageOperationTimes", avgTimes);
            
            return stats;
            
        } catch (Exception e) {
            log.error("Error getting statistics", e);
            return new HashMap<>();
        }
    }
    
    @Override
    public boolean isHealthy() {
        try {
            // Simple health check
            return true;
        } catch (Exception e) {
            log.error("Health check failed", e);
            return false;
        }
    }
    
    @Override
    public Map<String, Object> getInfo() {
        try {
            Map<String, Object> info = new HashMap<>();
            info.put("type", "PineconeVectorDatabase");
            info.put("implementation", "InMemory");
            info.put("version", "1.0.0");
            info.put("healthy", isHealthy());
            info.put("totalVectors", vectorStore.values().stream().mapToInt(List::size).sum());
            return info;
        } catch (Exception e) {
            log.error("Error getting info", e);
            return new HashMap<>();
        }
    }
    
    @Override
    public void batchStoreVectors(List<VectorData> vectors) {
        try {
            log.debug("Batch storing {} vectors", vectors.size());
            
            for (VectorData vector : vectors) {
                storeVector(vector.getEntityType(), vector.getEntityId(), 
                          vector.getContent(), vector.getEmbedding(), vector.getMetadata());
            }
            
            updateMetrics("batchStore", System.currentTimeMillis());
            log.debug("Successfully batch stored {} vectors", vectors.size());
            
        } catch (Exception e) {
            log.error("Error batch storing vectors", e);
            throw new AIServiceException("Failed to batch store vectors", e);
        }
    }
    
    @Override
    public List<AISearchResponse> batchSearch(List<VectorSearchQuery> queries) {
        try {
            log.debug("Batch searching {} queries", queries.size());
            
            List<AISearchResponse> responses = new ArrayList<>();
            for (VectorSearchQuery query : queries) {
                AISearchResponse response = search(query.getQueryVector(), query.getRequest());
                responses.add(response);
            }
            
            updateMetrics("batchSearch", System.currentTimeMillis());
            log.debug("Successfully batch searched {} queries", queries.size());
            
            return responses;
            
        } catch (Exception e) {
            log.error("Error batch searching", e);
            throw new AIServiceException("Failed to batch search", e);
        }
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
    
    /**
     * Create empty search response
     */
    private AISearchResponse createEmptyResponse(AISearchRequest request, long startTime) {
        return AISearchResponse.builder()
            .results(Collections.emptyList())
            .totalResults(0)
            .maxScore(0.0)
            .processingTimeMs(System.currentTimeMillis() - startTime)
            .requestId(UUID.randomUUID().toString())
            .query(request.getQuery())
            .model(config.getOpenaiEmbeddingModel())
            .build();
    }
    
    /**
     * Update operation metrics
     */
    private void updateMetrics(String operation, long time) {
        operationCounts.merge(operation, 1L, Long::sum);
        operationTimes.merge(operation, time, Long::sum);
    }
}
