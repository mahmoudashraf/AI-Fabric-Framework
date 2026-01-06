package com.ai.infrastructure.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * RAG Request DTO - Retrieval-focused request.
 * 
 * <p>This DTO is used for retrieval operations only. It does NOT include:</p>
 * <ul>
 *   <li>PII detection - handled by orchestrator pipeline</li>
 *   <li>Generation parameters - handled by orchestrator pipeline</li>
 * </ul>
 * 
 * @author AI Infrastructure Team
 * @version 2.0.0
 * @since 1.1
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RAGRequest {
    
    /**
     * The query text for retrieval.
     */
    private String query;
    
    /**
     * The type of entity being queried (e.g., "product", "document").
     */
    private String entityType;
    
    /**
     * Maximum number of results to retrieve.
     */
    @Builder.Default
    private Integer limit = 10;
    
    /**
     * Similarity threshold for results (0.0 to 1.0).
     */
    @Builder.Default
    private Double threshold = 0.7;
    
    /**
     * Context parameters for enhanced retrieval.
     */
    private Map<String, Object> context;
    
    /**
     * Metadata filters for the search.
     */
    private Map<String, Object> filters;
    
    /**
     * Additional request metadata.
     */
    private Map<String, Object> metadata;
    
    /**
     * User ID for personalized/filtered results.
     */
    private String userId;
    
    /**
     * Request ID for tracking.
     */
    private String requestId;
    
    /**
     * Whether to use hybrid search (vector + keyword).
     */
    @Builder.Default
    private Boolean enableHybridSearch = false;
    
    /**
     * Whether to use contextual search.
     */
    @Builder.Default
    private Boolean enableContextualSearch = false;
    
    /**
     * Categories to search within.
     */
    private List<String> categories;
    
    /**
     * Specific fields to search.
     */
    private List<String> searchFields;
}
