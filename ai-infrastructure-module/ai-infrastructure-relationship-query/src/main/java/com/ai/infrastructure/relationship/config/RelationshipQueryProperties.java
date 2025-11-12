package com.ai.infrastructure.relationship.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Relationship Query functionality
 */
@Data
@ConfigurationProperties(prefix = "ai.infrastructure.relationship")
public class RelationshipQueryProperties {
    
    /**
     * Enable relationship query functionality
     */
    private boolean enabled = true;
    
    /**
     * Default similarity threshold for semantic search
     */
    private double defaultSimilarityThreshold = 0.7;
    
    /**
     * Maximum traversal depth for relationship queries
     */
    private int maxTraversalDepth = 3;
    
    /**
     * Enable query caching
     */
    private boolean enableQueryCaching = true;
    
    /**
     * Query cache TTL in seconds
     */
    private long queryCacheTtlSeconds = 3600;
    
    /**
     * Enable query validation
     */
    private boolean enableQueryValidation = true;
    
    /**
     * Fallback to metadata-based approach if JPA fails
     */
    private boolean fallbackToMetadata = true;
}
