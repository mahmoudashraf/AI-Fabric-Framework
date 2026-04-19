package com.ai.fabric.product.shopify.bridge.analytics.repository;

import com.ai.fabric.product.shopify.bridge.analytics.entity.ShopifyBridgeUsageDailyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ShopifyBridgeUsageDailyRepository extends JpaRepository<ShopifyBridgeUsageDailyEntity, String> {

    Optional<ShopifyBridgeUsageDailyEntity> findByShopDomainIgnoreCaseAndUsageDateAndEventType(
        String shopDomain,
        LocalDate usageDate,
        String eventType
    );

    @Modifying
    @Query(
        value = """
            insert into shopify_bridge_usage_daily (
                id,
                shop_domain,
                usage_date,
                event_type,
                event_count,
                last_event_at,
                created_at,
                updated_at
            ) values (
                :id,
                :shopDomain,
                :usageDate,
                :eventType,
                1,
                :now,
                :now,
                :now
            )
            on conflict (shop_domain, usage_date, event_type)
            do update set
                event_count = shopify_bridge_usage_daily.event_count + 1,
                last_event_at = excluded.last_event_at,
                updated_at = excluded.updated_at
            """,
        nativeQuery = true
    )
    int incrementDailyEvent(@Param("id") String id,
                            @Param("shopDomain") String shopDomain,
                            @Param("usageDate") LocalDate usageDate,
                            @Param("eventType") String eventType,
                            @Param("now") Instant now);

    List<ShopifyBridgeUsageDailyEntity> findByShopDomainIgnoreCaseAndUsageDateGreaterThanEqualOrderByUsageDateAscEventTypeAsc(
        String shopDomain,
        LocalDate usageDate
    );

    List<ShopifyBridgeUsageDailyEntity> findByUsageDateGreaterThanEqualOrderByUsageDateAscShopDomainAscEventTypeAsc(
        LocalDate usageDate
    );
}
