package com.ai.fabric.platform.backend.audit.repository;

import com.ai.fabric.platform.backend.audit.entity.PlatformAuditEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlatformAuditEventRepository extends JpaRepository<PlatformAuditEventEntity, String> {

    List<PlatformAuditEventEntity> findTop100ByOrderByCreatedAtDesc();
}
