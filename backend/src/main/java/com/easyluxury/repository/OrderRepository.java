package com.easyluxury.repository;

import com.easyluxury.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * OrderRepository
 * 
 * Repository interface for Order entity operations.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    
    /**
     * Find orders by user ID ordered by creation date descending
     */
    List<Order> findByUserIdOrderByCreatedAtDesc(UUID userId);
    
    /**
     * Find orders by user ID and created after specified date
     */
    List<Order> findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(UUID userId, LocalDateTime createdAfter);
    
    /**
     * Find orders by status
     */
    List<Order> findByStatusOrderByCreatedAtDesc(Order.OrderStatus status);
    
    /**
     * Find orders by risk level
     */
    List<Order> findByRiskLevelOrderByCreatedAtDesc(Order.RiskLevel riskLevel);
    
    /**
     * Find orders by payment method
     */
    List<Order> findByPaymentMethodOrderByCreatedAtDesc(String paymentMethod);
    
    /**
     * Find orders created after specified date
     */
    List<Order> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime createdAfter);
    
    /**
     * Find orders by user ID and status
     */
    List<Order> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, Order.OrderStatus status);
    
    /**
     * Count orders by user ID
     */
    long countByUserId(UUID userId);
    
    /**
     * Count orders by status
     */
    long countByStatus(Order.OrderStatus status);
    
    /**
     * Count orders by risk level
     */
    long countByRiskLevel(Order.RiskLevel riskLevel);
    
    /**
     * Find orders with high fraud score
     */
    @Query("SELECT o FROM Order o WHERE o.fraudScore >= :minScore ORDER BY o.fraudScore DESC")
    List<Order> findOrdersWithHighFraudScore(@Param("minScore") Double minScore);
    
    /**
     * Find orders with anomaly score
     */
    @Query("SELECT o FROM Order o WHERE o.anomalyScore >= :minScore ORDER BY o.anomalyScore DESC")
    List<Order> findOrdersWithAnomalyScore(@Param("minScore") Double minScore);
    
    /**
     * Find orders by amount range
     */
    @Query("SELECT o FROM Order o WHERE o.totalAmount BETWEEN :minAmount AND :maxAmount ORDER BY o.totalAmount DESC")
    List<Order> findByAmountRange(@Param("minAmount") BigDecimal minAmount, @Param("maxAmount") BigDecimal maxAmount);
    
    /**
     * Find orders by user ID and amount range
     */
    @Query("SELECT o FROM Order o WHERE o.userId = :userId AND o.totalAmount BETWEEN :minAmount AND :maxAmount ORDER BY o.totalAmount DESC")
    List<Order> findByUserIdAndAmountRange(@Param("userId") UUID userId, @Param("minAmount") BigDecimal minAmount, @Param("maxAmount") BigDecimal maxAmount);
    
    /**
     * Calculate total revenue by user ID
     */
    @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.userId = :userId AND o.status = 'DELIVERED'")
    BigDecimal calculateTotalRevenueByUser(@Param("userId") UUID userId);
    
    /**
     * Calculate total revenue by date range
     */
    @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.createdAt BETWEEN :startDate AND :endDate AND o.status = 'DELIVERED'")
    BigDecimal calculateTotalRevenueByDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
    
    /**
     * Calculate average order value by user ID
     */
    @Query("SELECT AVG(o.totalAmount) FROM Order o WHERE o.userId = :userId")
    BigDecimal calculateAverageOrderValueByUser(@Param("userId") UUID userId);
    
    /**
     * Calculate average order value by date range
     */
    @Query("SELECT AVG(o.totalAmount) FROM Order o WHERE o.createdAt BETWEEN :startDate AND :endDate")
    BigDecimal calculateAverageOrderValueByDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find order statistics by user ID
     */
    @Query("SELECT o.status, COUNT(o), SUM(o.totalAmount) FROM Order o WHERE o.userId = :userId GROUP BY o.status")
    List<Object[]> findOrderStatisticsByUser(@Param("userId") UUID userId);
    
    /**
     * Find order statistics by date range
     */
    @Query("SELECT o.status, COUNT(o), SUM(o.totalAmount) FROM Order o WHERE o.createdAt BETWEEN :startDate AND :endDate GROUP BY o.status")
    List<Object[]> findOrderStatisticsByDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find top users by order count
     */
    @Query("SELECT o.userId, COUNT(o) FROM Order o GROUP BY o.userId ORDER BY COUNT(o) DESC")
    List<Object[]> findTopUsersByOrderCount();
    
    /**
     * Find top users by revenue
     */
    @Query("SELECT o.userId, SUM(o.totalAmount) FROM Order o WHERE o.status = 'DELIVERED' GROUP BY o.userId ORDER BY SUM(o.totalAmount) DESC")
    List<Object[]> findTopUsersByRevenue();
    
    /**
     * Find orders with missing AI analysis
     */
    @Query("SELECT o FROM Order o WHERE o.aiAnalysis IS NULL ORDER BY o.createdAt DESC")
    List<Order> findOrdersWithMissingAIAnalysis();
    
    /**
     * Find orders with missing AI patterns
     */
    @Query("SELECT o FROM Order o WHERE o.aiPatterns IS NULL ORDER BY o.createdAt DESC")
    List<Order> findOrdersWithMissingAIPatterns();
}