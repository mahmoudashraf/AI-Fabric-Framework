package com.ai.fabric.platform.backend.deployment.repository;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentManagedVectorResourceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeploymentManagedVectorResourceRepository extends JpaRepository<DeploymentManagedVectorResourceEntity, String> {

    List<DeploymentManagedVectorResourceEntity> findByDeploymentIdOrderByUpdatedAtDesc(String deploymentId);

    Optional<DeploymentManagedVectorResourceEntity> findByDeploymentIdAndVendorIgnoreCaseAndResourceTypeIgnoreCaseAndResourceNameIgnoreCase(
        String deploymentId,
        String vendor,
        String resourceType,
        String resourceName
    );
}
