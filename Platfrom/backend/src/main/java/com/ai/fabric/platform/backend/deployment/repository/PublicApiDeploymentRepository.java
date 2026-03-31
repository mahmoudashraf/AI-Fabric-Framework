package com.ai.fabric.platform.backend.deployment.repository;

import com.ai.fabric.platform.backend.deployment.entity.PublicApiDeploymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PublicApiDeploymentRepository extends JpaRepository<PublicApiDeploymentEntity, String> {

    Optional<PublicApiDeploymentEntity> findByClientIdAndExternalDeploymentKey(String clientId, String externalDeploymentKey);

    Optional<PublicApiDeploymentEntity> findByClientIdAndDeploymentId(String clientId, String deploymentId);
}
