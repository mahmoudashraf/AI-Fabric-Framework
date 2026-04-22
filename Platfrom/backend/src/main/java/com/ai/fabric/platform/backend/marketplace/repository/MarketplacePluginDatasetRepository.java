package com.ai.fabric.platform.backend.marketplace.repository;

import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginDatasetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MarketplacePluginDatasetRepository extends JpaRepository<MarketplacePluginDatasetEntity, String> {

    List<MarketplacePluginDatasetEntity> findByPluginVersionIdOrderByDatasetIdAsc(String pluginVersionId);

    Optional<MarketplacePluginDatasetEntity> findByPluginVersionIdAndDatasetId(String pluginVersionId, String datasetId);
}
