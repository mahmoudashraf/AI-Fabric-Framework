package com.ai.behavior.storage;

import com.ai.behavior.model.BehaviorInsights;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface BehaviorInsightsRepository extends JpaRepository<BehaviorInsights, UUID> {

    Optional<BehaviorInsights> findTopByUserIdOrderByAnalyzedAtDesc(UUID userId);

    long deleteByValidUntilBefore(LocalDateTime cutoff);

    void deleteByUserId(UUID userId);
}
