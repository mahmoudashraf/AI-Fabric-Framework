package com.ai.fabric.platform.backend.partner.repository;

import com.ai.fabric.platform.backend.partner.entity.PartnerAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartnerAccountRepository extends JpaRepository<PartnerAccountEntity, String> {
}
