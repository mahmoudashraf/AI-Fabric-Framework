package com.ai.fabric.platform.backend.secret.repository;

import com.ai.fabric.platform.backend.secret.entity.DeploymentProviderSecretBindingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeploymentProviderSecretBindingRepository extends JpaRepository<DeploymentProviderSecretBindingEntity, String> {

    Optional<DeploymentProviderSecretBindingEntity> findByDeploymentIdAndSecretPurpose(String deploymentId, String secretPurpose);

    List<DeploymentProviderSecretBindingEntity> findByDeploymentIdOrderBySecretPurposeAsc(String deploymentId);

    long countBySecretName(String secretName);

    long countBySecondarySecretName(String secondarySecretName);

    void deleteByDeploymentId(String deploymentId);
}
