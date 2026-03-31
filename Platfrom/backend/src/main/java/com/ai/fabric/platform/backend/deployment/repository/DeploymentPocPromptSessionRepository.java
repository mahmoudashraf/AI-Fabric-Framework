package com.ai.fabric.platform.backend.deployment.repository;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentPocPromptSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeploymentPocPromptSessionRepository extends JpaRepository<DeploymentPocPromptSessionEntity, String> {

    Optional<DeploymentPocPromptSessionEntity> findByDeploymentIdAndActorId(String deploymentId, String actorId);
}
