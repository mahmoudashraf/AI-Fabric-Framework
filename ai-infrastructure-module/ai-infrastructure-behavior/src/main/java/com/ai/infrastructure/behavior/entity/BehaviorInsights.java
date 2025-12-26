package com.ai.infrastructure.behavior.entity;

import com.ai.infrastructure.annotation.AICapable;
import com.ai.infrastructure.behavior.converter.JsonbListConverter;
import com.ai.infrastructure.behavior.converter.JsonbMapConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ai_behavior_insights")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@AICapable(
    entityType = "behavior-insight",
    autoEmbedding = true,
    indexable = true
)
public class BehaviorInsights {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;
    
    @Column(name = "segment", length = 100)
    private String segment;
    
    @Column(name = "patterns", columnDefinition = "jsonb")
    @Convert(converter = JsonbListConverter.class)
    private List<String> patterns;
    
    @Column(name = "recommendations", columnDefinition = "jsonb")
    @Convert(converter = JsonbListConverter.class)
    private List<String> recommendations;
    
    @Column(name = "insights", columnDefinition = "jsonb")
    @Convert(converter = JsonbMapConverter.class)
    private Map<String, Object> insights;
    
    @Column(name = "analyzed_at", nullable = false)
    private LocalDateTime analyzedAt;
    
    @Column(name = "confidence")
    private Double confidence;
    
    @Column(name = "ai_model_used", length = 50)
    private String aiModelUsed;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    /**
     * Framework uses this to build searchable content for AISearchableEntity.
     */
    public String getSearchableContent() {
        return String.format(
            "Segment: %s. Patterns: %s. Recommendations: %s. Confidence: %.2f",
            segment != null ? segment : "Unknown",
            patterns != null ? String.join(", ", patterns) : "None",
            recommendations != null ? String.join(", ", recommendations) : "None",
            confidence != null ? confidence : 0.0
        );
    }
}
