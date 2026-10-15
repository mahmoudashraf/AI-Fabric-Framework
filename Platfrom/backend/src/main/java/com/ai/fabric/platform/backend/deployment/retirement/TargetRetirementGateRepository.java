package com.ai.fabric.platform.backend.deployment.retirement;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TargetRetirementGateRepository extends JpaRepository<TargetRetirementGateEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from TargetRetirementGateEntity g where g.deploymentId = :deployment and g.targetProfileId = :target")
    Optional<TargetRetirementGateEntity> findForUpdate(@Param("deployment") String deployment, @Param("target") String target);

    @Query("select g from TargetRetirementGateEntity g where g.deploymentId = :deployment and g.targetProfileId = :target")
    Optional<TargetRetirementGateEntity> findByDeploymentIdAndTargetProfileId(
        @Param("deployment") String deployment, @Param("target") String target);
}
