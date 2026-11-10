package com.ai.fabric.platform.backend.assistant;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthoringSessionRepository extends JpaRepository<AuthoringSessionEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AuthoringSessionEntity s where s.id = :id")
    Optional<AuthoringSessionEntity> lock(@Param("id") String id);
    List<AuthoringSessionEntity> findByActorIdAndResourceKindAndResourceIdOrderByUpdatedAtDesc(String actorId, String resourceKind, String resourceId);
    List<AuthoringSessionEntity> findTop20ByActorIdAndCustomerIdAndExpiresAtAfterOrderByUpdatedAtDesc(String actorId, String customerId, Instant expiry);
    long countByActorIdAndExpiresAtAfter(String actorId, Instant now);
    long countByCustomerIdAndStatus(String customerId, String status);
    long countByStatus(String status);
    long deleteByExpiresAtBefore(Instant now);
    List<AuthoringSessionEntity> findByStatusAndDeadlineBefore(String status, Instant deadline);
}
