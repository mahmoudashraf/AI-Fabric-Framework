package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DeploymentProvisioningService {

    private final PlatformProvisioningProperties provisioningProperties;
    private final List<DeploymentProvisioningProvider> providers;

    public DeploymentProvisioningService(PlatformProvisioningProperties provisioningProperties,
                                         List<DeploymentProvisioningProvider> providers) {
        this.provisioningProperties = provisioningProperties;
        this.providers = providers;
    }

    public ProvisioningResult provision(DeploymentEntity deployment,
                                        DeploymentVersionEntity version,
                                        DeploymentReleaseEntity release) {
        return providers.stream()
            .filter(provider -> provider.supports(provisioningProperties.mode()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "No provisioning provider registered for mode: " + provisioningProperties.mode()
            ))
            .provision(deployment, version, release);
    }

    public String selectedTarget() {
        return provisioningProperties.mode();
    }
}
