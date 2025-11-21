package com.ai.behavior.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Canonical behavior signal entity. Optimized for write-heavy workloads with a compact set of fields.
 */
@Entity
@Table(name = "behavior_signals",
    indexes = {
        @Index(name = "idx_behavior_signals_user_time", columnList = "user_id,timestamp DESC"),
        @Index(name = "idx_behavior_signals_session_time", columnList = "session_id,timestamp DESC"),
        @Index(name = "idx_behavior_signals_entity", columnList = "entity_type,entity_id"),
        @Index(name = "idx_behavior_signals_schema_time", columnList = "schema_id,timestamp DESC"),
        @Index(name = "idx_behavior_signal_key", columnList = "schema_id,signal_key")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BehaviorSignal {

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

    @Column(name = "schema_id", nullable = false, length = 128)
    private String schemaId;

    @Column(name = "signal_key", length = 128)
    private String signalKey;

    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "entity_id", length = 255)
    private String entityId;

    @Column(name = "source", length = 50)
    private String source;

    @Column(name = "channel", length = 50)
    private String channel;

    @Builder.Default
    @Column(name = "version", length = 16)
    private String version = "1.0";

    @NotNull
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @CreationTimestamp
    @Column(name = "ingested_at", nullable = false, updatable = false)
    private LocalDateTime ingestedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attributes", columnDefinition = "jsonb")
    private Map<String, Object> attributes;

    public boolean isAnonymous() {
        return userId == null;
    }

    public String getUserIdentifier() {
        if (userId != null) {
            return userId.toString();
        }
        return sessionId;
    }

    public Map<String, Object> safeAttributes() {
        if (attributes == null) {
            attributes = new HashMap<>();
        }
        return attributes;
    }

    public Optional<String> attributeValue(String key) {
        if (attributes == null || !attributes.containsKey(key)) {
            return Optional.empty();
        }
        Object value = attributes.get(key);
        return Optional.ofNullable(value).map(Object::toString);
    }

}
