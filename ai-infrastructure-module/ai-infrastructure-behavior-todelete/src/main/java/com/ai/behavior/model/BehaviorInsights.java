package com.ai.behavior.model;

import com.ai.infrastructure.annotation.AICapable;
import com.ai.infrastructure.annotation.AIProcess;
import com.ai.infrastructure.indexing.IndexingStrategy;
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

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pre-computed behavior insights for sub-10ms retrieval.
 */
@Entity
@Table(name = "behavior_insights",
    indexes = {
        @Index(name = "idx_behavior_insights_user", columnList = "user_id"),
        @Index(name = "idx_behavior_insights_valid_until", columnList = "valid_until")
    }
)
@AICapable(entityType = "behavior-insight", indexingStrategy = IndexingStrategy.ASYNC)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BehaviorInsights {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "patterns", columnDefinition = "jsonb")
    private List<String> patterns;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scores", columnDefinition = "jsonb")
    private Map<String, Double> scores;

    @Column(name = "segment", length = 50)
    private String segment;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferences", columnDefinition = "jsonb")
    private Map<String, Object> preferences;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recommendations", columnDefinition = "jsonb")
    private List<String> recommendations;

    @Column(name = "analyzed_at", nullable = false)
    private LocalDateTime analyzedAt;

    @Column(name = "valid_until", nullable = false)
    private LocalDateTime validUntil;

    @Column(name = "analysis_version", length = 32)
    private String analysisVersion;

    public boolean isValid() {
        return validUntil != null && validUntil.isAfter(LocalDateTime.now());
    }

    public Map<String, Double> safeScores() {
        if (scores == null) {
            scores = new HashMap<>();
        }
        return scores;
    }

    public String getSearchableContent() {
        return String.format(
            "Segment: %s Patterns: %s Recommendations: %s Scores: %s",
            segment != null ? segment : "unknown",
            patterns != null ? String.join(", ", patterns) : "",
            recommendations != null ? String.join(", ", recommendations) : "",
            scores != null ? scores.toString() : "{}"
        );
    }

    @AIProcess(
        entityType = "behavior-insight",
        processType = "analyze",
        indexingStrategy = IndexingStrategy.ASYNC
    )
    public void notifyInsightsReady() {
        // Intentionally no-op. AI Core intercepts this hook to trigger indexing.
    }
}
