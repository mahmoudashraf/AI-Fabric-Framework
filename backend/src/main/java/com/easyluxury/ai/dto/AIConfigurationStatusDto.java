package com.easyluxury.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI Configuration Status DTO for Easy Luxury
 * 
 * This DTO contains AI configuration status information
 * specific to the Easy Luxury application.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIConfigurationStatusDto {
    
    /**
     * Provider configuration status
     */
    private Boolean openaiConfigured;
    private Boolean pineconeConfigured;
    
    /**
     * Service configuration status
     */
    private Boolean aiServicesEnabled;
    private Boolean cachingEnabled;
    private Boolean metricsEnabled;
    private Boolean healthChecksEnabled;
    private Boolean asyncEnabled;
    private Boolean batchProcessingEnabled;
    private Boolean rateLimitingEnabled;
    private Boolean circuitBreakerEnabled;
    
    /**
     * Easy Luxury specific AI features
     */
    private Boolean productRecommendationsEnabled;
    private Boolean userBehaviorTrackingEnabled;
    private Boolean smartValidationEnabled;
    private Boolean aiContentGenerationEnabled;
    private Boolean aiSearchEnabled;
    private Boolean aiRAGEnabled;
    
    /**
     * Model configuration
     */
    private String defaultAIModel;
    private String defaultEmbeddingModel;
    private Integer maxTokens;
    private Double temperature;
    private Long timeoutSeconds;
    
    /**
     * Index names
     */
    private String productIndexName;
    private String userIndexName;
    private String orderIndexName;
}