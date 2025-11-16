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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Real-time aggregated metrics updated by async workers.
 */
@Entity
@Table(name = "behavior_metrics",
    indexes = {
        @Index(name = "idx_behavior_metrics_user_date", columnList = "user_id,metric_date DESC"),
        @Index(name = "idx_behavior_metrics_date", columnList = "metric_date DESC")
    }
)
@Getter
@Setter
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
    private int viewCount;

    @Column(name = "click_count")
    private int clickCount;

    @Column(name = "search_count")
    private int searchCount;

    @Column(name = "add_to_cart_count")
    private int addToCartCount;

    @Column(name = "purchase_count")
    private int purchaseCount;

    @Column(name = "feedback_count")
    private int feedbackCount;

    @Column(name = "session_count")
    private int sessionCount;

    @Column(name = "avg_session_duration_seconds")
    private int avgSessionDurationSeconds;

    @Column(name = "conversion_rate")
    private double conversionRate;

    @Column(name = "total_revenue")
    private double totalRevenue;

    public void incrementView() {
        viewCount++;
    }

    public void incrementClick() {
        clickCount++;
    }

    public void incrementSearch() {
        searchCount++;
    }

    public void incrementAddToCart() {
        addToCartCount++;
    }

    public void incrementFeedback() {
        feedbackCount++;
    }

    public void incrementPurchase(double amount) {
        purchaseCount++;
        totalRevenue += amount;
        recalculateConversionRate();
    }

    private void recalculateConversionRate() {
        if (viewCount <= 0) {
            conversionRate = 0.0d;
            return;
        }
        conversionRate = (double) purchaseCount / viewCount;
    }
}
