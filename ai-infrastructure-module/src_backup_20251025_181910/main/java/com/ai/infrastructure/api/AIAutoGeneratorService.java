package com.ai.infrastructure.api;

import java.util.List;
import java.util.Map;

/**
 * AI Auto Generator Service Interface
 * 
 * Service for automatically generating AI-powered APIs based on entity annotations
 * and configuration. Provides dynamic endpoint generation and API documentation.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
public interface AIAutoGeneratorService {
    
    /**
     * Generate API endpoints for a specific entity type
     * 
     * @param entityType the type of entity to generate endpoints for
     * @return list of generated API endpoint definitions
     */
    List<APIEndpointDefinition> generateEndpoints(String entityType);
    
    /**
     * Generate API specification for a specific entity type
     * 
     * @param entityType the type of entity to generate specification for
     * @return API specification containing all endpoints and metadata
     */
    APISpecification generateSpecification(String entityType);
    
    /**
     * Register a new API endpoint
     * 
     * @param endpoint the endpoint definition to register
     */
    void registerEndpoint(APIEndpointDefinition endpoint);
    
    /**
     * Unregister an API endpoint
     * 
     * @param endpointId the ID of the endpoint to unregister
     */
    void unregisterEndpoint(String endpointId);
    
    /**
     * Get all registered endpoints
     * 
     * @return list of all registered endpoint definitions
     */
    List<APIEndpointDefinition> getRegisteredEndpoints();
    
    /**
     * Get registered endpoints for a specific entity type
     * 
     * @param entityType the entity type to get endpoints for
     * @return list of endpoint definitions for the entity type
     */
    List<APIEndpointDefinition> getEndpointsByEntityType(String entityType);
    
    /**
     * Get a specific endpoint by ID
     * 
     * @param endpointId the ID of the endpoint to retrieve
     * @return the endpoint definition or null if not found
     */
    APIEndpointDefinition getEndpoint(String endpointId);
    
    /**
     * Check if an endpoint is registered
     * 
     * @param endpointId the ID of the endpoint to check
     * @return true if the endpoint is registered, false otherwise
     */
    boolean isEndpointRegistered(String endpointId);
    
    /**
     * Update an existing endpoint
     * 
     * @param endpoint the updated endpoint definition
     */
    void updateEndpoint(APIEndpointDefinition endpoint);
    
    /**
     * Enable an endpoint
     * 
     * @param endpointId the ID of the endpoint to enable
     */
    void enableEndpoint(String endpointId);
    
    /**
     * Disable an endpoint
     * 
     * @param endpointId the ID of the endpoint to disable
     */
    void disableEndpoint(String endpointId);
    
    /**
     * Get endpoint statistics
     * 
     * @return map of endpoint statistics
     */
    java.util.Map<String, Object> getEndpointStatistics();
    
    /**
     * Validate an endpoint definition
     * 
     * @param endpoint the endpoint definition to validate
     * @return true if valid, false otherwise
     */
    boolean validateEndpoint(APIEndpointDefinition endpoint);
    
    /**
     * Generate OpenAPI specification for all registered endpoints
     * 
     * @return OpenAPI specification as JSON string
     */
    String generateOpenAPISpecification();
    
    /**
     * Generate OpenAPI specification for a specific entity type
     * 
     * @param entityType the entity type to generate specification for
     * @return OpenAPI specification as JSON string
     */
    String generateOpenAPISpecification(String entityType);
    
    /**
     * Get API documentation for all endpoints
     * 
     * @return API documentation as HTML string
     */
    String generateAPIDocumentation();
    
    /**
     * Get API documentation for a specific entity type
     * 
     * @param entityType the entity type to generate documentation for
     * @return API documentation as HTML string
     */
    String generateAPIDocumentation(String entityType);
    
    /**
     * Refresh all endpoints (re-generate based on current configuration)
     */
    void refreshEndpoints();
    
    /**
     * Refresh endpoints for a specific entity type
     * 
     * @param entityType the entity type to refresh endpoints for
     */
    void refreshEndpoints(String entityType);
    
    /**
     * Clear all registered endpoints
     */
    void clearEndpoints();
    
    /**
     * Get service health status
     * 
     * @return health status information
     */
    java.util.Map<String, Object> getHealthStatus();
    
    /**
     * Get service metrics
     * 
     * @return service metrics
     */
    java.util.Map<String, Object> getMetrics();
    
    // Additional methods for backend compatibility
    APIEndpointDefinition generateAPIEndpoint(String entityType, Map<String, String> parameters);
    APISpecification generateCompleteAPISpecification();
    String generateClientSDK(String language, APISpecification specification);
}