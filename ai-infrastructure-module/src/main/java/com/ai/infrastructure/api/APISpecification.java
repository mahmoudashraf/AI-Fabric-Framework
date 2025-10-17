package com.ai.infrastructure.api;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * API Specification
 * 
 * Represents a complete API specification including all endpoints,
 * OpenAPI documentation, and metadata for auto-generation.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Data
@Builder
public class APISpecification {
    
    /**
     * API title
     */
    private String title;
    
    /**
     * API version
     */
    private String version;
    
    /**
     * API description
     */
    private String description;
    
    /**
     * Base URL for the API
     */
    private String baseUrl;
    
    /**
     * List of all API endpoints
     */
    private List<APIEndpointDefinition> endpoints;
    
    /**
     * Complete OpenAPI specification
     */
    private String openApiSpec;
    
    /**
     * API contact information
     */
    private ContactInfo contact;
    
    /**
     * API license information
     */
    private LicenseInfo license;
    
    /**
     * Server information
     */
    private List<ServerInfo> servers;
    
    /**
     * Security schemes
     */
    private List<SecurityScheme> securitySchemes;
    
    /**
     * Global tags
     */
    private List<TagInfo> tags;
    
    /**
     * External documentation
     */
    private ExternalDocs externalDocs;
    
    /**
     * Contact information
     */
    @Data
    @Builder
    public static class ContactInfo {
        private String name;
        private String email;
        private String url;
    }
    
    /**
     * License information
     */
    @Data
    @Builder
    public static class LicenseInfo {
        private String name;
        private String url;
    }
    
    /**
     * Server information
     */
    @Data
    @Builder
    public static class ServerInfo {
        private String url;
        private String description;
        private Map<String, Object> variables;
    }
    
    /**
     * Security scheme
     */
    @Data
    @Builder
    public static class SecurityScheme {
        private String name;
        private String type;
        private String scheme;
        private String bearerFormat;
        private String description;
    }
    
    /**
     * Tag information
     */
    @Data
    @Builder
    public static class TagInfo {
        private String name;
        private String description;
        private ExternalDocs externalDocs;
    }
    
    /**
     * External documentation
     */
    @Data
    @Builder
    public static class ExternalDocs {
        private String description;
        private String url;
    }
}