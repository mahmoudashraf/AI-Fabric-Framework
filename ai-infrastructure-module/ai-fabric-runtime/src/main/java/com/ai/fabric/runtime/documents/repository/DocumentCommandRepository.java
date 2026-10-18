package com.ai.fabric.runtime.documents.repository;

import com.ai.fabric.runtime.documents.entity.DocumentCommandEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface DocumentCommandRepository extends JpaRepository<DocumentCommandEntity, String> {
    Optional<DocumentCommandEntity> findByTenantIdAndCustomerIdAndDeploymentIdAndTargetProfileIdAndReleaseIdAndOperationAndIdempotencyKey(
        String tenantId,
        String customerId,
        String deploymentId,
        String targetProfileId,
        String releaseId,
        String operation,
        String idempotencyKey
    );

    long deleteByTenantIdAndCustomerIdAndDeploymentIdAndTargetProfileIdAndReleaseIdAndCreatedAtBefore(
        String tenantId,
        String customerId,
        String deploymentId,
        String targetProfileId,
        String releaseId,
        Instant cutoff
    );
}
