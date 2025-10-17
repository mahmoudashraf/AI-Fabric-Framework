package com.ai.infrastructure.api;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * API Endpoint Definition
 * 
 * Represents a single API endpoint definition with all necessary metadata
 * for auto-generation of REST APIs, OpenAPI specs, and client SDKs.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Data
@Builder
public class APIEndpointDefinition {
    
    /**
     * The type of AI operation (e.g., content-generation, embedding-generation)
     */
    private String operationType;
    
    /**
     * The REST endpoint path (e.g., /api/v1/ai/content-generation)
     */
    private String endpointPath;
    
    /**
     * The HTTP method (GET, POST, PUT, DELETE)
     */
    private String httpMethod;
    
    /**
     * Request schema definition
     */
    private Map<String, Object> requestSchema;
    
    /**
     * Response schema definition
     */
    private Map<String, Object> responseSchema;
    
    /**
     * OpenAPI specification for this endpoint
     */
    private String openApiSpec;
    
    /**
     * List of required parameters
     */
    private List<String> requiredParameters;
    
    /**
     * List of optional parameters
     */
    private List<String> optionalParameters;
    
    /**
     * Example request payload
     */
    private Map<String, Object> exampleRequest;
    
    /**
     * Example response payload
     */
    private Map<String, Object> exampleResponse;
    
    /**
     * Endpoint description
     */
    private String description;
    
    /**
     * Tags for categorization
     */
    private List<String> tags;
    
    /**
     * Whether this endpoint requires authentication
     */
    private boolean requiresAuth;
    
    /**
     * Rate limiting information
     */
    private RateLimitInfo rateLimit;
    
    /**
     * Rate limiting information
     */
    @Data
    @Builder
    public static class RateLimitInfo {
        private int requestsPerMinute;
        private int requestsPerHour;
        private int requestsPerDay;
        private String rateLimitStrategy;
    }
}