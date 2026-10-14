package com.ai.fabric.platform.backend.customerselfservice.repository;

import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerPlacementEvidenceEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerPlacementEvidenceRepository extends JpaRepository<CustomerPlacementEvidenceEntity, String> {
    Optional<CustomerPlacementEvidenceEntity> findFirstByTargetProfileIdAndIntentAndProfileFingerprintOrderByCheckedAtDesc(
        String targetProfileId, TargetIntent intent, String profileFingerprint);
}
