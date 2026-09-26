package com.ai.fabric.platform.backend.deployment.repository;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface DeploymentReleaseRepository extends JpaRepository<DeploymentReleaseEntity, String> {

    List<DeploymentReleaseEntity> findByStatusIn(Collection<String> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from DeploymentReleaseEntity r where r.id = :id")
    java.util.Optional<DeploymentReleaseEntity> findByIdForUpdate(@Param("id") String id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update DeploymentReleaseEntity r set r.updatedAt = :updatedAt where r.id = :id")
    int touchUpdatedAt(@Param("id") String id, @Param("updatedAt") Instant updatedAt);

    List<DeploymentReleaseEntity> findByDeploymentIdOrderByCreatedAtDesc(String deploymentId);

    java.util.Optional<DeploymentReleaseEntity> findTopByDeploymentIdOrderByCreatedAtDesc(String deploymentId);

    long countByDeploymentId(String deploymentId);

    java.util.Optional<DeploymentReleaseEntity> findTopByDeploymentIdAndDeploymentVersionIdOrderByCreatedAtDesc(
        String deploymentId,
        String deploymentVersionId
    );

    java.util.Optional<DeploymentReleaseEntity> findTopByDeploymentIdAndDeploymentVersionIdAndTargetProfileIdOrderByCreatedAtDesc(
        String deploymentId,
        String deploymentVersionId,
        String targetProfileId
    );

    List<DeploymentReleaseEntity> findByDeploymentIdAndTargetProfileIdOrderByCreatedAtDesc(
        String deploymentId,
        String targetProfileId
    );

    long deleteByDeploymentId(String deploymentId);
}
