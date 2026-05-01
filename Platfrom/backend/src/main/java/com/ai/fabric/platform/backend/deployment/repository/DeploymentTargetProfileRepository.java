package com.ai.fabric.platform.backend.deployment.repository;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeploymentTargetProfileRepository extends JpaRepository<DeploymentTargetProfileEntity, String> {

    Optional<DeploymentTargetProfileEntity> findFirstByProviderTypeAndActiveTrueAndDefaultForRuntimeTrueOrderByUpdatedAtDesc(
        DeploymentProviderType providerType
    );

    List<DeploymentTargetProfileEntity> findByProviderTypeOrderByEnvironmentNameAscUpdatedAtDesc(DeploymentProviderType providerType);
}
