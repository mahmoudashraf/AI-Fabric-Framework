package com.ai.fabric.platform.backend.vectorization.repository;

import com.ai.fabric.platform.backend.vectorization.entity.VectorizationSourceConnectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VectorizationSourceConnectionRepository extends JpaRepository<VectorizationSourceConnectionEntity, String> {

    Optional<VectorizationSourceConnectionEntity> findByDeploymentId(String deploymentId);

    void deleteByDeploymentId(String deploymentId);
}
