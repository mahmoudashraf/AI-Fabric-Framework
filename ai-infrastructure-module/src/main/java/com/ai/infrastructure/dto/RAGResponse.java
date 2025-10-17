package com.ai.infrastructure.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Response DTO for RAG operations
 * 
 * This DTO contains the results of Retrieval-Augmented Generation
 * operations including retrieved context and generated responses.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RAGResponse {
    
    /**
     * The generated response
     */
    private String response;
    
    /**
     * Retrieved context used for generation
     */
    private String context;
    
    /**
     * Retrieved documents
     */
    private List<Map<String, Object>> documents;
    
    /**
     * Total number of documents found
     */
    private int totalDocuments;
    
    /**
     * Number of documents used in context
     */
    private int usedDocuments;
    
    /**
     * Confidence score for the response
     */
    private double confidence;
    
    /**
     * Relevance scores for retrieved documents
     */
    private List<Double> relevanceScores;
    
    /**
     * Processing time in milliseconds
     */
    private long processingTimeMs;
    
    /**
     * Request ID for tracking
     */
    private String requestId;
    
    /**
     * Model used for generation
     */
    private String model;
    
    /**
     * Whether the response was generated successfully
     */
    private boolean success;
    
    /**
     * Error message if generation failed
     */
    private String errorMessage;
    
    /**
     * Metadata about the generation process
     */
    private Map<String, Object> metadata;
    
    /**
     * Search statistics
     */
    private Map<String, Object> searchStats;
    
    /**
     * Generation statistics
     */
    private Map<String, Object> generationStats;
    
    /**
     * Whether hybrid search was used
     */
    private boolean hybridSearchUsed;
    
    /**
     * Whether contextual search was used
     */
    private boolean contextualSearchUsed;
    
    /**
     * Query that was processed
     */
    private String originalQuery;
    
    /**
     * Entity type that was searched
     */
    private String entityType;
    
    /**
     * Knowledge categories that were searched
     */
    private List<String> searchedCategories;
    
    /**
     * Whether semantic search was used
     */
    private boolean semanticSearchUsed;
    
    /**
     * Whether keyword search was used
     */
    private boolean keywordSearchUsed;
}
