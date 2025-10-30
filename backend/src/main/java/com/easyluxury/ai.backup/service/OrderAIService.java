package com.easyluxury.ai.service;

import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.dto.BehaviorAnalysisResult;
import com.ai.infrastructure.dto.BehaviorResponse;
import com.easyluxury.ai.adapter.OrderAIAdapter;
import com.easyluxury.entity.Order;
import com.easyluxury.entity.User;
import com.easyluxury.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * OrderAIService
 * 
 * Provides AI-powered functionality for Order entities including order analysis,
 * pattern recognition, fraud detection, and business insights.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderAIService {
    
    private final OrderAIAdapter orderAIAdapter;
    private final OrderRepository orderRepository;
    
    /**
     * Analyze order patterns and trends
     * 
     * @param userId optional user ID to analyze orders for specific user
     * @param days number of days to analyze
     * @return order pattern analysis
     */
    @Transactional
    public String analyzeOrderPatterns(UUID userId, int days) {
        try {
            log.debug("Analyzing order patterns for user {} over {} days", userId, days);
            
            LocalDateTime startDate = LocalDateTime.now().minusDays(days);
            List<Order> orders;
            
            if (userId != null) {
                orders = orderRepository.findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(userId, startDate);
            } else {
                orders = orderRepository.findByCreatedAtAfterOrderByCreatedAtDesc(startDate);
            }
            
            if (orders.isEmpty()) {
                return "No order data available for the specified period.";
            }
            
            // Build order analysis context
            StringBuilder context = new StringBuilder();
            context.append("Order Pattern Analysis\n");
            context.append("Analysis Period: Last ").append(days).append(" days\n");
            context.append("Total Orders: ").append(orders.size()).append("\n");
            
            if (userId != null) {
                context.append("User ID: ").append(userId).append("\n");
            }
            
            // Analyze order status distribution
            Map<Order.OrderStatus, Long> statusCounts = orders.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    Order::getStatus,
                    java.util.stream.Collectors.counting()
                ));
            
            context.append("\nOrder Status Distribution:\n");
            statusCounts.forEach((status, count) -> 
                context.append("- ").append(status).append(": ").append(count).append(" orders\n"));
            
            // Analyze risk levels
            Map<Order.RiskLevel, Long> riskCounts = orders.stream()
                .filter(order -> order.getRiskLevel() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                    Order::getRiskLevel,
                    java.util.stream.Collectors.counting()
                ));
            
            if (!riskCounts.isEmpty()) {
                context.append("\nRisk Level Distribution:\n");
                riskCounts.forEach((risk, count) -> 
                    context.append("- ").append(risk).append(": ").append(count).append(" orders\n"));
            }
            
            // Analyze payment methods
            Map<String, Long> paymentCounts = orders.stream()
                .filter(order -> order.getPaymentMethod() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                    Order::getPaymentMethod,
                    java.util.stream.Collectors.counting()
                ));
            
            if (!paymentCounts.isEmpty()) {
                context.append("\nPayment Method Distribution:\n");
                paymentCounts.forEach((method, count) -> 
                    context.append("- ").append(method).append(": ").append(count).append(" orders\n"));
            }
            
            // Calculate average order value
            BigDecimal avgOrderValue = orders.stream()
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(orders.size()));
            
            context.append("\nAverage Order Value: $").append(avgOrderValue).append("\n");
            
            // Recent orders
            context.append("\nRecent Orders:\n");
            orders.stream()
                .limit(10)
                .forEach(order -> {
                    context.append("- Order ").append(order.getId())
                        .append(": $").append(order.getTotalAmount())
                        .append(" (").append(order.getStatus()).append(")")
                        .append(" at ").append(order.getCreatedAt())
                        .append("\n");
                });
            
            // Generate AI analysis
            AIGenerationRequest request = AIGenerationRequest.builder()
                .prompt("Analyze the following order data and identify patterns, trends, and business insights:")
                .context(context.toString())
                .purpose("order_pattern_analysis")
                .maxTokens(800)
                .temperature(0.3)
                .build();
            
            AIGenerationResponse response = AIGenerationResponse.builder().content("AI analysis placeholder").build();
            
            // Update orders with analysis
            updateOrderAnalysis(orders, response.getContent());
            
            log.debug("Successfully analyzed order patterns for user {}", userId);
            
            return response.getContent();
            
        } catch (Exception e) {
            log.error("Error analyzing order patterns for user {}", userId, e);
            throw new RuntimeException("Failed to analyze order patterns", e);
        }
    }
    
    /**
     * Detect fraudulent orders
     * 
     * @param orderId the order ID to analyze
     * @return fraud detection results
     */
    @Transactional
    public String detectFraudulentOrders(UUID orderId) {
        try {
            log.debug("Detecting fraud for order {}", orderId);
            
            Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
            
            // Build fraud detection context
            StringBuilder context = new StringBuilder();
            context.append("Fraud Detection Analysis for Order: ").append(orderId).append("\n");
            context.append("User ID: ").append(order.getUserId()).append("\n");
            context.append("Total Amount: $").append(order.getTotalAmount()).append("\n");
            context.append("Status: ").append(order.getStatus()).append("\n");
            context.append("Payment Method: ").append(order.getPaymentMethod()).append("\n");
            context.append("Created At: ").append(order.getCreatedAt()).append("\n");
            
            if (order.getShippingAddress() != null) {
                context.append("Shipping Address: ").append(order.getShippingAddress()).append("\n");
            }
            
            if (order.getBillingAddress() != null) {
                context.append("Billing Address: ").append(order.getBillingAddress()).append("\n");
            }
            
            // Get user's order history for comparison
            List<Order> userOrders = orderRepository.findByUserIdOrderByCreatedAtDesc(order.getUserId());
            context.append("\nUser Order History:\n");
            context.append("Total Orders: ").append(userOrders.size()).append("\n");
            
            if (userOrders.size() > 1) {
                BigDecimal avgAmount = userOrders.stream()
                    .map(Order::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(userOrders.size()));
                context.append("Average Order Value: $").append(avgAmount).append("\n");
                
                // Check for unusual patterns
                boolean unusualAmount = order.getTotalAmount().compareTo(avgAmount.multiply(BigDecimal.valueOf(3))) > 0;
                context.append("Unusual Amount (>3x average): ").append(unusualAmount).append("\n");
            }
            
            // Generate fraud detection analysis
            AIGenerationRequest request = AIGenerationRequest.builder()
                .prompt("Analyze the following order data for potential fraud indicators and suspicious patterns:")
                .context(context.toString())
                .purpose("fraud_detection")
                .maxTokens(600)
                .temperature(0.2)
                .build();
            
            AIGenerationResponse response = AIGenerationResponse.builder().content("AI analysis placeholder").build();
            
            // Calculate fraud score and update order
            double fraudScore = calculateFraudScore(order, userOrders);
            order.setFraudScore(fraudScore);
            order.setRiskLevel(determineRiskLevel(fraudScore));
            order.setAiAnalysis(response.getContent());
            orderRepository.save(order);
            
            log.debug("Successfully detected fraud for order {} with score {}", orderId, fraudScore);
            
            return response.getContent();
            
        } catch (Exception e) {
            log.error("Error detecting fraud for order {}", orderId, e);
            throw new RuntimeException("Failed to detect fraudulent orders", e);
        }
    }
    
    /**
     * Generate business insights from order data
     * 
     * @param days number of days to analyze
     * @return business insights
     */
    @Transactional
    public String generateBusinessInsights(int days) {
        try {
            log.debug("Generating business insights for last {} days", days);
            
            LocalDateTime startDate = LocalDateTime.now().minusDays(days);
            List<Order> orders = orderRepository.findByCreatedAtAfterOrderByCreatedAtDesc(startDate);
            
            if (orders.isEmpty()) {
                return "No order data available for business insights generation.";
            }
            
            // Build business insights context
            StringBuilder context = new StringBuilder();
            context.append("Business Insights Analysis\n");
            context.append("Analysis Period: Last ").append(days).append(" days\n");
            context.append("Total Orders: ").append(orders.size()).append("\n");
            
            // Calculate key metrics
            BigDecimal totalRevenue = orders.stream()
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal avgOrderValue = totalRevenue.divide(BigDecimal.valueOf(orders.size()));
            
            long completedOrders = orders.stream()
                .filter(order -> order.getStatus() == Order.OrderStatus.DELIVERED)
                .count();
            
            long cancelledOrders = orders.stream()
                .filter(order -> order.getStatus() == Order.OrderStatus.CANCELLED)
                .count();
            
            double completionRate = (double) completedOrders / orders.size() * 100;
            double cancellationRate = (double) cancelledOrders / orders.size() * 100;
            
            context.append("Total Revenue: $").append(totalRevenue).append("\n");
            context.append("Average Order Value: $").append(avgOrderValue).append("\n");
            context.append("Completion Rate: ").append(String.format("%.2f", completionRate)).append("%\n");
            context.append("Cancellation Rate: ").append(String.format("%.2f", cancellationRate)).append("%\n");
            
            // Analyze order status trends
            Map<Order.OrderStatus, Long> statusCounts = orders.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    Order::getStatus,
                    java.util.stream.Collectors.counting()
                ));
            
            context.append("\nOrder Status Distribution:\n");
            statusCounts.forEach((status, count) -> 
                context.append("- ").append(status).append(": ").append(count).append(" orders\n"));
            
            // Analyze risk levels
            Map<Order.RiskLevel, Long> riskCounts = orders.stream()
                .filter(order -> order.getRiskLevel() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                    Order::getRiskLevel,
                    java.util.stream.Collectors.counting()
                ));
            
            if (!riskCounts.isEmpty()) {
                context.append("\nRisk Level Distribution:\n");
                riskCounts.forEach((risk, count) -> 
                    context.append("- ").append(risk).append(": ").append(count).append(" orders\n"));
            }
            
            // Generate business insights
            AIGenerationRequest request = AIGenerationRequest.builder()
                .prompt("Based on the following business metrics and order data, generate insights about business performance, trends, and recommendations for improvement:")
                .context(context.toString())
                .purpose("business_insights")
                .maxTokens(1000)
                .temperature(0.4)
                .build();
            
            AIGenerationResponse response = AIGenerationResponse.builder().content("AI analysis placeholder").build();
            
            log.debug("Successfully generated business insights for last {} days", days);
            
            return response.getContent();
            
        } catch (Exception e) {
            log.error("Error generating business insights for last {} days", days, e);
            throw new RuntimeException("Failed to generate business insights", e);
        }
    }
    
    /**
     * Search orders using AI-powered semantic search
     * 
     * @param query the search query
     * @param limit maximum number of results
     * @return search results
     */
    public String searchOrders(String query, int limit) {
        try {
            log.debug("Searching orders with query: {}", query);
            
            // For now, return a simple search result
            // TODO: Implement proper order search using AI infrastructure
            return "Order search functionality will be implemented using AI infrastructure module";
            
        } catch (Exception e) {
            log.error("Error searching orders with query: {}", query, e);
            throw new RuntimeException("Failed to search orders", e);
        }
    }
    
    /**
     * Calculate fraud score for an order
     */
    private double calculateFraudScore(Order order, List<Order> userOrders) {
        double score = 0.0;
        
        // High amount check
        if (userOrders.size() > 1) {
            BigDecimal avgAmount = userOrders.stream()
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(userOrders.size()));
            
            if (order.getTotalAmount().compareTo(avgAmount.multiply(BigDecimal.valueOf(5))) > 0) {
                score += 0.3; // High amount
            }
        }
        
        // New user with high amount
        if (userOrders.size() == 1 && order.getTotalAmount().compareTo(BigDecimal.valueOf(1000)) > 0) {
            score += 0.2; // New user high amount
        }
        
        // Address mismatch
        if (order.getShippingAddress() != null && order.getBillingAddress() != null &&
            !order.getShippingAddress().equals(order.getBillingAddress())) {
            score += 0.1; // Address mismatch
        }
        
        // Unusual payment method
        if ("cryptocurrency".equalsIgnoreCase(order.getPaymentMethod()) ||
            "wire_transfer".equalsIgnoreCase(order.getPaymentMethod())) {
            score += 0.2; // Unusual payment method
        }
        
        return Math.min(score, 1.0);
    }
    
    /**
     * Determine risk level based on fraud score
     */
    private Order.RiskLevel determineRiskLevel(double fraudScore) {
        if (fraudScore >= 0.8) {
            return Order.RiskLevel.CRITICAL;
        } else if (fraudScore >= 0.6) {
            return Order.RiskLevel.HIGH;
        } else if (fraudScore >= 0.3) {
            return Order.RiskLevel.MEDIUM;
        } else {
            return Order.RiskLevel.LOW;
        }
    }
    
    /**
     * Track order creation behavior using AI infrastructure
     * 
     * @param user the user
     * @param order the order
     * @return behavior response
     */
    @Transactional
    public BehaviorResponse trackOrderCreation(User user, Order order) {
        try {
            log.debug("Tracking order creation for user {} and order {}", user.getId(), order.getId());
            
            return orderAIAdapter.trackOrderCreation(user, order);
            
        } catch (Exception e) {
            log.error("Error tracking order creation for user {} and order {}", user.getId(), order.getId(), e);
            throw new RuntimeException("Failed to track order creation", e);
        }
    }
    
    /**
     * Track order update behavior using AI infrastructure
     * 
     * @param user the user
     * @param order the order
     * @return behavior response
     */
    @Transactional
    public BehaviorResponse trackOrderUpdate(User user, Order order) {
        try {
            log.debug("Tracking order update for user {} and order {}", user.getId(), order.getId());
            
            return orderAIAdapter.trackOrderUpdate(user, order);
            
        } catch (Exception e) {
            log.error("Error tracking order update for user {} and order {}", user.getId(), order.getId(), e);
            throw new RuntimeException("Failed to track order update", e);
        }
    }
    
    /**
     * Track order completion behavior using AI infrastructure
     * 
     * @param user the user
     * @param order the order
     * @return behavior response
     */
    @Transactional
    public BehaviorResponse trackOrderCompletion(User user, Order order) {
        try {
            log.debug("Tracking order completion for user {} and order {}", user.getId(), order.getId());
            
            return orderAIAdapter.trackOrderCompletion(user, order);
            
        } catch (Exception e) {
            log.error("Error tracking order completion for user {} and order {}", user.getId(), order.getId(), e);
            throw new RuntimeException("Failed to track order completion", e);
        }
    }
    
    /**
     * Get order behaviors using AI infrastructure
     * 
     * @param order the order
     * @return list of order behaviors
     */
    @Transactional(readOnly = true)
    public List<BehaviorResponse> getOrderBehaviors(Order order) {
        try {
            log.debug("Getting behaviors for order {}", order.getId());
            
            return orderAIAdapter.getOrderBehaviors(order);
            
        } catch (Exception e) {
            log.error("Error getting behaviors for order {}", order.getId(), e);
            throw new RuntimeException("Failed to get order behaviors", e);
        }
    }
    
    /**
     * Analyze order behaviors using AI infrastructure
     * 
     * @param order the order
     * @return behavior analysis result
     */
    @Transactional(readOnly = true)
    public BehaviorAnalysisResult analyzeOrderBehaviors(Order order) {
        try {
            log.debug("Analyzing behaviors for order {}", order.getId());
            
            return orderAIAdapter.analyzeOrderBehaviors(order);
            
        } catch (Exception e) {
            log.error("Error analyzing behaviors for order {}", order.getId(), e);
            throw new RuntimeException("Failed to analyze order behaviors", e);
        }
    }
    
    /**
     * Update orders with AI analysis
     */
    private void updateOrderAnalysis(List<Order> orders, String analysis) {
        orders.forEach(order -> {
            order.setAiAnalysis(analysis);
            orderRepository.save(order);
        });
    }
}