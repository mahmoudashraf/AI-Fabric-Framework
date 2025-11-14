package com.ai.infrastructure.cache;

import com.ai.infrastructure.config.AIServiceConfig;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * AI Cache Configuration
 * 
 * This configuration sets up caching for AI services including
 * embeddings, search results, and other frequently accessed data.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@Configuration
@EnableCaching
@RequiredArgsConstructor
public class AICacheConfig {
    
    private final AIServiceConfig serviceConfig;
    
    /**
     * Configure Caffeine cache manager for AI services
     * 
     * @return configured cache manager
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        
        AIServiceConfig.PerformanceConfig performance = serviceConfig.getPerformance();
        AIServiceConfig.CacheConfig cache = serviceConfig.getCache();

        int maxConcurrentRequests = performance != null && performance.getMaxConcurrentRequests() != null
            ? performance.getMaxConcurrentRequests()
            : 10;
        long configuredMaxSize = cache != null && cache.getMaxSize() != null
            ? cache.getMaxSize()
            : maxConcurrentRequests * 10L;
        Duration ttl = cache != null && cache.getDefaultTtl() != null
            ? cache.getDefaultTtl()
            : Duration.ofMinutes(60);
        
        // Configure Caffeine cache
        Caffeine<Object, Object> caffeine = Caffeine.newBuilder()
            .maximumSize(Math.max(configuredMaxSize, 1))
            .expireAfterWrite(ttl)
            .recordStats()
            .removalListener((key, value, cause) -> 
                log.debug("Cache entry removed: {} - {}", key, cause));
        
        cacheManager.setCaffeine(caffeine);
        
        // Configure specific caches
        cacheManager.setCacheNames(java.util.Set.of(
            "embeddings",
            "vectorSearch",
            "textSearch",
            "aiGeneration",
            "aiValidation",
            "accessDecisions",
            "retentionStatus",
            "behaviorRetention"
        ));
        
        log.info("AI Cache Manager configured with max size {} and TTL {}", configuredMaxSize, ttl);
        
        return cacheManager;
    }
    
    /**
     * Configure embedding cache with specific settings
     * 
     * @return Caffeine cache for embeddings
     */
    @Bean("embeddingCache")
    public com.github.benmanes.caffeine.cache.Cache<String, Object> embeddingCache() {
        return Caffeine.newBuilder()
            .maximumSize(1000) // Embeddings can be large, limit size
            .expireAfterWrite(Duration.ofHours(24)) // Embeddings rarely change
            .recordStats()
            .build();
    }
    
    /**
     * Configure search cache with specific settings
     * 
     * @return Caffeine cache for search results
     */
    @Bean("searchCache")
    public com.github.benmanes.caffeine.cache.Cache<String, Object> searchCache() {
        return Caffeine.newBuilder()
            .maximumSize(500) // Search results are smaller
            .expireAfterWrite(Duration.ofMinutes(30)) // Search results change more frequently
            .recordStats()
            .build();
    }
    
    /**
     * Configure generation cache for AI content generation
     * 
     * @return Caffeine cache for generated content
     */
    @Bean("generationCache")
    public com.github.benmanes.caffeine.cache.Cache<String, Object> generationCache() {
        return Caffeine.newBuilder()
            .maximumSize(200) // Generated content can be large
            .expireAfterWrite(Duration.ofHours(6)) // Generated content has medium lifespan
            .recordStats()
            .build();
    }
}
