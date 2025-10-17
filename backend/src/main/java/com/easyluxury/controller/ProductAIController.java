package com.easyluxury.controller;

import com.easyluxury.facade.ProductAIFacade;
import com.easyluxury.entity.Product;
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

/**
 * Product AI Controller
 * 
 * REST endpoints for AI-powered product operations including search,
 * recommendations, content generation, and insights.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/products/ai")
@RequiredArgsConstructor
@Tag(name = "Product AI Operations", description = "AI-powered product operations including search, recommendations, and content generation")
public class ProductAIController {
    
    private final ProductAIFacade productAIFacade;
    
    /**
     * Search products using AI
     * 
     * @param query the search query
     * @param limit maximum number of results (default: 10)
     * @return list of matching products
     */
    @GetMapping("/search")
    @Operation(
        summary = "Search products using AI",
        description = "Perform semantic search on products using AI-powered natural language processing"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Search completed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid search parameters"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<List<Product>> searchProducts(
            @Parameter(description = "Search query", required = true)
            @RequestParam String query,
            @Parameter(description = "Maximum number of results")
            @RequestParam(defaultValue = "10") int limit) {
        
        log.info("AI product search requested: query={}, limit={}", query, limit);
        
        try {
            List<Product> results = productAIFacade.searchProducts(query, limit);
            return ResponseEntity.ok(results);
            
        } catch (Exception e) {
            log.error("Error in AI product search", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get product recommendations
     * 
     * @param productId the product ID to get recommendations for
     * @param limit maximum number of recommendations (default: 5)
     * @return list of recommended products
     */
    @GetMapping("/{productId}/recommendations")
    @Operation(
        summary = "Get product recommendations",
        description = "Get AI-powered product recommendations based on the specified product"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Recommendations generated successfully"),
        @ApiResponse(responseCode = "404", description = "Product not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<List<Product>> getProductRecommendations(
            @Parameter(description = "Product ID", required = true)
            @PathVariable String productId,
            @Parameter(description = "Maximum number of recommendations")
            @RequestParam(defaultValue = "5") int limit) {
        
        log.info("Product recommendations requested: productId={}, limit={}", productId, limit);
        
        try {
            List<Product> recommendations = productAIFacade.getProductRecommendations(productId, limit);
            return ResponseEntity.ok(recommendations);
            
        } catch (Exception e) {
            log.error("Error getting product recommendations", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get AI insights for a product
     * 
     * @param productId the product ID
     * @return AI insights
     */
    @GetMapping("/{productId}/insights")
    @Operation(
        summary = "Get product AI insights",
        description = "Get AI-powered insights and analysis for a product"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Insights generated successfully"),
        @ApiResponse(responseCode = "404", description = "Product not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> getProductInsights(
            @Parameter(description = "Product ID", required = true)
            @PathVariable String productId) {
        
        log.info("Product insights requested: productId={}", productId);
        
        try {
            Map<String, Object> insights = productAIFacade.getProductInsights(productId);
            return ResponseEntity.ok(insights);
            
        } catch (Exception e) {
            log.error("Error getting product insights", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Generate AI content for a product
     * 
     * @param productId the product ID
     * @param contentType the type of content to generate (description, categories, tags)
     * @return generated content
     */
    @PostMapping("/{productId}/generate-content")
    @Operation(
        summary = "Generate AI content for product",
        description = "Generate AI-powered content for a product (description, categories, or tags)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Content generated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid content type"),
        @ApiResponse(responseCode = "404", description = "Product not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> generateProductContent(
            @Parameter(description = "Product ID", required = true)
            @PathVariable String productId,
            @Parameter(description = "Content type (description, categories, tags)", required = true)
            @RequestParam String contentType) {
        
        log.info("Product content generation requested: productId={}, contentType={}", productId, contentType);
        
        try {
            Map<String, Object> content = productAIFacade.generateProductContent(productId, contentType);
            return ResponseEntity.ok(content);
            
        } catch (Exception e) {
            log.error("Error generating product content", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Enhance product with AI features
     * 
     * @param productId the product ID
     * @return enhanced product
     */
    @PostMapping("/{productId}/enhance")
    @Operation(
        summary = "Enhance product with AI features",
        description = "Enhance a product with all AI features including description, categories, tags, and indexing"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Product enhanced successfully"),
        @ApiResponse(responseCode = "404", description = "Product not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Product> enhanceProduct(
            @Parameter(description = "Product ID", required = true)
            @PathVariable String productId) {
        
        log.info("Product enhancement requested: productId={}", productId);
        
        try {
            return productAIFacade.enhanceProduct(productId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
            
        } catch (Exception e) {
            log.error("Error enhancing product", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Index product for AI search
     * 
     * @param productId the product ID
     * @return success status
     */
    @PostMapping("/{productId}/index")
    @Operation(
        summary = "Index product for AI search",
        description = "Index a product for AI-powered search functionality"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Product indexed successfully"),
        @ApiResponse(responseCode = "404", description = "Product not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> indexProduct(
            @Parameter(description = "Product ID", required = true)
            @PathVariable String productId) {
        
        log.info("Product indexing requested: productId={}", productId);
        
        try {
            boolean success = productAIFacade.indexProduct(productId);
            return ResponseEntity.ok(Map.of("success", success, "productId", productId));
            
        } catch (Exception e) {
            log.error("Error indexing product", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Remove product from AI index
     * 
     * @param productId the product ID
     * @return success status
     */
    @DeleteMapping("/{productId}/index")
    @Operation(
        summary = "Remove product from AI index",
        description = "Remove a product from the AI search index"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Product removed from index successfully"),
        @ApiResponse(responseCode = "404", description = "Product not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> removeProductFromIndex(
            @Parameter(description = "Product ID", required = true)
            @PathVariable String productId) {
        
        log.info("Product index removal requested: productId={}", productId);
        
        try {
            boolean success = productAIFacade.removeProductFromIndex(productId);
            return ResponseEntity.ok(Map.of("success", success, "productId", productId));
            
        } catch (Exception e) {
            log.error("Error removing product from index", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get AI statistics for products
     * 
     * @return AI statistics
     */
    @GetMapping("/statistics")
    @Operation(
        summary = "Get AI statistics for products",
        description = "Get statistics about AI usage and content generation for products"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> getAIStatistics() {
        
        log.info("AI statistics requested");
        
        try {
            Map<String, Object> statistics = productAIFacade.getAIStatistics();
            return ResponseEntity.ok(statistics);
            
        } catch (Exception e) {
            log.error("Error getting AI statistics", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}