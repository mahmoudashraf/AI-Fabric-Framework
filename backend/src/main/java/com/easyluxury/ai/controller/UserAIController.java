package com.easyluxury.ai.controller;

import com.ai.infrastructure.dto.BehaviorAnalysisResult;
import com.ai.infrastructure.dto.BehaviorResponse;
import com.ai.infrastructure.dto.AIProfileResponse;
import com.easyluxury.ai.adapter.UserAIAdapter;
import com.easyluxury.ai.dto.*;
import com.easyluxury.entity.User;
import com.easyluxury.entity.UserBehavior;
import com.easyluxury.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * UserAIController
 * 
 * REST controller for user-specific AI operations including behavioral tracking,
 * insights, and recommendations.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/users")
@RequiredArgsConstructor
@Tag(name = "User AI", description = "AI-powered user operations")
public class UserAIController {
    
    private final UserAIAdapter userAIAdapter;
    private final UserRepository userRepository;
    
    /**
     * Track user behavior
     */
    @PostMapping("/behavior/track")
    @Operation(summary = "Track user behavior", description = "Track user behavior for AI analysis")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Behavior tracked successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid behavior request"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<BehaviorResponse> trackBehavior(
            @Valid @RequestBody UserBehaviorRequest request) {
        log.info("User behavior tracking request for user: {} - {}", 
            request.getUserId(), request.getBehaviorType());
        
        try {
            User user = userRepository.findById(UUID.fromString(request.getUserId()))
                .orElseThrow(() -> new RuntimeException("User not found: " + request.getUserId()));
            
            UserBehavior behavior = UserBehavior.builder()
                .userId(UUID.fromString(request.getUserId()))
                .behaviorType(UserBehavior.BehaviorType.valueOf(request.getBehaviorType()))
                .entityType(request.getEntityType())
                .entityId(request.getEntityId())
                .action(request.getAction())
                .context(request.getContext())
                .metadata(request.getMetadata())
                .build();
            
            BehaviorResponse response = userAIAdapter.trackUserBehavior(user, behavior);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error tracking user behavior: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get user AI insights
     */
    @PostMapping("/insights")
    @Operation(summary = "Get user insights", description = "Get comprehensive AI insights for a user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Insights retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid insights request"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<BehaviorAnalysisResult> getUserInsights(
            @Valid @RequestBody UserAIInsightsRequest request) {
        log.info("User AI insights request for user: {}", request.getUserId());
        
        try {
            User user = userRepository.findById(UUID.fromString(request.getUserId()))
                .orElseThrow(() -> new RuntimeException("User not found: " + request.getUserId()));
            
            BehaviorAnalysisResult response = userAIAdapter.analyzeUserBehaviors(user);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting user insights: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Generate user recommendations
     */
    @PostMapping("/recommendations")
    @Operation(summary = "Generate user recommendations", description = "Generate AI-powered recommendations for a user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Recommendations generated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid recommendation request"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<String> generateRecommendations(
            @Valid @RequestBody UserAIRecommendationRequest request) {
        log.info("User AI recommendations request for user: {}", request.getUserId());
        
        try {
            User user = userRepository.findById(UUID.fromString(request.getUserId()))
                .orElseThrow(() -> new RuntimeException("User not found: " + request.getUserId()));
            
            BehaviorAnalysisResult analysis = userAIAdapter.analyzeUserBehaviors(user);
            String recommendations = analysis.getRecommendations() != null && !analysis.getRecommendations().isEmpty() 
                ? String.join(", ", analysis.getRecommendations())
                : "No specific recommendations available at this time.";
            
            return ResponseEntity.ok(recommendations);
        } catch (Exception e) {
            log.error("Error generating recommendations: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get user behaviors
     */
    @GetMapping("/{userId}/behaviors")
    @Operation(summary = "Get user behaviors", description = "Get all behaviors for a user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Behaviors retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<List<BehaviorResponse>> getUserBehaviors(
            @Parameter(description = "User ID") @PathVariable UUID userId) {
        log.info("User behaviors request for user: {}", userId);
        
        try {
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
            
            List<BehaviorResponse> response = userAIAdapter.getUserBehaviors(user);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting user behaviors: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Analyze user behavior patterns
     */
    @GetMapping("/{userId}/patterns")
    @Operation(summary = "Analyze behavior patterns", description = "Analyze user behavior patterns")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Pattern analysis completed"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<BehaviorAnalysisResult> analyzeBehaviorPatterns(
            @Parameter(description = "User ID") @PathVariable UUID userId,
            @Parameter(description = "Number of days to analyze") @RequestParam(defaultValue = "30") int days) {
        log.info("User behavior pattern analysis request for user: {} over {} days", userId, days);
        
        try {
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
            
            BehaviorAnalysisResult response = userAIAdapter.analyzeUserBehaviors(user);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error analyzing behavior patterns: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Detect behavioral anomalies
     */
    @GetMapping("/{userId}/anomalies")
    @Operation(summary = "Detect behavioral anomalies", description = "Detect anomalies in user behavior")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Anomaly detection completed"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<String> detectBehavioralAnomalies(
            @Parameter(description = "User ID") @PathVariable UUID userId) {
        log.info("User behavioral anomaly detection request for user: {}", userId);
        
        String anomalies = userAIFacade.detectBehavioralAnomalies(userId);
        
        return ResponseEntity.ok(anomalies);
    }
    
    /**
     * Generate behavioral insights
     */
    @GetMapping("/{userId}/insights")
    @Operation(summary = "Generate behavioral insights", description = "Generate AI-powered behavioral insights")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Insights generated successfully"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<String> generateBehavioralInsights(
            @Parameter(description = "User ID") @PathVariable UUID userId) {
        log.info("User behavioral insights request for user: {}", userId);
        
        String insights = userAIFacade.generateBehavioralInsights(userId);
        
        return ResponseEntity.ok(insights);
    }
    
    /**
     * Get user behavior history
     */
    @GetMapping("/{userId}/behavior-history")
    @Operation(summary = "Get behavior history", description = "Get user behavior history with AI analysis")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Behavior history retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<List<UserBehaviorResponse>> getBehaviorHistory(
            @Parameter(description = "User ID") @PathVariable UUID userId,
            @Parameter(description = "Maximum number of behaviors to return") @RequestParam(defaultValue = "50") int limit) {
        log.info("User behavior history request for user: {} with limit: {}", userId, limit);
        
        List<UserBehaviorResponse> history = userAIFacade.getUserBehaviorHistory(userId, limit);
        
        return ResponseEntity.ok(history);
    }
}