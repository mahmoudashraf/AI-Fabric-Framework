package com.ai.fabric.platform.backend.deployment.repository;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentDeletionOperationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeploymentDeletionOperationRepository extends JpaRepository<DeploymentDeletionOperationEntity, String> {

    List<DeploymentDeletionOperationEntity> findTop200ByOrderByCreatedAtDesc();

    List<DeploymentDeletionOperationEntity> findTop200ByStatusOrderByCreatedAtDesc(String status);

    List<DeploymentDeletionOperationEntity> findByDeploymentIdOrderByCreatedAtDesc(String deploymentId);
}
