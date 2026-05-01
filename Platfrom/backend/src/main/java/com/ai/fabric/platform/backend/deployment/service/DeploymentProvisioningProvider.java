package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;

public interface DeploymentProvisioningProvider {

    DeploymentProviderType providerType();

    default boolean supports(String mode) {
        return providerType().matchesLegacyMode(mode);
    }

    ProvisioningResult provision(DeploymentEntity deployment,
                                 DeploymentVersionEntity version,
                                 DeploymentReleaseEntity release,
                                 ProvisioningProgressTracker progressTracker);
}
