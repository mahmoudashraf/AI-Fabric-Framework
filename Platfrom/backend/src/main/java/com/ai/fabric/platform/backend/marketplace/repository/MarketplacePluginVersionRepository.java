package com.ai.fabric.platform.backend.marketplace.repository;

import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MarketplacePluginVersionRepository extends JpaRepository<MarketplacePluginVersionEntity, String> {

    List<MarketplacePluginVersionEntity> findByPluginIdOrderByPublishedAtDesc(String pluginId);

    Optional<MarketplacePluginVersionEntity> findByPluginIdAndVersion(String pluginId, String version);
}
