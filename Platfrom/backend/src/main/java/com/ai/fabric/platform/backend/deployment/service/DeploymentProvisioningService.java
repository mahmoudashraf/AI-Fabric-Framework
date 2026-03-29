package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class DeploymentProvisioningService {

    private final ObjectMapper objectMapper;

    public DeploymentProvisioningService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ProvisioningResult provision(DeploymentEntity deployment,
                                        DeploymentVersionEntity version,
                                        DeploymentReleaseEntity release) {
        String runtimeBaseUrl = deployment.getRuntimeBaseUrl() != null
            ? deployment.getRuntimeBaseUrl()
            : "https://runtime-" + deployment.getId() + ".placeholder.local";
        String connectorBaseUrl = deployment.getConnectorBaseUrl() != null
            ? deployment.getConnectorBaseUrl()
            : "https://connector-" + deployment.getId() + ".placeholder.local";

        ObjectNode details = objectMapper.createObjectNode();
        details.put("provider", "RAILWAY_STUB");
        details.put("releaseId", release.getId());
        details.put("versionId", version.getId());
        details.put("versionLabel", version.getVersionLabel());
        details.put("configHash", version.getConfigHash());
        details.put("projectName", normalizeName(deployment.getName()) + "-" + deployment.getEnvironmentName());
        details.put("environment", deployment.getEnvironmentName());

        ObjectNode services = details.putObject("services");
        ObjectNode runtime = services.putObject("runtime");
        runtime.put("serviceName", "runtime-" + deployment.getId());
        runtime.put("baseUrl", runtimeBaseUrl);
        runtime.set("envKeys", envKeys(
            "AI_ACTIONS_CATALOG_PATH",
            "AI_ENTITY_CONFIG_PATH",
            "OPENAI_API_KEY",
            "ACTIONS_CONNECTOR_BASE_URL",
            "ACTIONS_CONNECTOR_API_KEY"
        ));

        ObjectNode connector = services.putObject("restConnector");
        connector.put("serviceName", "rest-connector-" + deployment.getId());
        connector.put("baseUrl", connectorBaseUrl);
        connector.set("envKeys", envKeys(
            "REST_CONNECTOR_ROUTING_CONFIG_PATH",
            "REST_CONNECTOR_RUNTIME_PROXY_BASE_URL",
            "CONNECTOR_API_KEY"
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

    private ArrayNode envKeys(String... keys) {
        ArrayNode arrayNode = objectMapper.createArrayNode();
        for (String key : keys) {
            arrayNode.add(key);
        }
        return arrayNode;
    }

    private String normalizeName(String value) {
        return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    public record ProvisioningResult(
        String status,
        String target,
        String runtimeBaseUrl,
        String connectorBaseUrl,
        String detailsJson
    ) {
    }
}
