package com.ai.behavior.repository;

import com.ai.behavior.model.BehaviorMetrics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BehaviorMetricsRepository extends JpaRepository<BehaviorMetrics, UUID> {

    Optional<BehaviorMetrics> findByUserIdAndMetricDate(UUID userId, LocalDate metricDate);

    List<BehaviorMetrics> findByUserIdAndMetricDateBetweenOrderByMetricDateDesc(UUID userId, LocalDate start, LocalDate end);

    void deleteByUserId(UUID userId);
}
