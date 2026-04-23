package com.ai.fabric.product.shopify.bridge.analytics.repository;

import com.ai.fabric.product.shopify.bridge.analytics.entity.ShopifyBridgeQueryInsightDailyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface ShopifyBridgeQueryInsightDailyRepository extends JpaRepository<ShopifyBridgeQueryInsightDailyEntity, String> {

    @Modifying
    @Query(
        value = """
            merge into shopify_bridge_query_insights_daily as target
            using (
                values (
                    :id,
                    :shopDomain,
                    :usageDate,
                    :surfaceId,
                    :eventType,
                    :queryKey,
                    :sampleQuery,
                    :now,
                    :now,
                    :now
                )
            ) as source (
                id,
                shop_domain,
                usage_date,
                surface_id,
                event_type,
                query_key,
                sample_query,
                last_queried_at,
                created_at,
                updated_at
            )
            on target.shop_domain = source.shop_domain
               and target.usage_date = source.usage_date
               and target.surface_id = source.surface_id
               and target.event_type = source.event_type
               and target.query_key = source.query_key
            when matched then
                update set
                    sample_query = source.sample_query,
                    query_count = target.query_count + 1,
                    last_queried_at = source.last_queried_at,
                    updated_at = source.updated_at
            when not matched then
                insert (
                    id,
                    shop_domain,
                    usage_date,
                    surface_id,
                    event_type,
                    query_key,
                    sample_query,
                    query_count,
                    last_queried_at,
                    created_at,
                    updated_at
                )
                values (
                    source.id,
                    source.shop_domain,
                    source.usage_date,
                    source.surface_id,
                    source.event_type,
                    source.query_key,
                    source.sample_query,
                    1,
                    source.last_queried_at,
                    source.created_at,
                    source.updated_at
                )
            """,
        nativeQuery = true
    )
    int incrementDailyInsight(@Param("id") String id,
                              @Param("shopDomain") String shopDomain,
                              @Param("usageDate") LocalDate usageDate,
                              @Param("surfaceId") String surfaceId,
                              @Param("eventType") String eventType,
                              @Param("queryKey") String queryKey,
                              @Param("sampleQuery") String sampleQuery,
                              @Param("now") Instant now);

    List<ShopifyBridgeQueryInsightDailyEntity> findByShopDomainIgnoreCaseAndUsageDateGreaterThanEqualOrderByUsageDateAscSurfaceIdAscSampleQueryAsc(
        String shopDomain,
        LocalDate usageDate
    );

    List<ShopifyBridgeQueryInsightDailyEntity> findByUsageDateGreaterThanEqualOrderByUsageDateAscShopDomainAscSurfaceIdAscSampleQueryAsc(
        LocalDate usageDate
    );
}
