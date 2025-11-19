package com.ai.behavior.storage;

import com.ai.behavior.model.BehaviorSignal;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BehaviorSignalRepository extends JpaRepository<BehaviorSignal, UUID>, JpaSpecificationExecutor<BehaviorSignal> {

    List<BehaviorSignal> findByUserIdOrderByTimestampDesc(UUID userId);

    List<BehaviorSignal> findBySessionIdOrderByTimestampDesc(String sessionId);

    List<BehaviorSignal> findTop200ByUserIdOrderByTimestampDesc(UUID userId);

    List<BehaviorSignal> findByTimestampBetween(LocalDateTime start, LocalDateTime end);

    List<BehaviorSignal> findByUserIdAndTimestampBetween(UUID userId, LocalDateTime start, LocalDateTime end);

    Optional<BehaviorSignal> findFirstByUserIdOrderByTimestampDesc(UUID userId);

    long countByIngestedAtAfter(LocalDateTime ingestedAfter);

    long countByUserId(UUID userId);

    long countByUserIdAndTimestampAfter(UUID userId, LocalDateTime timestamp);

    void deleteByUserId(UUID userId);

    long deleteByTimestampBefore(LocalDateTime cutoff);

    @Query("select distinct e.userId from BehaviorSignal e where e.userId is not null and e.timestamp >= :since")
    List<UUID> findDistinctUserIdsSince(@Param("since") LocalDateTime since);

    @Query("select e from BehaviorSignal e where e.userId = :userId order by e.timestamp desc")
    List<BehaviorSignal> findRecentEvents(@Param("userId") UUID userId, Pageable pageable);
}
