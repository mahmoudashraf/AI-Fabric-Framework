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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Selective text embeddings for feedback, reviews, and search queries.
 */
@Entity
@Table(name = "behavior_embeddings",
    indexes = {
        @Index(name = "idx_behavior_embedding_event", columnList = "behavior_event_id"),
        @Index(name = "idx_behavior_embedding_type", columnList = "embedding_type")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BehaviorEmbedding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "behavior_signal_id", nullable = false)
    private UUID behaviorSignalId;

    @Column(name = "embedding_type", nullable = false, length = 40)
    private String embeddingType;

    @Column(name = "original_text", columnDefinition = "TEXT")
    private String originalText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "embedding", columnDefinition = "jsonb")
    private List<Double> embedding;

    @Column(name = "model", length = 100)
    private String model;

    @Column(name = "detected_category", length = 100)
    private String detectedCategory;

    @Column(name = "sentiment_score")
    private Double sentimentScore;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
