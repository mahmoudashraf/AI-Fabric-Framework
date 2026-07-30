package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.entityconfig.EntityConfigContractService;
import com.ai.fabric.platform.backend.deployment.entityconfig.EntityConfigContractValidation;
import com.ai.fabric.platform.backend.deployment.entityconfig.EntityConfigValidationContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class DeploymentConfigCompiler {

    private final ObjectMapper objectMapper;
    private final ObjectMapper yamlMapper;
    private final EntityConfigContractService entityConfigContractService;
    private final String aiFabricFrameworkVersion;

    public DeploymentConfigCompiler(ObjectMapper objectMapper) {
        this(objectMapper, new EntityConfigContractService(objectMapper), "0.4.0");
    }

    @Autowired
    public DeploymentConfigCompiler(ObjectMapper objectMapper,
                                    EntityConfigContractService entityConfigContractService,
                                    @Value("${platform.ai-fabric.framework-version:0.4.0}") String aiFabricFrameworkVersion) {
        this.objectMapper = objectMapper;
        this.entityConfigContractService = entityConfigContractService;
        this.aiFabricFrameworkVersion = aiFabricFrameworkVersion;
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
            JsonNode marketplaceDatasetNode = objectMapper.readTree(draft.getMarketplaceDatasetConfigJson());
            JsonNode effectiveRoutingNode = compileRoutingConfig(actionsNode, routingNode, securityNode);
            EntityConfigValidationContext entityContext = new EntityConfigValidationContext(
                false,
                ManagedDeploymentProfileCatalog.sharedVectorStorageRequested(providerNode)
            );
            EntityConfigContractValidation entityValidation =
                entityConfigContractService.requireValid(entityNode, entityContext);
            JsonNode runtimeEntityNode = entityValidation.runtimeConfig();

            String actionsArtifactYaml = yamlMapper.writeValueAsString(actionsNode);
            String entityArtifactYaml = yamlMapper.writeValueAsString(runtimeEntityNode);
            String routingArtifactYaml = yamlMapper.writeValueAsString(effectiveRoutingNode);
            JsonNode entityRoundTripNode = yamlMapper.readTree(entityArtifactYaml);
            EntityConfigContractValidation roundTripValidation =
                entityConfigContractService.requireValid(entityRoundTripNode, entityContext);
            if (!canonicalJson(runtimeEntityNode).equals(canonicalJson(roundTripValidation.runtimeConfig()))) {
                throw new IllegalStateException(
                    "Entity configuration changed during the AI_ENTITY_CONFIG_V0_4 YAML round trip."
                );
            }
            String entityConfigHash = sha256(canonicalJson(runtimeEntityNode));

            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("deploymentId", deployment.getId());
            manifest.put("deploymentName", deployment.getName());
            manifest.put("environment", deployment.getEnvironmentName());
            manifest.put("templateId", deployment.getTemplateId());
            manifest.put("versionId", versionId);
            manifest.put("versionLabel", versionLabel);
            manifest.put("publishedAt", Instant.now().toString());
            manifest.put("reindexRequired", reindexRequired);
            manifest.put("aiFabricFrameworkVersion", aiFabricFrameworkVersion);
            manifest.put("entityConfigContractVersion", EntityConfigContractService.CONTRACT_VERSION_V04);
            manifest.put("entityConfigHash", entityConfigHash);
            manifest.put("actionsConfig", actionsNode);
            manifest.put("entityConfig", runtimeEntityNode);
            manifest.put("routingConfig", effectiveRoutingNode);
            manifest.put("providerConfig", providerNode);
            manifest.put("securityConfig", securityNode);
            manifest.put("promptConfig", promptNode);
            manifest.put("knowledgeSourceConfig", knowledgeSourceNode);
            manifest.put("shellConfig", shellNode);
            manifest.put("marketplaceDatasetConfig", marketplaceDatasetNode);

            Map<String, Object> configHashMaterial = new LinkedHashMap<>();
            configHashMaterial.put("aiFabricFrameworkVersion", aiFabricFrameworkVersion);
            configHashMaterial.put("entityConfigContractVersion", EntityConfigContractService.CONTRACT_VERSION_V04);
            configHashMaterial.put("actionsConfig", canonicalize(actionsNode));
            configHashMaterial.put("entityConfig", canonicalize(runtimeEntityNode));
            configHashMaterial.put("routingConfig", canonicalize(effectiveRoutingNode));
            configHashMaterial.put("providerConfig", canonicalize(providerNode));
            configHashMaterial.put("securityConfig", canonicalize(securityNode));
            configHashMaterial.put("promptConfig", canonicalize(promptNode));
            configHashMaterial.put("knowledgeSourceConfig", canonicalize(knowledgeSourceNode));
            configHashMaterial.put("shellConfig", canonicalize(shellNode));
            configHashMaterial.put("marketplaceDatasetConfig", canonicalize(marketplaceDatasetNode));
            String configHash = sha256(objectMapper.writeValueAsString(configHashMaterial));
            String manifestJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest);

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
        try {
            JsonNode draftEntity = objectMapper.readTree(draft.getEntityConfigJson());
            JsonNode activeEntity = objectMapper.readTree(activeVersion.getEntityConfigJson());
            JsonNode draftProvider = objectMapper.readTree(draft.getProviderConfigJson());
            JsonNode activeProvider = objectMapper.readTree(activeVersion.getProviderConfigJson());
            EntityConfigContractValidation draftValidation = entityConfigContractService.requireValid(
                draftEntity,
                EntityConfigValidationContext.standard()
            );
            EntityConfigContractValidation activeValidation = entityConfigContractService.requireValid(
                activeEntity,
                EntityConfigValidationContext.standard()
            );
            return !canonicalJson(draftValidation.runtimeConfig()).equals(
                canonicalJson(activeValidation.runtimeConfig())
            ) || !canonicalJson(draftProvider).equals(canonicalJson(activeProvider));
        } catch (Exception ex) {
            return true;
        }
    }

    public String frameworkVersion() {
        return aiFabricFrameworkVersion;
    }

    public void requireRuntimeArtifactCompatible(DeploymentVersionEntity version) {
        List<String> issues = new ArrayList<>();
        if (version == null) {
            throw incompatible(List.of("Deployment version is required."));
        }
        if (!EntityConfigContractService.CONTRACT_VERSION_V04.equals(
            version.getEntityConfigContractVersion()
        )) {
            issues.add(
                "Entity contract "
                    + display(version.getEntityConfigContractVersion())
                    + " is not compatible with the required "
                    + EntityConfigContractService.CONTRACT_VERSION_V04
                    + " runtime contract."
            );
        }
        if (!aiFabricFrameworkVersion.equals(version.getAiFabricFrameworkVersion())) {
            issues.add(
                "Published framework "
                    + display(version.getAiFabricFrameworkVersion())
                    + " does not match runtime framework "
                    + aiFabricFrameworkVersion
                    + "."
            );
        }
        if (!issues.isEmpty()) {
            throw incompatible(issues);
        }

        try {
            JsonNode providerConfig = objectMapper.readTree(version.getProviderConfigJson());
            EntityConfigValidationContext context = new EntityConfigValidationContext(
                false,
                ManagedDeploymentProfileCatalog.sharedVectorStorageRequested(providerConfig)
            );
            EntityConfigContractValidation persistedValidation = entityConfigContractService.requireValid(
                objectMapper.readTree(version.getEntityConfigJson()),
                context
            );
            JsonNode expectedRuntimeConfig = persistedValidation.runtimeConfig();
            JsonNode artifactConfig = yamlMapper.readTree(version.getEntityArtifactYaml());
            EntityConfigContractValidation artifactValidation =
                entityConfigContractService.requireValid(artifactConfig, context);
            if (!canonicalJson(expectedRuntimeConfig).equals(
                canonicalJson(artifactValidation.runtimeConfig())
            )) {
                issues.add("Entity artifact does not match the persisted normalized V0_4 projection.");
            }

            JsonNode manifest = objectMapper.readTree(version.getManifestJson());
            requireManifestText(manifest, "deploymentId", version.getDeploymentId(), issues);
            requireManifestText(manifest, "versionId", version.getId(), issues);
            requireManifestText(
                manifest,
                "entityConfigContractVersion",
                version.getEntityConfigContractVersion(),
                issues
            );
            requireManifestText(
                manifest,
                "aiFabricFrameworkVersion",
                version.getAiFabricFrameworkVersion(),
                issues
            );
            EntityConfigContractValidation manifestValidation = entityConfigContractService.requireValid(
                manifest.path("entityConfig"),
                context
            );
            if (!canonicalJson(expectedRuntimeConfig).equals(
                canonicalJson(manifestValidation.runtimeConfig())
            )) {
                issues.add("Manifest entityConfig does not match the persisted normalized V0_4 projection.");
            }
            String expectedEntityHash = sha256(canonicalJson(expectedRuntimeConfig));
            requireManifestText(manifest, "entityConfigHash", expectedEntityHash, issues);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            issues.add("Published entity artifact could not be verified: " + safeMessage(ex));
        }

        if (!issues.isEmpty()) {
            throw incompatible(issues);
        }
    }

    private void requireManifestText(JsonNode manifest,
                                     String field,
                                     String expected,
                                     List<String> issues) {
        String actual = manifest.path(field).asText(null);
        if (!java.util.Objects.equals(expected, actual)) {
            issues.add(
                "Manifest "
                    + field
                    + " "
                    + display(actual)
                    + " does not match published value "
                    + display(expected)
                    + "."
            );
        }
    }

    private ResponseStatusException incompatible(List<String> issues) {
        return new ResponseStatusException(
            CONFLICT,
            "AI_FABRIC_RUNTIME_ARTIFACT_INCOMPATIBLE: " + String.join(" | ", issues)
        );
    }

    private String display(String value) {
        return StringUtils.hasText(value) ? "'" + value + "'" : "<missing>";
    }

    private String safeMessage(Exception ex) {
        return StringUtils.hasText(ex.getMessage())
            ? ex.getMessage()
            : ex.getClass().getSimpleName();
    }

    private JsonNode canonicalize(JsonNode node) {
        if (node == null || node.isNull()) {
            return objectMapper.nullNode();
        }
        if (node.isArray()) {
            ArrayNode array = objectMapper.createArrayNode();
            node.forEach(item -> array.add(canonicalize(item)));
            return array;
        }
        if (!node.isObject()) {
            return node.deepCopy();
        }
        ObjectNode object = objectMapper.createObjectNode();
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        names.sort(Comparator.naturalOrder());
        names.forEach(name -> object.set(name, canonicalize(node.get(name))));
        return object;
    }

    private String canonicalJson(JsonNode node) throws JsonProcessingException {
        return objectMapper.writeValueAsString(canonicalize(node));
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
