package com.ai.fabric.platform.backend.marketplace.repository;

import com.ai.fabric.platform.backend.marketplace.entity.MarketplaceDatasetSyncRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MarketplaceDatasetSyncRunRepository extends JpaRepository<MarketplaceDatasetSyncRunEntity, String> {

    List<MarketplaceDatasetSyncRunEntity> findByDeploymentIdOrderByCreatedAtDesc(String deploymentId);

    Optional<MarketplaceDatasetSyncRunEntity> findFirstByDatasetHandleIdOrderByCreatedAtDesc(String datasetHandleId);

    List<MarketplaceDatasetSyncRunEntity> findByReleaseIdOrderByCreatedAtAsc(String releaseId);
}
