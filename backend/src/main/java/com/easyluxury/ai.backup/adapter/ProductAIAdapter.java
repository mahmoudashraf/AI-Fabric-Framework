package com.easyluxury.ai.adapter;

import com.ai.infrastructure.dto.BehaviorAnalysisResult;
import com.ai.infrastructure.dto.BehaviorRequest;
import com.ai.infrastructure.dto.BehaviorResponse;
import com.ai.infrastructure.service.BehaviorService;
import com.easyluxury.entity.Product;
import com.easyluxury.entity.User;
import com.easyluxury.entity.UserBehavior;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Product AI Adapter
 * 
 * Adapter to bridge between Product domain services and generic AI infrastructure services.
 * This adapter handles the conversion between domain-specific entities and generic AI DTOs.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductAIAdapter {
    
    private final BehaviorService behaviorService;
    
    /**
     * Track product view behavior using AI infrastructure
     */
    public BehaviorResponse trackProductView(User user, Product product) {
        log.info("Tracking product view for user: {} and product: {}", user.getId(), product.getId());
        
        BehaviorRequest request = BehaviorRequest.builder()
                .userId(user.getId().toString())
                .behaviorType("PRODUCT_VIEW")
                .entityType("product")
                .entityId(product.getId().toString())
                .action("view")
                .context("Product: " + product.getName())
                .metadata(String.format("{\"productId\":\"%s\",\"productName\":\"%s\",\"category\":\"%s\",\"price\":%f}", 
                        product.getId(), product.getName(), product.getCategory(), product.getPrice()))
                .build();
        
        return behaviorService.createBehavior(request);
    }
    
    /**
     * Track product click behavior using AI infrastructure
     */
    public BehaviorResponse trackProductClick(User user, Product product) {
        log.info("Tracking product click for user: {} and product: {}", user.getId(), product.getId());
        
        BehaviorRequest request = BehaviorRequest.builder()
                .userId(user.getId().toString())
                .behaviorType("CLICK")
                .entityType("product")
                .entityId(product.getId().toString())
                .action("click")
                .context("Product: " + product.getName())
                .metadata(String.format("{\"productId\":\"%s\",\"productName\":\"%s\",\"category\":\"%s\",\"price\":%f}", 
                        product.getId(), product.getName(), product.getCategory(), product.getPrice()))
                .build();
        
        return behaviorService.createBehavior(request);
    }
    
    /**
     * Track add to cart behavior using AI infrastructure
     */
    public BehaviorResponse trackAddToCart(User user, Product product) {
        log.info("Tracking add to cart for user: {} and product: {}", user.getId(), product.getId());
        
        BehaviorRequest request = BehaviorRequest.builder()
                .userId(user.getId().toString())
                .behaviorType("ADD_TO_CART")
                .entityType("product")
                .entityId(product.getId().toString())
                .action("add_to_cart")
                .context("Product: " + product.getName())
                .metadata(String.format("{\"productId\":\"%s\",\"productName\":\"%s\",\"category\":\"%s\",\"price\":%f}", 
                        product.getId(), product.getName(), product.getCategory(), product.getPrice()))
                .build();
        
        return behaviorService.createBehavior(request);
    }
    
    /**
     * Track remove from cart behavior using AI infrastructure
     */
    public BehaviorResponse trackRemoveFromCart(User user, Product product) {
        log.info("Tracking remove from cart for user: {} and product: {}", user.getId(), product.getId());
        
        BehaviorRequest request = BehaviorRequest.builder()
                .userId(user.getId().toString())
                .behaviorType("REMOVE_FROM_CART")
                .entityType("product")
                .entityId(product.getId().toString())
                .action("remove_from_cart")
                .context("Product: " + product.getName())
                .metadata(String.format("{\"productId\":\"%s\",\"productName\":\"%s\",\"category\":\"%s\",\"price\":%f}", 
                        product.getId(), product.getName(), product.getCategory(), product.getPrice()))
                .build();
        
        return behaviorService.createBehavior(request);
    }
    
    /**
     * Track product purchase behavior using AI infrastructure
     */
    public BehaviorResponse trackProductPurchase(User user, Product product) {
        log.info("Tracking product purchase for user: {} and product: {}", user.getId(), product.getId());
        
        BehaviorRequest request = BehaviorRequest.builder()
                .userId(user.getId().toString())
                .behaviorType("PURCHASE")
                .entityType("product")
                .entityId(product.getId().toString())
                .action("purchase")
                .context("Product: " + product.getName())
                .metadata(String.format("{\"productId\":\"%s\",\"productName\":\"%s\",\"category\":\"%s\",\"price\":%f}", 
                        product.getId(), product.getName(), product.getCategory(), product.getPrice()))
                .build();
        
        return behaviorService.createBehavior(request);
    }
    
    /**
     * Track product wishlist behavior using AI infrastructure
     */
    public BehaviorResponse trackProductWishlist(User user, Product product) {
        log.info("Tracking product wishlist for user: {} and product: {}", user.getId(), product.getId());
        
        BehaviorRequest request = BehaviorRequest.builder()
                .userId(user.getId().toString())
                .behaviorType("WISHLIST")
                .entityType("product")
                .entityId(product.getId().toString())
                .action("wishlist")
                .context("Product: " + product.getName())
                .metadata(String.format("{\"productId\":\"%s\",\"productName\":\"%s\",\"category\":\"%s\",\"price\":%f}", 
                        product.getId(), product.getName(), product.getCategory(), product.getPrice()))
                .build();
        
        return behaviorService.createBehavior(request);
    }
    
    /**
     * Track product review behavior using AI infrastructure
     */
    public BehaviorResponse trackProductReview(User user, Product product, String reviewText, Integer rating) {
        log.info("Tracking product review for user: {} and product: {}", user.getId(), product.getId());
        
        BehaviorRequest request = BehaviorRequest.builder()
                .userId(user.getId().toString())
                .behaviorType("REVIEW")
                .entityType("product")
                .entityId(product.getId().toString())
                .action("review")
                .context("Product: " + product.getName())
                .metadata(String.format("{\"productId\":\"%s\",\"productName\":\"%s\",\"category\":\"%s\",\"price\":%f,\"reviewText\":\"%s\",\"rating\":%d}", 
                        product.getId(), product.getName(), product.getCategory(), product.getPrice(), reviewText, rating))
                .build();
        
        return behaviorService.createBehavior(request);
    }
    
    /**
     * Get product behaviors using AI infrastructure
     */
    public List<BehaviorResponse> getProductBehaviors(Product product) {
        log.info("Getting behaviors for product: {}", product.getId());
        
        return behaviorService.getBehaviorsByEntity("product", product.getId().toString());
    }
    
    /**
     * Get product behaviors by type using AI infrastructure
     */
    public List<BehaviorResponse> getProductBehaviorsByType(Product product, String behaviorType) {
        log.info("Getting behaviors for product: {} and type: {}", product.getId(), behaviorType);
        
        return behaviorService.getBehaviorsByEntity("product", product.getId().toString()).stream()
                .filter(behavior -> behavior.getBehaviorType().equals(behaviorType))
                .collect(Collectors.toList());
    }
    
    /**
     * Get product view behaviors using AI infrastructure
     */
    public List<BehaviorResponse> getProductViewBehaviors(Product product) {
        log.info("Getting view behaviors for product: {}", product.getId());
        
        return getProductBehaviorsByType(product, "PRODUCT_VIEW");
    }
    
    /**
     * Get product purchase behaviors using AI infrastructure
     */
    public List<BehaviorResponse> getProductPurchaseBehaviors(Product product) {
        log.info("Getting purchase behaviors for product: {}", product.getId());
        
        return getProductBehaviorsByType(product, "PURCHASE");
    }
    
    /**
     * Get product cart behaviors using AI infrastructure
     */
    public List<BehaviorResponse> getProductCartBehaviors(Product product) {
        log.info("Getting cart behaviors for product: {}", product.getId());
        
        return getProductBehaviorsByType(product, "ADD_TO_CART");
    }
    
    /**
     * Get product wishlist behaviors using AI infrastructure
     */
    public List<BehaviorResponse> getProductWishlistBehaviors(Product product) {
        log.info("Getting wishlist behaviors for product: {}", product.getId());
        
        return getProductBehaviorsByType(product, "WISHLIST");
    }
    
    /**
     * Get product review behaviors using AI infrastructure
     */
    public List<BehaviorResponse> getProductReviewBehaviors(Product product) {
        log.info("Getting review behaviors for product: {}", product.getId());
        
        return getProductBehaviorsByType(product, "REVIEW");
    }
    
    /**
     * Analyze product behaviors using AI infrastructure
     */
    public BehaviorAnalysisResult analyzeProductBehaviors(Product product) {
        log.info("Analyzing behaviors for product: {}", product.getId());
        
        List<BehaviorResponse> behaviors = getProductBehaviors(product);
        
        // Create a temporary user ID for analysis (could be improved)
        UUID tempUserId = UUID.randomUUID();
        
        // This would need to be enhanced to work with product-specific analysis
        return behaviorService.analyzeBehaviors(tempUserId);
    }
}