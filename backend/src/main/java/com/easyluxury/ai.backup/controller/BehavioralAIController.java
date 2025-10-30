package com.easyluxury.ai.controller;

import com.easyluxury.ai.service.BehaviorTrackingService;
import com.easyluxury.ai.service.UIAdaptationService;
import com.easyluxury.ai.service.RecommendationEngine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BehavioralAIController
 * 
 * REST controller for behavioral AI operations including tracking,
 * UI adaptation, and recommendation services.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/behavioral")
@RequiredArgsConstructor
@Tag(name = "Behavioral AI", description = "AI-powered behavioral tracking and adaptation")
public class BehavioralAIController {
    
    private final BehaviorTrackingService behaviorTrackingService;
    private final UIAdaptationService uiAdaptationService;
    private final RecommendationEngine recommendationEngine;
    
    /**
     * Track user behavior
     */
    @PostMapping("/track")
    @Operation(summary = "Track user behavior", description = "Track comprehensive user behavior with AI analysis")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Behavior tracked successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid behavior data"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> trackBehavior(
            @RequestBody Map<String, Object> behaviorData) {
        log.info("Tracking behavior for user: {}", behaviorData.get("userId"));
        
        // Extract behavior data
        UUID userId = UUID.fromString((String) behaviorData.get("userId"));
        String behaviorType = (String) behaviorData.get("behaviorType");
        String entityType = (String) behaviorData.get("entityType");
        String entityId = (String) behaviorData.get("entityId");
        String action = (String) behaviorData.get("action");
        String context = (String) behaviorData.get("context");
        Map<String, Object> metadata = (Map<String, Object>) behaviorData.get("metadata");
        String sessionId = (String) behaviorData.get("sessionId");
        Map<String, Object> deviceInfo = (Map<String, Object>) behaviorData.get("deviceInfo");
        Map<String, Object> locationInfo = (Map<String, Object>) behaviorData.get("locationInfo");
        
        // Track behavior
        var behavior = behaviorTrackingService.trackBehavior(
            userId, behaviorType, entityType, entityId, action, context,
            metadata, sessionId, deviceInfo, locationInfo
        );
        
        // Return response
        Map<String, Object> response = Map.of(
            "success", true,
            "behaviorId", behavior.getId(),
            "behaviorScore", behavior.getBehaviorScore(),
            "significanceScore", behavior.getSignificanceScore()
        );
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get personalized UI configuration
     */
    @GetMapping("/ui-config/{userId}")
    @Operation(summary = "Get personalized UI config", description = "Get AI-generated personalized UI configuration")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "UI config retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> getPersonalizedUIConfig(
            @Parameter(description = "User ID") @PathVariable UUID userId) {
        log.info("Getting personalized UI config for user: {}", userId);
        
        Map<String, Object> uiConfig = uiAdaptationService.generatePersonalizedUIConfig(userId);
        
        return ResponseEntity.ok(uiConfig);
    }
    
    /**
     * Adapt UI component
     */
    @GetMapping("/ui-adapt/{userId}/{componentType}")
    @Operation(summary = "Adapt UI component", description = "Get adapted UI component configuration")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "UI component adapted successfully"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> adaptUIComponent(
            @Parameter(description = "User ID") @PathVariable UUID userId,
            @Parameter(description = "Component type") @PathVariable String componentType) {
        log.info("Adapting UI component {} for user: {}", componentType, userId);
        
        Map<String, Object> adaptations = uiAdaptationService.adaptUIComponent(userId, componentType);
        
        return ResponseEntity.ok(adaptations);
    }
    
    /**
     * Generate content recommendations
     */
    @GetMapping("/recommendations/content/{userId}")
    @Operation(summary = "Generate content recommendations", description = "Get AI-powered content recommendations")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Recommendations generated successfully"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<List<Map<String, Object>>> generateContentRecommendations(
            @Parameter(description = "User ID") @PathVariable UUID userId,
            @Parameter(description = "Content type") @RequestParam String contentType,
            @Parameter(description = "Limit") @RequestParam(defaultValue = "10") int limit) {
        log.info("Generating content recommendations for user: {} - {}", userId, contentType);
        
        List<Map<String, Object>> recommendations = uiAdaptationService.generateContentRecommendations(userId, contentType, limit);
        
        return ResponseEntity.ok(recommendations);
    }
    
    /**
     * Generate product recommendations
     */
    @GetMapping("/recommendations/products/{userId}")
    @Operation(summary = "Generate product recommendations", description = "Get AI-powered product recommendations")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Product recommendations generated successfully"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<List<Map<String, Object>>> generateProductRecommendations(
            @Parameter(description = "User ID") @PathVariable UUID userId,
            @Parameter(description = "Limit") @RequestParam(defaultValue = "10") int limit,
            @Parameter(description = "Categories") @RequestParam(required = false) List<String> categories,
            @Parameter(description = "Price range") @RequestParam(required = false) String priceRange) {
        log.info("Generating product recommendations for user: {}", userId);
        
        var products = recommendationEngine.generateProductRecommendations(userId, limit, categories, priceRange);
        
        // Convert products to response format
        List<Map<String, Object>> recommendations = products.stream()
            .map(product -> {
                Map<String, Object> rec = new java.util.HashMap<>();
                rec.put("id", product.getId());
                rec.put("name", product.getName());
                rec.put("description", product.getDescription());
                rec.put("price", product.getPrice());
                rec.put("category", product.getCategory());
                return rec;
            })
            .toList();
        
        return ResponseEntity.ok(recommendations);
    }
    
    /**
     * Get behavior analytics
     */
    @GetMapping("/analytics/{userId}")
    @Operation(summary = "Get behavior analytics", description = "Get comprehensive behavior analytics for a user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Analytics retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> getBehaviorAnalytics(
            @Parameter(description = "User ID") @PathVariable UUID userId,
            @Parameter(description = "Days to analyze") @RequestParam(defaultValue = "30") int days) {
        log.info("Getting behavior analytics for user: {} over {} days", userId, days);
        
        Map<String, Object> analytics = behaviorTrackingService.getBehaviorAnalytics(userId, days);
        
        return ResponseEntity.ok(analytics);
    }
    
    /**
     * Analyze behavior patterns
     */
    @GetMapping("/patterns/{userId}")
    @Operation(summary = "Analyze behavior patterns", description = "Analyze user behavior patterns with AI")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Pattern analysis completed"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<String> analyzeBehaviorPatterns(
            @Parameter(description = "User ID") @PathVariable UUID userId,
            @Parameter(description = "Days to analyze") @RequestParam(defaultValue = "30") int days) {
        log.info("Analyzing behavior patterns for user: {} over {} days", userId, days);
        
        String patterns = behaviorTrackingService.analyzeBehaviorPatterns(userId, days);
        
        return ResponseEntity.ok(patterns);
    }
    
    /**
     * Detect behavioral anomalies
     */
    @GetMapping("/anomalies/{userId}")
    @Operation(summary = "Detect behavioral anomalies", description = "Detect anomalies in user behavior")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Anomaly detection completed"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<String> detectBehavioralAnomalies(
            @Parameter(description = "User ID") @PathVariable UUID userId) {
        log.info("Detecting behavioral anomalies for user: {}", userId);
        
        String anomalies = behaviorTrackingService.detectBehavioralAnomalies(userId);
        
        return ResponseEntity.ok(anomalies);
    }
}