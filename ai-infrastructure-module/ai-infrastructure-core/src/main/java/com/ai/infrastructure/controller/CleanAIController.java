package com.ai.infrastructure.controller;

import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.service.CleanAICapabilityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Clean AI Controller
 * 
 * REST API endpoints for vector database-based AI operations.
 * No backward compatibility with old storage methods.
 * 
 * @author AI Infrastructure Team
 * @version 2.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v2/ai")
@RequiredArgsConstructor
public class CleanAIController {
    
    private final CleanAICapabilityService aiCapabilityService;
    
    /**
     * Perform semantic search using vector similarity
     * 
     * @param request The search request
     * @return Search results with similarity scores
     */
    @PostMapping("/search")
    public ResponseEntity<AISearchResponse> search(@RequestBody AISearchRequest request) {
        try {
            log.debug("Vector search request: query='{}', entityType='{}'", 
                     request.getQuery(), request.getEntityType());
            
            AISearchResponse response = aiCapabilityService.search(request);
            
            log.debug("Vector search completed: {} results in {}ms", 
                     response.getTotalResults(), response.getProcessingTimeMs());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error performing vector search", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Search for similar entities by text query
     * 
     * @param query The search query text
     * @param entityType Optional entity type filter
     * @param limit Maximum number of results (default: 10)
     * @param threshold Minimum similarity threshold (default: 0.7)
     * @return Search results
     */
    @GetMapping("/search")
    public ResponseEntity<AISearchResponse> searchByText(
            @RequestParam String query,
            @RequestParam(required = false) String entityType,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "0.7") double threshold) {
        
        try {
            log.debug("Text search: query='{}', entityType='{}', limit={}, threshold={}", 
                     query, entityType, limit, threshold);
            
            AISearchResponse response = aiCapabilityService.searchSimilarEntities(
                query, entityType, limit, threshold);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error performing text search", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get vector database statistics
     * 
     * @return Database statistics and performance metrics
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        try {
            Map<String, Object> stats = aiCapabilityService.getStatistics();
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            log.error("Error getting vector database statistics", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Check AI system health
     * 
     * @return Health status
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealth() {
        try {
            boolean healthy = aiCapabilityService.isHealthy();
            String databaseType = aiCapabilityService.getDatabaseType();
            
            Map<String, Object> health = Map.of(
                "status", healthy ? "UP" : "DOWN",
                "vectorDatabase", Map.of(
                    "type", databaseType,
                    "healthy", healthy
                )
            );
            
            return ResponseEntity.ok(health);
            
        } catch (Exception e) {
            log.error("Error checking AI system health", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Clear all vectors (admin operation)
     * 
     * @return Operation result
     */
    @DeleteMapping("/vectors")
    public ResponseEntity<Map<String, Object>> clearAllVectors() {
        try {
            log.warn("Admin operation: Clearing all vectors");
            
            Map<String, Object> statsBefore = aiCapabilityService.getStatistics();
            aiCapabilityService.clearAllVectors();
            
            Map<String, Object> result = Map.of(
                "message", "All vectors cleared successfully",
                "vectorsCleared", statsBefore.getOrDefault("vectorCount", 0)
            );
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error clearing vectors", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}