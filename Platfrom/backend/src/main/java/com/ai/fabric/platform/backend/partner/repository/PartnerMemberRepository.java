package com.ai.fabric.platform.backend.partner.repository;

import com.ai.fabric.platform.backend.partner.entity.PartnerMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PartnerMemberRepository extends JpaRepository<PartnerMemberEntity, String> {

    Optional<PartnerMemberEntity> findBySupabaseUserId(String supabaseUserId);

    Optional<PartnerMemberEntity> findFirstByEmailIgnoreCaseOrderByCreatedAtAsc(String email);

    List<PartnerMemberEntity> findByPartnerAccountIdOrderByCreatedAtAsc(String partnerAccountId);

    Optional<PartnerMemberEntity> findByIdAndPartnerAccountId(String id, String partnerAccountId);
}
