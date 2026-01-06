package com.ai.infrastructure.spi;

import java.util.List;
import java.util.Map;

/**
 * Service Provider Interface for content retrieval operations.
 * 
 * <p>This is a generic interface that allows the orchestrator pipeline to
 * perform retrieval operations without depending on specific RAG implementations.
 * The RAG module provides a concrete implementation of this interface.</p>
 * 
 * <h2>Design Rationale</h2>
 * <p>This interface exists to avoid circular dependencies:</p>
 * <ul>
 *   <li>Core module defines this interface</li>
 *   <li>RAG module implements this interface</li>
 *   <li>Pipeline steps use this interface (not RAG-specific types)</li>
 * </ul>
 * 
 * <h2>Responsibility Separation</h2>
 * <ul>
 *   <li>ContentRetriever: Retrieves documents based on query</li>
 *   <li>Orchestrator: PII detection, LLM generation, response sanitization</li>
 * </ul>
 * 
 * @author AI Infrastructure Team
 * @version 2.0.0
 * @since 1.1
 */
public interface ContentRetriever {
    
    /**
     * Retrieve content matching the given query.
     * 
     * @param query the search query
     * @param entityType the type of entity to search (e.g., "product", "document")
     * @param limit maximum number of results
     * @param threshold minimum similarity threshold (0.0 to 1.0)
     * @param metadata additional search parameters
     * @return retrieval result containing documents and metadata
     */
    RetrievalResult retrieve(String query, String entityType, int limit, double threshold, Map<String, Object> metadata);
    
    /**
     * Index content for later retrieval.
     * 
     * @param entityType the type of entity
     * @param entityId unique identifier
     * @param content the content to index
     * @param metadata additional metadata
     */
    void indexContent(String entityType, String entityId, String content, Map<String, Object> metadata);
    
    /**
     * Remove content from the index.
     * 
     * @param entityType the type of entity
     * @param entityId unique identifier
     */
    void removeContent(String entityType, String entityId);
    
    /**
     * Check if the retriever is available.
     * 
     * @return true if ready to accept requests
     */
    default boolean isAvailable() {
        return true;
    }
    
    /**
     * Get the retriever name for logging.
     * 
     * @return retriever name
     */
    default String getRetrieverName() {
        return "default";
    }
    
    /**
     * Result of a retrieval operation.
     */
    record RetrievalResult(
        List<RetrievedDocument> documents,
        int totalDocuments,
        boolean success,
        String errorMessage,
        double maxScore,
        double averageScore,
        long processingTimeMs
    ) {
        /**
         * Create a successful result.
         */
        public static RetrievalResult success(List<RetrievedDocument> documents, int total, 
                double maxScore, double avgScore, long timeMs) {
            return new RetrievalResult(documents, total, true, null, maxScore, avgScore, timeMs);
        }
        
        /**
         * Create an error result.
         */
        public static RetrievalResult error(String message) {
            return new RetrievalResult(List.of(), 0, false, message, 0.0, 0.0, 0);
        }
        
        /**
         * Build context string from documents for LLM generation.
         */
        public String buildContext() {
            if (documents == null || documents.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < documents.size(); i++) {
                RetrievedDocument doc = documents.get(i);
                sb.append(String.format("[%d] %s (score: %.3f)\n", 
                    i + 1, doc.content(), doc.score()));
            }
            return sb.toString();
        }
    }
    
    /**
     * A retrieved document.
     */
    record RetrievedDocument(
        String id,
        String content,
        String title,
        String type,
        double score,
        Map<String, Object> metadata
    ) {}
}
