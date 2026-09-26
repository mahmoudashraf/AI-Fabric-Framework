package com.ai.fabric.runtime.documents.repository;

import com.ai.fabric.runtime.documents.entity.DocumentCommandEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface DocumentCommandRepository extends JpaRepository<DocumentCommandEntity, String> {
    Optional<DocumentCommandEntity> findByTenantIdAndDeploymentIdAndOperationAndIdempotencyKey(
        String tenantId,
        String deploymentId,
        String operation,
        String idempotencyKey
    );

    long deleteByTenantIdAndDeploymentIdAndCreatedAtBefore(
        String tenantId,
        String deploymentId,
        Instant cutoff
    );

    long deleteByCreatedAtBefore(Instant cutoff);
}
