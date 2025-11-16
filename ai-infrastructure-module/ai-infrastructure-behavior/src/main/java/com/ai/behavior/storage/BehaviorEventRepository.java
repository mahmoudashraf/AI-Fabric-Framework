package com.ai.behavior.storage;

import com.ai.behavior.model.BehaviorEvent;
import com.ai.behavior.model.EventType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BehaviorEventRepository extends JpaRepository<BehaviorEvent, UUID>, JpaSpecificationExecutor<BehaviorEvent> {

    List<BehaviorEvent> findByUserIdOrderByTimestampDesc(UUID userId);

    List<BehaviorEvent> findBySessionIdOrderByTimestampDesc(String sessionId);

    List<BehaviorEvent> findByUserIdAndEventTypeOrderByTimestampDesc(UUID userId, EventType eventType);

    List<BehaviorEvent> findTop200ByUserIdOrderByTimestampDesc(UUID userId);

    List<BehaviorEvent> findByTimestampBetween(LocalDateTime start, LocalDateTime end);

    List<BehaviorEvent> findByUserIdAndTimestampBetween(UUID userId, LocalDateTime start, LocalDateTime end);

    Optional<BehaviorEvent> findFirstByUserIdOrderByTimestampDesc(UUID userId);

    long countByIngestedAtAfter(LocalDateTime ingestedAfter);

    long countByUserId(UUID userId);

    void deleteByUserId(UUID userId);

    long deleteByTimestampBefore(LocalDateTime cutoff);

    @Query("select distinct e.userId from BehaviorEvent e where e.userId is not null and e.timestamp >= :since")
    List<UUID> findDistinctUserIdsSince(@Param("since") LocalDateTime since);

    @Query("select e from BehaviorEvent e where e.userId = :userId order by e.timestamp desc")
    List<BehaviorEvent> findRecentEvents(@Param("userId") UUID userId, Pageable pageable);
}
