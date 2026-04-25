package com.ai.fabric.platform.backend.partner.repository;

import com.ai.fabric.platform.backend.partner.entity.PartnerActionAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PartnerActionAuditRepository extends JpaRepository<PartnerActionAuditEntity, String> {

    List<PartnerActionAuditEntity> findTop20ByPartnerAccountIdOrderByCreatedAtDesc(String partnerAccountId);
}
