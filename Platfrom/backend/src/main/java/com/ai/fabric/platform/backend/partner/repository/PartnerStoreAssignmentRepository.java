package com.ai.fabric.platform.backend.partner.repository;

import com.ai.fabric.platform.backend.partner.entity.PartnerStoreAssignmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PartnerStoreAssignmentRepository extends JpaRepository<PartnerStoreAssignmentEntity, String> {

    List<PartnerStoreAssignmentEntity> findByPartnerAccountIdOrderByCreatedAtDesc(String partnerAccountId);

    List<PartnerStoreAssignmentEntity> findByPartnerAccountIdAndStatusOrderByCreatedAtDesc(String partnerAccountId, String status);

    Optional<PartnerStoreAssignmentEntity> findByIdAndPartnerAccountId(String id, String partnerAccountId);

    Optional<PartnerStoreAssignmentEntity> findByPartnerAccountIdAndShopDomainIgnoreCase(String partnerAccountId, String shopDomain);

    Optional<PartnerStoreAssignmentEntity> findByPartnerAccountIdAndStoreConnectionId(String partnerAccountId, String storeConnectionId);

    boolean existsByPartnerAccountIdAndStoreConnectionIdAndStatus(String partnerAccountId, String storeConnectionId, String status);

    long countByPartnerAccountIdAndStatus(String partnerAccountId, String status);
}
