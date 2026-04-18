package com.ai.fabric.product.shopify.bridge.install.repository;

import com.ai.fabric.product.shopify.bridge.install.entity.ShopifyInstallRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShopifyInstallRecordRepository extends JpaRepository<ShopifyInstallRecordEntity, String> {

    Optional<ShopifyInstallRecordEntity> findByShopDomainIgnoreCase(String shopDomain);
}
