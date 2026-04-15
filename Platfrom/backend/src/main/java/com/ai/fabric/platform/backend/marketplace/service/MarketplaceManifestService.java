package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.deployment.service.ManagedDeploymentProfileCatalog;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.ai.fabric.platform.backend.marketplace.model.MarketplacePluginCompatibilitySummary;
import com.ai.fabric.platform.backend.marketplace.model.MarketplacePluginContributionSummary;
import com.ai.fabric.platform.backend.marketplace.model.MarketplacePluginInstallFieldSummary;
import com.ai.fabric.platform.backend.marketplace.model.MarketplacePluginPermissionsSummary;
import com.ai.fabric.platform.backend.marketplace.model.MarketplacePluginPricingSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class MarketplaceManifestService {

    private static final Set<String> SUPPORTED_REQUIRED_CAPABILITIES = Set.of(
        "actions",
        "automation",
        "knowledgesources",
        "shellconfig",
        "templates"
    );
    private static final Set<String> SUPPORTED_CAPABILITY_PROFILES = Set.of(
        "SURFACE",
        "POLICY_LOGIC",
        "ANALYTICS_EVENT"
    );
    private static final Set<String> SUPPORTED_INSTALL_FIELD_TYPES = Set.of(
        "text",
        "url",
        "boolean",
        "select",
        "number",
        "secretref"
    );
    private static final Set<String> SUPPORTED_AUTH_MODES = Set.of(
        "PLATFORM_PROXY_SESSION",
        "PRIVATE_RUNTIME_BACKEND_MEDIATED",
        "PUBLIC_RUNTIME_AUTHENTICATED",
        "PUBLIC_RUNTIME_ANONYMOUS"
    );
    private static final Set<String> SUPPORTED_PROVIDER_MODE_KEYS = Set.of(
        "llm",
        "embedding",
        "vector",
        "runtime",
        "connector"
    );
    private static final Set<String> SUPPORTED_PRICING_MODELS = Set.of(
        "FREE",
        "ONE_OFF",
        "SUBSCRIPTION"
    );
    private static final Set<String> SUPPORTED_BILLING_INTERVALS = Set.of(
        "MONTHLY",
        "YEARLY"
    );
    private static final Set<String> SUPPORTED_DATASET_STORAGE_SCOPES = Set.of("PLUGIN_SCOPED");
    private static final Set<String> SUPPORTED_DATASET_SHARING_SCOPES = Set.of("TENANT_SHARED");
    private static final Set<String> SUPPORTED_DATASET_INGESTION_MODES = Set.of(
        "PACKAGED_SEED",
        "EXTERNAL_SYNC_SQL",
        "EXTERNAL_SYNC_FOLDER"
    );
    private static final Set<String> SUPPORTED_DATASET_UPDATE_STRATEGIES = Set.of("UPSERT_BY_ID");
    private static final Set<String> SUPPORTED_SYNC_CONNECTOR_TYPES = Set.of("SQL_QUERY", "FILE_FOLDER");

    private final ObjectMapper objectMapper;

    public MarketplaceManifestService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ParsedMarketplaceManifest parseAndValidate(MarketplacePluginEntity plugin,
                                                      MarketplacePluginVersionEntity version) {
        JsonNode manifest = readManifest(version);
        int schemaVersion = manifest.path("schemaVersion").asInt(-1);
        if (schemaVersion != 1) {
            throw invalid(plugin, version, "schemaVersion must be 1.");
        }

        String declaredType = normalizePluginType(firstText(manifest, "pluginType", "type"));
        String expectedType = normalizePluginType(plugin.getPluginType());
        if (!StringUtils.hasText(declaredType)) {
            throw invalid(plugin, version, "manifest pluginType is required.");
        }
        if (!declaredType.equals(expectedType)) {
            throw invalid(
                plugin,
                version,
                "manifest pluginType '" + declaredType + "' does not match catalog pluginType '" + expectedType + "'."
            );
        }

        MarketplacePluginCompatibilitySummary compatibility = parseCompatibility(plugin, version, manifest.path("compatibility"));

        JsonNode contributions = manifest.path("contributions");
        if (!contributions.isObject()) {
            throw invalid(plugin, version, "manifest contributions object is required.");
        }

        List<ParsedMarketplaceDatasetDefinition> datasets = "DATA".equals(expectedType)
            ? parseDataDatasets(plugin, version, contributions)
            : List.of();
        MarketplacePluginContributionSummary contributionSummary = switch (expectedType) {
            case "TEMPLATE" -> parseTemplateContribution(plugin, version, contributions);
            case "ACTION" -> parseActionContribution(plugin, version, contributions);
            case "DATA" -> parseDataContribution(plugin, version, contributions, datasets);
            case "AUTOMATION" -> parseAutomationContribution(plugin, version, contributions);
            default -> throw invalid(plugin, version, "unsupported pluginType: " + expectedType);
        };
        List<String> capabilityProfiles = parseCapabilityProfiles(plugin, version, manifest.path("capabilityProfiles"));
        List<MarketplacePluginInstallFieldSummary> installForm = parseInstallForm(plugin, version, manifest.path("installForm"));
        List<String> recommendedPluginIds = parseRecommendedPluginIds(manifest);
        MarketplacePluginPricingSummary pricing = parsePricing(plugin, version, manifest.path("pricing"));
        MarketplacePluginPermissionsSummary permissions = parsePermissions(
            manifest.path("permissions"),
            expectedType,
            capabilityProfiles,
            contributionSummary,
            installForm
        );
        validatePermissions(plugin, version, permissions, capabilityProfiles, contributionSummary, recommendedPluginIds, installForm);

        return new ParsedMarketplaceManifest(
            manifest,
            expectedType,
            contributionSummary,
            compatibility,
            installForm,
            permissions,
            capabilityProfiles,
            recommendedPluginIds,
            pricing,
            datasets
        );
    }

    private MarketplacePluginContributionSummary parseTemplateContribution(MarketplacePluginEntity plugin,
                                                                           MarketplacePluginVersionEntity version,
                                                                           JsonNode contributions) {
        JsonNode template = contributions.path("template");
        if (!template.isObject()) {
            throw invalid(plugin, version, "template plugins must declare contributions.template.");
        }
        String curatedModuleId = template.path("curatedModuleId").asText("").trim();
        JsonNode shell = template.path("shell");
        validateTemplateSecurityContribution(plugin, version, template.path("security"));
        return new MarketplacePluginContributionSummary(
            StringUtils.hasText(curatedModuleId) ? curatedModuleId : null,
            List.of(),
            List.of(),
            List.of(),
            readStringList(shell, "enabledModuleIds", "moduleRefs"),
            readStringList(shell, "enabledCardIds", "cardRefs")
        );
    }

    private void validateTemplateSecurityContribution(MarketplacePluginEntity plugin,
                                                      MarketplacePluginVersionEntity version,
                                                      JsonNode security) {
        if (security.isMissingNode() || security.isNull()) {
            return;
        }
        if (!security.isObject()) {
            throw invalid(plugin, version, "template security contribution must be an object when provided.");
        }
        String authzMode = security.path("authzMode").asText("").trim();
        if (StringUtils.hasText(authzMode)
            && !ManagedDeploymentProfileCatalog.SUPPORTED_AUTHZ_MODES.contains(authzMode.toUpperCase(Locale.ROOT))) {
            throw invalid(
                plugin,
                version,
                "template security contribution declares unsupported authzMode: " + authzMode
            );
        }
    }

    private MarketplacePluginContributionSummary parseActionContribution(MarketplacePluginEntity plugin,
                                                                         MarketplacePluginVersionEntity version,
                                                                         JsonNode contributions) {
        JsonNode actions = contributions.path("actions");
        if (!actions.isArray() || actions.isEmpty()) {
            throw invalid(plugin, version, "action plugins must declare a non-empty contributions.actions array.");
        }
        List<String> actionIds = new ArrayList<>();
        for (JsonNode action : actions) {
            if (!action.isObject()) {
                throw invalid(plugin, version, "each action contribution must be an object.");
            }
            String actionId = firstText(action, "id", "actionId");
            if (!StringUtils.hasText(actionId)) {
                throw invalid(plugin, version, "each action contribution must declare id or actionId.");
            }
            JsonNode route = action.path("route");
            if (route.isObject()) {
                String url = route.path("url").asText("").trim();
                String path = route.path("path").asText("").trim();
                if (StringUtils.hasText(url) && StringUtils.hasText(path)) {
                    throw invalid(plugin, version, "action route may declare either url or path, not both.");
                }
                if (!StringUtils.hasText(url) && !StringUtils.hasText(path)) {
                    throw invalid(plugin, version, "action route must declare url or path when route is present.");
                }
            }
            actionIds.add(actionId.trim());
        }
        JsonNode shell = contributions.path("shell");
        return new MarketplacePluginContributionSummary(
            null,
            List.copyOf(new LinkedHashSet<>(actionIds)),
            List.of(),
            List.of(),
            readStringList(shell, "moduleRefs", "enabledModuleIds"),
            readStringList(shell, "cardRefs", "enabledCardIds")
        );
    }

    private MarketplacePluginContributionSummary parseDataContribution(MarketplacePluginEntity plugin,
                                                                       MarketplacePluginVersionEntity version,
                                                                       JsonNode contributions,
                                                                       List<ParsedMarketplaceDatasetDefinition> datasets) {
        JsonNode knowledgeSources = contributions.path("knowledgeSources");
        if (!knowledgeSources.isArray() || knowledgeSources.isEmpty()) {
            throw invalid(plugin, version, "data plugins must declare a non-empty contributions.knowledgeSources array.");
        }
        if (datasets.isEmpty()) {
            throw invalid(plugin, version, "data plugins must declare a non-empty contributions.datasets array.");
        }
        Set<String> datasetIds = datasets.stream().map(ParsedMarketplaceDatasetDefinition::datasetId).collect(java.util.stream.Collectors.toSet());
        List<String> knowledgeSourceIds = new ArrayList<>();
        for (JsonNode source : knowledgeSources) {
            if (!source.isObject()) {
                throw invalid(plugin, version, "each knowledge source contribution must be an object.");
            }
            String sourceId = firstText(source, "id", "sourceKey");
            if (!StringUtils.hasText(sourceId)) {
                throw invalid(plugin, version, "each knowledge source contribution must declare id or sourceKey.");
            }
            String sourceType = firstText(source, "adapterType", "sourceType");
            if (!StringUtils.hasText(sourceType)) {
                throw invalid(plugin, version, "each knowledge source contribution must declare adapterType or sourceType.");
            }
            String datasetRef = firstText(source, "datasetRef");
            if (!StringUtils.hasText(datasetRef)) {
                if (datasets.size() == 1) {
                    datasetRef = datasets.getFirst().datasetId();
                } else {
                    throw invalid(plugin, version, "each knowledge source contribution must declare datasetRef when multiple datasets are present.");
                }
            }
            if (!datasetIds.contains(datasetRef)) {
                throw invalid(plugin, version, "knowledge source '" + sourceId + "' references unknown datasetRef '" + datasetRef + "'.");
            }
            knowledgeSourceIds.add(sourceId.trim());
        }
        validateEntityContribution(plugin, version, contributions.path("entityConfig"));
        JsonNode shell = contributions.path("shell");
        return new MarketplacePluginContributionSummary(
            null,
            List.of(),
            List.copyOf(new LinkedHashSet<>(knowledgeSourceIds)),
            List.of(),
            readStringList(shell, "moduleRefs", "enabledModuleIds"),
            readStringList(shell, "cardRefs", "enabledCardIds")
        );
    }

    private List<ParsedMarketplaceDatasetDefinition> parseDataDatasets(MarketplacePluginEntity plugin,
                                                                       MarketplacePluginVersionEntity version,
                                                                       JsonNode contributions) {
        JsonNode datasetsNode = contributions.path("datasets");
        if (!datasetsNode.isArray() || datasetsNode.isEmpty()) {
            return List.of();
        }
        List<ParsedMarketplaceDatasetDefinition> datasets = new ArrayList<>();
        Set<String> datasetIds = new LinkedHashSet<>();
        for (JsonNode dataset : datasetsNode) {
            if (!dataset.isObject()) {
                throw invalid(plugin, version, "data plugin datasets must be objects.");
            }
            String datasetId = firstText(dataset, "datasetId", "id");
            if (!StringUtils.hasText(datasetId)) {
                throw invalid(plugin, version, "each data dataset must declare datasetId.");
            }
            if (!datasetIds.add(datasetId)) {
                throw invalid(plugin, version, "duplicate data datasetId: " + datasetId);
            }
            String entityType = firstText(dataset, "entityType");
            if (!StringUtils.hasText(entityType)) {
                throw invalid(plugin, version, "dataset '" + datasetId + "' must declare entityType.");
            }
            String storageScope = normalizeUppercaseValue(dataset.path("storageScope").asText("PLUGIN_SCOPED"));
            if (!SUPPORTED_DATASET_STORAGE_SCOPES.contains(storageScope)) {
                throw invalid(plugin, version, "dataset '" + datasetId + "' declares unsupported storageScope: " + storageScope);
            }
            String sharingScope = normalizeUppercaseValue(dataset.path("sharingScope").asText("TENANT_SHARED"));
            if (!SUPPORTED_DATASET_SHARING_SCOPES.contains(sharingScope)) {
                throw invalid(plugin, version, "dataset '" + datasetId + "' declares unsupported sharingScope: " + sharingScope);
            }
            String ingestionMode = normalizeUppercaseValue(dataset.path("ingestionMode").asText(""));
            if (!SUPPORTED_DATASET_INGESTION_MODES.contains(ingestionMode)) {
                throw invalid(plugin, version, "dataset '" + datasetId + "' declares unsupported ingestionMode: " + dataset.path("ingestionMode").asText(""));
            }
            String updateStrategy = normalizeUppercaseValue(dataset.path("updateStrategy").asText("UPSERT_BY_ID"));
            if (!SUPPORTED_DATASET_UPDATE_STRATEGIES.contains(updateStrategy)) {
                throw invalid(plugin, version, "dataset '" + datasetId + "' declares unsupported updateStrategy: " + updateStrategy);
            }
            String seedDatasetRef = blankToNull(dataset.path("seedDatasetRef").asText(""));
            JsonNode syncConnector = dataset.path("syncConnector");
            String connectorType = null;
            String connectionRefField = null;
            String folderRefField = null;
            if ("PACKAGED_SEED".equals(ingestionMode)) {
                if (!StringUtils.hasText(seedDatasetRef)) {
                    throw invalid(plugin, version, "PACKAGED_SEED dataset '" + datasetId + "' must declare seedDatasetRef.");
                }
            } else {
                if (!syncConnector.isObject()) {
                    throw invalid(plugin, version, "dataset '" + datasetId + "' must declare syncConnector for external sync modes.");
                }
                connectorType = normalizeUppercaseValue(syncConnector.path("connectorType").asText(""));
                if (!SUPPORTED_SYNC_CONNECTOR_TYPES.contains(connectorType)) {
                    throw invalid(plugin, version, "dataset '" + datasetId + "' declares unsupported sync connector type: " + syncConnector.path("connectorType").asText(""));
                }
                if ("EXTERNAL_SYNC_SQL".equals(ingestionMode) && !"SQL_QUERY".equals(connectorType)) {
                    throw invalid(plugin, version, "dataset '" + datasetId + "' must use syncConnector.connectorType=SQL_QUERY for EXTERNAL_SYNC_SQL.");
                }
                if ("EXTERNAL_SYNC_FOLDER".equals(ingestionMode) && !"FILE_FOLDER".equals(connectorType)) {
                    throw invalid(plugin, version, "dataset '" + datasetId + "' must use syncConnector.connectorType=FILE_FOLDER for EXTERNAL_SYNC_FOLDER.");
                }
                connectionRefField = blankToNull(syncConnector.path("connectionRefField").asText(""));
                folderRefField = blankToNull(syncConnector.path("folderRefField").asText(""));
                String staticConnectionRef = blankToNull(syncConnector.path("connectionRef").asText(""));
                String staticFolderRef = blankToNull(syncConnector.path("folderRef").asText(""));
                if ("SQL_QUERY".equals(connectorType)
                    && !StringUtils.hasText(connectionRefField)
                    && !StringUtils.hasText(staticConnectionRef)) {
                    throw invalid(plugin, version, "dataset '" + datasetId + "' SQL_QUERY connector must declare connectionRefField or connectionRef.");
                }
                if ("FILE_FOLDER".equals(connectorType)
                    && !StringUtils.hasText(folderRefField)
                    && !StringUtils.hasText(staticFolderRef)) {
                    throw invalid(plugin, version, "dataset '" + datasetId + "' FILE_FOLDER connector must declare folderRefField or folderRef.");
                }
            }
            datasets.add(new ParsedMarketplaceDatasetDefinition(
                datasetId,
                entityType,
                storageScope,
                sharingScope,
                ingestionMode,
                updateStrategy,
                blankToNull(dataset.path("vectorizationProfile").asText("")),
                blankToNull(dataset.path("handleTemplate").asText("")),
                seedDatasetRef,
                connectorType,
                connectionRefField,
                folderRefField,
                syncConnector != null && syncConnector.isObject() ? syncConnector.deepCopy() : null
            ));
        }
        return List.copyOf(datasets);
    }

    private void validateEntityContribution(MarketplacePluginEntity plugin,
                                            MarketplacePluginVersionEntity version,
                                            JsonNode entityContribution) {
        if (entityContribution.isMissingNode() || entityContribution.isNull()) {
            return;
        }
        if (!entityContribution.isObject()) {
            throw invalid(plugin, version, "contributions.entityConfig must be an object when provided.");
        }
        JsonNode entities = entityContribution.path("ai-entities");
        if (entities.isMissingNode() || entities.isNull()) {
            return;
        }
        if (!entities.isObject()) {
            throw invalid(plugin, version, "contributions.entityConfig.ai-entities must be an object when provided.");
        }
        if (entities.isEmpty()) {
            throw invalid(plugin, version, "contributions.entityConfig.ai-entities must not be empty when provided.");
        }
        entities.fields().forEachRemaining(entry -> {
            String entityType = entry.getKey() == null ? "" : entry.getKey().trim();
            if (!StringUtils.hasText(entityType)) {
                throw invalid(plugin, version, "entity contribution keys must be non-blank entity types.");
            }
            if (!entry.getValue().isObject()) {
                throw invalid(plugin, version, "each contributions.entityConfig.ai-entities entry must be an object.");
            }
        });
    }

    private MarketplacePluginContributionSummary parseAutomationContribution(MarketplacePluginEntity plugin,
                                                                             MarketplacePluginVersionEntity version,
                                                                             JsonNode contributions) {
        JsonNode automation = contributions.path("automation");
        if (!automation.isObject()) {
            throw invalid(plugin, version, "automation plugins must declare contributions.automation.");
        }

        LinkedHashSet<String> automationIds = new LinkedHashSet<>();
        collectContributionIds(plugin, version, automation.path("triggers"), "triggers", automationIds);
        collectContributionIds(plugin, version, automation.path("actions"), "actions", automationIds);
        collectContributionIds(plugin, version, automation.path("workflows"), "workflows", automationIds);
        collectContributionIds(plugin, version, automation.path("schedules"), "schedules", automationIds);
        if (automationIds.isEmpty()) {
            throw invalid(plugin, version, "automation plugins must declare at least one automation contribution.");
        }

        JsonNode shell = contributions.path("shell");
        return new MarketplacePluginContributionSummary(
            null,
            List.of(),
            List.of(),
            List.copyOf(automationIds),
            readStringList(shell, "moduleRefs", "enabledModuleIds"),
            readStringList(shell, "cardRefs", "enabledCardIds")
        );
    }

    private void collectContributionIds(MarketplacePluginEntity plugin,
                                        MarketplacePluginVersionEntity version,
                                        JsonNode entries,
                                        String fieldName,
                                        LinkedHashSet<String> ids) {
        if (entries.isMissingNode() || entries.isNull()) {
            return;
        }
        if (!entries.isArray()) {
            throw invalid(plugin, version, "automation." + fieldName + " must be an array when provided.");
        }
        for (JsonNode entry : entries) {
            if (!entry.isObject()) {
                throw invalid(plugin, version, "automation." + fieldName + " entries must be objects.");
            }
            String id = firstText(entry, "id");
            if (!StringUtils.hasText(id)) {
                throw invalid(plugin, version, "automation." + fieldName + " entries must declare id.");
            }
            ids.add(id.trim());
        }
    }

    private void validateRequiredCapabilities(MarketplacePluginEntity plugin,
                                              MarketplacePluginVersionEntity version,
                                              JsonNode requiredCapabilities) {
        for (String capability : normalizeCapabilities(requiredCapabilities)) {
            if (!SUPPORTED_REQUIRED_CAPABILITIES.contains(capability)) {
                throw invalid(
                    plugin,
                    version,
                    "manifest declares unsupported required capability: " + capability
                );
            }
        }
    }

    private MarketplacePluginCompatibilitySummary parseCompatibility(MarketplacePluginEntity plugin,
                                                                     MarketplacePluginVersionEntity version,
                                                                     JsonNode compatibilityNode) {
        JsonNode node = compatibilityNode != null && compatibilityNode.isObject()
            ? compatibilityNode
            : objectMapper.createObjectNode();
        validateRequiredCapabilities(plugin, version, node.path("requiredCapabilities"));
        List<String> supportedAuthModes = normalizeUppercaseValues(node.path("supportedAuthModes"));
        for (String authMode : supportedAuthModes) {
            if (!SUPPORTED_AUTH_MODES.contains(authMode)) {
                throw invalid(plugin, version, "manifest declares unsupported auth mode: " + authMode);
            }
        }
        List<String> supportedProviderModes = normalizeProviderModes(plugin, version, node.path("supportedProviderModes"));
        return new MarketplacePluginCompatibilitySummary(
            blankToNull(node.path("minPlatformVersion").asText("")),
            blankToNull(node.path("maxPlatformVersion").asText("")),
            normalizeCapabilities(node.path("requiredCapabilities")),
            readStringList(node.path("supportedDeploymentTargets")),
            supportedAuthModes,
            supportedProviderModes
        );
    }

    private List<MarketplacePluginInstallFieldSummary> parseInstallForm(MarketplacePluginEntity plugin,
                                                                        MarketplacePluginVersionEntity version,
                                                                        JsonNode installFormNode) {
        if (!installFormNode.isArray()) {
            return List.of();
        }
        List<MarketplacePluginInstallFieldSummary> fields = new ArrayList<>();
        LinkedHashSet<String> fieldIds = new LinkedHashSet<>();
        for (JsonNode entry : installFormNode) {
            if (!entry.isObject()) {
                throw invalid(plugin, version, "installForm entries must be objects.");
            }
            String id = firstText(entry, "id");
            if (!StringUtils.hasText(id)) {
                throw invalid(plugin, version, "installForm entries must declare id.");
            }
            String normalizedType = normalizeInstallFieldType(firstText(entry, "type"));
            if (!SUPPORTED_INSTALL_FIELD_TYPES.contains(normalizedType)) {
                throw invalid(plugin, version, "installForm field '" + id + "' declares unsupported type '" + entry.path("type").asText("") + "'.");
            }
            if (!fieldIds.add(id)) {
                throw invalid(plugin, version, "installForm contains duplicate field id: " + id);
            }
            List<String> options = readStringList(entry.path("options"));
            if ("select".equals(normalizedType) && options.isEmpty()) {
                throw invalid(plugin, version, "installForm select field '" + id + "' must declare options.");
            }
            fields.add(new MarketplacePluginInstallFieldSummary(
                id,
                StringUtils.hasText(entry.path("label").asText("")) ? entry.path("label").asText("").trim() : id,
                normalizeInstallFieldTypeForOutput(normalizedType),
                entry.path("required").asBoolean(false),
                blankToNull(entry.path("description").asText("")),
                options
            ));
        }
        return List.copyOf(fields);
    }

    private List<String> parseCapabilityProfiles(MarketplacePluginEntity plugin,
                                                 MarketplacePluginVersionEntity version,
                                                 JsonNode capabilityProfilesNode) {
        List<String> profiles = normalizeUppercaseValues(capabilityProfilesNode);
        for (String profile : profiles) {
            if (!SUPPORTED_CAPABILITY_PROFILES.contains(profile)) {
                throw invalid(plugin, version, "manifest declares unsupported capability profile: " + profile);
            }
        }
        return profiles;
    }

    private MarketplacePluginPermissionsSummary parsePermissions(JsonNode permissionsNode,
                                                                 String pluginType,
                                                                 List<String> capabilityProfiles,
                                                                 MarketplacePluginContributionSummary contributions,
                                                                 List<MarketplacePluginInstallFieldSummary> installForm) {
        boolean hasShellPresentation = !contributions.shellModuleIds().isEmpty() || !contributions.shellCardIds().isEmpty();
        boolean requiresDeploymentSecrets = installForm.stream().anyMatch(field -> "secretRef".equals(field.type()));
        boolean requiresExternalHttpExecution = false;
        boolean requiresSharedDatasetAccess = "DATA".equals(pluginType) && !contributions.knowledgeSourceIds().isEmpty();
        return new MarketplacePluginPermissionsSummary(
            permissionsNode.path("contributesTemplate").asBoolean("TEMPLATE".equals(pluginType)),
            permissionsNode.path("contributesActions").asBoolean("ACTION".equals(pluginType)),
            permissionsNode.path("contributesKnowledgeSources").asBoolean("DATA".equals(pluginType)),
            permissionsNode.path("contributesAutomation").asBoolean("AUTOMATION".equals(pluginType)),
            permissionsNode.path("contributesShellPresentation").asBoolean(hasShellPresentation),
            permissionsNode.path("contributesSurfaceCapabilities").asBoolean(capabilityProfiles.contains("SURFACE")),
            permissionsNode.path("contributesPolicyLogicCapabilities").asBoolean(capabilityProfiles.contains("POLICY_LOGIC")),
            permissionsNode.path("contributesAnalyticsEventCapabilities").asBoolean(capabilityProfiles.contains("ANALYTICS_EVENT")),
            permissionsNode.path("requiresExternalHttpExecution").asBoolean(requiresExternalHttpExecution),
            permissionsNode.path("requiresSharedDatasetAccess").asBoolean(requiresSharedDatasetAccess),
            permissionsNode.path("requiresDeploymentSecrets").asBoolean(requiresDeploymentSecrets)
        );
    }

    private void validatePermissions(MarketplacePluginEntity plugin,
                                     MarketplacePluginVersionEntity version,
                                     MarketplacePluginPermissionsSummary permissions,
                                     List<String> capabilityProfiles,
                                     MarketplacePluginContributionSummary contributions,
                                     List<String> recommendedPluginIds,
                                     List<MarketplacePluginInstallFieldSummary> installForm) {
        if (!recommendedPluginIds.isEmpty() && !permissions.contributesTemplate()) {
            throw invalid(plugin, version, "recommendedPluginIds are only allowed for template contributions.");
        }
        if (!contributions.actionIds().isEmpty() && !permissions.contributesActions()) {
            throw invalid(plugin, version, "action contributions require permissions.contributesActions=true.");
        }
        if (!contributions.knowledgeSourceIds().isEmpty() && !permissions.contributesKnowledgeSources()) {
            throw invalid(plugin, version, "knowledge source contributions require permissions.contributesKnowledgeSources=true.");
        }
        if (!contributions.automationIds().isEmpty() && !permissions.contributesAutomation()) {
            throw invalid(plugin, version, "automation contributions require permissions.contributesAutomation=true.");
        }
        if ((!contributions.shellModuleIds().isEmpty() || !contributions.shellCardIds().isEmpty())
            && !permissions.contributesShellPresentation()) {
            throw invalid(plugin, version, "shell contributions require permissions.contributesShellPresentation=true.");
        }
        if (capabilityProfiles.contains("SURFACE") && !permissions.contributesSurfaceCapabilities()) {
            throw invalid(plugin, version, "SURFACE capability profile requires permissions.contributesSurfaceCapabilities=true.");
        }
        if (capabilityProfiles.contains("POLICY_LOGIC") && !permissions.contributesPolicyLogicCapabilities()) {
            throw invalid(plugin, version, "POLICY_LOGIC capability profile requires permissions.contributesPolicyLogicCapabilities=true.");
        }
        if (capabilityProfiles.contains("ANALYTICS_EVENT") && !permissions.contributesAnalyticsEventCapabilities()) {
            throw invalid(plugin, version, "ANALYTICS_EVENT capability profile requires permissions.contributesAnalyticsEventCapabilities=true.");
        }
        boolean requiresDeploymentSecrets = installForm.stream().anyMatch(field -> "secretRef".equals(field.type()));
        if (requiresDeploymentSecrets && !permissions.requiresDeploymentSecrets()) {
            throw invalid(plugin, version, "installForm secretRef fields require permissions.requiresDeploymentSecrets=true.");
        }
    }

    private List<String> parseRecommendedPluginIds(JsonNode manifest) {
        return readStringList(manifest.path("contributions").path("template").path("recommendedPluginIds"));
    }

    private MarketplacePluginPricingSummary parsePricing(MarketplacePluginEntity plugin,
                                                         MarketplacePluginVersionEntity version,
                                                         JsonNode pricingNode) {
        if (!pricingNode.isObject()) {
            return new MarketplacePluginPricingSummary("FREE", null, null, null, null, false);
        }
        String pricingModel = pricingNode.path("pricingModel").asText("FREE").trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_PRICING_MODELS.contains(pricingModel)) {
            throw invalid(plugin, version, "pricing.pricingModel must be FREE, ONE_OFF, or SUBSCRIPTION.");
        }
        BigDecimal amount = null;
        if (pricingNode.path("amount").isNumber()) {
            amount = pricingNode.path("amount").decimalValue();
        } else if (pricingNode.path("amount").isTextual() && StringUtils.hasText(pricingNode.path("amount").asText(""))) {
            try {
                amount = new BigDecimal(pricingNode.path("amount").asText("").trim());
            } catch (NumberFormatException ex) {
                throw invalid(plugin, version, "pricing.amount must be numeric.");
            }
        }
        String currency = blankToNull(pricingNode.path("currency").asText(""));
        String billingInterval = blankToNull(pricingNode.path("billingInterval").asText(""));
        Integer trialDays = pricingNode.path("trialDays").isNumber() ? pricingNode.path("trialDays").asInt() : null;
        if ("FREE".equals(pricingModel)) {
            return new MarketplacePluginPricingSummary("FREE", null, null, null, null, false);
        }
        if (amount == null || amount.signum() <= 0) {
            throw invalid(plugin, version, "paid marketplace pricing requires a positive pricing.amount.");
        }
        if (!StringUtils.hasText(currency)) {
            throw invalid(plugin, version, "paid marketplace pricing requires currency.");
        }
        currency = currency.toUpperCase(Locale.ROOT);
        if ("SUBSCRIPTION".equals(pricingModel)) {
            String normalizedInterval = billingInterval == null ? "MONTHLY" : billingInterval.toUpperCase(Locale.ROOT);
            if (!SUPPORTED_BILLING_INTERVALS.contains(normalizedInterval)) {
                throw invalid(plugin, version, "pricing.billingInterval must be MONTHLY or YEARLY for subscriptions.");
            }
            if (trialDays != null && trialDays < 0) {
                throw invalid(plugin, version, "pricing.trialDays must be non-negative.");
            }
            return new MarketplacePluginPricingSummary("SUBSCRIPTION", amount, currency, normalizedInterval, trialDays, true);
        }
        if (billingInterval != null) {
            throw invalid(plugin, version, "pricing.billingInterval is only allowed for SUBSCRIPTION pricing.");
        }
        if (trialDays != null) {
            throw invalid(plugin, version, "pricing.trialDays is only allowed for SUBSCRIPTION pricing.");
        }
        return new MarketplacePluginPricingSummary("ONE_OFF", amount, currency, null, null, true);
    }

    private JsonNode readManifest(MarketplacePluginVersionEntity version) {
        try {
            JsonNode manifest = objectMapper.readTree(version.getManifestJson());
            if (!manifest.isObject()) {
                throw invalid(null, version, "manifest must be a JSON object.");
            }
            return manifest;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw invalid(null, version, "manifest JSON is invalid: " + ex.getMessage());
        }
    }

    private String firstText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = node.path(fieldName).asText("").trim();
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String normalizePluginType(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private List<String> normalizeCapabilities(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (JsonNode entry : node) {
            String value = entry.asText("").trim();
            if (StringUtils.hasText(value)) {
                out.add(value.replace("-", "").replace("_", "").toLowerCase(Locale.ROOT));
            }
        }
        return List.copyOf(out);
    }

    private List<String> normalizeUppercaseValues(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (JsonNode entry : node) {
            String value = entry.asText("").trim();
            if (StringUtils.hasText(value)) {
                out.add(value.toUpperCase(Locale.ROOT));
            }
        }
        return List.copyOf(out);
    }

    private List<String> normalizeProviderModes(MarketplacePluginEntity plugin,
                                                MarketplacePluginVersionEntity version,
                                                JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (JsonNode entry : node) {
            String raw = entry.asText("").trim().toLowerCase(Locale.ROOT);
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            int separator = raw.indexOf(':');
            if (separator <= 0 || separator == raw.length() - 1) {
                throw invalid(plugin, version, "supportedProviderModes entries must use key:value format.");
            }
            String key = raw.substring(0, separator);
            String value = raw.substring(separator + 1);
            if (!SUPPORTED_PROVIDER_MODE_KEYS.contains(key) || !StringUtils.hasText(value)) {
                throw invalid(plugin, version, "unsupported supportedProviderModes entry: " + raw);
            }
            out.add(key + ":" + value);
        }
        return List.copyOf(out);
    }

    private List<String> readStringList(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode candidate = node.path(fieldName);
            if (!candidate.isArray()) {
                continue;
            }
            return readStringList(candidate);
        }
        return List.of();
    }

    private List<String> readStringList(JsonNode candidate) {
        if (!candidate.isArray()) {
            return List.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (JsonNode entry : candidate) {
            String value = entry.asText("").trim();
            if (StringUtils.hasText(value)) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    private String normalizeInstallFieldType(String value) {
        return value == null ? "" : value.trim().replace("-", "").replace("_", "").toLowerCase(Locale.ROOT);
    }

    private String normalizeUppercaseValue(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeInstallFieldTypeForOutput(String value) {
        return "secretref".equals(value) ? "secretRef" : value;
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private ResponseStatusException invalid(MarketplacePluginEntity plugin,
                                            MarketplacePluginVersionEntity version,
                                            String detail) {
        String pluginId = plugin == null ? (version == null ? "" : version.getPluginId()) : plugin.getId();
        String versionId = version == null ? "" : version.getVersion();
        String prefix = "Invalid marketplace manifest";
        if (StringUtils.hasText(pluginId)) {
            prefix += " for plugin " + pluginId;
        }
        if (StringUtils.hasText(versionId)) {
            prefix += "@" + versionId;
        }
        return new ResponseStatusException(BAD_REQUEST, prefix + ": " + detail);
    }

    public record ParsedMarketplaceManifest(
        JsonNode manifest,
        String pluginType,
        MarketplacePluginContributionSummary contributions,
        MarketplacePluginCompatibilitySummary compatibility,
        List<MarketplacePluginInstallFieldSummary> installForm,
        MarketplacePluginPermissionsSummary permissions,
        List<String> capabilityProfiles,
        List<String> recommendedPluginIds,
        MarketplacePluginPricingSummary pricing,
        List<ParsedMarketplaceDatasetDefinition> datasets
    ) {
    }

    public record ParsedMarketplaceDatasetDefinition(
        String datasetId,
        String entityType,
        String storageScope,
        String sharingScope,
        String ingestionMode,
        String updateStrategy,
        String vectorizationProfile,
        String handleTemplate,
        String seedDatasetRef,
        String connectorType,
        String connectionRefField,
        String folderRefField,
        JsonNode syncConnector
    ) {
    }
}
