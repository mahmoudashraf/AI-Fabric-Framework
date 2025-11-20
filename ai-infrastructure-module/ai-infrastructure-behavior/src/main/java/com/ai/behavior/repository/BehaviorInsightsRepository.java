package com.ai.behavior.repository;

import com.ai.behavior.model.BehaviorInsights;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BehaviorInsightsRepository extends JpaRepository<BehaviorInsights, UUID> {

    Optional<BehaviorInsights> findTopByUserIdOrderByAnalyzedAtDesc(UUID userId);

    List<BehaviorInsights> findByUserIdIn(List<UUID> userIds);

    List<BehaviorInsights> findBySegmentOrderByAnalyzedAtDesc(String segment);

    List<BehaviorInsights> findByValidUntilBefore(LocalDateTime cutoff);

    long deleteByValidUntilBefore(LocalDateTime cutoff);

    void deleteByUserId(UUID userId);
}
