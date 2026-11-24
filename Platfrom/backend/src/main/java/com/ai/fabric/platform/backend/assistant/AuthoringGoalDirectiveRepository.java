package com.ai.fabric.platform.backend.assistant;

import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthoringGoalDirectiveRepository extends JpaRepository<AuthoringGoalDirectiveEntity, String> {
    Optional<AuthoringGoalDirectiveEntity> findByGoalIdAndRequestKey(String goalId, String requestKey);
    Optional<AuthoringGoalDirectiveEntity> findByGoalIdAndRequestRevision(String goalId, long revision);
    List<AuthoringGoalDirectiveEntity> findByGoalIdOrderByRequestRevisionAsc(String goalId, Pageable page);
    List<AuthoringGoalDirectiveEntity> findByGoalId(String goalId);
}
