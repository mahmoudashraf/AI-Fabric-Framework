package com.ai.fabric.product.shopify.bridge.analytics.repository;

import com.ai.fabric.product.shopify.bridge.analytics.entity.ShopifyBridgeUsageDailyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ShopifyBridgeUsageDailyRepository extends JpaRepository<ShopifyBridgeUsageDailyEntity, String> {

    Optional<ShopifyBridgeUsageDailyEntity> findByShopDomainIgnoreCaseAndUsageDateAndEventType(
        String shopDomain,
        LocalDate usageDate,
        String eventType
    );

    List<ShopifyBridgeUsageDailyEntity> findByShopDomainIgnoreCaseAndUsageDateGreaterThanEqualOrderByUsageDateAscEventTypeAsc(
        String shopDomain,
        LocalDate usageDate
    );
}
