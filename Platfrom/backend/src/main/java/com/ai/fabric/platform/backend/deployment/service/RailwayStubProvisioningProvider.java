package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentArtifactBundleSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class RailwayStubProvisioningProvider implements DeploymentProvisioningProvider {

    private final ObjectMapper objectMapper;
    private final DeploymentArtifactService artifactService;
    private final PlatformProvisioningProperties provisioningProperties;

    public RailwayStubProvisioningProvider(ObjectMapper objectMapper,
                                           DeploymentArtifactService artifactService,
                                           PlatformProvisioningProperties provisioningProperties) {
        this.objectMapper = objectMapper;
        this.artifactService = artifactService;
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
        String runtimeBaseUrl = deployment.getRuntimeBaseUrl() != null
            ? deployment.getRuntimeBaseUrl()
            : "https://runtime-" + deployment.getId() + ".placeholder.local";
        String connectorBaseUrl = deployment.getConnectorBaseUrl() != null
            ? deployment.getConnectorBaseUrl()
            : "https://connector-" + deployment.getId() + ".placeholder.local";
        DeploymentArtifactBundleSummary artifacts = artifactService.toBundleSummary(version);

        ObjectNode details = objectMapper.createObjectNode();
        details.put("provider", "RAILWAY_STUB");
        details.put("mode", provisioningProperties.mode());
        details.put("releaseId", release.getId());
        details.put("versionId", version.getId());
        details.put("versionLabel", version.getVersionLabel());
        details.put("configHash", version.getConfigHash());
        details.put("projectName", normalizeName(deployment.getName()) + "-" + deployment.getEnvironmentName());
        details.put("environment", provisioningProperties.environmentName());
        details.put("repository", provisioningProperties.repository());
        details.put("branch", provisioningProperties.branch());
        if (!provisioningProperties.workspaceId().isBlank()) {
            details.put("workspaceId", provisioningProperties.workspaceId());
        }

        ObjectNode artifactUrls = details.putObject("artifactUrls");
        artifactUrls.put("actions", artifacts.actionsArtifactUrl());
        artifactUrls.put("entities", artifacts.entityArtifactUrl());
        artifactUrls.put("routing", artifacts.routingArtifactUrl());
        artifactUrls.put("manifest", artifacts.manifestUrl());

        ObjectNode services = details.putObject("services");
        ObjectNode runtime = services.putObject("runtime");
        runtime.put("serviceName", provisioningProperties.runtimeServiceNamePrefix() + "-" + deployment.getId());
        runtime.put("baseUrl", runtimeBaseUrl);
        runtime.put("rootDir", provisioningProperties.runtimeServiceRoot());
        runtime.set("env", envEntries(
            entry("AI_ACTIONS_CATALOG_PATH", artifacts.actionsArtifactUrl()),
            entry("AI_ENTITY_CONFIG_PATH", artifacts.entityArtifactUrl()),
            entry("ACTIONS_CONNECTOR_BASE_URL", connectorBaseUrl),
            entry("ACTIONS_CONNECTOR_API_KEY", "${secret:ACTIONS_CONNECTOR_API_KEY}"),
            entry("OPENAI_API_KEY", "${secret:OPENAI_API_KEY}")
        ));

        ObjectNode connector = services.putObject("restConnector");
        connector.put("serviceName", provisioningProperties.connectorServiceNamePrefix() + "-" + deployment.getId());
        connector.put("baseUrl", connectorBaseUrl);
        connector.put("rootDir", provisioningProperties.connectorServiceRoot());
        connector.set("env", envEntries(
            entry("REST_CONNECTOR_ROUTING_CONFIG_PATH", artifacts.routingArtifactUrl()),
            entry("REST_CONNECTOR_RUNTIME_PROXY_BASE_URL", runtimeBaseUrl),
            entry("CONNECTOR_API_KEY", "${secret:CONNECTOR_API_KEY}")
        ));

        details.put("artifactStrategy", "REMOTE_CONFIG_BUNDLES");
        details.put("statusMessage", "Provisioning plan generated for Railway-backed deployment.");
        details.put("generatedAt", Instant.now().toString());

        return new ProvisioningResult(
            "PLANNED",
            "RAILWAY_STUB",
            runtimeBaseUrl,
            connectorBaseUrl,
            details.toPrettyString()
        );
    }

    private ObjectNode entry(String key, String value) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("key", key);
        node.put("value", value);
        return node;
    }

    private ArrayNode envEntries(ObjectNode... entries) {
        ArrayNode arrayNode = objectMapper.createArrayNode();
        for (ObjectNode entry : entries) {
            arrayNode.add(entry);
        }
        return arrayNode;
    }

    private String normalizeName(String value) {
        return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
