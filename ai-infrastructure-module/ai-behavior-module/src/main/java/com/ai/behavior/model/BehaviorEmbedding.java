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
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Persisted embedding tied to a behavior event with textual payload.
 */
@Entity
@Table(
    name = "behavior_embeddings",
    indexes = {
        @Index(name = "idx_behavior_embedding_event", columnList = "behavior_event_id"),
        @Index(name = "idx_behavior_embedding_type", columnList = "embedding_type")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BehaviorEmbedding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "behavior_event_id", nullable = false)
    private UUID behaviorEventId;

    @Column(name = "embedding_type", nullable = false, length = 50)
    private String embeddingType;

    @Column(name = "original_text", columnDefinition = "TEXT")
    private String originalText;

    @Column(name = "embedding", columnDefinition = "float4[]")
    private float[] embedding;

    @Column(name = "model", length = 100)
    private String model;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "detected_category", length = 100)
    private String detectedCategory;

    @Column(name = "sentiment_score")
    private Double sentimentScore;
}
