package com.ai.fabric.platform.backend.assistant;

import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthoringOperationRepository extends JpaRepository<AuthoringOperationEntity, String> {
    Optional<AuthoringOperationEntity> findByGoalIdAndOperationId(String goalId, String operationId);
    List<AuthoringOperationEntity> findByGoalIdOrderByCreatedAtAscIdAsc(String goalId, Pageable page);
    long countByGoalId(String goalId);
    long countByGoalIdAndSegment(String goalId, int segment);
    boolean existsByGoalIdAndUndoes(String goalId, String undoes);
    void deleteByGoalId(String goalId);
}
