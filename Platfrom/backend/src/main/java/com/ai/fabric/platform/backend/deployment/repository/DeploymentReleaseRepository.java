package com.ai.fabric.platform.backend.deployment.repository;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DeploymentReleaseRepository extends JpaRepository<DeploymentReleaseEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from DeploymentReleaseEntity r where r.id = :id")
    java.util.Optional<DeploymentReleaseEntity> findByIdForUpdate(@Param("id") String id);

    List<DeploymentReleaseEntity> findByDeploymentIdOrderByCreatedAtDesc(String deploymentId);

    java.util.Optional<DeploymentReleaseEntity> findTopByDeploymentIdOrderByCreatedAtDesc(String deploymentId);

    long countByDeploymentId(String deploymentId);

    java.util.Optional<DeploymentReleaseEntity> findTopByDeploymentIdAndDeploymentVersionIdOrderByCreatedAtDesc(
        String deploymentId,
        String deploymentVersionId
    );

    long deleteByDeploymentId(String deploymentId);
}
