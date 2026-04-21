package com.ai.fabric.platform.backend.deployment.repository;

import com.ai.fabric.platform.backend.deployment.entity.PlatformVerificationSuiteRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PlatformVerificationSuiteRunRepository extends JpaRepository<PlatformVerificationSuiteRunEntity, String> {

    boolean existsBySuiteKeyAndStatusIn(String suiteKey, Collection<String> statuses);

    List<PlatformVerificationSuiteRunEntity> findTop20ByOrderByCreatedAtDesc();

    List<PlatformVerificationSuiteRunEntity> findAllByOrderByCreatedAtDesc();
}
