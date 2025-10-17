package com.ai.infrastructure.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI Health DTO
 * 
 * This DTO contains detailed health information for AI services
 * including configuration status, feature status, and service status.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIHealthDto {
    
    /**
     * Whether AI services are enabled
     */
    private boolean enabled;
    
    /**
     * Overall health status
     */
    private String status;
    
    /**
     * Whether configuration is valid
     */
    private boolean configurationValid;
    
    /**
     * Whether provider configuration is valid
     */
    private boolean providerConfigurationValid;
    
    /**
     * Whether service configuration is valid
     */
    private boolean serviceConfigurationValid;
    
    /**
     * Number of features enabled
     */
    private int featuresEnabled;
    
    /**
     * Total number of features
     */
    private int totalFeatures;
    
    /**
     * Number of services enabled
     */
    private int servicesEnabled;
    
    /**
     * Total number of services
     */
    private int totalServices;
    
    /**
     * Whether caching is enabled
     */
    private boolean cachingEnabled;
    
    /**
     * Whether metrics are enabled
     */
    private boolean metricsEnabled;
    
    /**
     * Whether health checks are enabled
     */
    private boolean healthChecksEnabled;
    
    /**
     * Whether async processing is enabled
     */
    private boolean asyncEnabled;
    
    /**
     * Whether batch processing is enabled
     */
    private boolean batchProcessingEnabled;
    
    /**
     * Whether rate limiting is enabled
     */
    private boolean rateLimitingEnabled;
    
    /**
     * Whether circuit breaker is enabled
     */
    private boolean circuitBreakerEnabled;
    
    /**
     * Whether OpenAI is configured
     */
    private boolean openaiConfigured;
    
    /**
     * Whether Pinecone is configured
     */
    private boolean pineconeConfigured;
}
