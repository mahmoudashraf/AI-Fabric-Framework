package com.ai.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * AI Service Configuration
 * 
 * This configuration class manages AI service settings, feature toggles,
 * and service-specific configurations.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.service")
public class AIServiceConfig {
    
    /**
     * Whether AI services are enabled
     */
    private boolean enabled = true;
    
    /**
     * Whether to enable auto-configuration
     */
    private boolean autoConfiguration = true;
    
    /**
     * Whether to enable caching
     */
    private boolean cachingEnabled = true;
    
    /**
     * Whether to enable metrics collection
     */
    private boolean metricsEnabled = true;
    
    /**
     * Whether to enable health checks
     */
    private boolean healthChecksEnabled = true;
    
    /**
     * Whether to enable logging
     */
    private boolean loggingEnabled = true;
    
    /**
     * Default timeout for AI operations in milliseconds
     */
    private long defaultTimeout = 30000;
    
    /**
     * Maximum retry attempts for AI operations
     */
    private int maxRetries = 3;
    
    /**
     * Retry delay in milliseconds
     */
    private long retryDelay = 1000;
    
    /**
     * Whether to enable async processing
     */
    private boolean asyncEnabled = true;
    
    /**
     * Thread pool size for async operations
     */
    private int threadPoolSize = 10;
    
    /**
     * Whether to enable batch processing
     */
    private boolean batchProcessingEnabled = true;
    
    /**
     * Batch size for processing
     */
    private int batchSize = 100;
    
    /**
     * Whether to enable rate limiting
     */
    private boolean rateLimitingEnabled = true;
    
    /**
     * Rate limit per minute
     */
    private int rateLimitPerMinute = 1000;
    
    /**
     * Whether to enable circuit breaker
     */
    private boolean circuitBreakerEnabled = true;
    
    /**
     * Circuit breaker failure threshold
     */
    private int circuitBreakerThreshold = 5;
    
    /**
     * Circuit breaker timeout in milliseconds
     */
    private long circuitBreakerTimeout = 60000;
    
    /**
     * Whether to enable feature flags
     */
    private boolean featureFlagsEnabled = true;
    
    /**
     * Feature flags configuration
     */
    private Map<String, Boolean> featureFlags = Map.of(
        "embedding.enabled", true,
        "search.enabled", true,
        "rag.enabled", true,
        "validation.enabled", true,
        "recommendations.enabled", true,
        "generation.enabled", true
    );
    
    /**
     * Service-specific configurations
     */
    private Map<String, ServiceConfig> services = Map.of(
        "embedding", new ServiceConfig(true, 1000, 5000),
        "search", new ServiceConfig(true, 500, 3000),
        "rag", new ServiceConfig(true, 2000, 10000),
        "validation", new ServiceConfig(true, 100, 1000),
        "recommendations", new ServiceConfig(true, 300, 2000),
        "generation", new ServiceConfig(true, 1000, 5000)
    );
    
    /**
     * Service configuration class
     */
    @Data
    public static class ServiceConfig {
        private boolean enabled;
        private int rateLimit;
        private long timeout;
        
        public ServiceConfig() {}
        
        public ServiceConfig(boolean enabled, int rateLimit, long timeout) {
            this.enabled = enabled;
            this.rateLimit = rateLimit;
            this.timeout = timeout;
        }
    }
}
