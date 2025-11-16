package com.ai.behavior.storage;

import com.ai.behavior.model.BehaviorEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface BehaviorEmbeddingRepository extends JpaRepository<BehaviorEmbedding, UUID> {

    List<BehaviorEmbedding> findByBehaviorEventIdIn(List<UUID> eventIds);

    void deleteByBehaviorEventId(UUID eventId);

    void deleteByBehaviorEventIdIn(List<UUID> eventIds);

    long deleteByCreatedAtBefore(LocalDateTime cutoff);
}
