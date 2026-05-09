package com.ai.fabric.platform.backend.partner.repository;

import com.ai.fabric.platform.backend.partner.entity.PartnerSupportEscalationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PartnerSupportEscalationRepository extends JpaRepository<PartnerSupportEscalationEntity, String> {

    List<PartnerSupportEscalationEntity> findByPartnerAccountIdOrderByUpdatedAtDesc(String partnerAccountId);

    List<PartnerSupportEscalationEntity> findByPartnerAccountIdAndStoreAssignmentIdOrderByUpdatedAtDesc(String partnerAccountId, String storeAssignmentId);

    Optional<PartnerSupportEscalationEntity> findByIdAndPartnerAccountId(String id, String partnerAccountId);
}
