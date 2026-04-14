package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.ai.fabric.platform.backend.marketplace.model.MarketplacePluginCompatibilitySummary;
import com.ai.fabric.platform.backend.marketplace.model.MarketplacePluginContributionSummary;
import com.ai.fabric.platform.backend.marketplace.model.MarketplacePluginInstallFieldSummary;
import com.ai.fabric.platform.backend.marketplace.model.MarketplacePluginPermissionsSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

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
        "knowledgesources",
        "shellconfig",
        "templates"
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

        MarketplacePluginContributionSummary contributionSummary = switch (expectedType) {
            case "TEMPLATE" -> parseTemplateContribution(plugin, version, contributions);
            case "ACTION" -> parseActionContribution(plugin, version, contributions);
            case "DATA" -> parseDataContribution(plugin, version, contributions);
            default -> throw invalid(plugin, version, "unsupported pluginType: " + expectedType);
        };
        List<MarketplacePluginInstallFieldSummary> installForm = parseInstallForm(plugin, version, manifest.path("installForm"));
        List<String> recommendedPluginIds = parseRecommendedPluginIds(manifest);
        MarketplacePluginPermissionsSummary permissions = parsePermissions(
            manifest.path("permissions"),
            expectedType,
            contributionSummary,
            installForm
        );
        validatePermissions(plugin, version, permissions, contributionSummary, recommendedPluginIds, installForm);

        return new ParsedMarketplaceManifest(
            manifest,
            expectedType,
            contributionSummary,
            compatibility,
            installForm,
            permissions,
            recommendedPluginIds
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
        return new MarketplacePluginContributionSummary(
            StringUtils.hasText(curatedModuleId) ? curatedModuleId : null,
            List.of(),
            List.of(),
            readStringList(shell, "enabledModuleIds", "moduleRefs"),
            readStringList(shell, "enabledCardIds", "cardRefs")
        );
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
            readStringList(shell, "moduleRefs", "enabledModuleIds"),
            readStringList(shell, "cardRefs", "enabledCardIds")
        );
    }

    private MarketplacePluginContributionSummary parseDataContribution(MarketplacePluginEntity plugin,
                                                                       MarketplacePluginVersionEntity version,
                                                                       JsonNode contributions) {
        JsonNode knowledgeSources = contributions.path("knowledgeSources");
        if (!knowledgeSources.isArray() || knowledgeSources.isEmpty()) {
            throw invalid(plugin, version, "data plugins must declare a non-empty contributions.knowledgeSources array.");
        }
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
            knowledgeSourceIds.add(sourceId.trim());
        }
        JsonNode shell = contributions.path("shell");
        return new MarketplacePluginContributionSummary(
            null,
            List.of(),
            List.copyOf(new LinkedHashSet<>(knowledgeSourceIds)),
            readStringList(shell, "moduleRefs", "enabledModuleIds"),
            readStringList(shell, "cardRefs", "enabledCardIds")
        );
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

    private MarketplacePluginPermissionsSummary parsePermissions(JsonNode permissionsNode,
                                                                 String pluginType,
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
            permissionsNode.path("contributesShellPresentation").asBoolean(hasShellPresentation),
            permissionsNode.path("requiresExternalHttpExecution").asBoolean(requiresExternalHttpExecution),
            permissionsNode.path("requiresSharedDatasetAccess").asBoolean(requiresSharedDatasetAccess),
            permissionsNode.path("requiresDeploymentSecrets").asBoolean(requiresDeploymentSecrets)
        );
    }

    private void validatePermissions(MarketplacePluginEntity plugin,
                                     MarketplacePluginVersionEntity version,
                                     MarketplacePluginPermissionsSummary permissions,
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
        if ((!contributions.shellModuleIds().isEmpty() || !contributions.shellCardIds().isEmpty())
            && !permissions.contributesShellPresentation()) {
            throw invalid(plugin, version, "shell contributions require permissions.contributesShellPresentation=true.");
        }
        boolean requiresDeploymentSecrets = installForm.stream().anyMatch(field -> "secretRef".equals(field.type()));
        if (requiresDeploymentSecrets && !permissions.requiresDeploymentSecrets()) {
            throw invalid(plugin, version, "installForm secretRef fields require permissions.requiresDeploymentSecrets=true.");
        }
    }

    private List<String> parseRecommendedPluginIds(JsonNode manifest) {
        return readStringList(manifest.path("contributions").path("template").path("recommendedPluginIds"));
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
        List<String> recommendedPluginIds
    ) {
    }
}
