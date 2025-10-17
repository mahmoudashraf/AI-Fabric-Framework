package com.easyluxury.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI Health Status DTO for Easy Luxury
 * 
 * This DTO contains comprehensive AI health status information
 * specific to the Easy Luxury application.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIHealthStatusDto {
    
    /**
     * Overall AI health status
     */
    private String overallStatus;
    
    /**
     * Whether AI services are healthy
     */
    private Boolean healthy;
    
    /**
     * Health status message
     */
    private String message;
    
    /**
     * Timestamp of health check
     */
    private Long timestamp;
    
    /**
     * Error information if any
     */
    private String error;
    
    /**
     * Easy Luxury specific AI features status
     */
    private Boolean productRecommendationsEnabled;
    private Boolean userBehaviorTrackingEnabled;
    private Boolean smartValidationEnabled;
    private Boolean aiContentGenerationEnabled;
    private Boolean aiSearchEnabled;
    private Boolean aiRAGEnabled;
    
    /**
     * Configuration status details
     */
    private AIConfigurationStatusDto configurationStatus;
    
    /**
     * Performance metrics
     */
    private Object performanceMetrics;
}