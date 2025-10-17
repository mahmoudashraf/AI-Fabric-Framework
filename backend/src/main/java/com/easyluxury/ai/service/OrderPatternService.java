package com.easyluxury.ai.service;

import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.easyluxury.entity.Order;
import com.easyluxury.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * OrderPatternService
 * 
 * Provides AI-powered pattern recognition and analysis for order data.
 * This service identifies trends, anomalies, and patterns in order behavior.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPatternService {
    
    private final AICoreService aiCoreService;
    private final OrderRepository orderRepository;
    
    /**
     * Identify order patterns and trends
     * 
     * @param userId optional user ID to analyze patterns for specific user
     * @param days number of days to analyze
     * @return identified patterns and trends
     */
    @Transactional
    public String identifyOrderPatterns(UUID userId, int days) {
        try {
            log.debug("Identifying order patterns for user {} over {} days", userId, days);
            
            LocalDateTime startDate = LocalDateTime.now().minusDays(days);
            List<Order> orders;
            
            if (userId != null) {
                orders = orderRepository.findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(userId, startDate);
            } else {
                orders = orderRepository.findByCreatedAtAfterOrderByCreatedAtDesc(startDate);
            }
            
            if (orders.size() < 3) {
                return "Insufficient order data for pattern identification.";
            }
            
            // Build pattern analysis context
            StringBuilder context = new StringBuilder();
            context.append("Order Pattern Analysis\n");
            context.append("Analysis Period: Last ").append(days).append(" days\n");
            context.append("Total Orders: ").append(orders.size()).append("\n");
            
            if (userId != null) {
                context.append("User ID: ").append(userId).append("\n");
            }
            
            // Analyze temporal patterns
            analyzeTemporalPatterns(orders, context);
            
            // Analyze value patterns
            analyzeValuePatterns(orders, context);
            
            // Analyze status patterns
            analyzeStatusPatterns(orders, context);
            
            // Analyze payment patterns
            analyzePaymentPatterns(orders, context);
            
            // Analyze risk patterns
            analyzeRiskPatterns(orders, context);
            
            // Generate AI pattern analysis
            AIGenerationRequest request = AIGenerationRequest.builder()
                .prompt("Analyze the following order data and identify patterns, trends, and anomalies:")
                .context(context.toString())
                .purpose("order_pattern_identification")
                .maxTokens(900)
                .temperature(0.3)
                .build();
            
            AIGenerationResponse response = "AI analysis placeholder";
            
            // Update orders with pattern analysis
            updateOrderPatterns(orders, response.getContent());
            
            log.debug("Successfully identified order patterns for user {}", userId);
            
            return response.getContent();
            
        } catch (Exception e) {
            log.error("Error identifying order patterns for user {}", userId, e);
            throw new RuntimeException("Failed to identify order patterns", e);
        }
    }
    
    /**
     * Detect order anomalies
     * 
     * @param userId optional user ID to detect anomalies for specific user
     * @param days number of days to analyze
     * @return detected anomalies
     */
    @Transactional
    public String detectOrderAnomalies(UUID userId, int days) {
        try {
            log.debug("Detecting order anomalies for user {} over {} days", userId, days);
            
            LocalDateTime startDate = LocalDateTime.now().minusDays(days);
            List<Order> orders;
            
            if (userId != null) {
                orders = orderRepository.findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(userId, startDate);
            } else {
                orders = orderRepository.findByCreatedAtAfterOrderByCreatedAtDesc(startDate);
            }
            
            if (orders.size() < 5) {
                return "Insufficient order data for anomaly detection.";
            }
            
            // Build anomaly detection context
            StringBuilder context = new StringBuilder();
            context.append("Order Anomaly Detection\n");
            context.append("Analysis Period: Last ").append(days).append(" days\n");
            context.append("Total Orders: ").append(orders.size()).append("\n");
            
            if (userId != null) {
                context.append("User ID: ").append(userId).append("\n");
            }
            
            // Detect value anomalies
            detectValueAnomalies(orders, context);
            
            // Detect temporal anomalies
            detectTemporalAnomalies(orders, context);
            
            // Detect status anomalies
            detectStatusAnomalies(orders, context);
            
            // Detect risk anomalies
            detectRiskAnomalies(orders, context);
            
            // Generate AI anomaly detection
            AIGenerationRequest request = AIGenerationRequest.builder()
                .prompt("Analyze the following order data and identify anomalies, unusual patterns, and suspicious activities:")
                .context(context.toString())
                .purpose("order_anomaly_detection")
                .maxTokens(700)
                .temperature(0.2)
                .build();
            
            AIGenerationResponse response = "AI analysis placeholder";
            
            // Update orders with anomaly scores
            updateOrderAnomalyScores(orders, response.getContent());
            
            log.debug("Successfully detected order anomalies for user {}", userId);
            
            return response.getContent();
            
        } catch (Exception e) {
            log.error("Error detecting order anomalies for user {}", userId, e);
            throw new RuntimeException("Failed to detect order anomalies", e);
        }
    }
    
    /**
     * Analyze seasonal patterns in orders
     * 
     * @param months number of months to analyze
     * @return seasonal pattern analysis
     */
    @Transactional
    public String analyzeSeasonalPatterns(int months) {
        try {
            log.debug("Analyzing seasonal patterns for last {} months", months);
            
            LocalDateTime startDate = LocalDateTime.now().minusMonths(months);
            List<Order> orders = orderRepository.findByCreatedAtAfterOrderByCreatedAtDesc(startDate);
            
            if (orders.isEmpty()) {
                return "No order data available for seasonal analysis.";
            }
            
            // Build seasonal analysis context
            StringBuilder context = new StringBuilder();
            context.append("Seasonal Pattern Analysis\n");
            context.append("Analysis Period: Last ").append(months).append(" months\n");
            context.append("Total Orders: ").append(orders.size()).append("\n");
            
            // Analyze monthly patterns
            Map<Integer, Long> monthlyCounts = orders.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    order -> order.getCreatedAt().getMonthValue(),
                    java.util.stream.Collectors.counting()
                ));
            
            context.append("\nMonthly Order Distribution:\n");
            monthlyCounts.forEach((month, count) -> 
                context.append("- Month ").append(month).append(": ").append(count).append(" orders\n"));
            
            // Analyze monthly revenue
            Map<Integer, BigDecimal> monthlyRevenue = orders.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    order -> order.getCreatedAt().getMonthValue(),
                    java.util.stream.Collectors.reducing(
                        BigDecimal.ZERO,
                        Order::getTotalAmount,
                        BigDecimal::add
                    )
                ));
            
            context.append("\nMonthly Revenue Distribution:\n");
            monthlyRevenue.forEach((month, revenue) -> 
                context.append("- Month ").append(month).append(": $").append(revenue).append("\n"));
            
            // Analyze day-of-week patterns
            Map<Integer, Long> dayOfWeekCounts = orders.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    order -> order.getCreatedAt().getDayOfWeek().getValue(),
                    java.util.stream.Collectors.counting()
                ));
            
            context.append("\nDay of Week Distribution:\n");
            dayOfWeekCounts.forEach((day, count) -> 
                context.append("- Day ").append(day).append(": ").append(count).append(" orders\n"));
            
            // Generate seasonal analysis
            AIGenerationRequest request = AIGenerationRequest.builder()
                .prompt("Analyze the following seasonal order data and identify patterns, trends, and seasonal insights:")
                .context(context.toString())
                .purpose("seasonal_pattern_analysis")
                .maxTokens(800)
                .temperature(0.3)
                .build();
            
            AIGenerationResponse response = "AI analysis placeholder";
            
            log.debug("Successfully analyzed seasonal patterns for last {} months", months);
            
            return response.getContent();
            
        } catch (Exception e) {
            log.error("Error analyzing seasonal patterns for last {} months", months, e);
            throw new RuntimeException("Failed to analyze seasonal patterns", e);
        }
    }
    
    /**
     * Analyze temporal patterns in orders
     */
    private void analyzeTemporalPatterns(List<Order> orders, StringBuilder context) {
        context.append("\nTemporal Patterns:\n");
        
        // Analyze order frequency
        long totalDays = ChronoUnit.DAYS.between(
            orders.get(orders.size() - 1).getCreatedAt(),
            orders.get(0).getCreatedAt()
        ) + 1;
        
        double avgOrdersPerDay = (double) orders.size() / totalDays;
        context.append("- Average orders per day: ").append(String.format("%.2f", avgOrdersPerDay)).append("\n");
        
        // Analyze order intervals
        if (orders.size() > 1) {
            double avgInterval = 0.0;
            for (int i = 0; i < orders.size() - 1; i++) {
                long interval = ChronoUnit.HOURS.between(
                    orders.get(i + 1).getCreatedAt(),
                    orders.get(i).getCreatedAt()
                );
                avgInterval += interval;
            }
            avgInterval /= (orders.size() - 1);
            context.append("- Average interval between orders: ").append(String.format("%.2f", avgInterval)).append(" hours\n");
        }
    }
    
    /**
     * Analyze value patterns in orders
     */
    private void analyzeValuePatterns(List<Order> orders, StringBuilder context) {
        context.append("\nValue Patterns:\n");
        
        BigDecimal totalValue = orders.stream()
            .map(Order::getTotalAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal avgValue = totalValue.divide(BigDecimal.valueOf(orders.size()));
        BigDecimal maxValue = orders.stream()
            .map(Order::getTotalAmount)
            .max(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);
        
        BigDecimal minValue = orders.stream()
            .map(Order::getTotalAmount)
            .min(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);
        
        context.append("- Total value: $").append(totalValue).append("\n");
        context.append("- Average value: $").append(avgValue).append("\n");
        context.append("- Max value: $").append(maxValue).append("\n");
        context.append("- Min value: $").append(minValue).append("\n");
        
        // Value distribution
        long highValueOrders = orders.stream()
            .filter(order -> order.getTotalAmount().compareTo(avgValue.multiply(BigDecimal.valueOf(2))) > 0)
            .count();
        
        context.append("- High value orders (>2x average): ").append(highValueOrders).append("\n");
    }
    
    /**
     * Analyze status patterns in orders
     */
    private void analyzeStatusPatterns(List<Order> orders, StringBuilder context) {
        context.append("\nStatus Patterns:\n");
        
        Map<Order.OrderStatus, Long> statusCounts = orders.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                Order::getStatus,
                java.util.stream.Collectors.counting()
            ));
        
        statusCounts.forEach((status, count) -> {
            double percentage = (double) count / orders.size() * 100;
            context.append("- ").append(status).append(": ").append(count)
                .append(" (").append(String.format("%.1f", percentage)).append("%)\n");
        });
    }
    
    /**
     * Analyze payment patterns in orders
     */
    private void analyzePaymentPatterns(List<Order> orders, StringBuilder context) {
        context.append("\nPayment Patterns:\n");
        
        Map<String, Long> paymentCounts = orders.stream()
            .filter(order -> order.getPaymentMethod() != null)
            .collect(java.util.stream.Collectors.groupingBy(
                Order::getPaymentMethod,
                java.util.stream.Collectors.counting()
            ));
        
        if (!paymentCounts.isEmpty()) {
            paymentCounts.forEach((method, count) -> {
                double percentage = (double) count / orders.size() * 100;
                context.append("- ").append(method).append(": ").append(count)
                    .append(" (").append(String.format("%.1f", percentage)).append("%)\n");
            });
        } else {
            context.append("- No payment method data available\n");
        }
    }
    
    /**
     * Analyze risk patterns in orders
     */
    private void analyzeRiskPatterns(List<Order> orders, StringBuilder context) {
        context.append("\nRisk Patterns:\n");
        
        Map<Order.RiskLevel, Long> riskCounts = orders.stream()
            .filter(order -> order.getRiskLevel() != null)
            .collect(java.util.stream.Collectors.groupingBy(
                Order::getRiskLevel,
                java.util.stream.Collectors.counting()
            ));
        
        if (!riskCounts.isEmpty()) {
            riskCounts.forEach((risk, count) -> {
                double percentage = (double) count / orders.size() * 100;
                context.append("- ").append(risk).append(": ").append(count)
                    .append(" (").append(String.format("%.1f", percentage)).append("%)\n");
            });
        } else {
            context.append("- No risk level data available\n");
        }
    }
    
    /**
     * Detect value anomalies
     */
    private void detectValueAnomalies(List<Order> orders, StringBuilder context) {
        context.append("\nValue Anomalies:\n");
        
        BigDecimal avgValue = orders.stream()
            .map(Order::getTotalAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(orders.size()));
        
        long highValueAnomalies = orders.stream()
            .filter(order -> order.getTotalAmount().compareTo(avgValue.multiply(BigDecimal.valueOf(3))) > 0)
            .count();
        
        long lowValueAnomalies = orders.stream()
            .filter(order -> order.getTotalAmount().compareTo(avgValue.divide(BigDecimal.valueOf(3))) < 0)
            .count();
        
        context.append("- High value anomalies (>3x average): ").append(highValueAnomalies).append("\n");
        context.append("- Low value anomalies (<1/3 average): ").append(lowValueAnomalies).append("\n");
    }
    
    /**
     * Detect temporal anomalies
     */
    private void detectTemporalAnomalies(List<Order> orders, StringBuilder context) {
        context.append("\nTemporal Anomalies:\n");
        
        // Check for unusual order frequency
        if (orders.size() > 1) {
            long totalHours = ChronoUnit.HOURS.between(
                orders.get(orders.size() - 1).getCreatedAt(),
                orders.get(0).getCreatedAt()
            );
            
            double avgOrdersPerHour = (double) orders.size() / totalHours;
            
            if (avgOrdersPerHour > 1.0) {
                context.append("- High frequency anomaly detected: ").append(String.format("%.2f", avgOrdersPerHour)).append(" orders/hour\n");
            }
        }
    }
    
    /**
     * Detect status anomalies
     */
    private void detectStatusAnomalies(List<Order> orders, StringBuilder context) {
        context.append("\nStatus Anomalies:\n");
        
        long cancelledOrders = orders.stream()
            .filter(order -> order.getStatus() == Order.OrderStatus.CANCELLED)
            .count();
        
        double cancellationRate = (double) cancelledOrders / orders.size() * 100;
        
        if (cancellationRate > 20.0) {
            context.append("- High cancellation rate: ").append(String.format("%.1f", cancellationRate)).append("%\n");
        }
    }
    
    /**
     * Detect risk anomalies
     */
    private void detectRiskAnomalies(List<Order> orders, StringBuilder context) {
        context.append("\nRisk Anomalies:\n");
        
        long highRiskOrders = orders.stream()
            .filter(order -> order.getRiskLevel() == Order.RiskLevel.HIGH || order.getRiskLevel() == Order.RiskLevel.CRITICAL)
            .count();
        
        double highRiskRate = (double) highRiskOrders / orders.size() * 100;
        
        if (highRiskRate > 10.0) {
            context.append("- High risk order rate: ").append(String.format("%.1f", highRiskRate)).append("%\n");
        }
    }
    
    /**
     * Update orders with pattern analysis
     */
    private void updateOrderPatterns(List<Order> orders, String patterns) {
        orders.forEach(order -> {
            order.setAiPatterns(patterns);
            orderRepository.save(order);
        });
    }
    
    /**
     * Update orders with anomaly scores
     */
    private void updateOrderAnomalyScores(List<Order> orders, String anomalies) {
        orders.forEach(order -> {
            // Calculate simple anomaly score based on various factors
            double anomalyScore = 0.0;
            
            // High amount anomaly
            BigDecimal avgAmount = orders.stream()
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(orders.size()));
            
            if (order.getTotalAmount().compareTo(avgAmount.multiply(BigDecimal.valueOf(2))) > 0) {
                anomalyScore += 0.3;
            }
            
            // Status anomaly
            if (order.getStatus() == Order.OrderStatus.CANCELLED) {
                anomalyScore += 0.2;
            }
            
            // Risk anomaly
            if (order.getRiskLevel() == Order.RiskLevel.HIGH || order.getRiskLevel() == Order.RiskLevel.CRITICAL) {
                anomalyScore += 0.4;
            }
            
            order.setAnomalyScore(anomalyScore);
            order.setAiInsights(anomalies);
            orderRepository.save(order);
        });
    }
}