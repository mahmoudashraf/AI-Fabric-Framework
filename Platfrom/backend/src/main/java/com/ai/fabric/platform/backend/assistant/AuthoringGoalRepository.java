package com.ai.fabric.platform.backend.assistant;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AuthoringGoalRepository extends JpaRepository<AuthoringGoalEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from AuthoringGoalEntity g where g.id = :id")
    Optional<AuthoringGoalEntity> lock(@Param("id") String id);
    Optional<AuthoringGoalEntity> findTopBySessionIdOrderByCreatedAtDesc(String sessionId);
    List<AuthoringGoalEntity> findBySessionId(String sessionId);
    List<AuthoringGoalEntity> findByActorIdAndStateNotIn(String actorId, Collection<String> terminalStates);
    List<AuthoringGoalEntity> findTop20ByNextPollAtBeforeOrderByNextPollAtAsc(Instant now);
}
