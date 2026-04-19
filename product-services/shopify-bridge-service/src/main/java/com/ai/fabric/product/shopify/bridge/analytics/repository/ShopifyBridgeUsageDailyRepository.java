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
            merge into shopify_bridge_usage_daily as target
            using (
                values (
                    :id,
                    :shopDomain,
                    :usageDate,
                    :eventType,
                    :now,
                    :now,
                    :now
                )
            ) as source (
                id,
                shop_domain,
                usage_date,
                event_type,
                last_event_at,
                created_at,
                updated_at
            )
            on target.shop_domain = source.shop_domain
               and target.usage_date = source.usage_date
               and target.event_type = source.event_type
            when matched then
                update set
                    event_count = target.event_count + 1,
                    last_event_at = source.last_event_at,
                    updated_at = source.updated_at
            when not matched then
                insert (
                    id,
                    shop_domain,
                    usage_date,
                    event_type,
                    event_count,
                    last_event_at,
                    created_at,
                    updated_at
                )
                values (
                    source.id,
                    source.shop_domain,
                    source.usage_date,
                    source.event_type,
                    1,
                    source.last_event_at,
                    source.created_at,
                    source.updated_at
                )
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
