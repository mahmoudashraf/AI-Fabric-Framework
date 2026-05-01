package com.ai.fabric.platform.backend.partner.repository;

import com.ai.fabric.platform.backend.partner.entity.PartnerVerificationRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PartnerVerificationRunRepository extends JpaRepository<PartnerVerificationRunEntity, String> {

    List<PartnerVerificationRunEntity> findByPartnerAccountIdOrderByStartedAtDesc(String partnerAccountId);

    List<PartnerVerificationRunEntity> findByPartnerAccountIdAndStoreAssignmentIdOrderByStartedAtDesc(String partnerAccountId, String storeAssignmentId);

    Optional<PartnerVerificationRunEntity> findByIdAndPartnerAccountId(String id, String partnerAccountId);
}
