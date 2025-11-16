package com.ai.behavior.storage;

import com.ai.behavior.model.BehaviorMetrics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface BehaviorMetricsRepository extends JpaRepository<BehaviorMetrics, UUID> {

    Optional<BehaviorMetrics> findByUserIdAndMetricDate(UUID userId, LocalDate date);

    long deleteByMetricDateBefore(LocalDate cutoff);

    void deleteByUserId(UUID userId);

    List<BehaviorMetrics> findTop30ByUserIdOrderByMetricDateDesc(UUID userId);
}
