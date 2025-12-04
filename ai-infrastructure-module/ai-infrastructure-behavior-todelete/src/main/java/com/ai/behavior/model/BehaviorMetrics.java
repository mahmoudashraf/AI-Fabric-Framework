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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Real-time aggregated metrics updated by async workers.
 */
@Entity
@Table(name = "behavior_signal_metrics",
    indexes = {
        @Index(name = "idx_behavior_signal_metrics_user_date", columnList = "user_id,metric_date DESC")
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

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metrics", columnDefinition = "jsonb")
    private Map<String, Double> metrics;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attributes", columnDefinition = "jsonb")
    private Map<String, Object> attributes;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Map<String, Double> safeMetrics() {
        if (metrics == null) {
            metrics = new HashMap<>();
        }
        return metrics;
    }

    public void incrementMetric(String key, double delta) {
        safeMetrics().merge(key, delta, Double::sum);
    }

    public void setMetric(String key, double value) {
        safeMetrics().put(key, value);
    }

    public double metricValue(String key) {
        return safeMetrics().getOrDefault(key, 0.0d);
    }

    public Map<String, Object> safeAttributes() {
        if (attributes == null) {
            attributes = new HashMap<>();
        }
        return attributes;
    }
}
