package com.ai.infrastructure.service;

import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI Performance Service
 * 
 * Provides performance optimizations for AI operations including:
 * - Caching for embedding generation
 * - Batch processing
 * - Async operations
 * - Connection pooling
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIPerformanceService {
    
    private final AIEmbeddingService embeddingService;
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    private final ConcurrentHashMap<String, List<Double>> embeddingCache = new ConcurrentHashMap<>();
    
    /**
     * Generate embedding with caching
     */
    @Cacheable(value = "embeddings", key = "#request.text.hashCode()")
    public List<Double> generateEmbeddingWithCache(AIEmbeddingRequest request) {
        log.debug("Generating embedding for text: {}", request.getText());
        
        // Check cache first
        String cacheKey = request.getText().hashCode() + "_" + request.getModel();
        List<Double> cachedEmbedding = embeddingCache.get(cacheKey);
        if (cachedEmbedding != null) {
            log.debug("Cache hit for embedding");
            return cachedEmbedding;
        }
        
        // Generate new embedding
        AIEmbeddingResponse response = embeddingService.generateEmbedding(request);
        List<Double> embedding = response.getEmbedding();
        
        // Cache the result
        embeddingCache.put(cacheKey, embedding);
        log.debug("Generated and cached embedding with {} dimensions", embedding.size());
        
        return embedding;
    }
    
    /**
     * Generate embeddings asynchronously
     */
    @Async
    public CompletableFuture<List<Double>> generateEmbeddingAsync(AIEmbeddingRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            log.debug("Generating embedding asynchronously for text: {}", request.getText());
            AIEmbeddingResponse response = embeddingService.generateEmbedding(request);
            return response.getEmbedding();
        }, executorService);
    }
    
    /**
     * Batch generate embeddings
     */
    public List<List<Double>> generateEmbeddingsBatch(List<AIEmbeddingRequest> requests) {
        log.debug("Generating {} embeddings in batch", requests.size());
        
        return requests.parallelStream()
            .map(this::generateEmbeddingWithCache)
            .toList();
    }
    
    /**
     * Generate embeddings with performance metrics
     */
    public AIPerformanceResult generateEmbeddingWithMetrics(AIEmbeddingRequest request) {
        long startTime = System.currentTimeMillis();
        
        try {
            List<Double> embedding = generateEmbeddingWithCache(request);
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            return AIPerformanceResult.builder()
                .success(true)
                .embedding(embedding)
                .durationMs(duration)
                .dimensions(embedding.size())
                .cached(embeddingCache.containsKey(request.getText().hashCode() + "_" + request.getModel()))
                .build();
                
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            log.error("Error generating embedding", e);
            
            return AIPerformanceResult.builder()
                .success(false)
                .error(e.getMessage())
                .durationMs(duration)
                .build();
        }
    }
    
    /**
     * Clear embedding cache
     */
    public void clearCache() {
        log.info("Clearing embedding cache");
        embeddingCache.clear();
    }
    
    /**
     * Get cache statistics
     */
    public CacheStatistics getCacheStatistics() {
        return CacheStatistics.builder()
            .cacheSize(embeddingCache.size())
            .memoryUsage(embeddingCache.size() * 1536 * 8) // Approximate memory usage
            .build();
    }
    
    /**
     * Performance Result DTO
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AIPerformanceResult {
        private boolean success;
        private List<Double> embedding;
        private long durationMs;
        private int dimensions;
        private boolean cached;
        private String error;
    }
    
    /**
     * Cache Statistics DTO
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CacheStatistics {
        private int cacheSize;
        private long memoryUsage;
    }
}
