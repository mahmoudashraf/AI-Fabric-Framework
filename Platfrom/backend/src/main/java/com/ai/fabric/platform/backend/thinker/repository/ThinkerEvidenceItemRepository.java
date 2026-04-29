package com.ai.fabric.platform.backend.thinker.repository;

import com.ai.fabric.platform.backend.thinker.entity.ThinkerEvidenceItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ThinkerEvidenceItemRepository extends JpaRepository<ThinkerEvidenceItemEntity, String> {
    List<ThinkerEvidenceItemEntity> findBySessionIdOrderByCapturedAtAsc(String sessionId);
}
