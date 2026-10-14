package com.ai.fabric.platform.backend.deployment.authority;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RuntimeAuthorityRepository extends JpaRepository<RuntimeAuthorityEntity, String> {
    Optional<RuntimeAuthorityEntity> findByDeploymentIdAndTargetProfileId(String deploymentId, String targetProfileId);
}
