package com.ai.fabric.platform.backend.vectorization.repository;

import com.ai.fabric.platform.backend.vectorization.entity.VectorizationPlanRevisionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VectorizationPlanRevisionRepository extends JpaRepository<VectorizationPlanRevisionEntity, String> {

    List<VectorizationPlanRevisionEntity> findByPlanIdOrderByRevisionNumberDesc(String planId);

    Optional<VectorizationPlanRevisionEntity> findTopByPlanIdOrderByRevisionNumberDesc(String planId);
}
