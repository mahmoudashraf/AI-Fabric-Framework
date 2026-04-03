package com.ai.fabric.platform.backend.deployment.repository;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentAssignmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeploymentAssignmentRepository extends JpaRepository<DeploymentAssignmentEntity, String> {

    List<DeploymentAssignmentEntity> findAllByOrderByCreatedAtAsc();

    List<DeploymentAssignmentEntity> findByDeploymentIdOrderByCreatedAtAsc(String deploymentId);

    List<DeploymentAssignmentEntity> findByUserIdOrderByCreatedAtAsc(String userId);

    Optional<DeploymentAssignmentEntity> findByUserIdAndDeploymentId(String userId, String deploymentId);

    void deleteByDeploymentId(String deploymentId);
}
