package com.ai.behavior.repository;

import com.ai.behavior.model.BehaviorEventEntity;
import com.ai.behavior.model.BehaviorEventProcessingStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface BehaviorEventRepository extends JpaRepository<BehaviorEventEntity, UUID> {

    @Query("""
        SELECT e FROM BehaviorEventEntity e
        WHERE e.processed = false
        ORDER BY e.createdAt ASC
        """)
    List<BehaviorEventEntity> findUnprocessedEvents(Pageable pageable);

    @Query("""
        SELECT e FROM BehaviorEventEntity e
        WHERE e.expiresAt IS NOT NULL AND e.expiresAt <= :cutoff
        """)
    List<BehaviorEventEntity> findExpiredEvents(@Param("cutoff") OffsetDateTime cutoff);

    List<BehaviorEventEntity> findByUserIdAndProcessedIsFalse(UUID userId);

    List<BehaviorEventEntity> findByProcessingStatus(BehaviorEventProcessingStatus status, Pageable pageable);

    long deleteByExpiresAtBefore(OffsetDateTime cutoff);
}
