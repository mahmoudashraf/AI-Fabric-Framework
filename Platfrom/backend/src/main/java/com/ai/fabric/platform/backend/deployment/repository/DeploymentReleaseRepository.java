package com.ai.fabric.platform.backend.deployment.repository;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeploymentReleaseRepository extends JpaRepository<DeploymentReleaseEntity, String> {

    List<DeploymentReleaseEntity> findByDeploymentIdOrderByCreatedAtDesc(String deploymentId);
}

