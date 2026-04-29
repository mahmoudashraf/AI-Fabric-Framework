package com.ai.fabric.platform.backend.shopify.repository;

import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreProvisioningJobEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ShopifyStoreProvisioningJobRepository extends JpaRepository<ShopifyStoreProvisioningJobEntity, String> {

    List<ShopifyStoreProvisioningJobEntity> findByShopDomainIgnoreCaseOrderByCreatedAtDesc(String shopDomain);

    Optional<ShopifyStoreProvisioningJobEntity> findFirstByShopDomainIgnoreCaseOrderByCreatedAtDesc(String shopDomain);

    Optional<ShopifyStoreProvisioningJobEntity> findFirstByShopDomainIgnoreCaseAndStatusInOrderByCreatedAtDesc(
        String shopDomain,
        Collection<String> statuses
    );

    @Query("""
        select job
        from ShopifyStoreProvisioningJobEntity job
        where job.status in :statuses
          and (job.leaseExpiresAt is null or job.leaseExpiresAt < :now)
        order by job.createdAt asc
        """)
    List<ShopifyStoreProvisioningJobEntity> findAvailableForLease(@Param("statuses") Collection<String> statuses,
                                                                  @Param("now") Instant now,
                                                                  Pageable pageable);
}
