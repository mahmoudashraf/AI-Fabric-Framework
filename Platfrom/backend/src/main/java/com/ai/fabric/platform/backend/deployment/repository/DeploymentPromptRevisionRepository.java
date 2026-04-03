package com.ai.fabric.platform.backend.deployment.repository;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentPromptRevisionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeploymentPromptRevisionRepository extends JpaRepository<DeploymentPromptRevisionEntity, String> {

    List<DeploymentPromptRevisionEntity> findByDeploymentIdOrderByCreatedAtDesc(String deploymentId);

    void deleteByDeploymentId(String deploymentId);
}
