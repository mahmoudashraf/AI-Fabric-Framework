package com.ai.fabric.platform.backend.deployment.repository;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentPocScenarioEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeploymentPocScenarioRepository extends JpaRepository<DeploymentPocScenarioEntity, String> {

    List<DeploymentPocScenarioEntity> findByDeploymentIdOrderByCreatedAtAsc(String deploymentId);
}
