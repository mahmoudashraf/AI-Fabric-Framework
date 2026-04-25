package com.ai.fabric.platform.backend.partner.repository;

import com.ai.fabric.platform.backend.partner.entity.PartnerClientImplementationRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PartnerClientImplementationRequestRepository extends JpaRepository<PartnerClientImplementationRequestEntity, String> {

    List<PartnerClientImplementationRequestEntity> findByPartnerAccountIdOrderByCreatedAtDesc(String partnerAccountId);

    Optional<PartnerClientImplementationRequestEntity> findByIdAndPartnerAccountId(String id, String partnerAccountId);
}
