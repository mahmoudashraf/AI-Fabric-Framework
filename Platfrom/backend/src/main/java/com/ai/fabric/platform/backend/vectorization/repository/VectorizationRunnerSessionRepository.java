package com.ai.fabric.platform.backend.vectorization.repository;

import com.ai.fabric.platform.backend.vectorization.entity.VectorizationRunnerSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VectorizationRunnerSessionRepository extends JpaRepository<VectorizationRunnerSessionEntity, String> {

    Optional<VectorizationRunnerSessionEntity> findBySessionTokenHash(String sessionTokenHash);

    List<VectorizationRunnerSessionEntity> findByRegistrationIdOrderByUpdatedAtDesc(String registrationId);

    void deleteByDeploymentId(String deploymentId);
}
