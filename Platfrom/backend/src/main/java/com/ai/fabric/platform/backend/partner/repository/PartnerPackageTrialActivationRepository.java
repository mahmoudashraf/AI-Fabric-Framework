package com.ai.fabric.platform.backend.partner.repository;

import com.ai.fabric.platform.backend.partner.entity.PartnerPackageTrialActivationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PartnerPackageTrialActivationRepository extends JpaRepository<PartnerPackageTrialActivationEntity, String> {

    Optional<PartnerPackageTrialActivationEntity> findFirstByStoreAssignmentIdAndStatusOrderByCreatedAtDesc(String storeAssignmentId,
                                                                                                            String status);

    List<PartnerPackageTrialActivationEntity> findTop10ByStoreAssignmentIdOrderByCreatedAtDesc(String storeAssignmentId);

    Optional<PartnerPackageTrialActivationEntity> findByIdAndPartnerAccountId(String id, String partnerAccountId);
}
