package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;

public interface DeploymentProvisioningProvider {

    boolean supports(String mode);

    ProvisioningResult provision(DeploymentEntity deployment,
                                 DeploymentVersionEntity version,
                                 DeploymentReleaseEntity release,
                                 ProvisioningProgressTracker progressTracker);
}
