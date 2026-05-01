package com.ai.fabric.platform.backend.shopify.repository;

import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreVectorizationLedgerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShopifyStoreVectorizationLedgerRepository extends JpaRepository<ShopifyStoreVectorizationLedgerEntity, String> {

    Optional<ShopifyStoreVectorizationLedgerEntity> findByDeploymentIdAndEntityTypeAndSourceObjectId(String deploymentId,
                                                                                                      String entityType,
                                                                                                      String sourceObjectId);

    List<ShopifyStoreVectorizationLedgerEntity> findByDeploymentIdAndEntityType(String deploymentId, String entityType);

    List<ShopifyStoreVectorizationLedgerEntity> findByDeploymentIdAndEntityTypeAndSourceCategory(String deploymentId,
                                                                                                  String entityType,
                                                                                                  String sourceCategory);
}
