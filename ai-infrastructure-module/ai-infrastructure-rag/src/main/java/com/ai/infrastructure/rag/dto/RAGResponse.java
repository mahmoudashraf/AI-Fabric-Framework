package com.ai.infrastructure.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * RAG Response DTO - Retrieval-focused response.
 * 
 * <p>This DTO contains only retrieval results. It does NOT include:</p>
 * <ul>
 *   <li>Generated response text - generation is orchestrator's responsibility</li>
 *   <li>PII detection results - PII handling is orchestrator's responsibility</li>
 * </ul>
 * 
 * <p>The orchestrator pipeline should use retrieved documents to:</p>
 * <ol>
 *   <li>Build context for LLM generation (if needed)</li>
 *   <li>Apply PII sanitization to output (if needed)</li>
 * </ol>
 * 
 * @author AI Infrastructure Team
 * @version 2.0.0
 * @since 1.1
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RAGResponse {
    
    /**
     * Retrieved documents with relevance scores.
     */
    private List<RetrievedDocument> documents;
    
    /**
     * Total documents found matching the query.
     */
    private Integer totalDocuments;
    
    /**
     * Number of documents returned in this response.
     */
    private Integer returnedDocuments;
    
    /**
     * Whether the retrieval was successful.
     */
    private Boolean success;
    
    /**
     * Error message if retrieval failed.
     */
    private String errorMessage;
    
    /**
     * Maximum relevance score among results.
     */
    private Double maxScore;
    
    /**
     * Average relevance score of results.
     */
    private Double averageScore;
    
    /**
     * Relevance scores for all returned documents.
     */
    private List<Double> relevanceScores;
    
    /**
     * Processing time in milliseconds.
     */
    private Long processingTimeMs;
    
    /**
     * Request ID for tracking.
     */
    private String requestId;
    
    /**
     * The query that was processed.
     */
    private String query;
    
    /**
     * Entity type that was searched.
     */
    private String entityType;
    
    /**
     * Timestamp of the response.
     */
    private LocalDateTime timestamp;
    
    /**
     * Additional response metadata.
     */
    private Map<String, Object> metadata;
    
    /**
     * Whether hybrid search was used.
     */
    private Boolean hybridSearchUsed;
    
    /**
     * Whether contextual search was used.
     */
    private Boolean contextualSearchUsed;
    
    /**
     * Categories that were searched.
     */
    private List<String> searchedCategories;
    
    /**
     * Retrieved Document - represents a single retrieved result.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RetrievedDocument {
        
        /**
         * Document ID.
         */
        private String id;
        
        /**
         * Document content.
         */
        private String content;
        
        /**
         * Document title.
         */
        private String title;
        
        /**
         * Document type.
         */
        private String type;
        
        /**
         * Relevance score (0.0 to 1.0).
         */
        private Double score;
        
        /**
         * Similarity score from vector search.
         */
        private Double similarity;
        
        /**
         * Document metadata.
         */
        private Map<String, Object> metadata;
        
        /**
         * Document source.
         */
        private String source;
    }
    
    /**
     * Build context string from retrieved documents for LLM generation.
     * 
     * <p>This is a utility method for the orchestrator to use when
     * building context for LLM generation.</p>
     * 
     * @return formatted context string from documents
     */
    public String buildContextForGeneration() {
        if (documents == null || documents.isEmpty()) {
            return "";
        }
        
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            RetrievedDocument doc = documents.get(i);
            context.append(String.format("[%d] %s (score: %.3f)\n", 
                i + 1, doc.getContent(), doc.getScore() != null ? doc.getScore() : 0.0));
        }
        return context.toString();
    }
}
