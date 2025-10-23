package com.easyluxury.entity;

import com.ai.infrastructure.annotation.AICapable;
import com.ai.infrastructure.annotation.AIEmbedding;
import com.ai.infrastructure.annotation.AIKnowledge;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Order Entity
 * 
 * Represents a customer order in the Easy Luxury system with AI capabilities.
 * This entity is marked as @AICapable to enable AI-powered features like
 * order analysis, pattern recognition, and fraud detection.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Entity
@Table(name = "orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@AICapable(entityType = "order")
public class Order {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @NotNull(message = "User ID is required")
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @NotNull(message = "Total amount is required")
    @Positive(message = "Total amount must be positive")
    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;
    
    @Column(name = "shipping_address")
    private String shippingAddress;
    
    @Column(name = "billing_address")
    private String billingAddress;
    
    @Column(name = "payment_method")
    private String paymentMethod;
    
    @Column(name = "notes")
    private String notes;
    
    @Column(name = "search_vector")
    private String searchVector;
    
    // AI Analysis fields
    @Column(name = "ai_analysis")
    private String aiAnalysis;
    
    @Column(name = "ai_patterns")
    private String aiPatterns;
    
    @Column(name = "fraud_score")
    private Double fraudScore;
    
    @Column(name = "risk_level")
    @Enumerated(EnumType.STRING)
    private RiskLevel riskLevel;
    
    @Column(name = "ai_insights")
    private String aiInsights;
    
    @Column(name = "pattern_flags")
    private String patternFlags;
    
    @Column(name = "anomaly_score")
    private Double anomalyScore;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    public enum OrderStatus {
        PENDING,
        CONFIRMED,
        PROCESSING,
        SHIPPED,
        DELIVERED,
        CANCELLED,
        REFUNDED
    }
    
    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
    
    // Helper methods for AI operations
    
    /**
     * Get the primary searchable text for AI operations
     */
    public String getSearchableText() {
        StringBuilder text = new StringBuilder();
        text.append(status.toString()).append(" ");
        if (shippingAddress != null) {
            text.append(shippingAddress).append(" ");
        }
        if (billingAddress != null) {
            text.append(billingAddress).append(" ");
        }
        if (paymentMethod != null) {
            text.append(paymentMethod).append(" ");
        }
        if (notes != null) {
            text.append(notes).append(" ");
        }
        if (aiAnalysis != null) {
            text.append(aiAnalysis).append(" ");
        }
        if (aiPatterns != null) {
            text.append(aiPatterns).append(" ");
        }
        if (aiInsights != null) {
            text.append(aiInsights).append(" ");
        }
        return text.toString().trim();
    }
    
    /**
     * Get metadata for AI operations
     */
    public Map<String, Object> getAIMetadata() {
        return Map.of(
            "id", id.toString(),
            "userId", userId.toString(),
            "totalAmount", totalAmount.toString(),
            "status", status.toString(),
            "fraudScore", fraudScore != null ? fraudScore : 0.0,
            "riskLevel", riskLevel != null ? riskLevel.toString() : "LOW",
            "anomalyScore", anomalyScore != null ? anomalyScore : 0.0,
            "patternFlags", patternFlags != null ? patternFlags : "",
            "createdAt", createdAt.toString()
        );
    }
}