package com.ai.fabric.platform.backend.marketplace.repository;

import com.ai.fabric.platform.backend.marketplace.entity.MarketplaceDatasetHandleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MarketplaceDatasetHandleRepository extends JpaRepository<MarketplaceDatasetHandleEntity, String> {

    Optional<MarketplaceDatasetHandleEntity> findByPluginIdAndTenantIdAndDatasetId(String pluginId, String tenantId, String datasetId);

    List<MarketplaceDatasetHandleEntity> findByDeploymentIdOrderByUpdatedAtDesc(String deploymentId);
}
