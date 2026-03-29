package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.RailwayArtifactUrlsSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayEnvVarSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayProvisioningPlanSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayProvisioningServicesSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayProvisioningStepSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayServicePlanSummary;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RailwayProvisioningPlanService {

    private final PlatformProvisioningProperties provisioningProperties;
    private final DeploymentArtifactService artifactService;

    public RailwayProvisioningPlanService(PlatformProvisioningProperties provisioningProperties,
                                          DeploymentArtifactService artifactService) {
        this.provisioningProperties = provisioningProperties;
        this.artifactService = artifactService;
    }

    public RailwayProvisioningPlanSummary buildPlan(DeploymentEntity deployment, DeploymentVersionEntity version) {
        String runtimeBaseUrl = deployment.getRuntimeBaseUrl() != null
            ? deployment.getRuntimeBaseUrl()
            : "https://runtime-" + deployment.getId() + ".placeholder.local";
        String connectorBaseUrl = deployment.getConnectorBaseUrl() != null
            ? deployment.getConnectorBaseUrl()
            : "https://connector-" + deployment.getId() + ".placeholder.local";

        var artifacts = artifactService.toBundleSummary(version);
        RailwayArtifactUrlsSummary artifactUrls = new RailwayArtifactUrlsSummary(
            artifacts.actionsArtifactUrl(),
            artifacts.entityArtifactUrl(),
            artifacts.routingArtifactUrl(),
            artifacts.manifestUrl()
        );

        RailwayServicePlanSummary runtime = new RailwayServicePlanSummary(
            provisioningProperties.runtimeServiceNamePrefix() + "-" + deployment.getId(),
            provisioningProperties.runtimeServiceRoot(),
            runtimeBaseUrl,
            List.of(
                new RailwayEnvVarSummary("AI_ACTIONS_CATALOG_PATH", artifactUrls.actions()),
                new RailwayEnvVarSummary("AI_ENTITY_CONFIG_PATH", artifactUrls.entities()),
                new RailwayEnvVarSummary("ACTIONS_CONNECTOR_BASE_URL", connectorBaseUrl),
                new RailwayEnvVarSummary("ACTIONS_CONNECTOR_API_KEY", "${secret:ACTIONS_CONNECTOR_API_KEY}"),
                new RailwayEnvVarSummary("OPENAI_API_KEY", "${secret:OPENAI_API_KEY}")
            )
        );

        RailwayServicePlanSummary restConnector = new RailwayServicePlanSummary(
            provisioningProperties.connectorServiceNamePrefix() + "-" + deployment.getId(),
            provisioningProperties.connectorServiceRoot(),
            connectorBaseUrl,
            List.of(
                new RailwayEnvVarSummary("REST_CONNECTOR_ROUTING_CONFIG_PATH", artifactUrls.routing()),
                new RailwayEnvVarSummary("REST_CONNECTOR_RUNTIME_PROXY_BASE_URL", runtimeBaseUrl),
                new RailwayEnvVarSummary("CONNECTOR_API_KEY", "${secret:CONNECTOR_API_KEY}")
            )
        );

        return new RailwayProvisioningPlanSummary(
            deployment.getId(),
            deployment.getName(),
            deployment.getEnvironmentName(),
            deployment.getTemplateId(),
            version.getId(),
            version.getVersionLabel(),
            version.getConfigHash(),
            provisioningProperties.mode(),
            normalizeName(deployment.getName()) + "-" + deployment.getEnvironmentName(),
            provisioningProperties.repository(),
            provisioningProperties.branch(),
            provisioningProperties.workspaceId().isBlank() ? null : provisioningProperties.workspaceId(),
            "REMOTE_CONFIG_BUNDLES",
            artifactUrls,
            new RailwayProvisioningServicesSummary(runtime, restConnector),
            List.of(
                new RailwayProvisioningStepSummary(1, "publish_artifacts", "Resolve immutable config artifact URLs for the selected version."),
                new RailwayProvisioningStepSummary(2, "prepare_project", "Create or reuse the Railway project for this customer environment."),
                new RailwayProvisioningStepSummary(3, "configure_runtime", "Create or update the runtime service root and its environment variables."),
                new RailwayProvisioningStepSummary(4, "configure_rest_connector", "Create or update the REST connector service root and its environment variables."),
                new RailwayProvisioningStepSummary(5, "trigger_deploy", "Commit staged changes or trigger Railway deployment/redeploy for both services."),
                new RailwayProvisioningStepSummary(6, "wait_for_active", "Wait for Railway deployment states to become active."),
                new RailwayProvisioningStepSummary(7, "run_verification", "Run post-deploy verification against runtime and connector endpoints.")
            )
        );
    }

    private String normalizeName(String value) {
        return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
