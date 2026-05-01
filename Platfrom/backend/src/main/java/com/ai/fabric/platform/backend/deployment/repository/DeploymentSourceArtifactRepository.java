package com.ai.fabric.platform.backend.deployment.repository;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentSourceArtifactEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeploymentSourceArtifactRepository extends JpaRepository<DeploymentSourceArtifactEntity, String> {
}
