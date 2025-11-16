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
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Canonical behavior event entity. Optimized for write-heavy workloads with a compact set of fields.
 */
@Entity
@Table(name = "behavior_events",
    indexes = {
        @Index(name = "idx_behavior_user_time", columnList = "user_id,timestamp DESC"),
        @Index(name = "idx_behavior_session_time", columnList = "session_id,timestamp DESC"),
        @Index(name = "idx_behavior_entity", columnList = "entity_type,entity_id"),
        @Index(name = "idx_behavior_event_type", columnList = "event_type")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BehaviorEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Optional tenant identifier for multi-tenant deployments.
     */
    @Column(name = "tenant_id", length = 64)
    private String tenantId;

    /**
     * Resolved user identifier. May be {@code null} for anonymous sessions.
     */
    @Column(name = "user_id")
    private UUID userId;

    /**
     * Session identifier – required for anonymous tracking.
     */
    @Column(name = "session_id", length = 128)
    private String sessionId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private EventType eventType;

    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "entity_id", length = 255)
    private String entityId;

    @Column(name = "source", length = 50)
    private String source;

    @Column(name = "channel", length = 50)
    private String channel;

    @NotNull
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @CreationTimestamp
    @Column(name = "ingested_at", nullable = false, updatable = false)
    private LocalDateTime ingestedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    public boolean isAnonymous() {
        return userId == null;
    }

    public String getUserIdentifier() {
        if (userId != null) {
            return userId.toString();
        }
        return sessionId;
    }

    public Map<String, Object> safeMetadata() {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        return metadata;
    }

    public Optional<String> metadataValue(String key) {
        if (metadata == null || !metadata.containsKey(key)) {
            return Optional.empty();
        }
        Object value = metadata.get(key);
        return Optional.ofNullable(value).map(Object::toString);
    }
}
