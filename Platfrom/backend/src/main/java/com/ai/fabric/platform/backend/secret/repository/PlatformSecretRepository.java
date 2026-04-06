package com.ai.fabric.platform.backend.secret.repository;

import com.ai.fabric.platform.backend.secret.entity.PlatformSecretEntity;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretScopeType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlatformSecretRepository extends JpaRepository<PlatformSecretEntity, String> {

    List<PlatformSecretEntity> findByScopeTypeOrderByUpdatedAtDesc(PlatformSecretScopeType scopeType);

    List<PlatformSecretEntity> findByScopeTypeAndDeploymentIdOrderByUpdatedAtDesc(PlatformSecretScopeType scopeType, String deploymentId);
}
