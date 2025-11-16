package com.ai.behavior.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Core immutable behavior event persisted for every tracked signal.
 */
@Entity
@Table(
    name = "behavior_events",
    indexes = {
        @Index(name = "idx_behavior_user_time", columnList = "user_id, timestamp DESC"),
        @Index(name = "idx_behavior_entity", columnList = "entity_type, entity_id"),
        @Index(name = "idx_behavior_type", columnList = "event_type"),
        @Index(name = "idx_behavior_session", columnList = "session_id"),
        @Index(name = "idx_behavior_timestamp", columnList = "timestamp DESC")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BehaviorEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private EventType eventType;

    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "entity_id", length = 255)
    private String entityId;

    @Column(name = "session_id", length = 255)
    private String sessionId;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @CreationTimestamp
    @Column(name = "ingested_at", nullable = false, updatable = false)
    private LocalDateTime ingestedAt;

    public void ensureMetadata() {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
    }

    public boolean isAnonymous() {
        return userId == null;
    }

    public String getUserIdentifier() {
        return userId != null ? userId.toString() : sessionId;
    }
}
