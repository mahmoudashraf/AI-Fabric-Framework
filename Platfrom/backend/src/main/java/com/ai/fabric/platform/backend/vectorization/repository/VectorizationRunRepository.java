package com.ai.fabric.platform.backend.vectorization.repository;

import com.ai.fabric.platform.backend.vectorization.entity.VectorizationRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VectorizationRunRepository extends JpaRepository<VectorizationRunEntity, String> {

    List<VectorizationRunEntity> findByDeploymentIdOrderByCreatedAtDesc(String deploymentId);

    List<VectorizationRunEntity> findByDeploymentIdAndRequestedStatusInOrderByCreatedAtAsc(String deploymentId,
                                                                                           List<String> requestedStatuses);
}
