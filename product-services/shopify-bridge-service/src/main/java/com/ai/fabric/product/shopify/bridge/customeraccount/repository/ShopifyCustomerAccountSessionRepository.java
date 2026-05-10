package com.ai.fabric.product.shopify.bridge.customeraccount.repository;

import com.ai.fabric.product.shopify.bridge.customeraccount.entity.ShopifyCustomerAccountSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShopifyCustomerAccountSessionRepository extends JpaRepository<ShopifyCustomerAccountSessionEntity, String> {

    Optional<ShopifyCustomerAccountSessionEntity> findByShopDomainIgnoreCaseAndShopperSessionIdHash(
        String shopDomain,
        String shopperSessionIdHash
    );

    Optional<ShopifyCustomerAccountSessionEntity> findByShopDomainIgnoreCaseAndShopperSessionIdHashAndRevokedAtIsNull(
        String shopDomain,
        String shopperSessionIdHash
    );
}
