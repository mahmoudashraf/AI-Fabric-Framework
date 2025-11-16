package com.ai.infrastructure.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Legacy compatibility entity mapping onto the new behavior_events table.
 * Non-core fields are sourced from the metadata JSON column.
 */
@Entity
@Table(name = "behavior_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Behavior {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private BehaviorType behaviorType;

    @Column(name = "entity_type")
    private String entityType;

    @Column(name = "entity_id")
    private String entityId;

    @Column(name = "session_id")
    private String sessionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime createdAt;

    @CreationTimestamp
    @Column(name = "ingested_at", nullable = false, updatable = false)
    private LocalDateTime ingestedAt;

    public String getAction() {
        return metadataValue("action");
    }

    public String getContext() {
        return metadataValue("context");
    }

    public String getDeviceInfo() {
        return metadataValue("deviceInfo");
    }

    public String getLocationInfo() {
        return metadataValue("locationInfo");
    }

    public Long getDurationSeconds() {
        String value = metadataValue("durationSeconds");
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public String getValue() {
        return metadataValue("value");
    }

    public String getMetadataAsString() {
        return metadata != null ? metadata.toString() : null;
    }

    private String metadataValue(String key) {
        if (metadata == null || key == null) {
            return null;
        }
        Object value = metadata.get(key);
        return value != null ? value.toString() : null;
    }

    public enum BehaviorType {
        VIEW,
        CLICK,
        SEARCH,
        FILTER,
        ADD_TO_CART,
        REMOVE_FROM_CART,
        PURCHASE,
        WISHLIST,
        FEEDBACK,
        REVIEW,
        RATING,
        SHARE,
        SAVE,
        PRODUCT_VIEW,
        PAGE_VIEW,
        SEARCH_QUERY,
        RECOMMENDATION_VIEW,
        SESSION_START,
        ORDER_CREATED,
        LOGIN,
        CUSTOM
    }
}
