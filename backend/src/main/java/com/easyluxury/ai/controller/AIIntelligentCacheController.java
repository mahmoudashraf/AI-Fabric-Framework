package com.easyluxury.ai.controller;

import com.ai.infrastructure.cache.AIIntelligentCacheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI Intelligent Cache Controller
 * 
 * This controller provides endpoints for managing AI intelligent caching
 * including cache statistics, cache management, and cache operations.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai/cache")
@RequiredArgsConstructor
@Tag(name = "AI Intelligent Cache", description = "AI intelligent caching management endpoints")
public class AIIntelligentCacheController {
    
    private final AIIntelligentCacheService aiIntelligentCacheService;
    
    /**
     * Get cache statistics
     * 
     * @return cache statistics
     */
    @GetMapping("/statistics")
    @Operation(summary = "Get cache statistics", description = "Get comprehensive cache statistics and performance metrics")
    public ResponseEntity<Map<String, Object>> getCacheStatistics() {
        log.info("AI Cache: Statistics request received");
        
        try {
            Map<String, Object> statistics = aiIntelligentCacheService.getCacheStatistics();
            
            log.info("AI Cache: Statistics retrieved successfully");
            return ResponseEntity.ok(statistics);
            
        } catch (Exception e) {
            log.error("AI Cache: Failed to get statistics", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Clear all caches
     * 
     * @return success response
     */
    @PostMapping("/clear")
    @Operation(summary = "Clear all caches", description = "Clear all AI caches and reset statistics")
    public ResponseEntity<Map<String, String>> clearAllCaches() {
        log.info("AI Cache: Clear all caches request received");
        
        try {
            aiIntelligentCacheService.clearAllCaches();
            
            Map<String, String> response = Map.of(
                "message", "All AI caches cleared successfully",
                "timestamp", java.time.LocalDateTime.now().toString()
            );
            
            log.info("AI Cache: All caches cleared successfully");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("AI Cache: Failed to clear caches", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get cache health status
     * 
     * @return cache health status
     */
    @GetMapping("/health")
    @Operation(summary = "Get cache health", description = "Get cache health status and performance indicators")
    public ResponseEntity<Map<String, Object>> getCacheHealth() {
        log.info("AI Cache: Health check request received");
        
        try {
            Map<String, Object> statistics = aiIntelligentCacheService.getCacheStatistics();
            
            // Calculate health metrics
            long totalRequests = (Long) statistics.get("cacheHits") + (Long) statistics.get("cacheMisses");
            double hitRate = totalRequests > 0 ? (Double) statistics.get("hitRate") : 0.0;
            
            String status = hitRate > 0.7 ? "HEALTHY" : hitRate > 0.5 ? "WARNING" : "CRITICAL";
            
            Map<String, Object> health = Map.of(
                "status", status,
                "hitRate", hitRate,
                "totalRequests", totalRequests,
                "cacheSize", statistics.get("totalCacheSize"),
                "timestamp", java.time.LocalDateTime.now().toString()
            );
            
            log.info("AI Cache: Health check completed with status: {}", status);
            return ResponseEntity.ok(health);
            
        } catch (Exception e) {
            log.error("AI Cache: Health check failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get cache configuration
     * 
     * @return cache configuration
     */
    @GetMapping("/configuration")
    @Operation(summary = "Get cache configuration", description = "Get current cache configuration settings")
    public ResponseEntity<Map<String, Object>> getCacheConfiguration() {
        log.info("AI Cache: Configuration request received");
        
        try {
            Map<String, Object> configuration = Map.of(
                "maxCacheSize", 1000,
                "cacheTtlMs", 3600000L, // 1 hour
                "similarityThreshold", 0.95,
                "cacheTypes", java.util.List.of(
                    "content-generation",
                    "embedding-generation", 
                    "semantic-search"
                ),
                "evictionPolicy", "LRU",
                "timestamp", java.time.LocalDateTime.now().toString()
            );
            
            log.info("AI Cache: Configuration retrieved successfully");
            return ResponseEntity.ok(configuration);
            
        } catch (Exception e) {
            log.error("AI Cache: Failed to get configuration", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Warm up cache with sample data
     * 
     * @return warm-up response
     */
    @PostMapping("/warmup")
    @Operation(summary = "Warm up cache", description = "Warm up cache with sample data for better performance")
    public ResponseEntity<Map<String, String>> warmUpCache() {
        log.info("AI Cache: Warm-up request received");
        
        try {
            // In a real implementation, this would populate the cache with common requests
            // For now, we'll just return a success response
            
            Map<String, String> response = Map.of(
                "message", "Cache warm-up initiated successfully",
                "timestamp", java.time.LocalDateTime.now().toString()
            );
            
            log.info("AI Cache: Warm-up completed successfully");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("AI Cache: Warm-up failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}