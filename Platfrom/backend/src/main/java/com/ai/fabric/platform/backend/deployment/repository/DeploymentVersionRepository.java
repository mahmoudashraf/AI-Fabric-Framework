package com.ai.fabric.platform.backend.deployment.repository;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeploymentVersionRepository extends JpaRepository<DeploymentVersionEntity, String> {

    long countByDeploymentId(String deploymentId);

    List<DeploymentVersionEntity> findByDeploymentIdOrderByPublishedAtDesc(String deploymentId);
}

