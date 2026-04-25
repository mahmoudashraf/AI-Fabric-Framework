package com.ai.fabric.platform.backend.partner.repository;

import com.ai.fabric.platform.backend.partner.entity.PartnerStoreAccessRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PartnerStoreAccessRequestRepository extends JpaRepository<PartnerStoreAccessRequestEntity, String> {

    Optional<PartnerStoreAccessRequestEntity> findByApprovalCode(String approvalCode);

    Optional<PartnerStoreAccessRequestEntity> findFirstByImplementationRequestIdOrderByCreatedAtDesc(String implementationRequestId);
}
