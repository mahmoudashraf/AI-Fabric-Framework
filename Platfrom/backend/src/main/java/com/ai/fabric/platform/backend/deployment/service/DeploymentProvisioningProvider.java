package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderPreflightSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderResourceActionSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderResourceLogsSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderResourceStatusSummary;
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

    default DeploymentProviderPreflightSummary preflight(DeploymentTargetProfileEntity targetProfile) {
        throw unsupported("preflight");
    }

    default DeploymentProviderResourceActionSummary start(DeploymentProviderResourceHandleEntity handle,
                                                          String reason) {
        throw unsupported("start");
    }

    default DeploymentProviderResourceActionSummary stop(DeploymentProviderResourceHandleEntity handle,
                                                         String reason) {
        throw unsupported("stop");
    }

    default DeploymentProviderResourceActionSummary restart(DeploymentProviderResourceHandleEntity handle,
                                                            String reason) {
        throw unsupported("restart");
    }

    default DeploymentProviderResourceActionSummary delete(DeploymentProviderResourceHandleEntity handle,
                                                           String reason) {
        throw unsupported("delete");
    }

    default DeploymentProviderResourceStatusSummary status(DeploymentProviderResourceHandleEntity handle) {
        throw unsupported("status");
    }

    default DeploymentProviderResourceLogsSummary logs(DeploymentProviderResourceHandleEntity handle, int lines) {
        throw unsupported("logs");
    }

    private UnsupportedOperationException unsupported(String action) {
        return new UnsupportedOperationException(providerType() + " provider does not support " + action + ".");
    }
}
