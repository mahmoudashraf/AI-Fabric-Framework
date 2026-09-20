package com.ai.fabric.platform.backend.deployment.repository;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentBehaviorReadinessEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeploymentBehaviorReadinessRepository
    extends JpaRepository<DeploymentBehaviorReadinessEntity, String> {

    Optional<DeploymentBehaviorReadinessEntity> findByMaterialHash(String materialHash);

    List<DeploymentBehaviorReadinessEntity> findByBehaviorTypeOrderByUpdatedAtDesc(String behaviorType);

    List<DeploymentBehaviorReadinessEntity> findByTemplatePluginIdOrderByUpdatedAtDesc(String templatePluginId);

    List<DeploymentBehaviorReadinessEntity> findAllByOrderByUpdatedAtDesc();
}
