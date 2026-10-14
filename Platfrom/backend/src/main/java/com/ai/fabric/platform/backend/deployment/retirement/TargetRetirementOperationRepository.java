package com.ai.fabric.platform.backend.deployment.retirement;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import org.springframework.data.domain.Pageable;

import static com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.*;

public interface TargetRetirementOperationRepository extends JpaRepository<TargetRetirementOperationEntity, String> {
    Optional<TargetRetirementOperationEntity> findByDeploymentIdAndTargetProfileIdAndOperationKey(
        String deploymentId, String targetProfileId, String operationKey);
    List<TargetRetirementOperationEntity> findByDeploymentIdAndTargetProfileIdOrderByCreatedAtDesc(
        String deploymentId, String targetProfileId);
    @Query("select o from TargetRetirementOperationEntity o where o.state in :states and (o.nextRetryAt is null or o.nextRetryAt <= :now) order by o.updatedAt")
    List<TargetRetirementOperationEntity> findDue(@Param("states") Collection<State> states, @Param("now") Instant now, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from TargetRetirementOperationEntity o where o.id = :id")
    Optional<TargetRetirementOperationEntity> findForUpdate(@Param("id") String id);
}
