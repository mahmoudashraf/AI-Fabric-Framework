package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
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
    public DeploymentProviderType providerType() {
        return DeploymentProviderType.RAILWAY_STUB;
    }

    @Override
    public ProvisioningResult provision(DeploymentEntity deployment,
                                        DeploymentVersionEntity version,
                                        DeploymentReleaseEntity release,
                                        ProvisioningProgressTracker progressTracker) {
        progressTracker.stepStarted("publish_artifacts", "Resolve immutable config artifact URLs for the selected version.");
        var plan = railwayProvisioningPlanService.buildPlan(deployment, version);
        progressTracker.stepCompleted("publish_artifacts", "Resolve immutable config artifact URLs for the selected version.");
        progressTracker.stepStarted("prepare_project", "Generate stub Railway provisioning details.");
        progressTracker.stepCompleted("prepare_project", "Generate stub Railway provisioning details.");
        progressTracker.stepStarted("configure_runtime", "Prepare stub runtime service configuration.");
        progressTracker.stepCompleted("configure_runtime", "Prepare stub runtime service configuration.");
        progressTracker.stepStarted("configure_rest_connector", "Prepare stub REST connector configuration.");
        progressTracker.stepCompleted("configure_rest_connector", "Prepare stub REST connector configuration.");
        progressTracker.stepStarted("trigger_deploy", "Simulate Railway deployment trigger.");
        progressTracker.stepCompleted("trigger_deploy", "Simulate Railway deployment trigger.");
        progressTracker.stepStarted("wait_for_active", "Simulate Railway deployment readiness.");
        progressTracker.stepCompleted("wait_for_active", "Simulate Railway deployment readiness.");

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
