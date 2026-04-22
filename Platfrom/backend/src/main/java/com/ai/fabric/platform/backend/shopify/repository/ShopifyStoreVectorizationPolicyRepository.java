package com.ai.fabric.platform.backend.shopify.repository;

import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreVectorizationPolicyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShopifyStoreVectorizationPolicyRepository extends JpaRepository<ShopifyStoreVectorizationPolicyEntity, String> {

    Optional<ShopifyStoreVectorizationPolicyEntity> findByShopDomainIgnoreCase(String shopDomain);
}
