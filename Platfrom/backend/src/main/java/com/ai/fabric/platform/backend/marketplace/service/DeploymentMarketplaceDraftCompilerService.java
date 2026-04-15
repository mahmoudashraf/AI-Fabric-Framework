package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DraftValidationIssue;
import com.ai.fabric.platform.backend.deployment.model.DraftValidationResponse;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentDraftRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentDraftValidationService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.marketplace.entity.DeploymentMarketplacePluginInstallEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.ai.fabric.platform.backend.marketplace.repository.DeploymentMarketplacePluginInstallRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class DeploymentMarketplaceDraftCompilerService {

    private static final String MARKETPLACE_MANAGED_FIELD = "marketplaceManaged";
    private static final String MARKETPLACE_PLUGIN_ID_FIELD = "marketplacePluginId";
    private static final String MARKETPLACE_INSTALL_ID_FIELD = "marketplaceInstallId";
    private static final String MARKETPLACE_PLUGIN_VERSION_FIELD = "marketplacePluginVersion";
    private static final String DEFAULT_KNOWLEDGE_SOURCE_CONTRACT_VERSION = "KNOWLEDGE_SOURCE_CONFIG_V1";
    private static final String DEFAULT_SHELL_CONTRACT_VERSION = "SHELL_CONFIG_V1";
    private static final String DEFAULT_MARKETPLACE_DATASET_CONTRACT_VERSION = "MARKETPLACE_DATASET_CONFIG_V1";

    private final DeploymentService deploymentService;
    private final DeploymentDraftValidationService deploymentDraftValidationService;
    private final DeploymentRepository deploymentRepository;
    private final DeploymentMarketplacePluginInstallRepository installRepository;
    private final MarketplaceCatalogService marketplaceCatalogService;
    private final MarketplaceManifestService marketplaceManifestService;
    private final MarketplaceEntitlementService marketplaceEntitlementService;
    private final MarketplaceDatasetHandleResolver marketplaceDatasetHandleResolver;
    private final ObjectMapper objectMapper;

    public DeploymentMarketplaceDraftCompilerService(DeploymentService deploymentService,
                                                     DeploymentDraftValidationService deploymentDraftValidationService,
                                                     DeploymentRepository deploymentRepository,
                                                     DeploymentMarketplacePluginInstallRepository installRepository,
                                                     MarketplaceCatalogService marketplaceCatalogService,
                                                     MarketplaceManifestService marketplaceManifestService,
                                                     MarketplaceEntitlementService marketplaceEntitlementService,
                                                     MarketplaceDatasetHandleResolver marketplaceDatasetHandleResolver,
                                                     ObjectMapper objectMapper) {
        this.deploymentService = deploymentService;
        this.deploymentDraftValidationService = deploymentDraftValidationService;
        this.deploymentRepository = deploymentRepository;
        this.installRepository = installRepository;
        this.marketplaceCatalogService = marketplaceCatalogService;
        this.marketplaceManifestService = marketplaceManifestService;
        this.marketplaceEntitlementService = marketplaceEntitlementService;
        this.marketplaceDatasetHandleResolver = marketplaceDatasetHandleResolver;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DeploymentDraftResponse syncDeploymentDraft(String deploymentId) {
        DeploymentDraftResponse draft = deploymentService.getActiveDraftForDeployment(deploymentId);
        ObjectNode actionsRoot = ensureObject(draft.actionsConfig());
        ObjectNode entityRoot = normalizeEntityRoot(draft.entityConfig());
        ObjectNode knowledgeSourceRoot = normalizeKnowledgeSourceRoot(draft.knowledgeSourceConfig());
        ObjectNode shellRoot = normalizeShellRoot(draft.shellConfig());
        ObjectNode marketplaceDatasetRoot = normalizeMarketplaceDatasetRoot(draft.marketplaceDatasetConfig());
        DeploymentEntity deployment = deploymentRepository.findById(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(CONFLICT, "Deployment not found: " + deploymentId));

        stripMarketplaceManagedActions(actionsRoot);
        stripMarketplaceManagedEntities(entityRoot);
        stripMarketplaceManagedKnowledgeSources(knowledgeSourceRoot);
        stripMarketplaceManagedShell(shellRoot);
        stripMarketplaceManagedDatasets(marketplaceDatasetRoot);

        List<DeploymentMarketplacePluginInstallEntity> installs =
            installRepository.findByDeploymentIdOrderByUpdatedAtDesc(deploymentId);
        Set<String> existingActionNames = actionNames(actionsRoot.path("actions"));
        Set<String> existingEntityTypes = entityTypes(entityRoot.path("ai-entities"));
        Set<String> existingKnowledgeSourceIds = knowledgeSourceIds(knowledgeSourceRoot.path("sources"));

        for (DeploymentMarketplacePluginInstallEntity install : installs) {
            if (!isEnabledForCompilation(install.getStatus())) {
                continue;
            }
            MarketplacePluginEntity plugin = marketplaceCatalogService.requirePluginEntity(install.getPluginId());
            MarketplacePluginVersionEntity version =
                marketplaceCatalogService.requirePluginVersionEntityById(install.getPluginVersionId());
            MarketplaceManifestService.ParsedMarketplaceManifest parsed =
                marketplaceManifestService.parseAndValidate(plugin, version);
            if (!marketplaceEntitlementService.evaluate(
                parsed,
                marketplaceEntitlementService.findByInstallId(install.getId())
            ).entitledForCompilation()) {
                continue;
            }

            switch (parsed.pluginType()) {
                case "ACTION" -> applyActionPlugin(actionsRoot, shellRoot, install, plugin, version, parsed, existingActionNames);
                case "DATA" -> applyDataPlugin(
                    entityRoot,
                    knowledgeSourceRoot,
                    shellRoot,
                    marketplaceDatasetRoot,
                    deployment,
                    install,
                    plugin,
                    version,
                    parsed,
                    existingEntityTypes,
                    existingKnowledgeSourceIds
                );
                case "TEMPLATE" -> applyTemplateShell(
                    shellRoot,
                    plugin,
                    version,
                    parsed.manifest().path("contributions").path("template").path("shell")
                );
                default -> throw new ResponseStatusException(
                    CONFLICT,
                    "Unsupported marketplace plugin type during draft compilation: " + parsed.pluginType()
                );
            }
        }

        DeploymentDraftResponse updated = deploymentService.updateDraft(
            draft.id(),
            new UpdateDeploymentDraftRequest(
                actionsRoot,
                entityRoot,
                null,
                null,
                null,
                null,
                knowledgeSourceRoot,
                shellRoot,
                marketplaceDatasetRoot
            )
        );
        DraftValidationResponse validation = deploymentDraftValidationService.validate(asDraftEntity(updated));
        if (!validation.publishReady()) {
            throw new ResponseStatusException(
                CONFLICT,
                "Marketplace install compilation produced an invalid draft: " + summarizeIssues(validation.issues())
            );
        }
        return updated;
    }

    public ObjectNode compileTemplateShellBaseline(MarketplacePluginEntity plugin,
                                                   MarketplacePluginVersionEntity version,
                                                   JsonNode existingShellConfig) {
        MarketplaceManifestService.ParsedMarketplaceManifest parsed =
            marketplaceManifestService.parseAndValidate(plugin, version);
        if (!"TEMPLATE".equals(parsed.pluginType())) {
            throw new ResponseStatusException(CONFLICT, "Marketplace plugin is not a template plugin: " + plugin.getId());
        }
        ObjectNode shellRoot = normalizeShellRoot(existingShellConfig);
        JsonNode templateShell = parsed.manifest().path("contributions").path("template").path("shell");
        applyTemplateShell(shellRoot, plugin, version, templateShell);
        return shellRoot;
    }

    public ObjectNode compileTemplateSecurityBaseline(MarketplacePluginEntity plugin,
                                                      MarketplacePluginVersionEntity version,
                                                      JsonNode existingSecurityConfig) {
        MarketplaceManifestService.ParsedMarketplaceManifest parsed =
            marketplaceManifestService.parseAndValidate(plugin, version);
        if (!"TEMPLATE".equals(parsed.pluginType())) {
            throw new ResponseStatusException(CONFLICT, "Marketplace plugin is not a template plugin: " + plugin.getId());
        }
        ObjectNode securityRoot = ensureObject(existingSecurityConfig);
        JsonNode templateSecurity = parsed.manifest().path("contributions").path("template").path("security");
        applyTemplateSecurity(securityRoot, templateSecurity);
        return securityRoot;
    }

    private void applyActionPlugin(ObjectNode actionsRoot,
                                   ObjectNode shellRoot,
                                   DeploymentMarketplacePluginInstallEntity install,
                                   MarketplacePluginEntity plugin,
                                   MarketplacePluginVersionEntity version,
                                   MarketplaceManifestService.ParsedMarketplaceManifest parsed,
                                   Set<String> existingActionNames) {
        ArrayNode actions = ensureArray(actionsRoot, "actions");
        JsonNode actionEntries = parsed.manifest().path("contributions").path("actions");
        for (JsonNode actionEntry : iterable(actionEntries)) {
            if (!actionEntry.isObject()) {
                continue;
            }
            String actionName = text(actionEntry, "actionId", "id");
            if (!StringUtils.hasText(actionName)) {
                continue;
            }
            if (!existingActionNames.add(actionName)) {
                throw new ResponseStatusException(
                    CONFLICT,
                    "Marketplace action conflicts with an existing deployment action: " + actionName
                );
            }
            ObjectNode compiled = objectMapper.createObjectNode();
            compiled.put("name", actionName);
            copyIfText(actionEntry, compiled, "description", "description");
            if (!StringUtils.hasText(compiled.path("description").asText(""))) {
                compiled.put("description", plugin.getDisplayName() + " action: " + actionName);
            }
            copyIfText(actionEntry, compiled, "category", "category");
            if (!StringUtils.hasText(compiled.path("category").asText(""))) {
                compiled.put("category", "marketplace");
            }
            if (actionEntry.path("readOnly").isBoolean()) {
                compiled.put("accessMode", actionEntry.path("readOnly").asBoolean() ? "READ" : "WRITE_ONLY");
            } else if (StringUtils.hasText(actionEntry.path("accessMode").asText(""))) {
                compiled.put("accessMode", actionEntry.path("accessMode").asText("").trim());
            }
            if (actionEntry.path("confirmationRequired").isBoolean()) {
                compiled.put("requiresConfirmation", actionEntry.path("confirmationRequired").asBoolean());
            } else if (actionEntry.path("requiresConfirmation").isBoolean()) {
                compiled.put("requiresConfirmation", actionEntry.path("requiresConfirmation").asBoolean());
            }
            if (actionEntry.path("anonymousAllowed").isBoolean()) {
                compiled.put("anonymousAllowed", actionEntry.path("anonymousAllowed").asBoolean());
            }
            if (actionEntry.path("confirmationMessage").isTextual()) {
                compiled.put("confirmationMessage", actionEntry.path("confirmationMessage").asText("").trim());
            }
            if (actionEntry.path("params").isArray()) {
                compiled.set("params", actionEntry.path("params").deepCopy());
            }
            if (actionEntry.path("requiredParameters").isArray()) {
                compiled.set("requiredParameters", actionEntry.path("requiredParameters").deepCopy());
            }
            if (actionEntry.path("route").isObject()) {
                compiled.set("route", actionEntry.path("route").deepCopy());
            }
            applyMarketplaceProvenance(compiled, install, plugin, version);
            actions.add(compiled);
        }

        applyShellContribution(
            shellRoot,
            parsed.manifest().path("contributions").path("shell"),
            install,
            plugin,
            version
        );
    }

    private void applyDataPlugin(ObjectNode entityRoot,
                                 ObjectNode knowledgeSourceRoot,
                                 ObjectNode shellRoot,
                                 ObjectNode marketplaceDatasetRoot,
                                 DeploymentEntity deployment,
                                 DeploymentMarketplacePluginInstallEntity install,
                                 MarketplacePluginEntity plugin,
                                 MarketplacePluginVersionEntity version,
                                 MarketplaceManifestService.ParsedMarketplaceManifest parsed,
                                 Set<String> existingEntityTypes,
                                 Set<String> existingKnowledgeSourceIds) {
        ArrayNode sources = ensureArray(knowledgeSourceRoot, "sources");
        ArrayNode datasets = ensureArray(marketplaceDatasetRoot, "datasets");
        JsonNode installConfig = readJson(install.getConfigJson());
        JsonNode installSecretRefs = readJson(install.getSecretRefsJson());
        applyEntityContribution(
            entityRoot,
            parsed.manifest().path("contributions").path("entityConfig"),
            install,
            plugin,
            version,
            existingEntityTypes
        );
        java.util.Map<String, MarketplaceManifestService.ParsedMarketplaceDatasetDefinition> datasetsById = parsed.datasets().stream()
            .collect(java.util.stream.Collectors.toMap(
                MarketplaceManifestService.ParsedMarketplaceDatasetDefinition::datasetId,
                java.util.function.Function.identity(),
                (left, right) -> left,
                java.util.LinkedHashMap::new
            ));
        for (MarketplaceManifestService.ParsedMarketplaceDatasetDefinition dataset : parsed.datasets()) {
            ObjectNode compiledDataset = objectMapper.createObjectNode();
            compiledDataset.put("datasetId", dataset.datasetId());
            compiledDataset.put("entityType", dataset.entityType());
            compiledDataset.put("storageScope", dataset.storageScope());
            compiledDataset.put("sharingScope", dataset.sharingScope());
            compiledDataset.put("ingestionMode", dataset.ingestionMode());
            compiledDataset.put("updateStrategy", dataset.updateStrategy());
            if (StringUtils.hasText(dataset.vectorizationProfile())) {
                compiledDataset.put("vectorizationProfile", dataset.vectorizationProfile());
            }
            ObjectNode resolvedSyncConnector = resolveSyncConnector(dataset, installConfig, installSecretRefs);
            String datasetHash = datasetHash(plugin, version, install, dataset, installConfig, installSecretRefs, resolvedSyncConnector);
            String resolvedHandleRef = marketplaceDatasetHandleResolver.resolveHandleRef(deployment, plugin, dataset, datasetHash);
            compiledDataset.put("handleRef", resolvedHandleRef);
            compiledDataset.put("datasetHash", datasetHash);
            if (StringUtils.hasText(dataset.seedDatasetRef())) {
                compiledDataset.put("seedDatasetRef", dataset.seedDatasetRef());
            }
            if (StringUtils.hasText(dataset.connectorType())) {
                compiledDataset.put("connectorType", dataset.connectorType());
            }
            if (!resolvedSyncConnector.isEmpty()) {
                compiledDataset.set("syncConnector", resolvedSyncConnector);
            }
            if (installConfig != null && installConfig.isObject() && !installConfig.isEmpty()) {
                compiledDataset.set("config", installConfig.deepCopy());
            }
            applyMarketplaceProvenance(compiledDataset, install, plugin, version);
            datasets.add(compiledDataset);
        }
        JsonNode sourceEntries = parsed.manifest().path("contributions").path("knowledgeSources");
        for (JsonNode sourceEntry : iterable(sourceEntries)) {
            if (!sourceEntry.isObject()) {
                continue;
            }
            String sourceId = text(sourceEntry, "id", "sourceKey");
            if (!StringUtils.hasText(sourceId)) {
                continue;
            }
            if (!existingKnowledgeSourceIds.add(sourceId.toLowerCase())) {
                throw new ResponseStatusException(
                    CONFLICT,
                    "Marketplace knowledge source conflicts with an existing deployment source: " + sourceId
                );
            }
            ObjectNode compiled = objectMapper.createObjectNode();
            compiled.put("id", sourceId);
            String sourceType = text(sourceEntry, "type", "sourceType");
            String adapterType = text(sourceEntry, "adapterType");
            if (StringUtils.hasText(sourceType)) {
                compiled.put("type", sourceType);
            }
            if (StringUtils.hasText(adapterType)) {
                compiled.put("adapterType", adapterType);
            }
            if (!StringUtils.hasText(compiled.path("adapterType").asText("")) && StringUtils.hasText(sourceType)) {
                compiled.put("adapterType", sourceType);
            }
            String attributionLabel = text(sourceEntry, "attributionLabel");
            compiled.put(
                "attributionLabel",
                StringUtils.hasText(attributionLabel) ? attributionLabel : plugin.getDisplayName()
            );
            copyIfText(sourceEntry, compiled, "entityType", "entityType");
            if (sourceEntry.path("filters").isObject()) {
                compiled.set("filters", sourceEntry.path("filters").deepCopy());
            }
            if (sourceEntry.path("authModes").isArray()) {
                compiled.set("authModes", sourceEntry.path("authModes").deepCopy());
            }
            String handleRef = text(sourceEntry, "handleRef");
            String datasetRef = text(sourceEntry, "datasetRef");
            if (!StringUtils.hasText(datasetRef) && parsed.datasets().size() == 1) {
                datasetRef = parsed.datasets().getFirst().datasetId();
            }
            if (StringUtils.hasText(datasetRef)) {
                compiled.put("datasetRef", datasetRef);
                MarketplaceManifestService.ParsedMarketplaceDatasetDefinition dataset = datasetsById.get(datasetRef);
                if (dataset != null && !StringUtils.hasText(handleRef)) {
                    ObjectNode resolvedSyncConnector = resolveSyncConnector(dataset, installConfig, installSecretRefs);
                    String datasetHash = datasetHash(plugin, version, install, dataset, installConfig, installSecretRefs, resolvedSyncConnector);
                    handleRef = marketplaceDatasetHandleResolver.resolveHandleRef(deployment, plugin, dataset, datasetHash);
                }
            }
            if (!StringUtils.hasText(handleRef) && "shared-index".equalsIgnoreCase(compiled.path("adapterType").asText(""))) {
                handleRef = sourceId;
            }
            if (StringUtils.hasText(handleRef)) {
                compiled.put("handleRef", handleRef);
                ObjectNode filters = sourceEntry.path("filters").isObject()
                    ? (ObjectNode) sourceEntry.path("filters").deepCopy()
                    : objectMapper.createObjectNode();
                filters.put("knowledgeSourceHandleRef", handleRef);
                compiled.set("filters", filters);
            }
            if (sourceEntry.path("enabled").isBoolean()) {
                compiled.put("enabled", sourceEntry.path("enabled").asBoolean());
            } else {
                compiled.put("enabled", true);
            }
            applyMarketplaceProvenance(compiled, install, plugin, version);
            sources.add(compiled);
        }

        applyShellContribution(
            shellRoot,
            parsed.manifest().path("contributions").path("shell"),
            install,
            plugin,
            version
        );
    }

    private void applyTemplateShell(ObjectNode shellRoot,
                                    MarketplacePluginEntity plugin,
                                    MarketplacePluginVersionEntity version,
                                    JsonNode templateShell) {
        if (!templateShell.isObject()) {
            return;
        }
        ObjectNode greeting = templateShell.path("greeting").isObject()
            ? (ObjectNode) templateShell.path("greeting").deepCopy()
            : null;
        if (greeting != null && !hasOperatorOwnedGreeting(shellRoot.path("greeting"))) {
            greeting.put(MARKETPLACE_MANAGED_FIELD, true);
            greeting.put(MARKETPLACE_PLUGIN_ID_FIELD, plugin.getId());
            greeting.put(MARKETPLACE_PLUGIN_VERSION_FIELD, version.getVersion());
            shellRoot.set("greeting", greeting);
        }
        upsertShellIds(shellRoot, "modules", templateShell.path("enabledModuleIds"), plugin.getId(), null, version.getVersion());
        upsertShellIds(shellRoot, "cards", templateShell.path("enabledCardIds"), plugin.getId(), null, version.getVersion());
        if (templateShell.path("starterPrompts").isArray()) {
            ArrayNode starterPrompts = ensureArray(shellRoot, "starterPrompts");
            for (JsonNode prompt : iterable(templateShell.path("starterPrompts"))) {
                if (!prompt.isObject()) {
                    continue;
                }
                String starterId = text(prompt, "id");
                if (!StringUtils.hasText(starterId) || hasStarterPrompt(shellRoot.path("starterPrompts"), starterId)) {
                    continue;
                }
                ObjectNode compiled = ((ObjectNode) prompt).deepCopy();
                compiled.put(MARKETPLACE_MANAGED_FIELD, true);
                compiled.put(MARKETPLACE_PLUGIN_ID_FIELD, plugin.getId());
                compiled.put(MARKETPLACE_PLUGIN_VERSION_FIELD, version.getVersion());
                starterPrompts.add(compiled);
            }
        }
        if (templateShell.path("defaultConversationMode").isTextual()
            && !StringUtils.hasText(shellRoot.path("defaultConversationMode").asText(""))) {
            shellRoot.put("defaultConversationMode", templateShell.path("defaultConversationMode").asText("").trim());
        }
    }

    private void applyTemplateSecurity(ObjectNode securityRoot, JsonNode templateSecurity) {
        if (!templateSecurity.isObject()) {
            return;
        }
        String authzMode = text(templateSecurity, "authzMode");
        if (StringUtils.hasText(authzMode)) {
            securityRoot.put("authzMode", authzMode);
        }
    }

    private void applyShellContribution(ObjectNode shellRoot,
                                        JsonNode shellContribution,
                                        DeploymentMarketplacePluginInstallEntity install,
                                        MarketplacePluginEntity plugin,
                                        MarketplacePluginVersionEntity version) {
        if (!shellContribution.isObject()) {
            return;
        }
        upsertShellIds(shellRoot, "modules", firstArray(shellContribution, "moduleRefs", "enabledModuleIds"), plugin.getId(), install.getId(), version.getVersion());
        upsertShellIds(shellRoot, "cards", firstArray(shellContribution, "cardRefs", "enabledCardIds"), plugin.getId(), install.getId(), version.getVersion());
    }

    private void upsertShellIds(ObjectNode shellRoot,
                                String fieldName,
                                JsonNode ids,
                                String pluginId,
                                String installId,
                                String pluginVersion) {
        if (!ids.isArray()) {
            return;
        }
        ArrayNode entries = ensureArray(shellRoot, fieldName);
        String idField = "modules".equals(fieldName) ? "id" : "id";
        for (JsonNode idNode : ids) {
            String id = idNode.asText("").trim();
            if (!StringUtils.hasText(id) || hasShellEntry(entries, id)) {
                continue;
            }
            ObjectNode entry = objectMapper.createObjectNode();
            entry.put(idField, id);
            entry.put("enabled", true);
            entry.put(MARKETPLACE_MANAGED_FIELD, true);
            entry.put(MARKETPLACE_PLUGIN_ID_FIELD, pluginId);
            if (StringUtils.hasText(installId)) {
                entry.put(MARKETPLACE_INSTALL_ID_FIELD, installId);
            }
            entry.put(MARKETPLACE_PLUGIN_VERSION_FIELD, pluginVersion);
            entries.add(entry);
        }
    }

    private void stripMarketplaceManagedActions(ObjectNode actionsRoot) {
        ArrayNode actions = ensureArray(actionsRoot, "actions");
        removeMarketplaceManagedEntries(actions);
    }

    private void stripMarketplaceManagedEntities(ObjectNode entityRoot) {
        JsonNode entitiesNode = entityRoot.path("ai-entities");
        if (!(entitiesNode instanceof ObjectNode entities)) {
            return;
        }
        List<String> toRemove = new ArrayList<>();
        entities.fields().forEachRemaining(entry -> {
            if (isMarketplaceManaged(entry.getValue())) {
                toRemove.add(entry.getKey());
            }
        });
        toRemove.forEach(entities::remove);
    }

    private void stripMarketplaceManagedKnowledgeSources(ObjectNode knowledgeSourceRoot) {
        ArrayNode sources = ensureArray(knowledgeSourceRoot, "sources");
        removeMarketplaceManagedEntries(sources);
    }

    private void stripMarketplaceManagedShell(ObjectNode shellRoot) {
        removeMarketplaceManagedEntries(ensureArray(shellRoot, "modules"));
        removeMarketplaceManagedEntries(ensureArray(shellRoot, "cards"));
        removeMarketplaceManagedEntries(ensureArray(shellRoot, "starterPrompts"));
        JsonNode greeting = shellRoot.path("greeting");
        if (isMarketplaceManaged(greeting)) {
            shellRoot.remove("greeting");
        }
    }

    private void stripMarketplaceManagedDatasets(ObjectNode marketplaceDatasetRoot) {
        removeMarketplaceManagedEntries(ensureArray(marketplaceDatasetRoot, "datasets"));
    }

    private void removeMarketplaceManagedEntries(ArrayNode array) {
        for (int index = array.size() - 1; index >= 0; index--) {
            if (isMarketplaceManaged(array.get(index))) {
                array.remove(index);
            }
        }
    }

    private boolean isMarketplaceManaged(JsonNode node) {
        return node != null
            && node.isObject()
            && (node.path(MARKETPLACE_MANAGED_FIELD).asBoolean(false)
                || StringUtils.hasText(node.path(MARKETPLACE_INSTALL_ID_FIELD).asText(""))
                || StringUtils.hasText(node.path(MARKETPLACE_PLUGIN_ID_FIELD).asText("")));
    }

    private void applyMarketplaceProvenance(ObjectNode node,
                                            DeploymentMarketplacePluginInstallEntity install,
                                            MarketplacePluginEntity plugin,
                                            MarketplacePluginVersionEntity version) {
        node.put(MARKETPLACE_MANAGED_FIELD, true);
        node.put(MARKETPLACE_PLUGIN_ID_FIELD, plugin.getId());
        node.put(MARKETPLACE_INSTALL_ID_FIELD, install.getId());
        node.put(MARKETPLACE_PLUGIN_VERSION_FIELD, version.getVersion());
    }

    private boolean isEnabledForCompilation(String status) {
        if (!StringUtils.hasText(status)) {
            return false;
        }
        String normalized = status.trim().toUpperCase();
        return "ENABLED".equals(normalized) || "BOOTSTRAPPED".equals(normalized);
    }

    private boolean hasShellEntry(ArrayNode entries, String id) {
        for (JsonNode entry : entries) {
            if (id.equals(entry.path("id").asText("").trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasStarterPrompt(JsonNode starterPrompts, String starterId) {
        if (!starterPrompts.isArray()) {
            return false;
        }
        for (JsonNode starterPrompt : starterPrompts) {
            if (starterId.equals(starterPrompt.path("id").asText("").trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasOperatorOwnedGreeting(JsonNode greeting) {
        if (greeting == null || !greeting.isObject() || isMarketplaceManaged(greeting)) {
            return false;
        }
        return StringUtils.hasText(greeting.path("title").asText(""))
            || StringUtils.hasText(greeting.path("message").asText(""))
            || greeting.size() > 0;
    }

    private Set<String> actionNames(JsonNode actions) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (!actions.isArray()) {
            return names;
        }
        for (JsonNode action : actions) {
            String name = action.path("name").asText("").trim();
            if (StringUtils.hasText(name)) {
                names.add(name);
            }
        }
        return names;
    }

    private Set<String> knowledgeSourceIds(JsonNode sources) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (!sources.isArray()) {
            return ids;
        }
        for (JsonNode source : sources) {
            String id = source.path("id").asText("").trim();
            if (StringUtils.hasText(id)) {
                ids.add(id.toLowerCase());
            }
        }
        return ids;
    }

    private Set<String> entityTypes(JsonNode entitiesNode) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (!entitiesNode.isObject()) {
            return ids;
        }
        Iterator<String> names = entitiesNode.fieldNames();
        while (names.hasNext()) {
            String id = names.next();
            if (StringUtils.hasText(id)) {
                ids.add(id.toLowerCase());
            }
        }
        return ids;
    }

    private void applyEntityContribution(ObjectNode entityRoot,
                                         JsonNode entityContribution,
                                         DeploymentMarketplacePluginInstallEntity install,
                                         MarketplacePluginEntity plugin,
                                         MarketplacePluginVersionEntity version,
                                         Set<String> existingEntityTypes) {
        if (!entityContribution.isObject()) {
            return;
        }
        JsonNode entitiesNode = entityContribution.path("ai-entities");
        if (!(entitiesNode instanceof ObjectNode contributedEntities)) {
            return;
        }
        ObjectNode targetEntities = ensureObjectNode(entityRoot, "ai-entities");
        Iterator<Map.Entry<String, JsonNode>> fields = contributedEntities.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String entityType = entry.getKey() == null ? "" : entry.getKey().trim();
            if (!StringUtils.hasText(entityType)) {
                continue;
            }
            if (!existingEntityTypes.add(entityType.toLowerCase())) {
                throw new ResponseStatusException(
                    CONFLICT,
                    "Marketplace entity type conflicts with an existing deployment entity type: " + entityType
                );
            }
            ObjectNode compiled = ensureObject(entry.getValue());
            applyMarketplaceProvenance(compiled, install, plugin, version);
            targetEntities.set(entityType, compiled);
        }
    }

    private ObjectNode normalizeKnowledgeSourceRoot(JsonNode candidate) {
        ObjectNode root = ensureObject(candidate);
        if (!StringUtils.hasText(root.path("contractVersion").asText(""))) {
            root.put("contractVersion", DEFAULT_KNOWLEDGE_SOURCE_CONTRACT_VERSION);
        }
        ensureArray(root, "sources");
        return root;
    }

    private ObjectNode normalizeEntityRoot(JsonNode candidate) {
        ObjectNode root = ensureObject(candidate);
        ensureObjectNode(root, "ai-config");
        ensureObjectNode(root, "ai-entities");
        return root;
    }

    private ObjectNode normalizeShellRoot(JsonNode candidate) {
        ObjectNode root = ensureObject(candidate);
        if (!StringUtils.hasText(root.path("contractVersion").asText(""))) {
            root.put("contractVersion", DEFAULT_SHELL_CONTRACT_VERSION);
        }
        ensureArray(root, "modules");
        ensureArray(root, "cards");
        ensureArray(root, "starterPrompts");
        return root;
    }

    private ObjectNode normalizeMarketplaceDatasetRoot(JsonNode candidate) {
        ObjectNode root = ensureObject(candidate);
        if (!StringUtils.hasText(root.path("contractVersion").asText(""))) {
            root.put("contractVersion", DEFAULT_MARKETPLACE_DATASET_CONTRACT_VERSION);
        }
        ensureArray(root, "datasets");
        return root;
    }

    private ObjectNode resolveSyncConnector(MarketplaceManifestService.ParsedMarketplaceDatasetDefinition dataset,
                                            JsonNode installConfig,
                                            JsonNode installSecretRefs) {
        ObjectNode resolved = dataset.syncConnector() != null && dataset.syncConnector().isObject()
            ? (ObjectNode) dataset.syncConnector().deepCopy()
            : objectMapper.createObjectNode();
        if (!StringUtils.hasText(dataset.connectorType())) {
            return resolved;
        }
        if ("SQL_QUERY".equals(dataset.connectorType())) {
            String connectionRef = resolveConfiguredReference(
                resolved,
                "connectionRef",
                dataset.connectionRefField(),
                installConfig,
                installSecretRefs
            );
            if (StringUtils.hasText(connectionRef)) {
                resolved.put("connectionRef", connectionRef);
            }
        }
        if ("FILE_FOLDER".equals(dataset.connectorType())) {
            String folderRef = resolveConfiguredReference(
                resolved,
                "folderRef",
                dataset.folderRefField(),
                installConfig,
                installSecretRefs
            );
            if (StringUtils.hasText(folderRef)) {
                resolved.put("folderRef", folderRef);
            }
        }
        return resolved;
    }

    private String resolveConfiguredReference(ObjectNode connector,
                                              String directField,
                                              String installField,
                                              JsonNode installConfig,
                                              JsonNode installSecretRefs) {
        String directValue = text(connector, directField);
        if (StringUtils.hasText(directValue)) {
            return directValue;
        }
        if (!StringUtils.hasText(installField)) {
            return null;
        }
        String configured = installConfig.path(installField).asText("").trim();
        if (StringUtils.hasText(configured)) {
            return configured;
        }
        String secretConfigured = installSecretRefs.path(installField).asText("").trim();
        return StringUtils.hasText(secretConfigured) ? secretConfigured : null;
    }

    private String datasetHash(MarketplacePluginEntity plugin,
                               MarketplacePluginVersionEntity version,
                               DeploymentMarketplacePluginInstallEntity install,
                               MarketplaceManifestService.ParsedMarketplaceDatasetDefinition dataset,
                               JsonNode installConfig,
                               JsonNode installSecretRefs,
                               JsonNode resolvedSyncConnector) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("pluginId", plugin.getId());
        payload.put("pluginVersion", version.getVersion());
        payload.put("pluginVersionId", version.getId());
        payload.put("installId", install.getId());
        payload.put("datasetId", dataset.datasetId());
        payload.put("entityType", dataset.entityType());
        payload.put("storageScope", dataset.storageScope());
        payload.put("sharingScope", dataset.sharingScope());
        payload.put("ingestionMode", dataset.ingestionMode());
        payload.put("updateStrategy", dataset.updateStrategy());
        payload.put("vectorizationProfile", blankToNull(dataset.vectorizationProfile()));
        payload.put("seedDatasetRef", blankToNull(dataset.seedDatasetRef()));
        if (resolvedSyncConnector != null && !resolvedSyncConnector.isEmpty()) {
            payload.set("syncConnector", resolvedSyncConnector.deepCopy());
        }
        payload.set("config", installConfig == null ? objectMapper.createObjectNode() : installConfig.deepCopy());
        payload.set("secretRefs", installSecretRefs == null ? objectMapper.createObjectNode() : installSecretRefs.deepCopy());
        return sha256(writeJson(payload));
    }

    private ObjectNode ensureObject(JsonNode node) {
        return node != null && node.isObject()
            ? (ObjectNode) node.deepCopy()
            : objectMapper.createObjectNode();
    }

    private ArrayNode ensureArray(ObjectNode parent, String fieldName) {
        JsonNode existing = parent.path(fieldName);
        if (existing instanceof ArrayNode arrayNode) {
            return arrayNode;
        }
        ArrayNode created = objectMapper.createArrayNode();
        parent.set(fieldName, created);
        return created;
    }

    private ObjectNode ensureObjectNode(ObjectNode parent, String fieldName) {
        JsonNode existing = parent.path(fieldName);
        if (existing instanceof ObjectNode objectNode) {
            return objectNode;
        }
        ObjectNode created = objectMapper.createObjectNode();
        parent.set(fieldName, created);
        return created;
    }

    private JsonNode firstArray(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode candidate = node.path(fieldName);
            if (candidate.isArray()) {
                return candidate;
            }
        }
        return objectMapper.createArrayNode();
    }

    private String text(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = node.path(fieldName).asText("").trim();
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private void copyIfText(JsonNode source, ObjectNode target, String targetField, String... sourceFields) {
        String value = text(source, sourceFields);
        if (StringUtils.hasText(value)) {
            target.put(targetField, value);
        }
    }

    private Iterable<JsonNode> iterable(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<JsonNode> values = new ArrayList<>();
        node.forEach(values::add);
        return values;
    }

    private String summarizeIssues(List<DraftValidationIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return "unknown validation failure";
        }
        return issues.stream()
            .filter(Objects::nonNull)
            .filter(issue -> "ERROR".equalsIgnoreCase(issue.severity()))
            .map(issue -> issue.code() + " at " + issue.path())
            .limit(5)
            .reduce((left, right) -> left + "; " + right)
            .orElse("unknown validation failure");
    }

    private com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity asDraftEntity(DeploymentDraftResponse draft) {
        com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity entity =
            new com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity();
        entity.setId(draft.id());
        entity.setDeploymentId(draft.deploymentId());
        entity.setRevisionNumber(draft.revisionNumber());
        entity.setStatus(draft.status());
        entity.setActionsConfigJson(writeJson(draft.actionsConfig()));
        entity.setEntityConfigJson(writeJson(draft.entityConfig()));
        entity.setRoutingConfigJson(writeJson(draft.routingConfig()));
        entity.setProviderConfigJson(writeJson(draft.providerConfig()));
        entity.setSecurityConfigJson(writeJson(draft.securityConfig()));
        entity.setPromptConfigJson(writeJson(draft.promptConfig()));
        entity.setKnowledgeSourceConfigJson(writeJson(draft.knowledgeSourceConfig()));
        entity.setShellConfigJson(writeJson(draft.shellConfig()));
        entity.setMarketplaceDatasetConfigJson(writeJson(draft.marketplaceDatasetConfig()));
        entity.setCreatedAt(draft.createdAt());
        entity.setUpdatedAt(draft.updatedAt());
        return entity;
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node == null ? objectMapper.createObjectNode() : node);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize marketplace-composed draft JSON.", ex);
        }
    }

    private JsonNode readJson(String json) {
        try {
            if (!StringUtils.hasText(json)) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse marketplace install JSON.", ex);
        }
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : hash) {
                out.append(String.format("%02x", b));
            }
            return out.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to hash marketplace dataset state.", ex);
        }
    }
}
