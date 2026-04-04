package com.ai.fabric.platform.backend.vectorization.repository;

import com.ai.fabric.platform.backend.vectorization.entity.VectorizationPlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VectorizationPlanRepository extends JpaRepository<VectorizationPlanEntity, String> {

    Optional<VectorizationPlanEntity> findByDeploymentId(String deploymentId);
}
