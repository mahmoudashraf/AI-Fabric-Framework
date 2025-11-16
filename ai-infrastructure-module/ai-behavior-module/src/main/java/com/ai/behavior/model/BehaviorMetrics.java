package com.ai.behavior.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Real-time aggregated counters for quick analytics.
 */
@Entity
@Table(
    name = "behavior_metrics",
    indexes = {
        @Index(name = "idx_behavior_metrics_user_date", columnList = "user_id, metric_date DESC"),
        @Index(name = "idx_behavior_metrics_date", columnList = "metric_date DESC")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BehaviorMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    @Column(name = "view_count")
    @Builder.Default
    private Integer viewCount = 0;

    @Column(name = "click_count")
    @Builder.Default
    private Integer clickCount = 0;

    @Column(name = "search_count")
    @Builder.Default
    private Integer searchCount = 0;

    @Column(name = "add_to_cart_count")
    @Builder.Default
    private Integer addToCartCount = 0;

    @Column(name = "purchase_count")
    @Builder.Default
    private Integer purchaseCount = 0;

    @Column(name = "feedback_count")
    @Builder.Default
    private Integer feedbackCount = 0;

    @Column(name = "session_count")
    @Builder.Default
    private Integer sessionCount = 0;

    @Column(name = "avg_session_duration_seconds")
    @Builder.Default
    private Integer avgSessionDurationSeconds = 0;

    @Column(name = "conversion_rate")
    @Builder.Default
    private Double conversionRate = 0.0;

    @Column(name = "total_revenue")
    @Builder.Default
    private Double totalRevenue = 0.0;

    public void incrementView() {
        viewCount = (viewCount == null ? 0 : viewCount) + 1;
    }

    public void incrementClick() {
        clickCount = (clickCount == null ? 0 : clickCount) + 1;
    }

    public void incrementSearch() {
        searchCount = (searchCount == null ? 0 : searchCount) + 1;
    }

    public void incrementAddToCart() {
        addToCartCount = (addToCartCount == null ? 0 : addToCartCount) + 1;
    }

    public void incrementFeedback() {
        feedbackCount = (feedbackCount == null ? 0 : feedbackCount) + 1;
    }

    public void incrementPurchase(double amount) {
        purchaseCount = (purchaseCount == null ? 0 : purchaseCount) + 1;
        totalRevenue = (totalRevenue == null ? 0 : totalRevenue) + amount;
        updateConversionRate();
    }

    private void updateConversionRate() {
        if (viewCount != null && viewCount > 0) {
            conversionRate = (double) purchaseCount / viewCount;
        }
    }
}
