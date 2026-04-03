package com.ai.fabric.platform.backend.deployment.repository;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentOperationApprovalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeploymentOperationApprovalRepository extends JpaRepository<DeploymentOperationApprovalEntity, String> {

    List<DeploymentOperationApprovalEntity> findByDeploymentIdOrderByCreatedAtDesc(String deploymentId);

    List<DeploymentOperationApprovalEntity> findByDeploymentIdAndRequestedByActorIdIgnoreCaseOrderByCreatedAtDesc(
        String deploymentId,
        String requestedByActorId
    );
}
