package com.easyluxury.ai.adapter;

import com.ai.infrastructure.dto.BehaviorAnalysisResult;
import com.ai.infrastructure.dto.BehaviorRequest;
import com.ai.infrastructure.dto.BehaviorResponse;
import com.ai.infrastructure.service.BehaviorService;
import com.easyluxury.entity.Order;
import com.easyluxury.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Order AI Adapter
 * 
 * Adapter to bridge between Order domain services and generic AI infrastructure services.
 * This adapter handles the conversion between domain-specific entities and generic AI DTOs.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderAIAdapter {
    
    private final BehaviorService behaviorService;
    
    /**
     * Track order creation behavior using AI infrastructure
     */
    public BehaviorResponse trackOrderCreation(User user, Order order) {
        log.info("Tracking order creation for user: {} and order: {}", user.getId(), order.getId());
        
        BehaviorRequest request = BehaviorRequest.builder()
                .userId(user.getId().toString())
                .behaviorType("ORDER_CREATED")
                .entityType("order")
                .entityId(order.getId().toString())
                .action("create_order")
                .context("Order: " + order.getOrderNumber())
                .metadata(String.format("{\"orderId\":\"%s\",\"orderNumber\":\"%s\",\"totalAmount\":%f,\"status\":\"%s\"}", 
                        order.getId(), order.getOrderNumber(), order.getTotalAmount(), order.getStatus()))
                .build();
        
        return behaviorService.createBehavior(request);
    }
    
    /**
     * Track order update behavior using AI infrastructure
     */
    public BehaviorResponse trackOrderUpdate(User user, Order order) {
        log.info("Tracking order update for user: {} and order: {}", user.getId(), order.getId());
        
        BehaviorRequest request = BehaviorRequest.builder()
                .userId(user.getId().toString())
                .behaviorType("ORDER_UPDATED")
                .entityType("order")
                .entityId(order.getId().toString())
                .action("update_order")
                .context("Order: " + order.getOrderNumber())
                .metadata(String.format("{\"orderId\":\"%s\",\"orderNumber\":\"%s\",\"totalAmount\":%f,\"status\":\"%s\"}", 
                        order.getId(), order.getOrderNumber(), order.getTotalAmount(), order.getStatus()))
                .build();
        
        return behaviorService.createBehavior(request);
    }
    
    /**
     * Track order cancellation behavior using AI infrastructure
     */
    public BehaviorResponse trackOrderCancellation(User user, Order order) {
        log.info("Tracking order cancellation for user: {} and order: {}", user.getId(), order.getId());
        
        BehaviorRequest request = BehaviorRequest.builder()
                .userId(user.getId().toString())
                .behaviorType("ORDER_CANCELLED")
                .entityType("order")
                .entityId(order.getId().toString())
                .action("cancel_order")
                .context("Order: " + order.getOrderNumber())
                .metadata(String.format("{\"orderId\":\"%s\",\"orderNumber\":\"%s\",\"totalAmount\":%f,\"status\":\"%s\"}", 
                        order.getId(), order.getOrderNumber(), order.getTotalAmount(), order.getStatus()))
                .build();
        
        return behaviorService.createBehavior(request);
    }
    
    /**
     * Track order completion behavior using AI infrastructure
     */
    public BehaviorResponse trackOrderCompletion(User user, Order order) {
        log.info("Tracking order completion for user: {} and order: {}", user.getId(), order.getId());
        
        BehaviorRequest request = BehaviorRequest.builder()
                .userId(user.getId().toString())
                .behaviorType("ORDER_COMPLETED")
                .entityType("order")
                .entityId(order.getId().toString())
                .action("complete_order")
                .context("Order: " + order.getOrderNumber())
                .metadata(String.format("{\"orderId\":\"%s\",\"orderNumber\":\"%s\",\"totalAmount\":%f,\"status\":\"%s\"}", 
                        order.getId(), order.getOrderNumber(), order.getTotalAmount(), order.getStatus()))
                .build();
        
        return behaviorService.createBehavior(request);
    }
    
    /**
     * Track order payment behavior using AI infrastructure
     */
    public BehaviorResponse trackOrderPayment(User user, Order order, String paymentMethod) {
        log.info("Tracking order payment for user: {} and order: {}", user.getId(), order.getId());
        
        BehaviorRequest request = BehaviorRequest.builder()
                .userId(user.getId().toString())
                .behaviorType("PAYMENT_SUCCESS")
                .entityType("order")
                .entityId(order.getId().toString())
                .action("payment")
                .context("Order: " + order.getOrderNumber())
                .metadata(String.format("{\"orderId\":\"%s\",\"orderNumber\":\"%s\",\"totalAmount\":%f,\"status\":\"%s\",\"paymentMethod\":\"%s\"}", 
                        order.getId(), order.getOrderNumber(), order.getTotalAmount(), order.getStatus(), paymentMethod))
                .build();
        
        return behaviorService.createBehavior(request);
    }
    
    /**
     * Track order refund behavior using AI infrastructure
     */
    public BehaviorResponse trackOrderRefund(User user, Order order, String reason) {
        log.info("Tracking order refund for user: {} and order: {}", user.getId(), order.getId());
        
        BehaviorRequest request = BehaviorRequest.builder()
                .userId(user.getId().toString())
                .behaviorType("REFUND_REQUEST")
                .entityType("order")
                .entityId(order.getId().toString())
                .action("refund")
                .context("Order: " + order.getOrderNumber())
                .metadata(String.format("{\"orderId\":\"%s\",\"orderNumber\":\"%s\",\"totalAmount\":%f,\"status\":\"%s\",\"refundReason\":\"%s\"}", 
                        order.getId(), order.getOrderNumber(), order.getTotalAmount(), order.getStatus(), reason))
                .build();
        
        return behaviorService.createBehavior(request);
    }
    
    /**
     * Track order return behavior using AI infrastructure
     */
    public BehaviorResponse trackOrderReturn(User user, Order order, String reason) {
        log.info("Tracking order return for user: {} and order: {}", user.getId(), order.getId());
        
        BehaviorRequest request = BehaviorRequest.builder()
                .userId(user.getId().toString())
                .behaviorType("RETURN_REQUEST")
                .entityType("order")
                .entityId(order.getId().toString())
                .action("return")
                .context("Order: " + order.getOrderNumber())
                .metadata(String.format("{\"orderId\":\"%s\",\"orderNumber\":\"%s\",\"totalAmount\":%f,\"status\":\"%s\",\"returnReason\":\"%s\"}", 
                        order.getId(), order.getOrderNumber(), order.getTotalAmount(), order.getStatus(), reason))
                .build();
        
        return behaviorService.createBehavior(request);
    }
    
    /**
     * Get order behaviors using AI infrastructure
     */
    public List<BehaviorResponse> getOrderBehaviors(Order order) {
        log.info("Getting behaviors for order: {}", order.getId());
        
        return behaviorService.getBehaviorsByEntity("order", order.getId().toString());
    }
    
    /**
     * Get order behaviors by type using AI infrastructure
     */
    public List<BehaviorResponse> getOrderBehaviorsByType(Order order, String behaviorType) {
        log.info("Getting behaviors for order: {} and type: {}", order.getId(), behaviorType);
        
        return behaviorService.getBehaviorsByEntity("order", order.getId().toString()).stream()
                .filter(behavior -> behavior.getBehaviorType().equals(behaviorType))
                .collect(Collectors.toList());
    }
    
    /**
     * Get order creation behaviors using AI infrastructure
     */
    public List<BehaviorResponse> getOrderCreationBehaviors(Order order) {
        log.info("Getting creation behaviors for order: {}", order.getId());
        
        return getOrderBehaviorsByType(order, "ORDER_CREATED");
    }
    
    /**
     * Get order update behaviors using AI infrastructure
     */
    public List<BehaviorResponse> getOrderUpdateBehaviors(Order order) {
        log.info("Getting update behaviors for order: {}", order.getId());
        
        return getOrderBehaviorsByType(order, "ORDER_UPDATED");
    }
    
    /**
     * Get order cancellation behaviors using AI infrastructure
     */
    public List<BehaviorResponse> getOrderCancellationBehaviors(Order order) {
        log.info("Getting cancellation behaviors for order: {}", order.getId());
        
        return getOrderBehaviorsByType(order, "ORDER_CANCELLED");
    }
    
    /**
     * Get order completion behaviors using AI infrastructure
     */
    public List<BehaviorResponse> getOrderCompletionBehaviors(Order order) {
        log.info("Getting completion behaviors for order: {}", order.getId());
        
        return getOrderBehaviorsByType(order, "ORDER_COMPLETED");
    }
    
    /**
     * Get order payment behaviors using AI infrastructure
     */
    public List<BehaviorResponse> getOrderPaymentBehaviors(Order order) {
        log.info("Getting payment behaviors for order: {}", order.getId());
        
        return getOrderBehaviorsByType(order, "PAYMENT_SUCCESS");
    }
    
    /**
     * Get order refund behaviors using AI infrastructure
     */
    public List<BehaviorResponse> getOrderRefundBehaviors(Order order) {
        log.info("Getting refund behaviors for order: {}", order.getId());
        
        return getOrderBehaviorsByType(order, "REFUND_REQUEST");
    }
    
    /**
     * Get order return behaviors using AI infrastructure
     */
    public List<BehaviorResponse> getOrderReturnBehaviors(Order order) {
        log.info("Getting return behaviors for order: {}", order.getId());
        
        return getOrderBehaviorsByType(order, "RETURN_REQUEST");
    }
    
    /**
     * Analyze order behaviors using AI infrastructure
     */
    public BehaviorAnalysisResult analyzeOrderBehaviors(Order order) {
        log.info("Analyzing behaviors for order: {}", order.getId());
        
        List<BehaviorResponse> behaviors = getOrderBehaviors(order);
        
        // Create a temporary user ID for analysis (could be improved)
        UUID tempUserId = UUID.randomUUID();
        
        // This would need to be enhanced to work with order-specific analysis
        return behaviorService.analyzeBehaviors(tempUserId);
    }
    
    /**
     * Get user order behaviors using AI infrastructure
     */
    public List<BehaviorResponse> getUserOrderBehaviors(User user) {
        log.info("Getting order behaviors for user: {}", user.getId());
        
        return behaviorService.getBehaviorsByUserId(user.getId()).stream()
                .filter(behavior -> "order".equals(behavior.getEntityType()))
                .collect(Collectors.toList());
    }
    
    /**
     * Get user order behaviors by type using AI infrastructure
     */
    public List<BehaviorResponse> getUserOrderBehaviorsByType(User user, String behaviorType) {
        log.info("Getting order behaviors for user: {} and type: {}", user.getId(), behaviorType);
        
        return getUserOrderBehaviors(user).stream()
                .filter(behavior -> behavior.getBehaviorType().equals(behaviorType))
                .collect(Collectors.toList());
    }
}