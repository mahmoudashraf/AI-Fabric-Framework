package com.ai.fabric.platform.backend.shopify.repository;

import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreVectorizationEventEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ShopifyStoreVectorizationEventRepository extends JpaRepository<ShopifyStoreVectorizationEventEntity, String> {

    Optional<ShopifyStoreVectorizationEventEntity> findByDedupeKey(String dedupeKey);

    @Query("""
        select event
        from ShopifyStoreVectorizationEventEntity event
        where event.status in :statuses
          and event.nextAttemptAt <= :availableAt
          and (event.leaseExpiresAt is null or event.leaseExpiresAt < :availableAt)
        order by event.occurredAt asc
        """)
    List<ShopifyStoreVectorizationEventEntity> findAvailableForLease(Collection<String> statuses, Instant availableAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select event
        from ShopifyStoreVectorizationEventEntity event
        where event.aggregateKey = :aggregateKey
          and event.status in :statuses
        order by event.occurredAt asc
        """)
    List<ShopifyStoreVectorizationEventEntity> findByAggregateKeyForUpdate(String aggregateKey, Collection<String> statuses);

    List<ShopifyStoreVectorizationEventEntity> findTop100ByShopDomainOrderByOccurredAtDesc(String shopDomain);

    List<ShopifyStoreVectorizationEventEntity> findByCoalescedRunId(String coalescedRunId);

    long countByShopDomainAndStatusIn(String shopDomain, Collection<String> statuses);

    Optional<ShopifyStoreVectorizationEventEntity> findTopByShopDomainAndStatusInOrderByOccurredAtDesc(String shopDomain, Collection<String> statuses);

    List<ShopifyStoreVectorizationEventEntity> findByStatusInAndRetentionExpiresAtBefore(Collection<String> statuses, Instant retentionExpiresAt);
}
