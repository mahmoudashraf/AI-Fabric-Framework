package com.ai.behavior.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Temporary storage for ingested behavior events prior to background processing.
 */
@Entity
@Table(
        name = "ai_behavior_events_temp",
        indexes = {
                @Index(name = "idx_behavior_events_temp_user_created", columnList = "user_id, created_at DESC"),
                @Index(name = "idx_behavior_events_temp_processed_expires", columnList = "processed, expires_at")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BehaviorEventEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "event_type", nullable = false, length = 255)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_data", columnDefinition = "jsonb", nullable = false)
    private JsonNode eventData;

    @Column(length = 100)
    private String source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private boolean processed;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", length = 50)
    private BehaviorEventProcessingStatus processingStatus;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "last_error")
    private String lastError;

    @PrePersist
    public void applyDefaults() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (processingStatus == null) {
            processingStatus = BehaviorEventProcessingStatus.PENDING;
        }
    }

    public boolean isExpired(OffsetDateTime referenceTime) {
        return expiresAt != null && referenceTime.isAfter(expiresAt);
    }
}
