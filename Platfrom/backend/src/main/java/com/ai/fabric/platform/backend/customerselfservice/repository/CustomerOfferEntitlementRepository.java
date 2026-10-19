package com.ai.fabric.platform.backend.customerselfservice.repository;

import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferEntitlementEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerOfferEntitlementRepository extends JpaRepository<CustomerOfferEntitlementEntity, String> {
    List<CustomerOfferEntitlementEntity> findByCustomerIdOrderByCreatedAtAsc(String customerId);
    Optional<CustomerOfferEntitlementEntity> findByCustomerIdAndOfferId(String customerId, String offerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from CustomerOfferEntitlementEntity e where e.customerId = :customerId and e.offerId = :offerId")
    Optional<CustomerOfferEntitlementEntity> findForUpdate(@Param("customerId") String customerId,
                                                        @Param("offerId") String offerId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from CustomerOfferEntitlementEntity e where e.id = :id")
    Optional<CustomerOfferEntitlementEntity> findByIdForUpdate(@Param("id") String id);

    @Query(value = """
        select count(*) from platform_deployment_releases r
          join platform_customer_offer_deployments d on d.deployment_id=r.deployment_id
          join deployment_external_integration_snapshots s on s.release_id=r.id
         where d.customer_id=:customerId and d.offer_id=:offerId
           and (r.status is null or r.status not in
                ('APPLIED_VERIFIED','APPLIED_VERIFICATION_FAILED','PRE_APPLY_BLOCKED','FAILED'))
        """, nativeQuery = true)
    long countUnfinishedExternalReleases(@Param("customerId") String customerId, @Param("offerId") String offerId);
}
