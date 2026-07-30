package com.ai.fabric.platform.backend.deployment.repository;

import com.ai.fabric.platform.backend.deployment.entity.EntityConfigMigrationAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EntityConfigMigrationAuditRepository
    extends JpaRepository<EntityConfigMigrationAuditEntity, String> {

    List<EntityConfigMigrationAuditEntity> findByDraftIdOrderByCreatedAtDesc(String draftId);
}
