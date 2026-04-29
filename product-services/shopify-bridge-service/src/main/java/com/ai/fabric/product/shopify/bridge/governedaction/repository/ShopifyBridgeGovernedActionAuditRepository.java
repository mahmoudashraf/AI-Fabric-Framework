package com.ai.fabric.product.shopify.bridge.governedaction.repository;

import com.ai.fabric.product.shopify.bridge.governedaction.entity.ShopifyBridgeGovernedActionAuditEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShopifyBridgeGovernedActionAuditRepository extends JpaRepository<ShopifyBridgeGovernedActionAuditEntity, String> {

    List<ShopifyBridgeGovernedActionAuditEntity> findByShopDomainIgnoreCaseOrderByCreatedAtDesc(String shopDomain, Pageable pageable);
}
