package com.ai.infrastructure.chat.domain;

import com.ai.infrastructure.chat.exception.SessionExpiredException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Conversation aggregate that tracks turns and lifecycle metadata.
 */
@Entity
@Table(
    name = "chat_sessions",
    indexes = {
        @Index(name = "idx_owner_status", columnList = "owner_id,status"),
        @Index(name = "idx_last_interaction", columnList = "last_interaction_at")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSession {

    @Id
    @Column(length = 64)
    private String id;

    /**
     * Owner identifier (userId for authenticated or sessionId for anonymous).
     */
    @NotBlank
    @Column(name = "owner_id", nullable = false, length = 100)
    private String ownerId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_interaction_at", nullable = false)
    private LocalDateTime lastInteractionAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SessionStatus status = SessionStatus.ACTIVE;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "session_id")
    @OrderBy("timestamp ASC")
    @Builder.Default
    private List<ChatTurn> turns = new ArrayList<>();

    @Convert(converter = MetadataConverter.class)
    @Column(columnDefinition = "TEXT", name = "session_metadata")
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    @Version
    private Long version;

    public static ChatSession newSession(String conversationId, String ownerId, Clock clock) {
        LocalDateTime now = LocalDateTime.now(clock);
        return ChatSession.builder()
            .id(conversationId != null && !conversationId.isBlank() ? conversationId : UUID.randomUUID().toString())
            .ownerId(ownerId)
            .createdAt(now)
            .lastInteractionAt(now)
            .status(SessionStatus.ACTIVE)
            .turns(new ArrayList<>())
            .metadata(new HashMap<>())
            .build();
    }

    public void addTurn(ChatTurn turn, Clock clock) {
        if (turn == null) {
            return;
        }
        if (turns == null) {
            turns = new ArrayList<>();
        }
        turns.add(turn);
        lastInteractionAt = LocalDateTime.now(clock);
    }

    public List<ChatTurn> getRecentTurns(int limit) {
        if (turns == null || turns.isEmpty()) {
            return Collections.emptyList();
        }
        if (limit <= 0 || turns.size() <= limit) {
            return new ArrayList<>(turns);
        }
        int fromIndex = Math.max(0, turns.size() - limit);
        return new ArrayList<>(turns.subList(fromIndex, turns.size()));
    }

    public boolean isOwnedBy(String owner) {
        return owner != null && owner.equals(ownerId);
    }

    public boolean isExpired(int ttlMinutes, Clock clock) {
        if (ttlMinutes <= 0) {
            return false;
        }
        return lastInteractionAt != null &&
            lastInteractionAt.isBefore(LocalDateTime.now(clock).minusMinutes(ttlMinutes));
    }

    public void ensureActive(int ttlMinutes, Clock clock) {
        if (status != SessionStatus.ACTIVE) {
            throw new SessionExpiredException(id);
        }
        if (isExpired(ttlMinutes, clock)) {
            status = SessionStatus.EXPIRED;
            throw new SessionExpiredException(id);
        }
    }
}
