package com.ai.infrastructure.rag;

import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;

import java.util.List;
import java.util.Map;

/**
 * Vector Database Service Interface
 * 
 * This interface defines the contract for vector database operations.
 * Different implementations can be provided for various vector databases
 * like Lucene, Pinecone, Chroma, etc.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
public interface VectorDatabaseService {
    
    /**
     * Store a vector in the database
     * 
     * @param entityType the type of entity
     * @param entityId the unique identifier
     * @param content the text content
     * @param embedding the vector embedding
     * @param metadata additional metadata
     */
    void storeVector(String entityType, String entityId, String content, 
                    List<Double> embedding, Map<String, Object> metadata);
    
    /**
     * Search for similar vectors
     * 
     * @param queryVector the query vector
     * @param request the search request
     * @return search results with similarity scores
     */
    AISearchResponse search(List<Double> queryVector, AISearchRequest request);
    
    /**
     * Remove a vector from the database
     * 
     * @param entityType the type of entity
     * @param entityId the unique identifier
     */
    void removeVector(String entityType, String entityId);
    
    /**
     * Get statistics about the vector store
     * 
     * @return map of statistics
     */
    Map<String, Object> getStatistics();
    
    /**
     * Clear all vectors from the database
     */
    void clearVectors();
}
