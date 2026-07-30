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
    private final DeploymentConfigCompiler deploymentConfigCompiler;

    public DeploymentProvisioningService(DeploymentTargetProfileService targetProfileService,
                                         DeploymentProviderRegistry providerRegistry,
                                         DeploymentConfigCompiler deploymentConfigCompiler) {
        this.targetProfileService = targetProfileService;
        this.providerRegistry = providerRegistry;
        this.deploymentConfigCompiler = deploymentConfigCompiler;
    }

    public ProvisioningResult provision(DeploymentEntity deployment,
                                        DeploymentVersionEntity version,
                                        DeploymentReleaseEntity release,
                                        ProvisioningProgressTracker progressTracker) {
        deploymentConfigCompiler.requireRuntimeArtifactCompatible(version);
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

    public DeploymentTargetProfileEntity selectedTargetProfile(String targetProfileId) {
        return targetProfileService.resolveForRequest(targetProfileId);
    }
}
