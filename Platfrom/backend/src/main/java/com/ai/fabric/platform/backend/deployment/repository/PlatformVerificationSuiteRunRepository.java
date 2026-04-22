package com.ai.fabric.platform.backend.deployment.repository;

import com.ai.fabric.platform.backend.deployment.entity.PlatformVerificationSuiteRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PlatformVerificationSuiteRunRepository extends JpaRepository<PlatformVerificationSuiteRunEntity, String> {

    boolean existsBySuiteKeyAndStatusIn(String suiteKey, Collection<String> statuses);

    Optional<PlatformVerificationSuiteRunEntity> findTopBySuiteKeyOrderByCreatedAtDesc(String suiteKey);

    List<PlatformVerificationSuiteRunEntity> findTop20ByOrderByCreatedAtDesc();

    List<PlatformVerificationSuiteRunEntity> findAllByOrderByCreatedAtDesc();
}
