package com.ai.fabric.platform.backend.partner.repository;

import com.ai.fabric.platform.backend.partner.entity.PartnerEvidenceBundleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PartnerEvidenceBundleRepository extends JpaRepository<PartnerEvidenceBundleEntity, String> {

    List<PartnerEvidenceBundleEntity> findByPartnerAccountIdOrderByGeneratedAtDesc(String partnerAccountId);
}
