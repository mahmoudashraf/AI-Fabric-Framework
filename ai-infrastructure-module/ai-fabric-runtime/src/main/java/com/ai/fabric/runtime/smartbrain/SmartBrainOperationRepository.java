package com.ai.fabric.runtime.smartbrain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SmartBrainOperationRepository extends JpaRepository<SmartBrainOperationEntity, String> {

    Optional<SmartBrainOperationEntity> findByTenantIdAndDeploymentIdAndTriggerCodeAndIdempotencyKey(
        String tenantId,
        String deploymentId,
        String triggerCode,
        String idempotencyKey
    );

    Optional<SmartBrainOperationEntity> findByOperationIdAndTenantIdAndDeploymentId(
        String operationId,
        String tenantId,
        String deploymentId
    );

    List<SmartBrainOperationEntity> findTop50ByStatusInOrderByCreatedAtAsc(Collection<String> statuses);

    List<SmartBrainOperationEntity> findTop50ByExpiresAtBeforeOrderByExpiresAtAsc(Instant cutoff);
}
