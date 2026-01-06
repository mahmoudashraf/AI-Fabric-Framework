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
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class RAGResponse {
    
    /**
     * Custom builder extensions for backward compatibility.
     */
    public static class RAGResponseBuilder {
        /**
         * Alias for {@link #query(String)}.
         * @deprecated Use {@link #query(String)} instead.
         */
        @Deprecated
        public RAGResponseBuilder originalQuery(String query) {
            this.query = query;
            return this;
        }
        
        /**
         * Alias for {@link #totalDocuments(Integer)}.
         * @deprecated Use {@link #totalDocuments(Integer)} instead.
         */
        @Deprecated
        public RAGResponseBuilder totalResults(int total) {
            this.totalDocuments = total;
            return this;
        }
        
        /**
         * Alias for {@link #returnedDocuments(Integer)}.
         * @deprecated Use {@link #returnedDocuments(Integer)} instead.
         */
        @Deprecated
        public RAGResponseBuilder returnedResults(int returned) {
            this.returnedDocuments = returned;
            return this;
        }
        
        /**
         * No-op for removed warnings field.
         * @deprecated Warnings are no longer part of retrieval response.
         */
        @Deprecated
        public RAGResponseBuilder warnings(List<String> warnings) {
            // No-op: warnings are no longer part of retrieval response
            return this;
        }
        
        /**
         * No-op for removed response field.
         * @deprecated Use orchestrator for generation. RAG is retrieval-only.
         */
        @Deprecated
        public RAGResponseBuilder response(String response) {
            // No-op: response generation is handled by orchestrator
            return this;
        }
        
        /**
         * Alias for {@link #maxScore(Double)}.
         * @deprecated Use individual document scores or {@link #maxScore(Double)}.
         */
        @Deprecated
        public RAGResponseBuilder confidenceScore(Double score) {
            this.maxScore = score;
            return this;
        }
    }
    
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
    
    // =========================================================================
    // Backward Compatibility Methods
    // =========================================================================
    
    /**
     * Alias for {@link #getTotalDocuments()}.
     * @deprecated Use {@link #getTotalDocuments()} instead.
     */
    @Deprecated
    public Integer getTotalResults() {
        return totalDocuments;
    }
    
    /**
     * Alias for {@link #getReturnedDocuments()}.
     * @deprecated Use {@link #getReturnedDocuments()} instead.
     */
    @Deprecated
    public Integer getReturnedResults() {
        return returnedDocuments;
    }
    
    /**
     * Alias for {@link #getQuery()}.
     * @deprecated Use {@link #getQuery()} instead.
     */
    @Deprecated
    public String getOriginalQuery() {
        return query;
    }
    
    /**
     * Placeholder for removed generation field.
     * RAG is now retrieval-only; generation happens in orchestrator.
     * @deprecated RAG is now retrieval-only. Use {@link #buildContextForGeneration()} to get context for generation.
     */
    @Deprecated
    public String getResponse() {
        return buildContextForGeneration();
    }
    
    /**
     * Placeholder for removed warnings field.
     * @deprecated Warnings are no longer part of retrieval response.
     */
    @Deprecated
    public List<String> getWarnings() {
        return List.of();
    }
    
    /**
     * Placeholder for removed confidence score field.
     * @deprecated Use individual document scores or {@link #getMaxScore()}.
     */
    @Deprecated
    public Double getConfidenceScore() {
        return maxScore;
    }
    
    /**
     * Type alias for backward compatibility.
     * @deprecated Use {@link RetrievedDocument} instead.
     */
    @Deprecated
    public static class RAGDocument extends RetrievedDocument {
        // Inherits all fields and methods from RetrievedDocument
    }
    
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
         * Document embeddings (if requested).
         */
        private List<Double> embeddings;
        
        /**
         * Highlighted content with search terms.
         */
        private String highlightedContent;
        
        /**
         * Document source.
         */
        private String source;
        
        /**
         * Document URL or reference.
         */
        private String url;
        
        /**
         * Document creation date.
         */
        private LocalDateTime createdAt;
        
        /**
         * Document last modified date.
         */
        private LocalDateTime modifiedAt;
        
        /**
         * Document author.
         */
        private String author;
        
        /**
         * Document tags.
         */
        private List<String> tags;
        
        /**
         * Document language.
         */
        private String language;
        
        /**
         * Document size in characters.
         */
        private Integer size;
        
        /**
         * Document word count.
         */
        private Integer wordCount;
        
        /**
         * Document reading time in minutes.
         */
        private Double readingTimeMinutes;
        
        /**
         * Document quality score.
         */
        private Double qualityScore;
        
        /**
         * Document freshness score.
         */
        private Double freshnessScore;
        
        /**
         * Document authority score.
         */
        private Double authorityScore;
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
