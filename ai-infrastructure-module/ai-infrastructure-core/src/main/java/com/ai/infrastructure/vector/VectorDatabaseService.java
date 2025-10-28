package com.ai.infrastructure.vector;

import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Vector Database Service
 * 
 * High-level service for vector database operations.
 * Provides a clean API for storing and searching vectors with AI entities.
 * 
 * @author AI Infrastructure Team
 * @version 2.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorDatabaseService {
    
    private final VectorDatabase vectorDatabase;
    
    /**
     * Store an AI entity's vector in the database
     * 
     * @param entityType The type of entity (e.g., "product", "user")
     * @param entityId The entity's unique identifier
     * @param content The searchable content
     * @param vector The embedding vector
     * @param metadata Additional metadata
     */
    public void storeEntityVector(String entityType, String entityId, String content, 
                                List<Double> vector, Map<String, Object> metadata) {
        
        String vectorId = generateVectorId(entityType, entityId);
        
        // Prepare metadata with entity information
        Map<String, Object> enrichedMetadata = new HashMap<>();
        if (metadata != null) {
            enrichedMetadata.putAll(metadata);
        }
        
        enrichedMetadata.put("entityType", entityType);
        enrichedMetadata.put("entityId", entityId);
        enrichedMetadata.put("content", content);
        enrichedMetadata.put("storedAt", LocalDateTime.now().toString());
        
        vectorDatabase.store(vectorId, vector, enrichedMetadata);
        
        log.debug("Stored vector for {} entity {}", entityType, entityId);
    }
    
    /**
     * Store multiple entity vectors in batch
     * 
     * @param entityVectors List of entity vectors to store
     */
    public void batchStoreEntityVectors(List<EntityVector> entityVectors) {
        if (entityVectors == null || entityVectors.isEmpty()) {
            return;
        }
        
        List<VectorRecord> records = entityVectors.stream()
            .map(this::convertToVectorRecord)
            .collect(Collectors.toList());
        
        vectorDatabase.batchStore(records);
        
        log.debug("Batch stored {} entity vectors", entityVectors.size());
    }
    
    /**
     * Search for similar entities using vector similarity
     * 
     * @param queryVector The query vector
     * @param entityType Optional entity type filter
     * @param limit Maximum number of results
     * @param threshold Minimum similarity threshold
     * @return AI search response with results
     */
    public AISearchResponse searchSimilarEntities(List<Double> queryVector, 
                                                String entityType,
                                                int limit, 
                                                double threshold) {
        
        long startTime = System.currentTimeMillis();
        
        // Prepare filter for entity type
        Map<String, Object> filter = null;
        if (entityType != null && !entityType.trim().isEmpty()) {
            filter = Map.of("entityType", entityType);
        }
        
        // Search vectors
        List<VectorSearchResult> searchResults = vectorDatabase.searchWithFilter(
            queryVector, filter, limit, threshold);
        
        // Convert to AI search response
        List<Map<String, Object>> results = searchResults.stream()
            .map(this::convertToAIResult)
            .collect(Collectors.toList());
        
        long processingTime = System.currentTimeMillis() - startTime;
        
        return AISearchResponse.builder()
            .results(results)
            .totalResults(results.size())
            .maxScore(results.isEmpty() ? 0.0 : (Double) results.get(0).get("similarity"))
            .processingTimeMs(processingTime)
            .requestId(UUID.randomUUID().toString())
            .query("vector-similarity-search")
            .model("vector-database")
            .build();
    }
    
    /**
     * Search with advanced AI search request
     * 
     * @param request The AI search request
     * @param queryVector The query vector
     * @return AI search response
     */
    public AISearchResponse search(AISearchRequest request, List<Double> queryVector) {
        return searchSimilarEntities(
            queryVector,
            request.getEntityType(),
            request.getLimit(),
            request.getThreshold()
        );
    }
    
    /**
     * Get an entity's vector by ID
     * 
     * @param entityType The entity type
     * @param entityId The entity ID
     * @return Optional containing the vector record if found
     */
    public Optional<VectorRecord> getEntityVector(String entityType, String entityId) {
        String vectorId = generateVectorId(entityType, entityId);
        return vectorDatabase.get(vectorId);
    }
    
    /**
     * Delete an entity's vector
     * 
     * @param entityType The entity type
     * @param entityId The entity ID
     * @return true if deleted, false if not found
     */
    public boolean deleteEntityVector(String entityType, String entityId) {
        String vectorId = generateVectorId(entityType, entityId);
        boolean deleted = vectorDatabase.delete(vectorId);
        
        if (deleted) {
            log.debug("Deleted vector for {} entity {}", entityType, entityId);
        }
        
        return deleted;
    }
    
    /**
     * Delete all vectors for a specific entity type
     * 
     * @param entityType The entity type to delete
     * @return Number of vectors deleted
     */
    public int deleteAllEntityVectors(String entityType) {
        // Note: This is a simple implementation. For large datasets,
        // you might want to implement batch deletion with filtering
        Map<String, Object> stats = vectorDatabase.getStatistics();
        log.warn("Clearing all vectors for entity type {} (current total: {})", 
                entityType, stats.get("vectorCount"));
        
        // For now, we'll need to implement entity-type-specific deletion
        // This is a limitation that could be improved with more advanced filtering
        return 0; // TODO: Implement entity-type-specific deletion
    }
    
    /**
     * Get vector database statistics
     * 
     * @return Statistics map
     */
    public Map<String, Object> getStatistics() {
        return vectorDatabase.getStatistics();
    }
    
    /**
     * Check if the vector database is healthy
     * 
     * @return true if healthy
     */
    public boolean isHealthy() {
        return vectorDatabase.isHealthy();
    }
    
    /**
     * Get the vector database type
     * 
     * @return Database type string
     */
    public String getDatabaseType() {
        return vectorDatabase.getType();
    }
    
    /**
     * Clear all vectors (use with caution!)
     */
    public void clearAllVectors() {
        vectorDatabase.clear();
        log.warn("Cleared all vectors from database");
    }
    
    /**
     * Generate a unique vector ID for an entity
     * 
     * @param entityType The entity type
     * @param entityId The entity ID
     * @return Unique vector ID
     */
    private String generateVectorId(String entityType, String entityId) {
        return entityType + ":" + entityId;
    }
    
    /**
     * Convert EntityVector to VectorRecord
     * 
     * @param entityVector The entity vector
     * @return Vector record
     */
    private VectorRecord convertToVectorRecord(EntityVector entityVector) {
        String vectorId = generateVectorId(entityVector.getEntityType(), entityVector.getEntityId());
        
        Map<String, Object> metadata = new HashMap<>();
        if (entityVector.getMetadata() != null) {
            metadata.putAll(entityVector.getMetadata());
        }
        
        metadata.put("entityType", entityVector.getEntityType());
        metadata.put("entityId", entityVector.getEntityId());
        metadata.put("content", entityVector.getContent());
        metadata.put("storedAt", LocalDateTime.now().toString());
        
        return VectorRecord.builder()
            .id(vectorId)
            .vector(entityVector.getVector())
            .metadata(metadata)
            .build();
    }
    
    /**
     * Convert VectorSearchResult to AI result format
     * 
     * @param searchResult The vector search result
     * @return AI result map
     */
    private Map<String, Object> convertToAIResult(VectorSearchResult searchResult) {
        Map<String, Object> result = new HashMap<>();
        
        VectorRecord record = searchResult.getRecord();
        Map<String, Object> metadata = record.getMetadata();
        
        result.put("id", metadata.get("entityId"));
        result.put("entityType", metadata.get("entityType"));
        result.put("content", metadata.get("content"));
        result.put("similarity", searchResult.getSimilarity());
        result.put("score", searchResult.getSimilarity());
        result.put("distance", searchResult.getDistance());
        result.put("metadata", metadata);
        result.put("vectorId", record.getId());
        
        return result;
    }
}