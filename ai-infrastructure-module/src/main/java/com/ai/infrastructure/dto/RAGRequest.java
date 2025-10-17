package com.ai.infrastructure.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Request DTO for RAG operations
 * 
 * This DTO contains all necessary information for performing
 * Retrieval-Augmented Generation operations.
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
     * The query or question to process
     */
    private String query;
    
    /**
     * Entity type to search within
     */
    private String entityType;
    
    /**
     * Maximum number of results to retrieve
     */
    private int limit;
    
    /**
     * Similarity threshold for results
     */
    private double threshold;
    
    /**
     * Context for the query
     */
    private String context;
    
    /**
     * Additional filters to apply
     */
    private Map<String, Object> filters;
    
    /**
     * Whether to include metadata in results
     */
    private boolean includeMetadata;
    
    /**
     * Whether to enable hybrid search
     */
    private boolean enableHybridSearch;
    
    /**
     * Whether to enable contextual search
     */
    private boolean enableContextualSearch;
    
    /**
     * Custom search parameters
     */
    private Map<String, Object> searchParams;
    
    /**
     * Knowledge categories to search
     */
    private List<String> categories;
    
    /**
     * Whether to enable semantic search
     */
    private boolean enableSemanticSearch;
    
    /**
     * Whether to enable keyword search
     */
    private boolean enableKeywordSearch;
    
    /**
     * Maximum context length for generation
     */
    private int maxContextLength;
    
    /**
     * Whether to rank results by relevance
     */
    private boolean rankByRelevance;
    
    /**
     * Custom ranking parameters
     */
    private Map<String, Object> rankingParams;
    
    /**
     * Request ID for tracking
     */
    private String requestId;
    
    /**
     * User ID for personalization
     */
    private String userId;
    
    /**
     * Session ID for context
     */
    private String sessionId;
}
