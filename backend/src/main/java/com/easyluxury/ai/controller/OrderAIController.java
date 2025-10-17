package com.easyluxury.ai.controller;

import com.easyluxury.ai.dto.*;
import com.easyluxury.ai.facade.OrderAIFacade;
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
 * OrderAIController
 * 
 * REST controller for order-specific AI operations including analysis,
 * pattern recognition, and fraud detection.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/orders")
@RequiredArgsConstructor
@Tag(name = "Order AI", description = "AI-powered order operations")
public class OrderAIController {
    
    private final OrderAIFacade orderAIFacade;
    
    /**
     * Analyze order patterns
     */
    @PostMapping("/patterns")
    @Operation(summary = "Analyze order patterns", description = "Analyze order patterns and trends using AI")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Pattern analysis completed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid pattern request"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<OrderPatternResponse> analyzePatterns(
            @Valid @RequestBody OrderPatternRequest request) {
        log.info("Order pattern analysis request for user: {} over {} days", 
            request.getUserId(), request.getDays());
        
        OrderPatternResponse response = orderAIFacade.analyzeOrderPatterns(request);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Analyze specific order
     */
    @PostMapping("/analyze")
    @Operation(summary = "Analyze order", description = "Analyze specific order for fraud and risk assessment")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Order analysis completed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid analysis request"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<OrderAIAnalysisResponse> analyzeOrder(
            @Valid @RequestBody OrderAIAnalysisRequest request) {
        log.info("Order analysis request for order: {}", request.getOrderId());
        
        OrderAIAnalysisResponse response = orderAIFacade.analyzeOrder(request);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Generate business insights
     */
    @PostMapping("/insights")
    @Operation(summary = "Generate business insights", description = "Generate AI-powered business insights from order data")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Business insights generated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid insights request"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<OrderAIInsightsResponse> generateBusinessInsights(
            @Valid @RequestBody OrderAIInsightsRequest request) {
        log.info("Business insights request for last {} days", request.getDays());
        
        OrderAIInsightsResponse response = orderAIFacade.generateBusinessInsights(request);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Detect fraud for specific order
     */
    @GetMapping("/{orderId}/fraud")
    @Operation(summary = "Detect fraud", description = "Detect fraudulent patterns in specific order")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Fraud detection completed"),
        @ApiResponse(responseCode = "404", description = "Order not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<String> detectFraud(
            @Parameter(description = "Order ID") @PathVariable UUID orderId) {
        log.info("Fraud detection request for order: {}", orderId);
        
        String fraudAnalysis = orderAIFacade.detectFraud(orderId);
        
        return ResponseEntity.ok(fraudAnalysis);
    }
    
    /**
     * Get order history with AI analysis
     */
    @GetMapping("/{userId}/history")
    @Operation(summary = "Get order history", description = "Get order history with AI analysis")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Order history retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<List<OrderAIAnalysisResponse>> getOrderHistory(
            @Parameter(description = "User ID") @PathVariable UUID userId,
            @Parameter(description = "Maximum number of orders to return") @RequestParam(defaultValue = "50") int limit) {
        log.info("Order history request for user: {} with limit: {}", userId, limit);
        
        List<OrderAIAnalysisResponse> history = orderAIFacade.getOrderHistory(userId, limit);
        
        return ResponseEntity.ok(history);
    }
    
    /**
     * Analyze seasonal patterns
     */
    @GetMapping("/seasonal-patterns")
    @Operation(summary = "Analyze seasonal patterns", description = "Analyze seasonal patterns in order data")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Seasonal analysis completed"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<String> analyzeSeasonalPatterns(
            @Parameter(description = "Number of months to analyze") @RequestParam(defaultValue = "6") int months) {
        log.info("Seasonal pattern analysis request for last {} months", months);
        
        String seasonalPatterns = orderAIFacade.analyzeSeasonalPatterns(months);
        
        return ResponseEntity.ok(seasonalPatterns);
    }
}