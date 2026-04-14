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
            JsonNode knowledgeSourceNode = objectMapper.readTree(draft.getKnowledgeSourceConfigJson());
            JsonNode shellNode = objectMapper.readTree(draft.getShellConfigJson());
            JsonNode effectiveRoutingNode = compileRoutingConfig(actionsNode, routingNode, securityNode);

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
            manifest.put("knowledgeSourceConfig", knowledgeSourceNode);
            manifest.put("shellConfig", shellNode);

            String manifestJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest);
            String configHash = sha256(manifestJson);

            return new CompiledDeploymentVersion(
                actionsArtifactYaml,
                entityArtifactYaml,
                routingArtifactYaml,
                objectMapper.writeValueAsString(knowledgeSourceNode),
                objectMapper.writeValueAsString(shellNode),
                manifestJson,
                configHash
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to compile deployment configuration: " + ex.getMessage(), ex);
        }
    }

    JsonNode compileRoutingConfig(JsonNode actionsNode, JsonNode routingNode, JsonNode securityNode) {
        ObjectNode root = routingNode != null && routingNode.isObject()
            ? routingNode.deepCopy()
            : objectMapper.createObjectNode();
        ObjectNode connector = object(root, "connector");
        ObjectNode inboundAuth = object(connector, "inbound-auth");
        ObjectNode apiKey = object(inboundAuth, "api-key");
        ObjectNode actions = object(root, "actions");

        applyInlineActionRoutes(actionsNode, actions);

        boolean connectorApiKeyEnabled = ManagedDeploymentProfileCatalog.connectorApiKeyEnabled(securityNode);
        inboundAuth.put("allow-unauthenticated", !connectorApiKeyEnabled);
        apiKey.put("enabled", connectorApiKeyEnabled);
        if (!StringUtils.hasText(apiKey.path("header").asText(""))) {
            apiKey.put("header", ManagedDeploymentProfileCatalog.CONNECTOR_API_KEY_HEADER);
        }
        apiKey.put("value", connectorApiKeyEnabled ? "${CONNECTOR_API_KEY}" : "");
        return root;
    }

    private void applyInlineActionRoutes(JsonNode actionsNode, ObjectNode compiledActions) {
        JsonNode actionDefinitions = actionsNode.path("actions");
        if (!actionDefinitions.isArray()) {
            return;
        }
        for (JsonNode actionDefinition : actionDefinitions) {
            if (actionDefinition == null || !actionDefinition.isObject()) {
                continue;
            }
            String actionName = actionDefinition.path("name").asText("").trim();
            JsonNode inlineRoute = actionDefinition.path("route");
            if (!StringUtils.hasText(actionName) || !inlineRoute.isObject()) {
                continue;
            }
            ObjectNode mergedRoute = ((ObjectNode) inlineRoute).deepCopy();
            JsonNode explicitRoute = compiledActions.path(actionName);
            if (explicitRoute.isObject()) {
                mergeObjectNodes(mergedRoute, (ObjectNode) explicitRoute);
            }
            normalizeRouteTarget(mergedRoute);
            compiledActions.set(actionName, mergedRoute);
        }
    }

    private void normalizeRouteTarget(ObjectNode route) {
        String url = route.path("url").asText("").trim();
        String path = route.path("path").asText("").trim();
        if (StringUtils.hasText(url)) {
            route.remove("path");
            return;
        }
        if (StringUtils.hasText(path)) {
            route.remove("url");
        }
    }

    private void mergeObjectNodes(ObjectNode target, ObjectNode overrides) {
        overrides.fields().forEachRemaining(entry -> {
            JsonNode existing = target.get(entry.getKey());
            JsonNode overrideValue = entry.getValue();
            if (existing instanceof ObjectNode existingObject && overrideValue instanceof ObjectNode overrideObject) {
                mergeObjectNodes(existingObject, overrideObject);
                return;
            }
            target.set(entry.getKey(), overrideValue.deepCopy());
        });
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
        String knowledgeSourceArtifactJson,
        String shellArtifactJson,
        String manifestJson,
        String configHash
    ) {
        public CompiledDeploymentVersion(String actionsArtifactYaml,
                                         String entityArtifactYaml,
                                         String routingArtifactYaml,
                                         String manifestJson,
                                         String configHash) {
            this(actionsArtifactYaml, entityArtifactYaml, routingArtifactYaml, "{}", "{}", manifestJson, configHash);
        }
    }
}
