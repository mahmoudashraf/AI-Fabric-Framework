package com.ai.behavior.repository;

import com.ai.behavior.model.BehaviorEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BehaviorEmbeddingRepository extends JpaRepository<BehaviorEmbedding, UUID> {

    List<BehaviorEmbedding> findByBehaviorEventId(UUID behaviorEventId);

    List<BehaviorEmbedding> findTop100ByEmbeddingTypeOrderByCreatedAtDesc(String embeddingType);

    void deleteByBehaviorEventIdIn(List<UUID> eventIds);
}
