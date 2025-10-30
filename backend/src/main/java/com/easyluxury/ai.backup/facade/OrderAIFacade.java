package com.easyluxury.ai.facade;

import com.easyluxury.ai.dto.OrderAIAnalysisRequest;
import com.easyluxury.ai.dto.OrderAIAnalysisResponse;
import com.easyluxury.ai.dto.OrderPatternRequest;
import com.easyluxury.ai.dto.OrderPatternResponse;
import com.easyluxury.ai.dto.OrderAIInsightsRequest;
import com.easyluxury.ai.dto.OrderAIInsightsResponse;
import com.easyluxury.ai.mapper.OrderAIMapper;
import com.easyluxury.ai.service.OrderAIService;
import com.easyluxury.ai.service.OrderPatternService;
import com.easyluxury.entity.Order;
import com.easyluxury.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * OrderAIFacade
 * 
 * Facade for order-specific AI operations providing a high-level interface
 * for AI-powered order features including analysis, pattern recognition, and fraud detection.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderAIFacade {
    
    private final OrderAIService orderAIService;
    private final OrderPatternService orderPatternService;
    private final OrderRepository orderRepository;
    private final OrderAIMapper orderAIMapper;
    
    /**
     * Analyze order patterns and trends
     * 
     * @param request the pattern analysis request
     * @return order pattern analysis
     */
    @Transactional(readOnly = true)
    public OrderPatternResponse analyzeOrderPatterns(OrderPatternRequest request) {
        try {
            log.debug("Analyzing order patterns for user: {} over {} days", 
                request.getUserId(), request.getDays());
            
            // Analyze patterns using OrderPatternService
            String patterns = orderPatternService.identifyOrderPatterns(
                request.getUserId(), 
                request.getDays()
            );
            
            // Detect anomalies if requested
            String anomalies = null;
            if (request.getIncludeTemporalPatterns()) {
                anomalies = orderPatternService.detectOrderAnomalies(
                    request.getUserId(), 
                    request.getDays()
                );
            }
            
            // Analyze seasonal patterns if requested
            String seasonalPatterns = null;
            if (request.getIncludeSeasonalPatterns()) {
                seasonalPatterns = orderPatternService.analyzeSeasonalPatterns(
                    request.getMonths()
                );
            }
            
            // Get order statistics
            List<Order> orders = request.getUserId() != null 
                ? orderRepository.findByUserIdOrderByCreatedAtDesc(request.getUserId())
                : orderRepository.findAll();
            
            // Convert to response
            OrderPatternResponse response = OrderPatternResponse.builder()
                .userId(request.getUserId())
                .patterns(patterns)
                .anomalies(anomalies)
                .seasonalPatterns(seasonalPatterns)
                .totalOrders((long) orders.size())
                .analysisTimestamp(java.time.LocalDateTime.now())
                .patternType(request.getPatternType())
                .build();
            
            log.debug("Successfully analyzed order patterns for user: {}", request.getUserId());
            
            return response;
            
        } catch (Exception e) {
            log.error("Error analyzing order patterns for user: {}", request.getUserId(), e);
            throw new RuntimeException("Failed to analyze order patterns", e);
        }
    }
    
    /**
     * Analyze specific order for fraud and risk assessment
     * 
     * @param request the order analysis request
     * @return order analysis results
     */
    @Transactional(readOnly = true)
    public OrderAIAnalysisResponse analyzeOrder(OrderAIAnalysisRequest request) {
        try {
            log.debug("Analyzing order: {}", request.getOrderId());
            
            Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new RuntimeException("Order not found: " + request.getOrderId()));
            
            // Perform fraud detection if requested
            String fraudAnalysis = null;
            if (request.getIncludeFraudDetection()) {
                fraudAnalysis = orderAIService.detectFraudulentOrders(request.getOrderId());
            }
            
            // Perform pattern analysis if requested
            String patternAnalysis = null;
            if (request.getIncludePatternAnalysis()) {
                patternAnalysis = orderPatternService.identifyOrderPatterns(
                    order.getUserId(), 
                    request.getDays()
                );
            }
            
            // Perform anomaly detection if requested
            String anomalyAnalysis = null;
            if (request.getIncludeAnomalyDetection()) {
                anomalyAnalysis = orderPatternService.detectOrderAnomalies(
                    order.getUserId(), 
                    request.getDays()
                );
            }
            
            // Convert to response
            OrderAIAnalysisResponse response = OrderAIAnalysisResponse.builder()
                .orderId(request.getOrderId())
                .userId(order.getUserId())
                .analysis(fraudAnalysis)
                .patterns(patternAnalysis)
                .anomalies(anomalyAnalysis)
                .fraudScore(order.getFraudScore())
                .riskLevel(order.getRiskLevel() != null ? order.getRiskLevel().name() : "LOW")
                .anomalyScore(order.getAnomalyScore())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus().name())
                .paymentMethod(order.getPaymentMethod())
                .createdAt(order.getCreatedAt())
                .analysisTimestamp(java.time.LocalDateTime.now())
                .analysisType(request.getAnalysisType())
                .build();
            
            log.debug("Successfully analyzed order: {}", request.getOrderId());
            
            return response;
            
        } catch (Exception e) {
            log.error("Error analyzing order: {}", request.getOrderId(), e);
            throw new RuntimeException("Failed to analyze order", e);
        }
    }
    
    /**
     * Generate business insights from order data
     * 
     * @param request the insights request
     * @return business insights
     */
    @Transactional(readOnly = true)
    public OrderAIInsightsResponse generateBusinessInsights(OrderAIInsightsRequest request) {
        try {
            log.debug("Generating business insights for last {} days", request.getDays());
            
            // Generate business insights
            String insights = orderAIService.generateBusinessInsights(request.getDays());
            
            // Analyze patterns for additional insights
            String patterns = orderPatternService.identifyOrderPatterns(null, request.getDays());
            
            // Get order statistics
            List<Order> recentOrders = orderRepository.findByCreatedAtAfterOrderByCreatedAtDesc(
                java.time.LocalDateTime.now().minusDays(request.getDays())
            );
            
            // Calculate key metrics
            double totalRevenue = recentOrders.stream()
                .filter(order -> order.getStatus() == Order.OrderStatus.DELIVERED)
                .mapToDouble(order -> order.getTotalAmount().doubleValue())
                .sum();
            
            double avgOrderValue = recentOrders.isEmpty() ? 0.0 : 
                totalRevenue / recentOrders.size();
            
            long completedOrders = recentOrders.stream()
                .filter(order -> order.getStatus() == Order.OrderStatus.DELIVERED)
                .count();
            
            double completionRate = recentOrders.isEmpty() ? 0.0 : 
                (double) completedOrders / recentOrders.size() * 100;
            
            // Convert to response
            OrderAIInsightsResponse response = OrderAIInsightsResponse.builder()
                .insights(insights)
                .patterns(patterns)
                .totalRevenue(java.math.BigDecimal.valueOf(totalRevenue))
                .averageOrderValue(java.math.BigDecimal.valueOf(avgOrderValue))
                .totalOrders((long) recentOrders.size())
                .completedOrders(completedOrders)
                .completionRate(completionRate)
                .analysisPeriodDays(request.getDays())
                .analysisTimestamp(java.time.LocalDateTime.now())
                .build();
            
            log.debug("Successfully generated business insights for last {} days", request.getDays());
            
            return response;
            
        } catch (Exception e) {
            log.error("Error generating business insights for last {} days", request.getDays(), e);
            throw new RuntimeException("Failed to generate business insights", e);
        }
    }
    
    /**
     * Detect fraudulent orders
     * 
     * @param orderId the order ID
     * @return fraud detection results
     */
    @Transactional(readOnly = true)
    public String detectFraud(UUID orderId) {
        try {
            log.debug("Detecting fraud for order: {}", orderId);
            
            String fraudAnalysis = orderAIService.detectFraudulentOrders(orderId);
            
            log.debug("Successfully detected fraud for order: {}", orderId);
            
            return fraudAnalysis;
            
        } catch (Exception e) {
            log.error("Error detecting fraud for order: {}", orderId, e);
            throw new RuntimeException("Failed to detect fraud", e);
        }
    }
    
    /**
     * Get order history with AI analysis
     * 
     * @param userId the user ID
     * @param limit maximum number of orders to return
     * @return order history with AI analysis
     */
    @Transactional(readOnly = true)
    public List<OrderAIAnalysisResponse> getOrderHistory(UUID userId, int limit) {
        try {
            log.debug("Getting order history with AI analysis for user: {}", userId);
            
            List<Order> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .limit(limit)
                .toList();
            
            List<OrderAIAnalysisResponse> responses = orderAIMapper.toOrderAIAnalysisResponseList(orders);
            
            log.debug("Retrieved {} orders with AI analysis for user: {}", responses.size(), userId);
            
            return responses;
            
        } catch (Exception e) {
            log.error("Error getting order history for user: {}", userId, e);
            throw new RuntimeException("Failed to get order history", e);
        }
    }
    
    /**
     * Analyze seasonal patterns
     * 
     * @param months number of months to analyze
     * @return seasonal pattern analysis
     */
    @Transactional(readOnly = true)
    public String analyzeSeasonalPatterns(int months) {
        try {
            log.debug("Analyzing seasonal patterns for last {} months", months);
            
            String seasonalPatterns = orderPatternService.analyzeSeasonalPatterns(months);
            
            log.debug("Successfully analyzed seasonal patterns for last {} months", months);
            
            return seasonalPatterns;
            
        } catch (Exception e) {
            log.error("Error analyzing seasonal patterns for last {} months", months, e);
            throw new RuntimeException("Failed to analyze seasonal patterns", e);
        }
    }
}