package com.ai.fabric.platform.backend.deployment.repository;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentPocImportRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeploymentPocImportRunRepository extends JpaRepository<DeploymentPocImportRunEntity, String> {

    List<DeploymentPocImportRunEntity> findTop10ByDeploymentIdOrderByCreatedAtDesc(String deploymentId);
}
