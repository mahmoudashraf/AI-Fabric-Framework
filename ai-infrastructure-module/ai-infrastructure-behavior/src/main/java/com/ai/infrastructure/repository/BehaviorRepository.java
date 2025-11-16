package com.ai.infrastructure.repository;

import com.ai.infrastructure.entity.Behavior;
import com.ai.infrastructure.entity.Behavior.BehaviorType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Legacy repository facade operating on the new behavior_events table.
 */
public interface BehaviorRepository extends JpaRepository<Behavior, UUID> {

    List<Behavior> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<Behavior> findByBehaviorType(BehaviorType behaviorType);

    List<Behavior> findByEntityTypeAndEntityId(String entityType, String entityId);

    List<Behavior> findBySessionId(String sessionId);

    List<Behavior> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    List<Behavior> findByUserIdAndCreatedAtBetween(UUID userId, LocalDateTime start, LocalDateTime end);

    Optional<Behavior> findByIdAndUserId(UUID id, UUID userId);
}
