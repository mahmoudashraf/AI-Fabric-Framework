package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import org.springframework.stereotype.Service;

@Service
public class DeploymentProvisioningService {

    private final DeploymentTargetProfileService targetProfileService;
    private final DeploymentProviderRegistry providerRegistry;

    public DeploymentProvisioningService(DeploymentTargetProfileService targetProfileService,
                                         DeploymentProviderRegistry providerRegistry) {
        this.targetProfileService = targetProfileService;
        this.providerRegistry = providerRegistry;
    }

    public ProvisioningResult provision(DeploymentEntity deployment,
                                        DeploymentVersionEntity version,
                                        DeploymentReleaseEntity release,
                                        ProvisioningProgressTracker progressTracker) {
        DeploymentTargetProfileEntity targetProfile = targetProfileService.resolveForRelease(release);
        targetProfileService.applyProfileToRelease(release, targetProfile);
        return providerRegistry.require(targetProfile.getProviderType())
            .provision(
                deployment,
                version,
                release,
                progressTracker == null ? ProvisioningProgressTracker.noop() : progressTracker
            );
    }

    public String selectedTarget() {
        return selectedTargetProfile().getProviderType().legacyTarget();
    }

    public DeploymentTargetProfileEntity selectedTargetProfile() {
        return targetProfileService.resolveDefaultRuntimeProfile();
    }
}
