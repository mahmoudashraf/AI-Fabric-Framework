package com.ai.fabric.platform.backend.deployment.repository;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface DeploymentRepository extends JpaRepository<DeploymentEntity, String> {

    List<DeploymentEntity> findAllByOrderByCreatedAtDesc();

    List<DeploymentEntity> findByArchivedAtIsNullOrderByCreatedAtDesc();

    Optional<DeploymentEntity> findByNameIgnoreCaseAndEnvironmentNameIgnoreCaseAndArchivedAtIsNull(String name,
                                                                                                    String environmentName);

    Optional<DeploymentEntity> findByTenantId(String tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select deployment from DeploymentEntity deployment where deployment.id = :id")
    Optional<DeploymentEntity> findByIdForUpdate(String id);
}
