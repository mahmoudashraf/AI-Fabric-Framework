package com.ai.fabric.platform.backend.partner.repository;

import com.ai.fabric.platform.backend.partner.entity.PartnerVerificationRunStepEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PartnerVerificationRunStepRepository extends JpaRepository<PartnerVerificationRunStepEntity, String> {

    List<PartnerVerificationRunStepEntity> findByRunIdOrderBySortOrderAsc(String runId);

    Optional<PartnerVerificationRunStepEntity> findByRunIdAndStepId(String runId, String stepId);
}
