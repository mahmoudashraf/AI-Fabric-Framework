package com.ai.fabric.platform.backend.marketplace.repository;

import com.ai.fabric.platform.backend.marketplace.entity.MarketplaceDatasetDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface MarketplaceDatasetDocumentRepository extends JpaRepository<MarketplaceDatasetDocumentEntity, String> {

    List<MarketplaceDatasetDocumentEntity> findByDatasetHandleIdOrderByDocumentIdAsc(String datasetHandleId);

    void deleteByDatasetHandleIdAndDocumentIdIn(String datasetHandleId, Collection<String> documentIds);
}
