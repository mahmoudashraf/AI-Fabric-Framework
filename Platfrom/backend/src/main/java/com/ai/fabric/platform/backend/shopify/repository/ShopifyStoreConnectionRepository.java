package com.ai.fabric.platform.backend.shopify.repository;

import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShopifyStoreConnectionRepository extends JpaRepository<ShopifyStoreConnectionEntity, String> {

    List<ShopifyStoreConnectionEntity> findAllByOrderByCreatedAtDesc();

    List<ShopifyStoreConnectionEntity> findAllByProductServiceIdOrderByShopDomainAsc(String productServiceId);

    Optional<ShopifyStoreConnectionEntity> findByProductServiceIdAndShopDomainIgnoreCase(String productServiceId, String shopDomain);

    Optional<ShopifyStoreConnectionEntity> findByShopDomainIgnoreCase(String shopDomain);

    Optional<ShopifyStoreConnectionEntity> findByConsumerIdIgnoreCase(String consumerId);

    Optional<ShopifyStoreConnectionEntity> findByDeploymentId(String deploymentId);

    long countByProductServiceId(String productServiceId);

    long countByProductServiceIdAndInstallStatusIgnoreCase(String productServiceId, String installStatus);
}
