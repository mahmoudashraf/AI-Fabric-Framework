package com.ai.infrastructure.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * RAG Request DTO
 * 
 * Represents a request for Retrieval-Augmented Generation (RAG) operations.
 * Contains query parameters, context preferences, and filtering options.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RAGRequest {
    
    /**
     * The query text for RAG processing
     */
    private String query;
    
    /**
     * The type of entity being queried
     */
    private String entityType;
    
    /**
     * Maximum number of results to retrieve
     */
    @Builder.Default
    private Integer limit = 10;
    
    /**
     * Similarity threshold for results (0.0 to 1.0)
     */
    @Builder.Default
    private Double threshold = 0.7;
    
    /**
     * Context parameters for enhanced retrieval
     */
    private Map<String, Object> context;
    
    /**
     * Search preferences and filters
     */
    private Map<String, Object> filters;
    
    /**
     * Additional metadata for the request
     */
    private Map<String, Object> metadata;
    
    /**
     * User ID for personalized results
     */
    private String userId;
    
    /**
     * Session ID for tracking
     */
    private String sessionId;
    
    /**
     * Whether to include embeddings in response
     */
    @Builder.Default
    private Boolean includeEmbeddings = false;
    
    /**
     * Whether to include metadata in response
     */
    @Builder.Default
    private Boolean includeMetadata = true;
    
    /**
     * Language preference for results
     */
    private String language;
    
    /**
     * Specific fields to search in
     */
    private List<String> searchFields;
    
    /**
     * Sort preferences
     */
    private Map<String, String> sortBy;
    
    /**
     * Request timeout in milliseconds
     */
    @Builder.Default
    private Long timeoutMs = 30000L;
    
    /**
     * Whether to use hybrid search (vector + text)
     */
    @Builder.Default
    private Boolean useHybridSearch = true;
    
    /**
     * Boost factors for different fields
     */
    private Map<String, Double> boostFactors;
    
    /**
     * Custom scoring function
     */
    private String scoringFunction;
    
    /**
     * Request ID for tracking
     */
    private String requestId;
    
    /**
     * Timestamp when request was created
     */
    private Long timestamp;
    
    /**
     * Priority level for processing
     */
    @Builder.Default
    private Integer priority = 1;
    
    /**
     * Whether to cache the results
     */
    @Builder.Default
    private Boolean cacheable = true;
    
    /**
     * Cache TTL in seconds
     */
    @Builder.Default
    private Long cacheTtlSeconds = 3600L;
}