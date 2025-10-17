package com.easyluxury.ai.controller;

import com.easyluxury.ai.dto.*;
import com.easyluxury.ai.facade.ProductAIFacade;
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

import java.util.UUID;

/**
 * ProductAIController
 * 
 * REST controller for product-specific AI operations including search,
 * recommendations, and content generation.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/products")
@RequiredArgsConstructor
@Tag(name = "Product AI", description = "AI-powered product operations")
public class ProductAIController {
    
    private final ProductAIFacade productAIFacade;
    
    /**
     * Search products using AI-powered semantic search
     */
    @PostMapping("/search")
    @Operation(summary = "Search products with AI", description = "Perform semantic search on products using AI")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Search completed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid search request"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ProductAISearchResponse> searchProducts(
            @Valid @RequestBody ProductAISearchRequest request) {
        log.info("AI product search request: {}", request.getQuery());
        
        ProductAISearchResponse response = productAIFacade.searchProducts(request);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Generate product recommendations
     */
    @PostMapping("/recommendations")
    @Operation(summary = "Generate product recommendations", description = "Generate AI-powered product recommendations")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Recommendations generated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid recommendation request"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ProductAIRecommendationResponse> generateRecommendations(
            @Valid @RequestBody ProductAIRecommendationRequest request) {
        log.info("AI product recommendations request for user: {}", request.getUserId());
        
        ProductAIRecommendationResponse response = productAIFacade.generateRecommendations(request);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Generate AI content for products
     */
    @PostMapping("/generate-content")
    @Operation(summary = "Generate AI content", description = "Generate AI content for products (descriptions, tags, etc.)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Content generated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid generation request"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ProductAIGenerationResponse> generateContent(
            @Valid @RequestBody ProductAIGenerationRequest request) {
        log.info("AI content generation request for product: {} - {}", 
            request.getProductId(), request.getContentType());
        
        ProductAIGenerationResponse response = productAIFacade.generateProductContent(request);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Analyze product performance
     */
    @GetMapping("/{productId}/performance")
    @Operation(summary = "Analyze product performance", description = "Get AI-powered product performance analysis")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Performance analysis completed"),
        @ApiResponse(responseCode = "404", description = "Product not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<String> analyzeProductPerformance(
            @Parameter(description = "Product ID") @PathVariable UUID productId) {
        log.info("AI product performance analysis request for product: {}", productId);
        
        String analysis = productAIFacade.analyzeProductPerformance(productId);
        
        return ResponseEntity.ok(analysis);
    }
    
    /**
     * Get product AI insights
     */
    @GetMapping("/{productId}/insights")
    @Operation(summary = "Get product insights", description = "Get AI-generated product insights")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Insights retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Product not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<String> getProductInsights(
            @Parameter(description = "Product ID") @PathVariable UUID productId) {
        log.info("AI product insights request for product: {}", productId);
        
        String insights = productAIFacade.getProductInsights(productId);
        
        return ResponseEntity.ok(insights);
    }
}