package com.ai.behavior.repository;

import com.ai.behavior.model.BehaviorEvent;
import com.ai.behavior.model.EventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BehaviorEventRepository extends JpaRepository<BehaviorEvent, UUID>, JpaSpecificationExecutor<BehaviorEvent> {

    List<BehaviorEvent> findByUserIdOrderByTimestampDesc(UUID userId);

    List<BehaviorEvent> findTop200ByUserIdOrderByTimestampDesc(UUID userId);

    List<BehaviorEvent> findByUserIdAndTimestampBetweenOrderByTimestampDesc(UUID userId, LocalDateTime start, LocalDateTime end);

    List<BehaviorEvent> findBySessionIdOrderByTimestampAsc(String sessionId);

    List<BehaviorEvent> findByEventTypeAndTimestampBetween(EventType eventType, LocalDateTime start, LocalDateTime end);

    @Query("select distinct e.userId from BehaviorEvent e where e.userId is not null and e.timestamp >= :since")
    List<UUID> findActiveUsersSince(LocalDateTime since);

    @Query("select e from BehaviorEvent e where e.timestamp >= :since order by e.timestamp desc")
    List<BehaviorEvent> findRecentEvents(LocalDateTime since);

    Optional<BehaviorEvent> findTop1ByUserIdOrderByTimestampDesc(UUID userId);

    void deleteByUserId(UUID userId);

    @Query("select e.id from BehaviorEvent e where e.userId = :userId")
    List<UUID> findIdsByUserId(UUID userId);
}
