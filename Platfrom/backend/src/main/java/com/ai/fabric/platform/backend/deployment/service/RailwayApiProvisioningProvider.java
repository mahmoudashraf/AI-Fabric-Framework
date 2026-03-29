package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import org.springframework.stereotype.Service;

@Service
public class RailwayApiProvisioningProvider implements DeploymentProvisioningProvider {

    private final PlatformProvisioningProperties provisioningProperties;

    public RailwayApiProvisioningProvider(PlatformProvisioningProperties provisioningProperties) {
        this.provisioningProperties = provisioningProperties;
    }

    @Override
    public boolean supports(String mode) {
        return "RAILWAY_API".equalsIgnoreCase(mode);
    }

    @Override
    public ProvisioningResult provision(DeploymentEntity deployment,
                                        DeploymentVersionEntity version,
                                        DeploymentReleaseEntity release) {
        throw new IllegalStateException(
            "RAILWAY_API mode is configured, but the live Railway provisioning client is not implemented yet. "
                + "Current mode=" + provisioningProperties.mode()
                + ". Use PLATFORM_PROVISIONING_MODE=RAILWAY_STUB until Railway credentials and client wiring are added."
        );
    }
}
