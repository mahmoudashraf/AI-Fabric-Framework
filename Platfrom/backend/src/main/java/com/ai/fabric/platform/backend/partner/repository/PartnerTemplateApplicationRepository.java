package com.ai.fabric.platform.backend.partner.repository;

import com.ai.fabric.platform.backend.partner.entity.PartnerTemplateApplicationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PartnerTemplateApplicationRepository extends JpaRepository<PartnerTemplateApplicationEntity, String> {

    List<PartnerTemplateApplicationEntity> findByPartnerAccountIdOrderByAppliedAtDesc(String partnerAccountId);

    List<PartnerTemplateApplicationEntity> findByPartnerAccountIdAndStoreAssignmentIdOrderByAppliedAtDesc(String partnerAccountId, String storeAssignmentId);
}
