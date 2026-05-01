package com.ai.fabric.platform.backend.deployment.repository;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentVerificationRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeploymentVerificationRunRepository extends JpaRepository<DeploymentVerificationRunEntity, String> {

    List<DeploymentVerificationRunEntity> findByDeploymentIdOrderByCreatedAtDesc(String deploymentId);

    List<DeploymentVerificationRunEntity> findByReleaseIdOrderByCreatedAtDesc(String releaseId);

    long deleteByDeploymentId(String deploymentId);
}
