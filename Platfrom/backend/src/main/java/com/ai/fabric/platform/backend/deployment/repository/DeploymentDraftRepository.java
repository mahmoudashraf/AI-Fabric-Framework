package com.ai.fabric.platform.backend.deployment.repository;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DeploymentDraftRepository extends JpaRepository<DeploymentDraftEntity, String> {

    @Query("select draft.deploymentId from DeploymentDraftEntity draft where draft.id = :id")
    Optional<String> findDeploymentIdById(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select draft from DeploymentDraftEntity draft where draft.id = :id")
    Optional<DeploymentDraftEntity> findByIdForUpdate(@Param("id") String id);

    Optional<DeploymentDraftEntity> findTopByDeploymentIdOrderByRevisionNumberDesc(String deploymentId);

    long deleteByDeploymentId(String deploymentId);
}
