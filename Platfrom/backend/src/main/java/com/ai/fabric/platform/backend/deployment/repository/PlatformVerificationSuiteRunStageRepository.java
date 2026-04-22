package com.ai.fabric.platform.backend.deployment.repository;

import com.ai.fabric.platform.backend.deployment.entity.PlatformVerificationSuiteRunStageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlatformVerificationSuiteRunStageRepository extends JpaRepository<PlatformVerificationSuiteRunStageEntity, String> {

    List<PlatformVerificationSuiteRunStageEntity> findBySuiteRunIdOrderByStageOrderAsc(String suiteRunId);
}
