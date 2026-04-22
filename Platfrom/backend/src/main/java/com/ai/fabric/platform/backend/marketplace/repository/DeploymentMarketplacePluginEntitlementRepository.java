package com.ai.fabric.platform.backend.marketplace.repository;

import com.ai.fabric.platform.backend.marketplace.entity.DeploymentMarketplacePluginEntitlementEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeploymentMarketplacePluginEntitlementRepository
    extends JpaRepository<DeploymentMarketplacePluginEntitlementEntity, String> {

    Optional<DeploymentMarketplacePluginEntitlementEntity> findByInstallId(String installId);
}
