package com.ai.fabric.platform.backend.partner.repository;

import com.ai.fabric.platform.backend.partner.entity.PartnerStoreNoteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PartnerStoreNoteRepository extends JpaRepository<PartnerStoreNoteEntity, String> {

    List<PartnerStoreNoteEntity> findByPartnerAccountIdAndStoreAssignmentIdOrderByCreatedAtDesc(String partnerAccountId, String storeAssignmentId);

    Optional<PartnerStoreNoteEntity> findByIdAndPartnerAccountId(String id, String partnerAccountId);
}
