package com.ai.fabric.platform.backend.deployment.repository;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeploymentProviderResourceHandleRepository extends JpaRepository<DeploymentProviderResourceHandleEntity, String> {

    Optional<DeploymentProviderResourceHandleEntity> findTopByDeploymentIdOrderByUpdatedAtDesc(String deploymentId);
}
