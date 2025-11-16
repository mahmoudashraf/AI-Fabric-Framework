package com.ai.behavior.repository;

import com.ai.behavior.model.BehaviorInsights;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BehaviorInsightsRepository extends JpaRepository<BehaviorInsights, UUID> {

    Optional<BehaviorInsights> findTop1ByUserIdOrderByAnalyzedAtDesc(UUID userId);

    Optional<BehaviorInsights> findByUserIdAndValidUntilAfter(UUID userId, LocalDateTime validAfter);

    List<BehaviorInsights> findByValidUntilBefore(LocalDateTime timestamp);

    void deleteByUserId(UUID userId);
}
