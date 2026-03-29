package com.ai.fabric.platform.backend.deployment.repository;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeploymentRepository extends JpaRepository<DeploymentEntity, String> {

    List<DeploymentEntity> findAllByOrderByCreatedAtDesc();

    List<DeploymentEntity> findByArchivedAtIsNullOrderByCreatedAtDesc();
}
