package com.ai.fabric.platform.backend.marketplace.repository;

import com.ai.fabric.platform.backend.marketplace.entity.DeploymentMarketplacePluginInstallEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeploymentMarketplacePluginInstallRepository extends JpaRepository<DeploymentMarketplacePluginInstallEntity, String> {

    List<DeploymentMarketplacePluginInstallEntity> findByDeploymentIdOrderByUpdatedAtDesc(String deploymentId);

    Optional<DeploymentMarketplacePluginInstallEntity> findByDeploymentIdAndPluginId(String deploymentId, String pluginId);
}
