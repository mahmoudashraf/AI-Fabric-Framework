package com.ai.fabric.platform.backend.thinker.repository;

import com.ai.fabric.platform.backend.thinker.entity.ThinkerSessionAuditEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ThinkerSessionAuditEventRepository extends JpaRepository<ThinkerSessionAuditEventEntity, String> {
    List<ThinkerSessionAuditEventEntity> findBySessionIdOrderByCreatedAtAsc(String sessionId);
}
