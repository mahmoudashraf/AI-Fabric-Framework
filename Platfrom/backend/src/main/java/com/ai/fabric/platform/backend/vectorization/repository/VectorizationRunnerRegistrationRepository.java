package com.ai.fabric.platform.backend.vectorization.repository;

import com.ai.fabric.platform.backend.vectorization.entity.VectorizationRunnerRegistrationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VectorizationRunnerRegistrationRepository extends JpaRepository<VectorizationRunnerRegistrationEntity, String> {

    Optional<VectorizationRunnerRegistrationEntity> findByDeploymentId(String deploymentId);

    Optional<VectorizationRunnerRegistrationEntity> findByTokenHash(String tokenHash);

    void deleteByDeploymentId(String deploymentId);
}
