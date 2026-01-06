package com.ai.infrastructure.rag.spi;

import com.ai.infrastructure.rag.dto.RAGRequest;
import com.ai.infrastructure.rag.dto.RAGResponse;

import java.util.Map;

/**
 * Service Provider Interface for RAG (Retrieval-Augmented Generation) operations.
 * 
 * <p>This interface defines the contract for RAG providers that perform
 * <strong>retrieval operations only</strong>. Generation (LLM calls) and PII
 * detection are handled by the orchestrator pipeline, not by RAG providers.</p>
 * 
 * <h2>Responsibility Separation</h2>
 * <table border="1">
 *   <tr><th>Component</th><th>Responsibility</th></tr>
 *   <tr><td>RAGProvider</td><td>Vector search, document retrieval, content indexing</td></tr>
 *   <tr><td>Orchestrator</td><td>PII detection, LLM generation, response sanitization</td></tr>
 * </table>
 * 
 * <h2>Implementation Requirements</h2>
 * <ul>
 *   <li>Implementations MUST be thread-safe</li>
 *   <li>Implementations MUST return non-null {@link RAGResponse} objects</li>
 *   <li>On error, return a RAGResponse with {@code success=false}</li>
 *   <li>Do NOT perform PII detection - that's the orchestrator's job</li>
 *   <li>Do NOT call LLMs for generation - that's the orchestrator's job</li>
 * </ul>
 * 
 * @author AI Infrastructure Team
 * @version 2.0.0
 * @see RAGRequest
 * @see RAGResponse
 * @since 1.1
 */
public interface RAGProvider {
    
    // =========================================================================
    // Constants
    // =========================================================================
    
    /**
     * Default limit for retrieval queries when not specified.
     */
    int DEFAULT_LIMIT = 10;
    
    /**
     * Default similarity threshold for retrieval queries.
     */
    double DEFAULT_THRESHOLD = 0.7;
    
    // =========================================================================
    // Core Retrieval Operations
    // =========================================================================
    
    /**
     * Perform retrieval operation.
     * 
     * <p>This method performs semantic search and returns matching documents.
     * It does NOT generate responses - that's the orchestrator's job.</p>
     * 
     * <p><strong>Process:</strong></p>
     * <ol>
     *   <li>Generate embedding for the query</li>
     *   <li>Perform vector similarity search</li>
     *   <li>Return matching documents with relevance scores</li>
     * </ol>
     * 
     * @param request the retrieval request containing query and options
     * @return RAGResponse with retrieved documents; never null
     * @throws IllegalArgumentException if request is null or query is blank
     */
    RAGResponse retrieve(RAGRequest request);
    
    // =========================================================================
    // Content Indexing Operations
    // =========================================================================
    
    /**
     * Index content for retrieval.
     * 
     * <p>Generates embeddings for the content and stores in vector database.</p>
     * 
     * @param entityType the type of entity (e.g., "product", "document")
     * @param entityId unique identifier for the entity
     * @param content the text content to index
     * @param metadata additional metadata to store
     * @throws IllegalArgumentException if required parameters are null/blank
     */
    void indexContent(String entityType, String entityId, String content, Map<String, Object> metadata);
    
    /**
     * Remove content from index.
     * 
     * @param entityType the type of entity
     * @param entityId unique identifier for the entity
     */
    void removeContent(String entityType, String entityId);
    
    // =========================================================================
    // Statistics and Monitoring
    // =========================================================================
    
    /**
     * Get retrieval statistics.
     * 
     * @return map of statistics; never null
     */
    Map<String, Object> getStatistics();
    
    // =========================================================================
    // Default Methods
    // =========================================================================
    
    /**
     * Check if the provider is available and ready.
     * 
     * @return true if ready to accept requests
     */
    default boolean isAvailable() {
        return true;
    }
    
    /**
     * Get the provider name for logging and monitoring.
     * 
     * @return provider name; never null
     */
    default String getProviderName() {
        return "default";
    }
}
