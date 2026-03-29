package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class RailwayStubProvisioningProvider implements DeploymentProvisioningProvider {

    private final ObjectMapper objectMapper;
    private final RailwayProvisioningPlanService railwayProvisioningPlanService;
    private final PlatformProvisioningProperties provisioningProperties;

    public RailwayStubProvisioningProvider(ObjectMapper objectMapper,
                                           RailwayProvisioningPlanService railwayProvisioningPlanService,
                                           PlatformProvisioningProperties provisioningProperties) {
        this.objectMapper = objectMapper;
        this.railwayProvisioningPlanService = railwayProvisioningPlanService;
        this.provisioningProperties = provisioningProperties;
    }

    @Override
    public boolean supports(String mode) {
        return "RAILWAY_STUB".equalsIgnoreCase(mode);
    }

    @Override
    public ProvisioningResult provision(DeploymentEntity deployment,
                                        DeploymentVersionEntity version,
                                        DeploymentReleaseEntity release) {
        var plan = railwayProvisioningPlanService.buildPlan(deployment, version);

        ObjectNode details = objectMapper.valueToTree(plan);
        details.put("provider", "RAILWAY_STUB");
        details.put("releaseId", release.getId());
        details.put("statusMessage", "Provisioning plan generated for Railway-backed deployment.");
        details.put("generatedAt", Instant.now().toString());

        return new ProvisioningResult(
            "PLANNED",
            "RAILWAY_STUB",
            plan.services().runtime().baseUrl(),
            plan.services().restConnector().baseUrl(),
            details.toPrettyString()
        );
    }
}
