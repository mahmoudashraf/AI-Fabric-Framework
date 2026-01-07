package com.ai.infrastructure.rag;

import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.dto.VectorRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Vector Database Service Interface
 * 
 * This interface defines the contract for vector database operations.
 * Different implementations can be provided for various vector databases
 * like Lucene, Pinecone, Chroma, etc.
 * 
 * @author AI Infrastructure Team
 * @version 2.0.0
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
     * @return the vector ID assigned by the database
     */
    String storeVector(String entityType, String entityId, String content, 
                      List<Double> embedding, Map<String, Object> metadata);
    
    /**
     * Update an existing vector in the database
     * 
     * @param vectorId the vector ID to update
     * @param entityType the type of entity
     * @param entityId the unique identifier
     * @param content the text content
     * @param embedding the vector embedding
     * @param metadata additional metadata
     * @return true if the vector was updated, false if not found
     */
    boolean updateVector(String vectorId, String entityType, String entityId, 
                        String content, List<Double> embedding, Map<String, Object> metadata);
    
    /**
     * Get a vector by its ID
     * 
     * @param vectorId the vector ID
     * @return the vector record if found
     */
    Optional<VectorRecord> getVector(String vectorId);
    
    /**
     * Get a vector by entity type and entity ID
     * 
     * @param entityType the type of entity
     * @param entityId the unique identifier
     * @return the vector record if found
     */
    Optional<VectorRecord> getVectorByEntity(String entityType, String entityId);
    
    /**
     * Search for similar vectors
     * 
     * @param queryVector the query vector
     * @param request the search request
     * @return search results with similarity scores
     */
    AISearchResponse search(List<Double> queryVector, AISearchRequest request);

    /**
     * Perform hybrid search combining vector similarity and keyword/text search.
     *
     * <p>This is an OPTIONAL method. Providers that support native hybrid search
     * should override this method. The default implementation falls back to
     * {@link #search(List, AISearchRequest)}.</p>
     *
     * @param queryVector the query vector for semantic search
     * @param queryText the original query text for keyword search
     * @param request the search request
     * @return search results combining vector + keyword signals
     */
    default AISearchResponse hybridSearch(List<Double> queryVector, String queryText, AISearchRequest request) {
        Logger log = LoggerFactory.getLogger(this.getClass());
        log.debug("Hybrid search not supported by {} - falling back to vector search",
            this.getClass().getSimpleName());
        return search(queryVector, request);
    }

    /**
     * Perform keyword/text-only search (BM25/full-text/etc.).
     *
     * <p>This is an OPTIONAL method. Providers that support keyword search
     * should override this method.</p>
     *
     * @param queryText the original query text
     * @param request the search request
     * @return keyword search results
     * @throws UnsupportedOperationException if not supported by provider
     */
    default AISearchResponse keywordSearch(String queryText, AISearchRequest request) {
        throw new UnsupportedOperationException(
            "Keyword search not supported by " + this.getClass().getSimpleName());
    }

    /**
     * Capability check: does this provider support actual hybrid search?
     *
     * @return true if hybrid search is supported
     */
    default boolean supportsHybridSearch() {
        return false;
    }

    /**
     * Capability check: does this provider support keyword search?
     *
     * @return true if keyword search is supported
     */
    default boolean supportsKeywordSearch() {
        return false;
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
    AISearchResponse searchByEntityType(List<Double> queryVector, String entityType, 
                                       int limit, double threshold);
    
    /**
     * Remove a vector from the database
     * 
     * @param entityType the type of entity
     * @param entityId the unique identifier
     * @return true if the vector was removed, false if not found
     */
    boolean removeVector(String entityType, String entityId);
    
    /**
     * Remove a vector by its ID
     * 
     * @param vectorId the vector ID
     * @return true if the vector was removed, false if not found
     */
    boolean removeVectorById(String vectorId);
    
    /**
     * Batch store multiple vectors
     * 
     * @param vectors list of vector records to store
     * @return list of vector IDs assigned by the database
     */
    List<String> batchStoreVectors(List<VectorRecord> vectors);
    
    /**
     * Batch update multiple vectors
     * 
     * @param vectors list of vector records to update
     * @return number of vectors successfully updated
     */
    int batchUpdateVectors(List<VectorRecord> vectors);
    
    /**
     * Batch remove multiple vectors
     * 
     * @param vectorIds list of vector IDs to remove
     * @return number of vectors successfully removed
     */
    int batchRemoveVectors(List<String> vectorIds);
    
    /**
     * Get all vectors for a specific entity type
     * 
     * @param entityType the entity type
     * @return list of vector records
     */
    List<VectorRecord> getVectorsByEntityType(String entityType);
    
    /**
     * Get vector count by entity type
     * 
     * @param entityType the entity type
     * @return number of vectors for the entity type
     */
    long getVectorCountByEntityType(String entityType);
    
    /**
     * Check if a vector exists
     * 
     * @param entityType the type of entity
     * @param entityId the unique identifier
     * @return true if the vector exists
     */
    boolean vectorExists(String entityType, String entityId);
    
    /**
     * Get statistics about the vector store
     * 
     * @return map of statistics
     */
    Map<String, Object> getStatistics();
    
    /**
     * Clear all vectors from the database
     * 
     * @return number of vectors cleared
     */
    long clearVectors();
    
    /**
     * Clear all vectors for a specific entity type
     * 
     * @param entityType the entity type
     * @return number of vectors cleared
     */
    long clearVectorsByEntityType(String entityType);
}
