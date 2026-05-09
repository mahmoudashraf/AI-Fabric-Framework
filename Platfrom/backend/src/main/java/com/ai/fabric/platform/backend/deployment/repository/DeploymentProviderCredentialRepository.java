package com.ai.fabric.platform.backend.deployment.repository;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderCredentialEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeploymentProviderCredentialRepository extends JpaRepository<DeploymentProviderCredentialEntity, String> {
}
