package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class DeploymentConfigCompiler {

    private final ObjectMapper objectMapper;
    private final ObjectMapper yamlMapper;

    public DeploymentConfigCompiler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.yamlMapper = new ObjectMapper(
            YAMLFactory.builder()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .build()
        );
    }

    public CompiledDeploymentVersion compile(DeploymentEntity deployment,
                                             DeploymentDraftEntity draft,
                                             String versionId,
                                             String versionLabel,
                                             boolean reindexRequired) {
        try {
            JsonNode actionsNode = objectMapper.readTree(draft.getActionsConfigJson());
            JsonNode entityNode = objectMapper.readTree(draft.getEntityConfigJson());
            JsonNode routingNode = objectMapper.readTree(draft.getRoutingConfigJson());
            JsonNode providerNode = objectMapper.readTree(draft.getProviderConfigJson());
            JsonNode securityNode = objectMapper.readTree(draft.getSecurityConfigJson());
            JsonNode promptNode = objectMapper.readTree(draft.getPromptConfigJson());
            JsonNode effectiveRoutingNode = compileRoutingConfig(routingNode, securityNode);

            String actionsArtifactYaml = yamlMapper.writeValueAsString(actionsNode);
            String entityArtifactYaml = yamlMapper.writeValueAsString(entityNode);
            String routingArtifactYaml = yamlMapper.writeValueAsString(effectiveRoutingNode);

            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("deploymentId", deployment.getId());
            manifest.put("deploymentName", deployment.getName());
            manifest.put("environment", deployment.getEnvironmentName());
            manifest.put("templateId", deployment.getTemplateId());
            manifest.put("versionId", versionId);
            manifest.put("versionLabel", versionLabel);
            manifest.put("publishedAt", Instant.now().toString());
            manifest.put("reindexRequired", reindexRequired);
            manifest.put("actionsConfig", actionsNode);
            manifest.put("entityConfig", entityNode);
            manifest.put("routingConfig", effectiveRoutingNode);
            manifest.put("providerConfig", providerNode);
            manifest.put("securityConfig", securityNode);
            manifest.put("promptConfig", promptNode);

            String manifestJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest);
            String configHash = sha256(manifestJson);

            return new CompiledDeploymentVersion(
                actionsArtifactYaml,
                entityArtifactYaml,
                routingArtifactYaml,
                manifestJson,
                configHash
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to compile deployment configuration: " + ex.getMessage(), ex);
        }
    }

    private JsonNode compileRoutingConfig(JsonNode routingNode, JsonNode securityNode) {
        ObjectNode root = routingNode != null && routingNode.isObject()
            ? routingNode.deepCopy()
            : objectMapper.createObjectNode();
        ObjectNode connector = object(root, "connector");
        ObjectNode inboundAuth = object(connector, "inbound-auth");
        ObjectNode apiKey = object(inboundAuth, "api-key");

        boolean connectorApiKeyEnabled = ManagedDeploymentProfileCatalog.connectorApiKeyEnabled(securityNode);
        inboundAuth.put("allow-unauthenticated", !connectorApiKeyEnabled);
        apiKey.put("enabled", connectorApiKeyEnabled);
        if (!StringUtils.hasText(apiKey.path("header").asText(""))) {
            apiKey.put("header", ManagedDeploymentProfileCatalog.CONNECTOR_API_KEY_HEADER);
        }
        apiKey.put("value", connectorApiKeyEnabled ? "${CONNECTOR_API_KEY}" : "");
        return root;
    }

    private ObjectNode object(ObjectNode parent, String fieldName) {
        JsonNode existing = parent.path(fieldName);
        if (existing instanceof ObjectNode objectNode) {
            return objectNode;
        }
        ObjectNode created = objectMapper.createObjectNode();
        parent.set(fieldName, created);
        return created;
    }

    public boolean requiresReindex(DeploymentDraftEntity draft, DeploymentVersionEntity activeVersion) {
        if (activeVersion == null) {
            return false;
        }
        return !safeEquals(draft.getEntityConfigJson(), activeVersion.getEntityConfigJson())
            || !safeEquals(draft.getProviderConfigJson(), activeVersion.getProviderConfigJson());
    }

    private boolean safeEquals(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : hash) {
                out.append(String.format("%02x", b));
            }
            return out.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute config hash", ex);
        }
    }

    public record CompiledDeploymentVersion(
        String actionsArtifactYaml,
        String entityArtifactYaml,
        String routingArtifactYaml,
        String manifestJson,
        String configHash
    ) {
    }
}
