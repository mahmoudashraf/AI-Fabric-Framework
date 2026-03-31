package com.ai.fabric.platform.backend.deployment.repository;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeploymentDraftRepository extends JpaRepository<DeploymentDraftEntity, String> {

    Optional<DeploymentDraftEntity> findTopByDeploymentIdOrderByRevisionNumberDesc(String deploymentId);
}

