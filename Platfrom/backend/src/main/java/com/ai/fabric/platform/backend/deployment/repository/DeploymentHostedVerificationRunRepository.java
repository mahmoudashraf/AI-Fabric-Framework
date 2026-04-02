package com.ai.fabric.platform.backend.deployment.repository;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentHostedVerificationRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface DeploymentHostedVerificationRunRepository extends JpaRepository<DeploymentHostedVerificationRunEntity, String> {

    List<DeploymentHostedVerificationRunEntity> findByDeploymentIdOrderByCreatedAtDesc(String deploymentId);

    List<DeploymentHostedVerificationRunEntity> findTop20ByOrderByCreatedAtDesc();

    boolean existsByDeploymentIdAndStatusIn(String deploymentId, Collection<String> statuses);
}
