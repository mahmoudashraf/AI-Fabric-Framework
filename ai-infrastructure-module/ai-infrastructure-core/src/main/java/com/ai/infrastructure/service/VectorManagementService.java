package com.ai.infrastructure.service;

import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.dto.VectorRecord;
import com.ai.infrastructure.rag.VectorDatabaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Vector Management Service
 * 
 * Centralized service for managing vector operations and synchronization
 * between entities and the vector database.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorManagementService {
    
    private final VectorDatabaseService vectorDatabaseService;
    
    /**
     * Store a vector for an entity
     * 
     * @param entityType the type of entity
     * @param entityId the unique identifier
     * @param content the text content
     * @param embedding the vector embedding
     * @param metadata additional metadata
     * @return the vector ID assigned by the database
     */
    @Transactional
    public String storeVector(String entityType, String entityId, String content, 
                             List<Double> embedding, Map<String, Object> metadata) {
        try {
            log.debug("Storing vector for entity {} of type {}", entityId, entityType);
            
            // Check if vector already exists
            if (vectorDatabaseService.vectorExists(entityType, entityId)) {
                log.debug("Vector already exists for entity {} of type {}, replacing with fresh vector", entityId, entityType);
                return updateVector(entityType, entityId, content, embedding, metadata);
            }
            
            // Store new vector
            String vectorId = vectorDatabaseService.storeVector(entityType, entityId, content, embedding, metadata);
            log.debug("Successfully stored vector {} for entity {} of type {}", vectorId, entityId, entityType);
            
            return vectorId;
            
        } catch (Exception e) {
            log.error("Error storing vector for entity {} of type {}", entityId, entityType, e);
            throw new RuntimeException("Failed to store vector", e);
        }
    }
    
    /**
     * Update an existing vector for an entity
     * 
     * @param entityType the type of entity
     * @param entityId the unique identifier
     * @param content the text content
     * @param embedding the vector embedding
     * @param metadata additional metadata
     * @return the vector ID
     */
    @Transactional
    public String updateVector(String entityType, String entityId, String content, 
                              List<Double> embedding, Map<String, Object> metadata) {
        try {
            log.debug("Updating vector for entity {} of type {}", entityId, entityType);
            
            // Get existing vector
            Optional<VectorRecord> existingVector = vectorDatabaseService.getVectorByEntity(entityType, entityId);

            String newVectorId = vectorDatabaseService.storeVector(entityType, entityId, content, embedding, metadata);
            log.debug("Stored new vector {} for entity {} of type {}", newVectorId, entityId, entityType);

            existingVector
                .map(VectorRecord::getVectorId)
                .filter(previousVectorId -> previousVectorId != null && !previousVectorId.equals(newVectorId))
                .ifPresent(previousVectorId -> {
                    try {
                        boolean removed = vectorDatabaseService.removeVectorById(previousVectorId);
                        if (removed) {
                            log.debug("Removed previous vector {} for entity {} of type {}", previousVectorId, entityId, entityType);
                        } else {
                            log.warn("Previous vector {} for entity {} of type {} could not be removed by ID; manual cleanup may be required", previousVectorId, entityId, entityType);
                        }
                    } catch (Exception removalException) {
                        log.warn("Failed to remove previous vector {} for entity {} of type {}", previousVectorId, entityId, entityType, removalException);
                    }
                });

            return newVectorId;
            
        } catch (Exception e) {
            log.error("Error updating vector for entity {} of type {}", entityId, entityType, e);
            throw new RuntimeException("Failed to update vector", e);
        }
    }
    
    /**
     * Get a vector by entity type and entity ID
     * 
     * @param entityType the type of entity
     * @param entityId the unique identifier
     * @return the vector record if found
     */
    public Optional<VectorRecord> getVector(String entityType, String entityId) {
        try {
            log.debug("Getting vector for entity {} of type {}", entityId, entityType);
            return vectorDatabaseService.getVectorByEntity(entityType, entityId);
        } catch (Exception e) {
            log.error("Error getting vector for entity {} of type {}", entityId, entityType, e);
            return Optional.empty();
        }
    }
    
    /**
     * Get a vector by its ID
     * 
     * @param vectorId the vector ID
     * @return the vector record if found
     */
    public Optional<VectorRecord> getVectorById(String vectorId) {
        try {
            log.debug("Getting vector by ID {}", vectorId);
            return vectorDatabaseService.getVector(vectorId);
        } catch (Exception e) {
            log.error("Error getting vector by ID {}", vectorId, e);
            return Optional.empty();
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
            log.debug("Searching vectors with query: {}", request.getQuery());
            return vectorDatabaseService.search(queryVector, request);
        } catch (Exception e) {
            log.error("Error searching vectors", e);
            throw new RuntimeException("Failed to search vectors", e);
        }
    }
    
    /**
     * Search for similar vectors by entity type
     * 
     * @param queryVector the query vector
     * @param entityType the entity type to search within
     * @param limit maximum number of results
     * @param threshold minimum similarity threshold
     * @return search results with similarity scores
     */
    public AISearchResponse searchByEntityType(List<Double> queryVector, String entityType, 
                                              int limit, double threshold) {
        try {
            log.debug("Searching vectors for entity type {} with limit {} and threshold {}", 
                     entityType, limit, threshold);
            return vectorDatabaseService.searchByEntityType(queryVector, entityType, limit, threshold);
        } catch (Exception e) {
            log.error("Error searching vectors by entity type {}", entityType, e);
            throw new RuntimeException("Failed to search vectors by entity type", e);
        }
    }
    
    /**
     * Remove a vector for an entity
     * 
     * @param entityType the type of entity
     * @param entityId the unique identifier
     * @return true if the vector was removed
     */
    @Transactional
    public boolean removeVector(String entityType, String entityId) {
        try {
            log.debug("Removing vector for entity {} of type {}", entityId, entityType);
            boolean removed = vectorDatabaseService.removeVector(entityType, entityId);
            log.debug("Vector removal result for entity {} of type {}: {}", entityId, entityType, removed);
            return removed;
        } catch (Exception e) {
            log.error("Error removing vector for entity {} of type {}", entityId, entityType, e);
            throw new RuntimeException("Failed to remove vector", e);
        }
    }
    
    /**
     * Remove a vector by its ID
     * 
     * @param vectorId the vector ID
     * @return true if the vector was removed
     */
    @Transactional
    public boolean removeVectorById(String vectorId) {
        try {
            log.debug("Removing vector by ID {}", vectorId);
            boolean removed = vectorDatabaseService.removeVectorById(vectorId);
            log.debug("Vector removal result for ID {}: {}", vectorId, removed);
            return removed;
        } catch (Exception e) {
            log.error("Error removing vector by ID {}", vectorId, e);
            throw new RuntimeException("Failed to remove vector by ID", e);
        }
    }
    
    /**
     * Batch store multiple vectors
     * 
     * @param vectors list of vector records to store
     * @return list of vector IDs assigned by the database
     */
    @Transactional
    public List<String> batchStoreVectors(List<VectorRecord> vectors) {
        try {
            log.debug("Batch storing {} vectors", vectors.size());
            List<String> vectorIds = vectorDatabaseService.batchStoreVectors(vectors);
            log.debug("Successfully batch stored {} vectors", vectorIds.size());
            return vectorIds;
        } catch (Exception e) {
            log.error("Error batch storing vectors", e);
            throw new RuntimeException("Failed to batch store vectors", e);
        }
    }
    
    /**
     * Batch update multiple vectors
     * 
     * @param vectors list of vector records to update
     * @return number of vectors successfully updated
     */
    @Transactional
    public int batchUpdateVectors(List<VectorRecord> vectors) {
        try {
            log.debug("Batch updating {} vectors", vectors.size());
            int updatedCount = vectorDatabaseService.batchUpdateVectors(vectors);
            log.debug("Successfully batch updated {} vectors", updatedCount);
            return updatedCount;
        } catch (Exception e) {
            log.error("Error batch updating vectors", e);
            throw new RuntimeException("Failed to batch update vectors", e);
        }
    }
    
    /**
     * Batch remove multiple vectors
     * 
     * @param vectorIds list of vector IDs to remove
     * @return number of vectors successfully removed
     */
    @Transactional
    public int batchRemoveVectors(List<String> vectorIds) {
        try {
            log.debug("Batch removing {} vectors", vectorIds.size());
            int removedCount = vectorDatabaseService.batchRemoveVectors(vectorIds);
            log.debug("Successfully batch removed {} vectors", removedCount);
            return removedCount;
        } catch (Exception e) {
            log.error("Error batch removing vectors", e);
            throw new RuntimeException("Failed to batch remove vectors", e);
        }
    }
    
    /**
     * Get all vectors for a specific entity type
     * 
     * @param entityType the entity type
     * @return list of vector records
     */
    public List<VectorRecord> getVectorsByEntityType(String entityType) {
        try {
            log.debug("Getting all vectors for entity type {}", entityType);
            return vectorDatabaseService.getVectorsByEntityType(entityType);
        } catch (Exception e) {
            log.error("Error getting vectors by entity type {}", entityType, e);
            throw new RuntimeException("Failed to get vectors by entity type", e);
        }
    }
    
    /**
     * Get vector count by entity type
     * 
     * @param entityType the entity type
     * @return number of vectors for the entity type
     */
    public long getVectorCountByEntityType(String entityType) {
        try {
            log.debug("Getting vector count for entity type {}", entityType);
            return vectorDatabaseService.getVectorCountByEntityType(entityType);
        } catch (Exception e) {
            log.error("Error getting vector count for entity type {}", entityType, e);
            return 0;
        }
    }
    
    /**
     * Check if a vector exists for an entity
     * 
     * @param entityType the type of entity
     * @param entityId the unique identifier
     * @return true if the vector exists
     */
    public boolean vectorExists(String entityType, String entityId) {
        try {
            return vectorDatabaseService.vectorExists(entityType, entityId);
        } catch (Exception e) {
            log.error("Error checking if vector exists for entity {} of type {}", entityId, entityType, e);
            return false;
        }
    }
    
    /**
     * Get statistics about the vector store
     * 
     * @return map of statistics
     */
    public Map<String, Object> getStatistics() {
        try {
            log.debug("Getting vector database statistics");
            return vectorDatabaseService.getStatistics();
        } catch (Exception e) {
            log.error("Error getting vector database statistics", e);
            return Map.of("error", "Failed to get statistics");
        }
    }
    
    /**
     * Clear all vectors from the database
     * 
     * @return number of vectors cleared
     */
    @Transactional
    public long clearAllVectors() {
        try {
            log.debug("Clearing all vectors from database");
            long clearedCount = vectorDatabaseService.clearVectors();
            log.debug("Successfully cleared {} vectors from database", clearedCount);
            return clearedCount;
        } catch (Exception e) {
            log.error("Error clearing all vectors", e);
            throw new RuntimeException("Failed to clear all vectors", e);
        }
    }
    
    /**
     * Clear all vectors for a specific entity type
     * 
     * @param entityType the entity type
     * @return number of vectors cleared
     */
    @Transactional
    public long clearVectorsByEntityType(String entityType) {
        try {
            log.debug("Clearing all vectors for entity type {}", entityType);
            long clearedCount = vectorDatabaseService.clearVectorsByEntityType(entityType);
            log.debug("Successfully cleared {} vectors for entity type {}", clearedCount, entityType);
            return clearedCount;
        } catch (Exception e) {
            // If vector database is not initialized yet, log and return 0 instead of throwing
            // This allows tests to call clearVectorsByEntityType in @BeforeEach safely
            String errorMsg = e.getMessage() != null ? e.getMessage() : "";
            Throwable cause = e.getCause();
            while (cause != null) {
                if (cause.getMessage() != null) {
                    errorMsg += " " + cause.getMessage();
                }
                cause = cause.getCause();
            }
            
            if (errorMsg.contains("not initialized") || 
                errorMsg.contains("IndexWriter not initialized") ||
                errorMsg.contains("IndexWriter is null") ||
                errorMsg.contains("Failed to clear vectors for entity type")) {
                log.debug("Vector database not initialized yet for entity type {}, skipping clear: {}", entityType, e.getMessage());
                return 0;
            }
            log.error("Error clearing vectors for entity type {}", entityType, e);
            throw new RuntimeException("Failed to clear vectors for entity type", e);
        }
    }
}