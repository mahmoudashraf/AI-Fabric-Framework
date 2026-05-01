package com.ai.fabric.platform.backend.partner.repository;

import com.ai.fabric.platform.backend.partner.entity.PartnerSupportReplyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PartnerSupportReplyRepository extends JpaRepository<PartnerSupportReplyEntity, String> {

    List<PartnerSupportReplyEntity> findByEscalationIdOrderByCreatedAtAsc(String escalationId);

    List<PartnerSupportReplyEntity> findByEscalationIdAndVisibilityOrderByCreatedAtAsc(String escalationId, String visibility);
}
