package com.ai.infrastructure.cache;

import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AI Intelligent Caching Service
 * 
 * This service provides intelligent caching capabilities for AI operations
 * with automatic cache invalidation, similarity-based caching, and
 * performance optimization.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIIntelligentCacheService {
    
    // Cache storage for different AI operations
    private final Map<String, CacheEntry> contentGenerationCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry> embeddingCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry> searchCache = new ConcurrentHashMap<>();
    
    // Cache statistics
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong cacheEvictions = new AtomicLong(0);
    
    // Cache configuration
    private static final int MAX_CACHE_SIZE = 1000;
    private static final long CACHE_TTL_MS = 3600000; // 1 hour
    private static final double SIMILARITY_THRESHOLD = 0.95;
    
    /**
     * Cache AI content generation response
     * 
     * @param request the AI generation request
     * @param response the AI generation response
     */
    @CachePut(value = "ai-content-generation", key = "#request.hashCode()")
    public AIGenerationResponse cacheContentGeneration(AIGenerationRequest request, AIGenerationResponse response) {
        log.debug("Caching content generation response for request: {}", request.getPrompt().substring(0, Math.min(50, request.getPrompt().length())));
        
        String cacheKey = generateCacheKey(request);
        CacheEntry entry = CacheEntry.builder()
            .key(cacheKey)
            .value(response)
            .timestamp(LocalDateTime.now())
            .accessCount(1)
            .lastAccessed(LocalDateTime.now())
            .build();
        
        contentGenerationCache.put(cacheKey, entry);
        evictIfNecessary(contentGenerationCache);
        
        return response;
    }
    
    /**
     * Get cached AI content generation response
     * 
     * @param request the AI generation request
     * @return cached response or null if not found
     */
    @Cacheable(value = "ai-content-generation", key = "#request.hashCode()")
    public AIGenerationResponse getCachedContentGeneration(AIGenerationRequest request) {
        log.debug("Checking cache for content generation request: {}", request.getPrompt().substring(0, Math.min(50, request.getPrompt().length())));
        
        String cacheKey = generateCacheKey(request);
        CacheEntry entry = contentGenerationCache.get(cacheKey);
        
        if (entry != null && !isExpired(entry)) {
            entry.setAccessCount(entry.getAccessCount() + 1);
            entry.setLastAccessed(LocalDateTime.now());
            cacheHits.incrementAndGet();
            log.debug("Cache hit for content generation request");
            return (AIGenerationResponse) entry.getValue();
        }
        
        cacheMisses.incrementAndGet();
        log.debug("Cache miss for content generation request");
        return null;
    }
    
    /**
     * Cache AI embedding response
     * 
     * @param request the AI embedding request
     * @param response the AI embedding response
     */
    @CachePut(value = "ai-embeddings", key = "#request.hashCode()")
    public AIEmbeddingResponse cacheEmbedding(AIEmbeddingRequest request, AIEmbeddingResponse response) {
        log.debug("Caching embedding response for text: {}", request.getText().substring(0, Math.min(50, request.getText().length())));
        
        String cacheKey = generateCacheKey(request);
        CacheEntry entry = CacheEntry.builder()
            .key(cacheKey)
            .value(response)
            .timestamp(LocalDateTime.now())
            .accessCount(1)
            .lastAccessed(LocalDateTime.now())
            .build();
        
        embeddingCache.put(cacheKey, entry);
        evictIfNecessary(embeddingCache);
        
        return response;
    }
    
    /**
     * Get cached AI embedding response
     * 
     * @param request the AI embedding request
     * @return cached response or null if not found
     */
    @Cacheable(value = "ai-embeddings", key = "#request.hashCode()")
    public AIEmbeddingResponse getCachedEmbedding(AIEmbeddingRequest request) {
        log.debug("Checking cache for embedding request: {}", request.getText().substring(0, Math.min(50, request.getText().length())));
        
        String cacheKey = generateCacheKey(request);
        CacheEntry entry = embeddingCache.get(cacheKey);
        
        if (entry != null && !isExpired(entry)) {
            entry.setAccessCount(entry.getAccessCount() + 1);
            entry.setLastAccessed(LocalDateTime.now());
            cacheHits.incrementAndGet();
            log.debug("Cache hit for embedding request");
            return (AIEmbeddingResponse) entry.getValue();
        }
        
        cacheMisses.incrementAndGet();
        log.debug("Cache miss for embedding request");
        return null;
    }
    
    /**
     * Cache AI search response
     * 
     * @param request the AI search request
     * @param response the AI search response
     */
    @CachePut(value = "ai-search", key = "#request.hashCode()")
    public AISearchResponse cacheSearch(AISearchRequest request, AISearchResponse response) {
        log.debug("Caching search response for query: {}", request.getQuery());
        
        String cacheKey = generateCacheKey(request);
        CacheEntry entry = CacheEntry.builder()
            .key(cacheKey)
            .value(response)
            .timestamp(LocalDateTime.now())
            .accessCount(1)
            .lastAccessed(LocalDateTime.now())
            .build();
        
        searchCache.put(cacheKey, entry);
        evictIfNecessary(searchCache);
        
        return response;
    }
    
    /**
     * Get cached AI search response
     * 
     * @param request the AI search request
     * @return cached response or null if not found
     */
    @Cacheable(value = "ai-search", key = "#request.hashCode()")
    public AISearchResponse getCachedSearch(AISearchRequest request) {
        log.debug("Checking cache for search request: {}", request.getQuery());
        
        String cacheKey = generateCacheKey(request);
        CacheEntry entry = searchCache.get(cacheKey);
        
        if (entry != null && !isExpired(entry)) {
            entry.setAccessCount(entry.getAccessCount() + 1);
            entry.setLastAccessed(LocalDateTime.now());
            cacheHits.incrementAndGet();
            log.debug("Cache hit for search request");
            return (AISearchResponse) entry.getValue();
        }
        
        cacheMisses.incrementAndGet();
        log.debug("Cache miss for search request");
        return null;
    }
    
    /**
     * Find similar cached content based on semantic similarity
     * 
     * @param request the AI generation request
     * @return similar cached response or null if not found
     */
    public AIGenerationResponse findSimilarCachedContent(AIGenerationRequest request) {
        log.debug("Searching for similar cached content for request: {}", request.getPrompt().substring(0, Math.min(50, request.getPrompt().length())));
        
        String requestPrompt = request.getPrompt().toLowerCase();
        double bestSimilarity = 0.0;
        AIGenerationResponse bestResponse = null;
        
        for (CacheEntry entry : contentGenerationCache.values()) {
            if (isExpired(entry)) continue;
            
            AIGenerationRequest cachedRequest = (AIGenerationRequest) entry.getMetadata().get("request");
            if (cachedRequest == null) continue;
            
            String cachedPrompt = cachedRequest.getPrompt().toLowerCase();
            double similarity = calculateTextSimilarity(requestPrompt, cachedPrompt);
            
            if (similarity > SIMILARITY_THRESHOLD && similarity > bestSimilarity) {
                bestSimilarity = similarity;
                bestResponse = (AIGenerationResponse) entry.getValue();
            }
        }
        
        if (bestResponse != null) {
            cacheHits.incrementAndGet();
            log.debug("Found similar cached content with similarity: {}", bestSimilarity);
        } else {
            cacheMisses.incrementAndGet();
            log.debug("No similar cached content found");
        }
        
        return bestResponse;
    }
    
    /**
     * Clear all caches
     */
    @CacheEvict(value = {"ai-content-generation", "ai-embeddings", "ai-search"}, allEntries = true)
    public void clearAllCaches() {
        log.info("Clearing all AI caches");
        
        contentGenerationCache.clear();
        embeddingCache.clear();
        searchCache.clear();
        
        cacheHits.set(0);
        cacheMisses.set(0);
        cacheEvictions.set(0);
        
        log.info("All AI caches cleared");
    }
    
    /**
     * Get cache statistics
     * 
     * @return cache statistics
     */
    public Map<String, Object> getCacheStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        long totalRequests = cacheHits.get() + cacheMisses.get();
        double hitRate = totalRequests > 0 ? (double) cacheHits.get() / totalRequests : 0.0;
        
        stats.put("cacheHits", cacheHits.get());
        stats.put("cacheMisses", cacheMisses.get());
        stats.put("cacheEvictions", cacheEvictions.get());
        stats.put("hitRate", hitRate);
        stats.put("contentGenerationCacheSize", contentGenerationCache.size());
        stats.put("embeddingCacheSize", embeddingCache.size());
        stats.put("searchCacheSize", searchCache.size());
        stats.put("totalCacheSize", contentGenerationCache.size() + embeddingCache.size() + searchCache.size());
        
        return stats;
    }
    
    /**
     * Generate cache key for request
     */
    private String generateCacheKey(Object request) {
        return String.valueOf(request.hashCode());
    }
    
    /**
     * Check if cache entry is expired
     */
    private boolean isExpired(CacheEntry entry) {
        return entry.getTimestamp().plusNanos(CACHE_TTL_MS * 1_000_000).isBefore(LocalDateTime.now());
    }
    
    /**
     * Evict entries if cache is full
     */
    private void evictIfNecessary(Map<String, CacheEntry> cache) {
        if (cache.size() > MAX_CACHE_SIZE) {
            // Remove least recently used entries
            cache.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.comparing(CacheEntry::getLastAccessed)))
                .limit(cache.size() - MAX_CACHE_SIZE + 10) // Remove 10 extra entries
                .forEach(entry -> {
                    cache.remove(entry.getKey());
                    cacheEvictions.incrementAndGet();
                });
        }
    }
    
    /**
     * Calculate text similarity using simple word overlap
     */
    private double calculateTextSimilarity(String text1, String text2) {
        Set<String> words1 = new HashSet<>(Arrays.asList(text1.split("\\s+")));
        Set<String> words2 = new HashSet<>(Arrays.asList(text2.split("\\s+")));
        
        Set<String> intersection = new HashSet<>(words1);
        intersection.retainAll(words2);
        
        Set<String> union = new HashSet<>(words1);
        union.addAll(words2);
        
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }
    
    /**
     * Cache entry
     */
    @lombok.Data
    @lombok.Builder
    public static class CacheEntry {
        private String key;
        private Object value;
        private LocalDateTime timestamp;
        private int accessCount;
        private LocalDateTime lastAccessed;
        private Map<String, Object> metadata;
    }
}