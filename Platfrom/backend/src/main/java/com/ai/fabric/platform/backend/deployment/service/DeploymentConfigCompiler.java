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
import java.util.Set;

import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class DeploymentConfigCompiler {

    private final ObjectMapper objectMapper;
    private final ObjectMapper yamlMapper;
    private final EntityConfigContractService entityConfigContractService;
    private final DeploymentCompositionProvenanceService deploymentCompositionProvenanceService;
    private final String aiFabricFrameworkVersion;

    public DeploymentConfigCompiler(ObjectMapper objectMapper) {
        this(objectMapper, new EntityConfigContractService(objectMapper), null, "0.8.13");
    }

    @Autowired
    public DeploymentConfigCompiler(ObjectMapper objectMapper,
                                    EntityConfigContractService entityConfigContractService,
                                    DeploymentCompositionProvenanceService deploymentCompositionProvenanceService,
                                    @Value("${platform.ai-fabric.framework-version:0.8.13}") String aiFabricFrameworkVersion) {
        this.objectMapper = objectMapper;
        this.entityConfigContractService = entityConfigContractService;
        this.deploymentCompositionProvenanceService = deploymentCompositionProvenanceService;
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
            JsonNode behaviorNode = objectMapper.readTree(draft.getBehaviorConfigJson());
            JsonNode effectiveRoutingNode = compileRoutingConfig(
                actionsNode,
                routingNode,
                securityNode,
                marketplaceDatasetNode,
                deployment.getId(),
                deployment.getTenantId()
            );
            EntityConfigValidationContext entityContext = new EntityConfigValidationContext(
                false,
                ManagedDeploymentProfileCatalog.sharedVectorStorageRequested(providerNode)
            );
            EntityConfigContractValidation entityValidation =
                entityConfigContractService.requireValid(entityNode, entityContext);
            JsonNode runtimeEntityNode = entityValidation.runtimeConfig();

            String actionsArtifactYaml = yamlMapper.writeValueAsString(actionsNode);
            JsonNode runtimeEntityArtifactNode = runtimeEntityArtifactProjection(runtimeEntityNode);
            String entityArtifactYaml = yamlMapper.writeValueAsString(runtimeEntityArtifactNode);
            String routingArtifactYaml = yamlMapper.writeValueAsString(effectiveRoutingNode);
            JsonNode entityRoundTripNode = restoreEmptyEntityMapForValidation(
                yamlMapper.readTree(entityArtifactYaml),
                runtimeEntityNode
            );
            EntityConfigContractValidation roundTripValidation =
                entityConfigContractService.requireValid(entityRoundTripNode, entityContext);
            if (!canonicalJson(runtimeEntityNode).equals(canonicalJson(roundTripValidation.runtimeConfig()))) {
                throw new IllegalStateException(
                    "Entity configuration changed during the AI_ENTITY_CONFIG_V0_4 YAML round trip."
                );
            }
            String entityConfigHash = sha256(canonicalJson(runtimeEntityNode));

            ObjectNode compositionProvenance = objectMapper.createObjectNode();
            compositionProvenance.put("schemaVersion", "loomai-composition-provenance-v1");
            compositionProvenance.put("deploymentBehaviorSchemaVersion", behaviorNode.path("schemaVersion").asText(""));
            compositionProvenance.put("deploymentBehaviorType", behaviorNode.path("type").asText(""));
            compositionProvenance.put("deploymentBehaviorContractVersion", behaviorNode.path("contractVersion").asInt(-1));
            compositionProvenance.put("templateId", deployment.getTemplateId());
            compositionProvenance.put("curatedModuleId", providerNode.path("curatedModuleId").asText("default"));
            compositionProvenance.put("aiFabricFrameworkVersion", aiFabricFrameworkVersion);
            ObjectNode sectionHashes = compositionProvenance.putObject("sectionHashes");
            sectionHashes.put("actions", sha256(canonicalJson(actionsNode)));
            sectionHashes.put("entities", entityConfigHash);
            sectionHashes.put("routing", sha256(canonicalJson(effectiveRoutingNode)));
            sectionHashes.put("providers", sha256(canonicalJson(providerNode)));
            sectionHashes.put("security", sha256(canonicalJson(securityNode)));
            sectionHashes.put("prompts", sha256(canonicalJson(promptNode)));
            sectionHashes.put("knowledgeSources", sha256(canonicalJson(knowledgeSourceNode)));
            sectionHashes.put("shell", sha256(canonicalJson(shellNode)));
            sectionHashes.put("marketplaceDatasets", sha256(canonicalJson(marketplaceDatasetNode)));
            sectionHashes.put("behavior", sha256(canonicalJson(behaviorNode)));
            compositionProvenance.set(
                "verificationPackIds",
                behaviorNode.path("runtimeRequirements").path("verificationPackIds").deepCopy()
            );
            compositionProvenance.set(
                "marketplaceInstalls",
                deploymentCompositionProvenanceService == null
                    ? objectMapper.createArrayNode()
                    : deploymentCompositionProvenanceService.marketplaceInstalls(deployment.getId())
            );
            String compositionHash = sha256(canonicalJson(compositionProvenance));
            compositionProvenance.put("compositionHash", compositionHash);

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
            manifest.put("deploymentBehaviorType", behaviorNode.path("type").asText(""));
            manifest.put("deploymentBehaviorSchemaVersion", behaviorNode.path("schemaVersion").asText(""));
            manifest.put("compositionHash", compositionHash);
            manifest.put("actionsConfig", actionsNode);
            manifest.put("entityConfig", runtimeEntityNode);
            manifest.put("routingConfig", effectiveRoutingNode);
            manifest.put("providerConfig", providerNode);
            manifest.put("securityConfig", securityNode);
            manifest.put("promptConfig", promptNode);
            manifest.put("knowledgeSourceConfig", knowledgeSourceNode);
            manifest.put("shellConfig", shellNode);
            manifest.put("marketplaceDatasetConfig", marketplaceDatasetNode);
            manifest.put("behaviorConfig", behaviorNode);
            manifest.put("compositionProvenance", compositionProvenance);

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
            configHashMaterial.put("behaviorConfig", canonicalize(behaviorNode));
            configHashMaterial.put("compositionProvenance", canonicalize(compositionProvenance));
            String configHash = sha256(objectMapper.writeValueAsString(configHashMaterial));
            String manifestJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest);

            return new CompiledDeploymentVersion(
                actionsArtifactYaml,
                entityArtifactYaml,
                routingArtifactYaml,
                objectMapper.writeValueAsString(knowledgeSourceNode),
                objectMapper.writeValueAsString(shellNode),
                objectMapper.writeValueAsString(compositionProvenance),
                manifestJson,
                configHash
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to compile deployment configuration: " + ex.getMessage(), ex);
        }
    }

    JsonNode compileRoutingConfig(JsonNode actionsNode, JsonNode routingNode, JsonNode securityNode) {
        return compileRoutingConfig(
            actionsNode,
            routingNode,
            securityNode,
            objectMapper.createObjectNode(),
            null,
            null
        );
    }

    JsonNode compileRoutingConfig(JsonNode actionsNode,
                                  JsonNode routingNode,
                                  JsonNode securityNode,
                                  JsonNode marketplaceDatasetNode,
                                  String deploymentId,
                                  String tenantId) {
        ObjectNode root = routingNode != null && routingNode.isObject()
            ? routingNode.deepCopy()
            : objectMapper.createObjectNode();
        ObjectNode connector = object(root, "connector");
        ObjectNode inboundAuth = object(connector, "inbound-auth");
        ObjectNode apiKey = object(inboundAuth, "api-key");
        ObjectNode actions = object(root, "actions");

        applyInlineActionRoutes(actionsNode, actions);
        applyHttpDataSources(root, marketplaceDatasetNode, deploymentId, tenantId);
        validateProviderActionRoutes(root);

        boolean connectorApiKeyEnabled = ManagedDeploymentProfileCatalog.connectorApiKeyEnabled(securityNode);
        inboundAuth.put("allow-unauthenticated", !connectorApiKeyEnabled);
        apiKey.put("enabled", connectorApiKeyEnabled);
        if (!StringUtils.hasText(apiKey.path("header").asText(""))) {
            apiKey.put("header", ManagedDeploymentProfileCatalog.CONNECTOR_API_KEY_HEADER);
        }
        apiKey.put("value", connectorApiKeyEnabled ? "${CONNECTOR_API_KEY}" : "");
        return root;
    }

    private void validateProviderActionRoutes(ObjectNode root) {
        JsonNode actions = root.path("actions");
        JsonNode profiles = root.path("connection-profiles");
        JsonNode resources = root.path("protected-resources");
        if (!actions.isObject()) {
            return;
        }
        actions.fields().forEachRemaining(entry -> {
            String actionId = entry.getKey();
            JsonNode route = entry.getValue();
            String profileRef = route.path("connection-profile-ref").asText("").trim();
            if (!StringUtils.hasText(profileRef)) {
                return;
            }
            JsonNode profile = profiles.path(profileRef);
            if (!profile.isObject()) {
                throw new IllegalStateException(
                    "Provider action '" + actionId + "' references an unavailable connection profile: " + profileRef
                );
            }
            String bindingRef = route.path("protected-resource-binding-ref").asText("").trim();
            JsonNode binding = resources.path(bindingRef);
            if (!StringUtils.hasText(bindingRef) || !binding.isObject()) {
                throw new IllegalStateException(
                    "Provider action '" + actionId + "' references an unavailable protected resource binding."
                );
            }
            if (!profileRef.equals(binding.path("connection-profile-ref").asText(""))) {
                throw new IllegalStateException(
                    "Provider action '" + actionId + "' uses a protected resource from a different connection profile."
                );
            }
            String path = route.path("path").asText("").trim();
            if (!StringUtils.hasText(path) || !path.startsWith("/") || path.contains("://")) {
                throw new IllegalStateException("Provider action '" + actionId + "' must use a relative path.");
            }
            String method = route.path("method").asText("").trim().toUpperCase(java.util.Locale.ROOT);
            if (!Set.of("GET", "POST", "PUT", "PATCH", "DELETE").contains(method)) {
                throw new IllegalStateException("Provider action '" + actionId + "' uses an unsupported HTTP method.");
            }
            JsonNode placements = route.path("trusted-resource-placements");
            if (!placements.isArray() || placements.isEmpty()) {
                throw new IllegalStateException(
                    "Provider action '" + actionId + "' must declare a server-owned protected resource placement."
                );
            }
            Set<String> required = stringSet(route.path("required-capability-grants"));
            if (required.isEmpty()
                || !stringSet(profile.path("capability-grants")).containsAll(required)
                || !stringSet(binding.path("capability-grants")).containsAll(required)) {
                throw new IllegalStateException(
                    "Provider action '" + actionId + "' requires capability grants absent from its compiled authority."
                );
            }
        });
    }

    private Set<String> stringSet(JsonNode node) {
        if (!node.isArray()) {
            return Set.of();
        }
        java.util.LinkedHashSet<String> values = new java.util.LinkedHashSet<>();
        node.forEach(value -> {
            String normalized = value.asText("").trim();
            if (StringUtils.hasText(normalized)) {
                values.add(normalized);
            }
        });
        return Set.copyOf(values);
    }

    private void applyHttpDataSources(ObjectNode root,
                                      JsonNode marketplaceDatasetNode,
                                      String deploymentId,
                                      String tenantId) {
        JsonNode datasets = marketplaceDatasetNode != null ? marketplaceDatasetNode.path("datasets") : null;
        if (datasets == null || !datasets.isArray()) {
            return;
        }
        ObjectNode profiles = object(root, "connection-profiles");
        ObjectNode resources = object(root, "protected-resources");
        ObjectNode sources = object(root, "data-sources");
        ObjectNode webhooks = object(root, "webhooks");
        boolean configured = false;
        for (JsonNode dataset : datasets) {
            if (!"EXTERNAL_SYNC_HTTP".equalsIgnoreCase(dataset.path("ingestionMode").asText(""))) {
                continue;
            }
            JsonNode connector = dataset.path("syncConnector");
            JsonNode profile = connector.path("connectionProfile");
            JsonNode resource = connector.path("protectedResource");
            JsonNode source = connector.path("httpSource");
            String profileId = profile.path("profileId").asText("").trim();
            String bindingId = resource.path("bindingId").asText("").trim();
            String sourceId = source.path("sourceId").asText("").trim();
            if (!StringUtils.hasText(profileId) || !StringUtils.hasText(bindingId) || !StringUtils.hasText(sourceId)) {
                throw new IllegalStateException("Compiled EXTERNAL_SYNC_HTTP dataset is missing profile, binding, or source identity.");
            }
            putUniqueIntegrationConfig(profiles, profileId, withoutIdentity(profile, "profileId"), "connection profile");
            putUniqueIntegrationConfig(resources, bindingId, withoutIdentity(resource, "bindingId"), "protected resource");
            ObjectNode compiledSource = withoutIdentity(source, "sourceId");
            String sourceVersion = dataset.path("datasetHash").asText("").trim();
            if (StringUtils.hasText(sourceVersion)) {
                compiledSource.put("source-version", sourceVersion);
            }
            String knowledgeSourceHandleRef = dataset.path("handleRef").asText("").trim();
            if (!StringUtils.hasText(knowledgeSourceHandleRef)) {
                throw new IllegalStateException(
                    "Compiled EXTERNAL_SYNC_HTTP dataset is missing its server-owned knowledge source handle."
                );
            }
            compiledSource.put("knowledge-source-handle-ref", knowledgeSourceHandleRef);
            putUniqueIntegrationConfig(sources, sourceId, compiledSource, "HTTP data source");
            JsonNode webhook = connector.path("webhook");
            if (webhook.isObject()) {
                String webhookId = webhook.path("sourceId").asText("").trim();
                if (!StringUtils.hasText(webhookId)) {
                    throw new IllegalStateException("Compiled integration webhook is missing sourceId.");
                }
                putUniqueIntegrationConfig(webhooks, webhookId, withoutIdentity(webhook, "sourceId"), "webhook source");
            }
            configured = true;
        }
        if (!configured) {
            return;
        }
        ObjectNode runtimeDataSync = object(root, "runtime-data-sync");
        runtimeDataSync.put("enabled", true);
        runtimeDataSync.put("base-url", "${AI_FABRIC_RUNTIME_INTERNAL_BASE_URL:http://runtime:8080}");
        runtimeDataSync.put("api-key-header", "X-AIFABRIC-INTEGRATION-KEY");
        runtimeDataSync.put("api-key-value", "${AI_FABRIC_RUNTIME_INTEGRATION_SERVICE_API_KEY}");
        if (StringUtils.hasText(deploymentId)) {
            runtimeDataSync.put("deployment-id", deploymentId);
        }
        if (StringUtils.hasText(tenantId)) {
            runtimeDataSync.put("tenant-id", tenantId);
        }
        runtimeDataSync.put("timeout-ms", 15000);
        runtimeDataSync.put("work-poll-interval-ms", 500);
        runtimeDataSync.put("work-poll-attempts", 60);
    }

    private ObjectNode withoutIdentity(JsonNode node, String identityField) {
        ObjectNode copy = node instanceof ObjectNode objectNode
            ? objectNode.deepCopy()
            : objectMapper.createObjectNode();
        copy.remove(identityField);
        return kebabize(copy, false);
    }

    private void putUniqueIntegrationConfig(ObjectNode target,
                                            String id,
                                            ObjectNode value,
                                            String label) {
        JsonNode existing = target.get(id);
        if (existing != null && !canonicalize(existing).equals(canonicalize(value))) {
            throw new IllegalStateException("Conflicting Marketplace " + label + " id: " + id);
        }
        target.set(id, value);
    }

    private ObjectNode kebabize(ObjectNode source, boolean preserveKeys) {
        ObjectNode out = objectMapper.createObjectNode();
        source.fields().forEachRemaining(entry -> {
            String field = preserveKeys ? entry.getKey() : camelToKebab(entry.getKey());
            boolean preserveChildren = Set.of(
                "credentialFields", "staticFields", "query", "headers",
                "contentFields", "entityFields", "metadataFields"
            ).contains(entry.getKey());
            out.set(field, kebabizeValue(entry.getValue(), preserveChildren));
        });
        return out;
    }

    private JsonNode kebabizeValue(JsonNode value, boolean preserveKeys) {
        if (value instanceof ObjectNode objectNode) {
            return kebabize(objectNode, preserveKeys);
        }
        if (value instanceof ArrayNode arrayNode) {
            ArrayNode out = objectMapper.createArrayNode();
            arrayNode.forEach(item -> out.add(kebabizeValue(item, false)));
            return out;
        }
        return value.deepCopy();
    }

    private String camelToKebab(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(java.util.Locale.ROOT);
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
        rename(route, "connectionProfileRef", "connection-profile-ref");
        rename(route, "protectedResourceBindingRef", "protected-resource-binding-ref");
        rename(route, "requiredCapabilityGrants", "required-capability-grants");
        rename(route, "idempotencyHeader", "idempotency-header");
        JsonNode placements = route.remove("trustedResourcePlacements");
        if (placements instanceof ArrayNode arrayNode) {
            route.set("trusted-resource-placements", kebabizeValue(arrayNode, false));
        }
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

    private void rename(ObjectNode node, String from, String to) {
        JsonNode value = node.remove(from);
        if (value != null && !node.has(to)) {
            node.set(to, value);
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
            JsonNode artifactConfig = restoreEmptyEntityMapForValidation(
                yamlMapper.readTree(version.getEntityArtifactYaml()),
                expectedRuntimeConfig
            );
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
            JsonNode persistedBehavior = objectMapper.readTree(version.getBehaviorConfigJson());
            if (!canonicalJson(persistedBehavior).equals(canonicalJson(manifest.path("behaviorConfig")))) {
                issues.add("Manifest behaviorConfig does not match the persisted behavior contract.");
            }
            JsonNode persistedProvenance = objectMapper.readTree(version.getCompositionProvenanceJson());
            if (!canonicalJson(persistedProvenance).equals(canonicalJson(manifest.path("compositionProvenance")))) {
                issues.add("Manifest composition provenance does not match the published version.");
            }
            requireManifestText(
                manifest,
                "deploymentBehaviorType",
                persistedBehavior.path("type").asText(""),
                issues
            );
            requireManifestText(
                manifest,
                "deploymentBehaviorSchemaVersion",
                persistedBehavior.path("schemaVersion").asText(""),
                issues
            );
            requireManifestText(
                manifest,
                "compositionHash",
                persistedProvenance.path("compositionHash").asText(""),
                issues
            );
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

    private JsonNode runtimeEntityArtifactProjection(JsonNode runtimeEntityConfig) {
        if (!(runtimeEntityConfig instanceof ObjectNode runtimeRoot)) {
            return runtimeEntityConfig;
        }
        ObjectNode artifactRoot = runtimeRoot.deepCopy();
        JsonNode entities = artifactRoot.path("ai-entities");
        if (entities.isObject() && entities.isEmpty()) {
            // Spring Config Data exposes an empty YAML map root as an empty scalar. Omitting
            // the root lets AI Fabric's optional map binding resolve to the same empty map.
            artifactRoot.remove("ai-entities");
        }
        return artifactRoot;
    }

    private JsonNode restoreEmptyEntityMapForValidation(JsonNode artifactConfig,
                                                        JsonNode expectedRuntimeConfig) {
        if (!(artifactConfig instanceof ObjectNode artifactRoot)
            || artifactRoot.has("ai-entities")
            || !expectedRuntimeConfig.path("ai-entities").isObject()
            || !expectedRuntimeConfig.path("ai-entities").isEmpty()) {
            return artifactConfig;
        }
        ObjectNode normalized = artifactRoot.deepCopy();
        normalized.putObject("ai-entities");
        return normalized;
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
        String compositionProvenanceJson,
        String manifestJson,
        String configHash
    ) {
        public CompiledDeploymentVersion(String actionsArtifactYaml,
                                         String entityArtifactYaml,
                                         String routingArtifactYaml,
                                         String manifestJson,
                                         String configHash) {
            this(actionsArtifactYaml, entityArtifactYaml, routingArtifactYaml, "{}", "{}", "{}", manifestJson, configHash);
        }
    }
}
