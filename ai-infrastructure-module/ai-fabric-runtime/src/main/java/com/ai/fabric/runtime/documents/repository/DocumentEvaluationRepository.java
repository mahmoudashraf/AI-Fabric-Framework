package com.ai.fabric.runtime.documents.repository;
import com.ai.fabric.runtime.documents.entity.DocumentEvaluationEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentEvaluationRepository extends JpaRepository<DocumentEvaluationEntity, String> {
    List<DocumentEvaluationEntity> findTop20BySourceIdOrderByCreatedAtDesc(String sourceId);
    List<DocumentEvaluationEntity> findTop100ByStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(java.util.Collection<String> states, java.time.Instant cutoff);
    @org.springframework.transaction.annotation.Transactional
    long deleteByCreatedAtBefore(java.time.Instant cutoff);
}
