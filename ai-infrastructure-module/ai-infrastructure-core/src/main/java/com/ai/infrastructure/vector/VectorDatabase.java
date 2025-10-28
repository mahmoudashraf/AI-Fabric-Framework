package com.ai.infrastructure.vector;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Vector Database Interface
 * 
 * Provides a unified interface for different vector database implementations.
 * Supports pluggable backends for development (in-memory) and production (Pinecone, Weaviate, etc.).
 * 
 * @author AI Infrastructure Team
 * @version 2.0.0
 */
public interface VectorDatabase {
    
    /**
     * Store a vector with metadata
     * 
     * @param id Unique identifier for the vector
     * @param vector The embedding vector (typically 1536 dimensions)
     * @param metadata Additional metadata for filtering and search
     */
    void store(String id, List<Double> vector, Map<String, Object> metadata);
    
    /**
     * Store multiple vectors in batch for efficiency
     * 
     * @param vectors List of vector records to store
     */
    void batchStore(List<VectorRecord> vectors);
    
    /**
     * Search for similar vectors using cosine similarity
     * 
     * @param queryVector The query vector to search for
     * @param limit Maximum number of results to return
     * @param threshold Minimum similarity threshold (0.0 to 1.0)
     * @return List of similar vectors with similarity scores
     */
    List<VectorSearchResult> search(List<Double> queryVector, int limit, double threshold);
    
    /**
     * Search with metadata filtering
     * 
     * @param queryVector The query vector to search for
     * @param filter Metadata filter conditions
     * @param limit Maximum number of results to return
     * @param threshold Minimum similarity threshold
     * @return List of similar vectors matching the filter
     */
    List<VectorSearchResult> searchWithFilter(List<Double> queryVector, 
                                            Map<String, Object> filter, 
                                            int limit, 
                                            double threshold);
    
    /**
     * Retrieve a vector by ID
     * 
     * @param id The vector ID
     * @return Optional containing the vector record if found
     */
    Optional<VectorRecord> get(String id);
    
    /**
     * Delete a vector by ID
     * 
     * @param id The vector ID to delete
     * @return true if the vector was deleted, false if not found
     */
    boolean delete(String id);
    
    /**
     * Delete multiple vectors by IDs
     * 
     * @param ids List of vector IDs to delete
     * @return Number of vectors actually deleted
     */
    int batchDelete(List<String> ids);
    
    /**
     * Get database statistics
     * 
     * @return Map containing statistics like vector count, memory usage, etc.
     */
    Map<String, Object> getStatistics();
    
    /**
     * Check if the database is healthy and ready
     * 
     * @return true if healthy, false otherwise
     */
    boolean isHealthy();
    
    /**
     * Clear all vectors from the database
     * WARNING: This operation cannot be undone
     */
    void clear();
    
    /**
     * Get the database type/implementation name
     * 
     * @return String identifier for the database type
     */
    String getType();
}