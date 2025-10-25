package com.ai.infrastructure.cache;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * AI Intelligent Cache Service Interface
 * 
 * Service for intelligent caching of AI responses with smart invalidation,
 * performance optimization, and analytics.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
public interface AIIntelligentCacheService {
    
    /**
     * Get cached value by key
     * 
     * @param key the cache key
     * @param type the expected type
     * @return the cached value or null if not found
     */
    <T> T get(String key, Class<T> type);
    
    /**
     * Put value in cache with TTL
     * 
     * @param key the cache key
     * @param value the value to cache
     * @param ttl the time to live
     */
    void put(String key, Object value, Duration ttl);
    
    /**
     * Put value in cache with default TTL
     * 
     * @param key the cache key
     * @param value the value to cache
     */
    void put(String key, Object value);
    
    /**
     * Evict specific key from cache
     * 
     * @param key the key to evict
     */
    void evict(String key);
    
    /**
     * Evict keys matching pattern
     * 
     * @param pattern the pattern to match
     */
    void evictByPattern(String pattern);
    
    /**
     * Evict keys by tag
     * 
     * @param tag the tag to evict
     */
    void evictByTag(String tag);
    
    /**
     * Evict keys by tags
     * 
     * @param tags the tags to evict
     */
    void evictByTags(List<String> tags);
    
    /**
     * Get cache statistics
     * 
     * @return cache statistics
     */
    CacheStatistics getStatistics();
    
    /**
     * Clear all cache entries
     */
    void clear();
    
    /**
     * Check if cache is enabled
     * 
     * @return true if enabled, false otherwise
     */
    boolean isEnabled();
    
    /**
     * Check if key exists in cache
     * 
     * @param key the key to check
     * @return true if exists, false otherwise
     */
    boolean exists(String key);
    
    /**
     * Get cache size
     * 
     * @return number of entries in cache
     */
    long size();
    
    /**
     * Get cache memory usage
     * 
     * @return memory usage in bytes
     */
    long getMemoryUsage();
    
    /**
     * Get cache hit rate
     * 
     * @return hit rate as percentage
     */
    double getHitRate();
    
    /**
     * Get cache miss rate
     * 
     * @return miss rate as percentage
     */
    double getMissRate();
    
    /**
     * Get cache eviction count
     * 
     * @return number of evictions
     */
    long getEvictionCount();
    
    /**
     * Get cache expiration count
     * 
     * @return number of expirations
     */
    long getExpirationCount();
    
    /**
     * Get cache load count
     * 
     * @return number of loads
     */
    long getLoadCount();
    
    /**
     * Get cache load time
     * 
     * @return total load time in milliseconds
     */
    long getLoadTime();
    
    /**
     * Get cache average load time
     * 
     * @return average load time in milliseconds
     */
    double getAverageLoadTime();
    
    /**
     * Get cache hit count
     * 
     * @return number of hits
     */
    long getHitCount();
    
    /**
     * Get cache miss count
     * 
     * @return number of misses
     */
    long getMissCount();
    
    /**
     * Get cache request count
     * 
     * @return total number of requests
     */
    long getRequestCount();
    
    /**
     * Get cache average response time
     * 
     * @return average response time in milliseconds
     */
    double getAverageResponseTime();
    
    /**
     * Get cache top keys by usage
     * 
     * @param limit maximum number of keys to return
     * @return list of top keys
     */
    List<String> getTopKeys(int limit);
    
    /**
     * Get cache keys by pattern
     * 
     * @param pattern the pattern to match
     * @return list of matching keys
     */
    List<String> getKeysByPattern(String pattern);
    
    /**
     * Get cache keys by tag
     * 
     * @param tag the tag to match
     * @return list of matching keys
     */
    List<String> getKeysByTag(String tag);
    
    /**
     * Get cache keys by tags
     * 
     * @param tags the tags to match
     * @return list of matching keys
     */
    List<String> getKeysByTags(List<String> tags);
    
    /**
     * Tag a cache key
     * 
     * @param key the cache key
     * @param tag the tag to add
     */
    void tag(String key, String tag);
    
    /**
     * Tag a cache key with multiple tags
     * 
     * @param key the cache key
     * @param tags the tags to add
     */
    void tag(String key, List<String> tags);
    
    /**
     * Remove tag from cache key
     * 
     * @param key the cache key
     * @param tag the tag to remove
     */
    void untag(String key, String tag);
    
    /**
     * Remove tags from cache key
     * 
     * @param key the cache key
     * @param tags the tags to remove
     */
    void untag(String key, List<String> tags);
    
    /**
     * Get tags for a cache key
     * 
     * @param key the cache key
     * @return list of tags
     */
    List<String> getTags(String key);
    
    /**
     * Get all tags in cache
     * 
     * @return list of all tags
     */
    List<String> getAllTags();
    
    /**
     * Get cache configuration
     * 
     * @return cache configuration
     */
    CacheConfig getConfiguration();
    
    /**
     * Update cache configuration
     * 
     * @param config the new configuration
     */
    void updateConfiguration(CacheConfig config);
    
    /**
     * Warm up cache with data
     * 
     * @param data the data to warm up with
     */
    void warmUp(Map<String, Object> data);
    
    /**
     * Preload cache with data
     * 
     * @param data the data to preload
     */
    void preload(Map<String, Object> data);
    
    /**
     * Refresh cache entry
     * 
     * @param key the key to refresh
     */
    void refresh(String key);
    
    /**
     * Refresh cache entries by pattern
     * 
     * @param pattern the pattern to refresh
     */
    void refreshByPattern(String pattern);
    
    /**
     * Refresh cache entries by tag
     * 
     * @param tag the tag to refresh
     */
    void refreshByTag(String tag);
    
    /**
     * Refresh cache entries by tags
     * 
     * @param tags the tags to refresh
     */
    void refreshByTags(List<String> tags);
    
    /**
     * Get cache health status
     * 
     * @return health status
     */
    Map<String, Object> getHealthStatus();
    
    /**
     * Get cache metrics
     * 
     * @return cache metrics
     */
    Map<String, Object> getMetrics();
    
    /**
     * Get cache recommendations
     * 
     * @return cache optimization recommendations
     */
    List<String> getRecommendations();
    
    /**
     * Optimize cache based on usage patterns
     */
    void optimize();
    
    /**
     * Reset cache statistics
     */
    void resetStatistics();
    
    /**
     * Export cache data
     * 
     * @return cache data as map
     */
    Map<String, Object> exportData();
    
    /**
     * Import cache data
     * 
     * @param data the data to import
     */
    void importData(Map<String, Object> data);
    
    /**
     * Backup cache data
     * 
     * @return backup data
     */
    Map<String, Object> backup();
    
    /**
     * Restore cache data from backup
     * 
     * @param backup the backup data
     */
    void restore(Map<String, Object> backup);
    
    /**
     * Shutdown cache service
     */
    void shutdown();
    
    // Additional methods for backend compatibility
    CacheStatistics getCacheStatistics();
    void clearAllCaches();
}