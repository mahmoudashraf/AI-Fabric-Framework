package com.ai.behavior.storage;

import com.ai.behavior.model.BehaviorEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface BehaviorEmbeddingRepository extends JpaRepository<BehaviorEmbedding, UUID> {

    List<BehaviorEmbedding> findByBehaviorSignalIdIn(List<UUID> eventIds);

    void deleteByBehaviorSignalId(UUID eventId);

    void deleteByBehaviorSignalIdIn(List<UUID> eventIds);

    long deleteByCreatedAtBefore(LocalDateTime cutoff);
}
