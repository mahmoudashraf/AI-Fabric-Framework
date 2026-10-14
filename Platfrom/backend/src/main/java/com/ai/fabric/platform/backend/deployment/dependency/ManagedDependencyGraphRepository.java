package com.ai.fabric.platform.backend.deployment.dependency;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

public interface ManagedDependencyGraphRepository extends JpaRepository<ManagedDependencyGraphEntity, String> {
    Optional<ManagedDependencyGraphEntity> findByDeploymentIdAndTargetProfileId(String deploymentId, String targetProfileId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from ManagedDependencyGraphEntity g where g.id = :id")
    Optional<ManagedDependencyGraphEntity> lockById(String id);
}
