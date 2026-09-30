package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.deployment.entityconfig.EntityConfigContractIssue;
import com.ai.fabric.platform.backend.deployment.entityconfig.EntityConfigContractService;
import com.ai.fabric.platform.backend.deployment.entityconfig.EntityConfigContractValidation;
import com.ai.fabric.platform.backend.deployment.entityconfig.EntityConfigValidationContext;
import com.ai.fabric.platform.backend.deployment.behavior.DeploymentBehaviorCatalogService;
import com.ai.fabric.platform.backend.deployment.model.DeploymentBehaviorSummary;
import com.ai.fabric.platform.backend.deployment.service.ManagedDeploymentProfileCatalog;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.ai.fabric.platform.backend.marketplace.model.MarketplacePluginCompatibilitySummary;
import com.ai.fabric.platform.backend.marketplace.model.MarketplacePluginContributionSummary;
import com.ai.fabric.platform.backend.marketplace.model.MarketplacePluginInstallFieldSummary;
import com.ai.fabric.platform.backend.marketplace.model.MarketplacePluginPermissionsSummary;
import com.ai.fabric.platform.backend.marketplace.model.MarketplacePluginPricingSummary;
import com.ai.fabric.platform.backend.marketplace.model.MarketplaceSpecialistBundleRefSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class MarketplaceManifestService {

    private static final Set<String> SUPPORTED_REQUIRED_CAPABILITIES = Set.of(
        "actions",
        "knowledgesources",
        "shellconfig",
        "templates",
        "providers",
        "specialists"
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
    private static final Set<String> SUPPORTED_DATASET_STORAGE_SCOPES = Set.of(
        "PLUGIN_SCOPED",
        "CUSTOMER_MANAGED"
    );
    private static final Set<String> SUPPORTED_DATASET_SHARING_SCOPES = Set.of(
        "TENANT_SHARED",
        "DEPLOYMENT_ONLY"
    );
    private static final Set<String> SUPPORTED_DATASET_INGESTION_MODES = Set.of(
        "PACKAGED_SEED",
        "EXTERNAL_SYNC_SQL",
        "EXTERNAL_SYNC_FOLDER",
        "EXTERNAL_SYNC_HTTP",
        "EXTERNAL_DOCUMENT_STORAGE"
    );
    private static final Set<String> SUPPORTED_DATASET_UPDATE_STRATEGIES = Set.of(
        "UPSERT_BY_ID",
        "VERSIONED_REPLACE"
    );
    private static final Set<String> SUPPORTED_SYNC_CONNECTOR_TYPES = Set.of("SQL_QUERY", "FILE_FOLDER", "HTTP_JSON");
    private static final Set<String> HTTP_SYNC_CONNECTOR_FIELDS = Set.of(
        "connectorType", "connectionProfile", "protectedResource", "httpSource", "webhook"
    );
    private static final Set<String> HTTP_CONNECTION_PROFILE_FIELDS = Set.of(
        "profileId", "environment", "baseUrl", "allowedHosts", "auth", "ratePolicy",
        "errorMappings", "capabilityGrants", "correlationResponseHeaders"
    );
    private static final Set<String> HTTP_AUTH_FIELDS = Set.of(
        "strategy", "apiKeyHeader", "apiKeySecretRefField", "tokenBaseUrl", "tokenPath", "tokenMethod",
        "credentialFields", "staticFields", "tokenJsonPointer", "absoluteExpiryJsonPointer",
        "relativeExpiryJsonPointer", "authorizationHeader", "authorizationScheme",
        "expirySkewSeconds", "timeoutMs"
    );
    private static final Set<String> HTTP_PROTECTED_RESOURCE_FIELDS = Set.of(
        "bindingId", "environment", "resourceType", "resourceIdField", "displayValueField",
        "policyRef", "capabilityGrants"
    );
    private static final Set<String> HTTP_SOURCE_FIELDS = Set.of(
        "sourceId", "enabled", "path", "method", "query", "headers", "trustedResourcePlacements",
        "requiredCapabilityGrants", "completeHttpStatuses", "pagination", "mapping", "tombstonePolicy", "scheduleSeconds"
    );
    private static final Set<String> HTTP_WEBHOOK_FIELDS = Set.of(
        "sourceId", "method", "verification", "eventIdJsonPointer", "eventTypeJsonPointer",
        "resourceJsonPointer", "allowedEventTypes", "allowedContentTypes", "maxBodyBytes",
        "maxReconcileAttempts", "retryDelaySeconds", "orderingPolicy", "registrationExpected",
        "manualReplayEnabled"
    );
    private static final Set<String> HTTP_RATE_POLICY_FIELDS = Set.of(
        "maxConcurrent", "minIntervalMs", "rateLimitedPauseMs", "unavailablePauseMs",
        "maxAttempts", "retryBackoffMs", "retryStatuses"
    );
    private static final Set<String> HTTP_ERROR_MAPPING_FIELDS = Set.of(
        "status", "bodyJsonPointer", "equalsValue", "errorClass"
    );
    private static final Set<String> HTTP_ERROR_CLASSES = Set.of(
        "BAD_REQUEST", "AUTHENTICATION_REQUIRED", "RESOURCE_ACCESS_DENIED", "CAPABILITY_DENIED",
        "RATE_LIMITED", "SERVICE_UNAVAILABLE", "TIMEOUT", "MALFORMED_RESPONSE"
    );
    private static final Set<String> CUSTOMER_BACKEND_INGESTION_FIELDS = Set.of(
        "enabled", "operations"
    );
    private static final Set<String> CUSTOMER_BACKEND_INGESTION_OPERATIONS = Set.of(
        "UPSERT", "DELETE", "WORK_STATUS", "READINESS"
    );
    private static final Set<String> SUPPORTED_DOCUMENT_CONNECTOR_TYPES = Set.of(
        "S3_COMPATIBLE_OBJECT_STORAGE",
        "MOUNTED_FOLDER"
    );
    private static final Set<String> DOCUMENT_SOURCE_CONNECTOR_FIELDS = Set.of(
        "connectorType",
        "storageOwnership",
        "bindingRefField",
        "allowedOperations",
        "deleteSourceOnRemoval"
    );
    private static final Set<String> DOCUMENT_POLICY_FIELDS = Set.of(
        "allowedMediaTypes",
        "allowedExtensions",
        "jsonContentKeys",
        "maxSourceBytes",
        "maxSources",
        "maxTotalIndexedBytes",
        "maxChunksPerSource",
        "maxChunkCharacters",
        "maxTotalCharacters",
        "previewMaxChunks",
        "previewMaxCharactersPerChunk",
        "evidenceRetentionDays",
        "commandRetentionDays",
        "retentionBatchSize",
        "allowedMetadataKeys",
        "initialIndexRequiresConfirmation",
        "trustedAutoIndexingAllowed"
    );
    private static final Set<String> DOCUMENT_PROTECTED_METADATA_KEYS = Set.of(
        "tenantId",
        "customerId",
        "deploymentId",
        "datasetId",
        "knowledgeSourceHandleRef",
        "sourceId",
        "sourceVersion",
        "sourceName",
        "sourceDocumentId",
        "chunkId",
        "chunkIndex",
        "chunkCount",
        "contentFingerprint"
    );
    private static final String ACTION_ADAPTER_TYPE_MCP_TOOL = "mcp-tool";
    private static final Set<String> SUPPORTED_MCP_DISPATCH_MODES = Set.of(
        "DIRECT_GATEWAY",
        "CONNECTOR"
    );
    private static final Set<String> SUPPORTED_MCP_TRANSPORTS = Set.of("STREAMABLE_HTTP");
    private static final Set<String> SUPPORTED_MCP_SCHEMA_DRIFT_POLICIES = Set.of(
        "WARN_ONLY",
        "DISABLE_ACTION",
        "BLOCK_RELEASE"
    );
    private static final Set<String> SUPPORTED_MCP_VERIFICATION_MODES = Set.of(
        "INITIALIZE_AND_TOOLS_LIST",
        "TOOLS_LIST_ONLY",
        "TOOLS_CALL_PROBE"
    );
    private static final Set<String> SUPPORTED_MCP_AUTH_MODES = Set.of(
        "NONE",
        "BEARER_TOKEN_SECRET_REF",
        "STATIC_BEARER_SECRET",
        "API_KEY_HEADER_SECRET",
        "OAUTH2_CLIENT_CREDENTIALS",
        "OAUTH2_AUTHORIZATION_CODE_PKCE",
        "CUSTOMER_OAUTH_PKCE",
        "SHOPIFY_AGENTIC_CLIENT_CREDENTIALS"
    );
    private static final Set<String> ALLOWED_MCP_API_KEY_HEADERS = Set.of(
        "X-API-KEY",
        "X-MCP-API-KEY",
        "X-LOOM-MCP-KEY"
    );
    private static final Set<String> BLOCKED_MCP_HEADER_NAMES = Set.of(
        "AUTHORIZATION",
        "COOKIE",
        "SET-COOKIE",
        "HOST",
        "ORIGIN",
        "REFERER",
        "X-FORWARDED-FOR",
        "X-FORWARDED-HOST",
        "X-FORWARDED-PROTO",
        "X-BRIDGE-API-KEY",
        "X-PLATFORM-API-KEY",
        "X-PLATFORM-PUBLIC-API-KEY"
    );
    private static final Pattern MCP_REF_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_.:-]{0,127}");
    private static final Pattern MCP_TOOL_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_.:-]{1,128}");
    private static final Pattern MCP_SCHEMA_HASH_PATTERN = Pattern.compile("sha256:[a-fA-F0-9]{64}");
    private static final Pattern MCP_RESPONSE_MAPPING_PATH_PATTERN =
        Pattern.compile("\\$(\\.[A-Za-z_][A-Za-z0-9_-]*|\\[[0-9]+])*");
    private static final Pattern PLUGIN_REF_PATTERN = Pattern.compile(
        "[A-Za-z0-9][A-Za-z0-9._-]{1,127}@[A-Za-z0-9][A-Za-z0-9._-]{0,63}"
    );
    private static final Pattern SPECIALIST_REF_PATTERN = Pattern.compile(
        "[a-z][a-z0-9-]{1,79}@[A-Za-z0-9][A-Za-z0-9._-]{0,39}"
    );
    private static final Pattern CONTENT_HASH_PATTERN = Pattern.compile("sha256:[a-f0-9]{64}");
    private static final Pattern SECRET_NAME_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{2,127}");
    private static final Set<String> SPECIALIST_CONTRIBUTION_FIELDS = Set.of(
        "contractVersion",
        "compatibleBehaviorTypes",
        "sourceBundleRefs",
        "requiredRuntimeCapabilityIds",
        "requiredMigrationIds",
        "requiredSecretNames",
        "verificationPackIds",
        "unsupportedClaims"
    );
    private static final Set<String> SPECIALIST_BUNDLE_REF_FIELDS = Set.of(
        "bundleId",
        "contractVersion",
        "contentHash",
        "specialistRefs",
        "chainRefs"
    );

    private final ObjectMapper objectMapper;
    private final EntityConfigContractService entityConfigContractService;
    private final DeploymentBehaviorCatalogService deploymentBehaviorCatalogService;

    public MarketplaceManifestService(ObjectMapper objectMapper) {
        this(objectMapper, new DeploymentBehaviorCatalogService(objectMapper));
    }

    @Autowired
    public MarketplaceManifestService(ObjectMapper objectMapper,
                                      DeploymentBehaviorCatalogService deploymentBehaviorCatalogService) {
        this.objectMapper = objectMapper;
        this.entityConfigContractService =
            new EntityConfigContractService(objectMapper);
        this.deploymentBehaviorCatalogService = deploymentBehaviorCatalogService;
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
            case "INFERENCE_PROFILE" -> parseInferenceContribution(plugin, version, contributions);
            case "SPECIALIST" -> parseSpecialistContribution(plugin, version, contributions);
            default -> throw invalid(plugin, version, "unsupported pluginType: " + expectedType);
        };
        if ("INFERENCE_PROFILE".equals(expectedType)
            && !compatibility.requiredCapabilities().contains("providers")) {
            throw invalid(plugin, version, "inference-profile plugins must declare compatibility.requiredCapabilities including providers.");
        }
        validateCapabilityProfiles(plugin, version, manifest.path("capabilityProfiles"));
        List<MarketplacePluginInstallFieldSummary> installForm = parseInstallForm(plugin, version, manifest.path("installForm"));
        List<String> recommendedPluginIds = parseRecommendedPluginIds(manifest);
        MarketplacePluginPricingSummary pricing = parsePricing(plugin, version, manifest.path("pricing"));
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
        validateEntityContribution(plugin, version, template.path("entityConfig"));
        TemplateBehaviorContribution behavior = parseTemplateBehaviorContribution(
            plugin,
            version,
            template.path("deploymentBehavior")
        );
        List<String> requiredPluginRefs = readStringList(template.path("requiredPluginRefs"));
        requiredPluginRefs.forEach(ref -> {
            if (!PLUGIN_REF_PATTERN.matcher(ref).matches()) {
                throw invalid(plugin, version, "template requiredPluginRefs must use exact pluginId@version references.");
            }
        });
        return new MarketplacePluginContributionSummary(
            StringUtils.hasText(curatedModuleId) ? curatedModuleId : null,
            List.of(),
            List.of(),
            readStringList(shell, "enabledModuleIds", "moduleRefs"),
            readStringList(shell, "enabledCardIds", "cardRefs"),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            behavior.type(),
            behavior.contractVersion(),
            behavior.requiredRuntimeCapabilityIds(),
            behavior.allowedExecutionExtensions(),
            behavior.allowedChannelBindings(),
            behavior.verificationPackIds(),
            requiredPluginRefs,
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of()
        );
    }

    private TemplateBehaviorContribution parseTemplateBehaviorContribution(MarketplacePluginEntity plugin,
                                                                            MarketplacePluginVersionEntity version,
                                                                            JsonNode behavior) {
        if (behavior.isMissingNode() || behavior.isNull()) {
            return TemplateBehaviorContribution.empty();
        }
        if (!behavior.isObject()) {
            throw invalid(plugin, version, "template deploymentBehavior must be an object when provided.");
        }
        String type = behavior.path("type").asText("").trim();
        if (!StringUtils.hasText(type)) {
            throw invalid(plugin, version, "template deploymentBehavior.type is required.");
        }
        DeploymentBehaviorSummary contract;
        try {
            contract = deploymentBehaviorCatalogService.requireSummary(type);
        } catch (ResponseStatusException ex) {
            throw invalid(plugin, version, ex.getReason());
        }
        int contractVersion = behavior.path("contractVersion").asInt(-1);
        if (contractVersion != contract.contractVersion()) {
            throw invalid(
                plugin,
                version,
                "template deploymentBehavior.contractVersion must be " + contract.contractVersion() + "."
            );
        }
        List<String> requiredCapabilities = resolvedExactTemplateValues(
            plugin,
            version,
            behavior,
            "requiredRuntimeCapabilityIds",
            contract.requiredRuntimeCapabilities()
        );
        List<String> verificationPacks = resolvedExactTemplateValues(
            plugin,
            version,
            behavior,
            "verificationPackIds",
            contract.baselineVerificationPackIds()
        );
        List<String> allowedExtensions = resolvedTemplateSubset(
            plugin,
            version,
            behavior,
            "allowedExecutionExtensions",
            contract.allowedExecutionExtensions()
        );
        List<String> allowedChannels = resolvedTemplateSubset(
            plugin,
            version,
            behavior,
            "allowedChannelBindings",
            contract.channelBindings()
        );
        return new TemplateBehaviorContribution(
            contract.code(),
            contract.contractVersion(),
            requiredCapabilities,
            allowedExtensions,
            allowedChannels,
            verificationPacks
        );
    }

    private List<String> resolvedExactTemplateValues(MarketplacePluginEntity plugin,
                                                     MarketplacePluginVersionEntity version,
                                                     JsonNode behavior,
                                                     String field,
                                                     List<String> contractValues) {
        if (!behavior.has(field)) {
            return contractValues;
        }
        List<String> configured = readStringList(behavior, field);
        if (!new LinkedHashSet<>(configured).equals(new LinkedHashSet<>(contractValues))) {
            throw invalid(plugin, version, "template deploymentBehavior." + field + " must match the behavior contract.");
        }
        return configured;
    }

    private List<String> resolvedTemplateSubset(MarketplacePluginEntity plugin,
                                                MarketplacePluginVersionEntity version,
                                                JsonNode behavior,
                                                String field,
                                                List<String> contractValues) {
        if (!behavior.has(field)) {
            return contractValues;
        }
        List<String> configured = readStringList(behavior, field);
        List<String> unsupported = configured.stream().filter(value -> !contractValues.contains(value)).toList();
        if (!unsupported.isEmpty()) {
            throw invalid(
                plugin,
                version,
                "template deploymentBehavior." + field + " contains unsupported values: " + String.join(", ", unsupported)
            );
        }
        return configured;
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
        Set<String> webhookTargetIds = parseWebhookTargets(plugin, version, contributions.path("webhookTargets"));
        Set<String> mcpServerRefs = parseMcpServerContributions(plugin, version, contributions.path("mcpServers"));
        List<String> actionIds = new ArrayList<>();
        for (JsonNode action : actions) {
            if (!action.isObject()) {
                throw invalid(plugin, version, "each action contribution must be an object.");
            }
            String actionId = firstText(action, "id", "actionId");
            if (!StringUtils.hasText(actionId)) {
                throw invalid(plugin, version, "each action contribution must declare id or actionId.");
            }
            validateActionExecutionContribution(plugin, version, action, actionId.trim(), mcpServerRefs);
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
                if (StringUtils.hasText(route.path("connectionProfileRef").asText(""))) {
                    validateProviderActionRoute(plugin, version, actionId.trim(), route);
                }
            }
            validateActionPostPolicies(plugin, version, action.path("postPolicies"), webhookTargetIds, actionId);
            actionIds.add(actionId.trim());
        }
        JsonNode shell = contributions.path("shell");
        return new MarketplacePluginContributionSummary(
            null,
            List.copyOf(new LinkedHashSet<>(actionIds)),
            List.of(),
            readStringList(shell, "moduleRefs", "enabledModuleIds"),
            readStringList(shell, "cardRefs", "enabledCardIds"),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of()
        );
    }

    private void validateProviderActionRoute(MarketplacePluginEntity plugin,
                                             MarketplacePluginVersionEntity version,
                                             String actionId,
                                             JsonNode route) {
        String prefix = "action '" + actionId + "' provider route ";
        if (StringUtils.hasText(route.path("url").asText(""))) {
            throw invalid(plugin, version, prefix + "must use a relative path, not an absolute URL.");
        }
        requireIdentifier(
            plugin,
            version,
            route.path("connectionProfileRef").asText(""),
            prefix + "connectionProfileRef"
        );
        requireIdentifier(
            plugin,
            version,
            route.path("protectedResourceBindingRef").asText(""),
            prefix + "protectedResourceBindingRef"
        );
        requireRelativePath(plugin, version, route.path("path").asText(""), prefix + "path");
        String method = normalizeUppercaseValue(route.path("method").asText(""));
        if (!Set.of("GET", "POST", "PUT", "PATCH", "DELETE").contains(method)) {
            throw invalid(plugin, version, prefix + "method is unsupported.");
        }
        requireCapabilityArray(
            plugin,
            version,
            route.path("requiredCapabilityGrants"),
            prefix + "requiredCapabilityGrants"
        );
        validateResourcePlacements(
            plugin,
            version,
            route.path("trustedResourcePlacements"),
            route.path("path").asText(""),
            prefix
        );
        if (StringUtils.hasText(route.path("idempotencyHeader").asText(""))) {
            requireHttpHeaderName(
                plugin,
                version,
                route.path("idempotencyHeader").asText(""),
                prefix + "idempotencyHeader"
            );
        }
        validateOptionalIntegerRange(plugin, version, route, "timeoutMs", 100, 120_000, prefix);
        validateStaticHeaders(plugin, version, route.path("headers"), prefix + "headers");
        JsonNode request = route.path("request");
        if (!request.isMissingNode() && !request.isNull()) {
            if (!request.isObject()) {
                throw invalid(plugin, version, prefix + "request must be an object.");
            }
            validateStaticQuery(plugin, version, request.path("query"), prefix + "request.query");
        }
    }

    private void validateActionExecutionContribution(MarketplacePluginEntity plugin,
                                                     MarketplacePluginVersionEntity version,
                                                     JsonNode action,
                                                     String actionId,
                                                     Set<String> mcpServerRefs) {
        JsonNode execution = action.path("execution");
        if (!execution.isMissingNode() && !execution.isNull() && !execution.isObject()) {
            throw invalid(plugin, version, "action '" + actionId + "' execution must be an object when provided.");
        }

        String actionAdapterType = firstText(action, "adapterType");
        String executionAdapterType = execution.isObject() ? firstText(execution, "adapterType") : null;
        JsonNode mcp = execution.path("mcp");
        boolean declaresMcpTool = ACTION_ADAPTER_TYPE_MCP_TOOL.equalsIgnoreCase(actionAdapterType)
            || ACTION_ADAPTER_TYPE_MCP_TOOL.equalsIgnoreCase(executionAdapterType)
            || mcp.isObject();
        if (!declaresMcpTool) {
            return;
        }

        if (!ACTION_ADAPTER_TYPE_MCP_TOOL.equalsIgnoreCase(actionAdapterType)) {
            throw invalid(plugin, version, "action '" + actionId + "' with execution.mcp must declare adapterType=mcp-tool.");
        }
        if (StringUtils.hasText(executionAdapterType)
            && !ACTION_ADAPTER_TYPE_MCP_TOOL.equalsIgnoreCase(executionAdapterType)) {
            throw invalid(plugin, version, "action '" + actionId + "' execution.adapterType must be mcp-tool when provided.");
        }
        if (!execution.isObject()) {
            throw invalid(plugin, version, "action '" + actionId + "' with adapterType=mcp-tool must declare execution.");
        }
        if (!mcp.isObject()) {
            throw invalid(plugin, version, "action '" + actionId + "' with adapterType=mcp-tool must declare execution.mcp.");
        }
        String serverRef = firstText(mcp, "serverRef");
        if (!StringUtils.hasText(serverRef)) {
            throw invalid(plugin, version, "action '" + actionId + "' execution.mcp.serverRef is required.");
        }
        if (!MCP_REF_PATTERN.matcher(serverRef.trim()).matches()) {
            throw invalid(plugin, version, "action '" + actionId + "' execution.mcp.serverRef is invalid.");
        }
        if (!mcpServerRefs.isEmpty() && !mcpServerRefs.contains(serverRef.trim())) {
            throw invalid(plugin, version, "action '" + actionId + "' execution.mcp.serverRef does not match contributions.mcpServers.");
        }
        String toolName = firstText(mcp, "toolName");
        if (!StringUtils.hasText(toolName)) {
            throw invalid(plugin, version, "action '" + actionId + "' execution.mcp.toolName is required.");
        }
        if (!MCP_TOOL_NAME_PATTERN.matcher(toolName.trim()).matches()) {
            throw invalid(plugin, version, "action '" + actionId + "' execution.mcp.toolName is invalid.");
        }
        String dispatchMode = firstText(mcp, "dispatchMode");
        if (StringUtils.hasText(dispatchMode)
            && !SUPPORTED_MCP_DISPATCH_MODES.contains(dispatchMode.trim().toUpperCase(Locale.ROOT))) {
            throw invalid(
                plugin,
                version,
                "action '" + actionId + "' execution.mcp.dispatchMode must be DIRECT_GATEWAY or CONNECTOR."
            );
        }
        JsonNode argumentTemplate = mcp.path("argumentTemplate");
        if (!argumentTemplate.isMissingNode() && !argumentTemplate.isNull() && !argumentTemplate.isObject()) {
            throw invalid(plugin, version, "action '" + actionId + "' execution.mcp.argumentTemplate must be an object when provided.");
        }
        validateMcpSchemaHash(plugin, version, mcp.path("toolSchemaHash"), "action '" + actionId + "' execution.mcp.toolSchemaHash");
        validateMcpSchemaDriftPolicy(plugin, version, firstText(mcp, "schemaDriftPolicy"), "action '" + actionId + "' execution.mcp.schemaDriftPolicy");
        validateMcpResponseMapping(plugin, version, mcp.path("responseMapping"), "action '" + actionId + "' execution.mcp.responseMapping");
        validateMcpRequiredAnyParams(
            plugin,
            version,
            action.path("params"),
            mcp.path("requiredAnyParams"),
            "action '" + actionId + "' execution.mcp.requiredAnyParams"
        );
        validateMcpRequiredAnyArguments(
            plugin,
            version,
            action.path("params"),
            argumentTemplate,
            mcp.path("requiredAnyArguments"),
            "action '" + actionId + "' execution.mcp.requiredAnyArguments"
        );
    }

    private void validateMcpRequiredAnyParams(MarketplacePluginEntity plugin,
                                              MarketplacePluginVersionEntity version,
                                              JsonNode params,
                                              JsonNode node,
                                              String label) {
        validateMcpRequiredPathList(plugin, version, node, label);
        if (node.isMissingNode() || node.isNull()) {
            return;
        }
        Set<String> declaredParams = declaredActionParamNames(params);
        for (JsonNode entry : node) {
            String rootParam = rootPathSegment(normalizedMcpPath(entry.asText(""), true));
            if (!declaredParams.contains(rootParam)) {
                throw invalid(plugin, version, label + " references undeclared action parameter '" + rootParam + "'.");
            }
        }
    }

    private void validateMcpRequiredAnyArguments(MarketplacePluginEntity plugin,
                                                 MarketplacePluginVersionEntity version,
                                                 JsonNode params,
                                                 JsonNode argumentTemplate,
                                                 JsonNode node,
                                                 String label) {
        validateMcpRequiredPathList(plugin, version, node, label);
        if (node.isMissingNode() || node.isNull()) {
            return;
        }
        Set<String> declaredParams = declaredActionParamNames(params);
        for (JsonNode entry : node) {
            String configuredPath = entry.asText("").trim();
            boolean declared = argumentTemplate.isObject()
                ? templateDeclaresPath(argumentTemplate, configuredPath)
                : declaredParams.contains(rootPathSegment(normalizedMcpPath(configuredPath, false)));
            if (!declared) {
                throw invalid(plugin, version, label + " path '" + configuredPath + "' is not emitted by execution.mcp.argumentTemplate.");
            }
        }
    }

    private void validateMcpRequiredPathList(MarketplacePluginEntity plugin,
                                             MarketplacePluginVersionEntity version,
                                             JsonNode node,
                                             String label) {
        if (node.isMissingNode() || node.isNull()) {
            return;
        }
        if (!node.isArray() || node.isEmpty()) {
            throw invalid(plugin, version, label + " must be a non-empty array when provided.");
        }
        for (JsonNode entry : node) {
            String value = entry.asText("").trim();
            if (!StringUtils.hasText(value)) {
                throw invalid(plugin, version, label + " entries must be non-empty strings.");
            }
            if (value.startsWith("$")) {
                if (!MCP_RESPONSE_MAPPING_PATH_PATTERN.matcher(value).matches()) {
                    throw invalid(plugin, version, label + " contains an invalid JSON path.");
                }
            } else if (!MCP_REF_PATTERN.matcher(value).matches()) {
                throw invalid(plugin, version, label + " contains an invalid argument name.");
            }
        }
    }

    private Set<String> declaredActionParamNames(JsonNode params) {
        if (!params.isArray()) {
            return Set.of();
        }
        Set<String> names = new LinkedHashSet<>();
        for (JsonNode param : params) {
            String name = firstText(param, "name");
            if (StringUtils.hasText(name)) {
                names.add(name.trim());
            }
        }
        return names;
    }

    private boolean templateDeclaresPath(JsonNode template, String configuredPath) {
        String path = normalizedMcpPath(configuredPath, false)
            .replaceAll("\\[([0-9]+)]", ".$1");
        JsonNode current = template;
        for (String segment : path.split("\\.")) {
            if (!StringUtils.hasText(segment)) {
                continue;
            }
            if (current.isArray() && segment.chars().allMatch(Character::isDigit)) {
                int index = Integer.parseInt(segment);
                if (index >= current.size()) {
                    return false;
                }
                current = current.get(index);
            } else if (current.isObject() && current.has(segment)) {
                current = current.get(segment);
            } else {
                return false;
            }
        }
        return current != null && !current.isMissingNode();
    }

    private String normalizedMcpPath(String configuredPath, boolean actionParamPath) {
        String path = configuredPath == null ? "" : configuredPath.trim();
        if (path.startsWith("$.")) {
            path = path.substring(2);
        } else if (path.startsWith("$")) {
            path = path.substring(1);
        }
        if (actionParamPath && path.startsWith("params.")) {
            path = path.substring("params.".length());
        }
        return path;
    }

    private String rootPathSegment(String path) {
        if (!StringUtils.hasText(path)) {
            return "";
        }
        int dot = path.indexOf('.');
        int bracket = path.indexOf('[');
        int end = dot < 0 ? bracket : bracket < 0 ? dot : Math.min(dot, bracket);
        return end < 0 ? path : path.substring(0, end);
    }

    private Set<String> parseMcpServerContributions(MarketplacePluginEntity plugin,
                                                    MarketplacePluginVersionEntity version,
                                                    JsonNode mcpServers) {
        if (mcpServers.isMissingNode() || mcpServers.isNull()) {
            return Set.of();
        }
        if (!mcpServers.isArray()) {
            throw invalid(plugin, version, "action plugin mcpServers must be an array when provided.");
        }
        Set<String> serverRefs = new LinkedHashSet<>();
        for (JsonNode server : mcpServers) {
            if (!server.isObject()) {
                throw invalid(plugin, version, "each MCP server contribution must be an object.");
            }
            String serverRef = firstText(server, "serverRef", "id");
            if (!StringUtils.hasText(serverRef)) {
                throw invalid(plugin, version, "each MCP server contribution must declare serverRef or id.");
            }
            serverRef = serverRef.trim();
            if (!MCP_REF_PATTERN.matcher(serverRef).matches()) {
                throw invalid(plugin, version, "MCP serverRef is invalid: " + serverRef);
            }
            if (!serverRefs.add(serverRef)) {
                throw invalid(plugin, version, "duplicate MCP serverRef: " + serverRef);
            }

            String transport = normalizedMcpEnum(firstText(server, "transport", "transportType"));
            if (!StringUtils.hasText(transport)) {
                throw invalid(plugin, version, "MCP server '" + serverRef + "' transport is required.");
            }
            if (!SUPPORTED_MCP_TRANSPORTS.contains(transport)) {
                throw invalid(plugin, version, "MCP server '" + serverRef + "' declares unsupported transport: " + transport);
            }

            boolean hasEndpoint = StringUtils.hasText(firstText(server, "endpointUrl", "url", "discoveryUrl"))
                || StringUtils.hasText(firstText(server, "endpointUrlTemplate", "urlTemplate", "discoveryUrlTemplate"))
                || StringUtils.hasText(firstText(server, "endpointUrlField", "urlField", "discoveryUrlField"));
            if (!hasEndpoint) {
                throw invalid(plugin, version, "MCP server '" + serverRef + "' must declare endpointUrl, endpointUrlTemplate, or endpointUrlField.");
            }

            validateMcpAllowedTools(plugin, version, server.path("allowedTools"), serverRef);
            validateMcpAuthContribution(plugin, version, server.path("auth"), serverRef);
            validateMcpVerificationContribution(plugin, version, server.path("verification"), serverRef);
        }
        return Set.copyOf(serverRefs);
    }

    private void validateMcpAllowedTools(MarketplacePluginEntity plugin,
                                         MarketplacePluginVersionEntity version,
                                         JsonNode allowedTools,
                                         String serverRef) {
        if (allowedTools.isMissingNode() || allowedTools.isNull()) {
            return;
        }
        if (!allowedTools.isArray() || allowedTools.isEmpty()) {
            throw invalid(plugin, version, "MCP server '" + serverRef + "' allowedTools must be a non-empty array when provided.");
        }
        for (JsonNode tool : allowedTools) {
            String toolName = tool.asText("").trim();
            if (!StringUtils.hasText(toolName) || !MCP_TOOL_NAME_PATTERN.matcher(toolName).matches()) {
                throw invalid(plugin, version, "MCP server '" + serverRef + "' allowedTools contains an invalid tool name.");
            }
        }
    }

    private void validateMcpAuthContribution(MarketplacePluginEntity plugin,
                                             MarketplacePluginVersionEntity version,
                                             JsonNode auth,
                                             String serverRef) {
        if (auth.isMissingNode() || auth.isNull()) {
            return;
        }
        if (!auth.isObject()) {
            throw invalid(plugin, version, "MCP server '" + serverRef + "' auth must be an object when provided.");
        }
        String mode = normalizedMcpEnum(firstText(auth, "mode", "authMode"));
        if (StringUtils.hasText(mode) && !SUPPORTED_MCP_AUTH_MODES.contains(mode)) {
            throw invalid(plugin, version, "MCP server '" + serverRef + "' auth declares unsupported mode: " + mode);
        }
        if ("API_KEY_HEADER_SECRET".equals(mode)) {
            String headerName = firstText(auth, "headerName");
            if (!StringUtils.hasText(headerName)) {
                throw invalid(plugin, version, "MCP server '" + serverRef + "' API key auth requires headerName.");
            }
            validateMcpHeaderName(plugin, version, headerName, serverRef);
            boolean hasSecretRef = StringUtils.hasText(firstText(auth, "secretRef", "valueSecretRef"))
                || StringUtils.hasText(firstText(auth, "secretRefField", "valueSecretRefField"));
            if (!hasSecretRef) {
                throw invalid(plugin, version, "MCP server '" + serverRef + "' API key auth requires secretRef or secretRefField.");
            }
        }
        if ("BEARER_TOKEN_SECRET_REF".equals(mode) || "STATIC_BEARER_SECRET".equals(mode)) {
            boolean hasSecretRef = StringUtils.hasText(firstText(auth, "secretRef", "tokenSecretRef"))
                || StringUtils.hasText(firstText(auth, "secretRefField", "tokenSecretRefField"));
            if (!hasSecretRef) {
                throw invalid(plugin, version, "MCP server '" + serverRef + "' bearer auth requires secretRef or secretRefField.");
            }
        }
    }

    private void validateMcpVerificationContribution(MarketplacePluginEntity plugin,
                                                    MarketplacePluginVersionEntity version,
                                                    JsonNode verification,
                                                    String serverRef) {
        if (verification.isMissingNode() || verification.isNull()) {
            return;
        }
        if (!verification.isObject()) {
            throw invalid(plugin, version, "MCP server '" + serverRef + "' verification must be an object when provided.");
        }
        String mode = normalizedMcpEnum(firstText(verification, "mode"));
        if (StringUtils.hasText(mode) && !SUPPORTED_MCP_VERIFICATION_MODES.contains(mode)) {
            throw invalid(plugin, version, "MCP server '" + serverRef + "' verification declares unsupported mode: " + mode);
        }
        validateMcpSchemaDriftPolicy(
            plugin,
            version,
            firstText(verification, "schemaDriftPolicy"),
            "MCP server '" + serverRef + "' verification.schemaDriftPolicy"
        );
    }

    private void validateMcpHeaderName(MarketplacePluginEntity plugin,
                                       MarketplacePluginVersionEntity version,
                                       String headerName,
                                       String serverRef) {
        String normalized = headerName == null ? "" : headerName.trim().toUpperCase(Locale.ROOT);
        if (BLOCKED_MCP_HEADER_NAMES.contains(normalized) || !ALLOWED_MCP_API_KEY_HEADERS.contains(normalized)) {
            throw invalid(plugin, version, "MCP server '" + serverRef + "' auth headerName is not allowlisted.");
        }
    }

    private void validateMcpSchemaHash(MarketplacePluginEntity plugin,
                                       MarketplacePluginVersionEntity version,
                                       JsonNode hashNode,
                                       String context) {
        if (hashNode.isMissingNode() || hashNode.isNull()) {
            return;
        }
        String hash = hashNode.asText("").trim();
        if (!MCP_SCHEMA_HASH_PATTERN.matcher(hash).matches()) {
            throw invalid(plugin, version, context + " must be sha256:<64 lowercase or uppercase hex chars>.");
        }
    }

    private void validateMcpSchemaDriftPolicy(MarketplacePluginEntity plugin,
                                              MarketplacePluginVersionEntity version,
                                              String policy,
                                              String context) {
        String normalized = normalizedMcpEnum(policy);
        if (StringUtils.hasText(normalized) && !SUPPORTED_MCP_SCHEMA_DRIFT_POLICIES.contains(normalized)) {
            throw invalid(plugin, version, context + " declares unsupported policy: " + normalized);
        }
    }

    private void validateMcpResponseMapping(MarketplacePluginEntity plugin,
                                            MarketplacePluginVersionEntity version,
                                            JsonNode responseMapping,
                                            String context) {
        if (responseMapping.isMissingNode() || responseMapping.isNull()) {
            return;
        }
        if (!responseMapping.isObject()) {
            throw invalid(plugin, version, context + " must be an object when provided.");
        }
        for (String field : List.of(
            "resultPath",
            "contentPath",
            "structuredContentPath",
            "citationsPath",
            "resourceLinksPath",
            "pinnedTargetsPath"
        )) {
            JsonNode value = responseMapping.path(field);
            if (value.isMissingNode() || value.isNull()) {
                continue;
            }
            if (!value.isTextual() || !MCP_RESPONSE_MAPPING_PATH_PATTERN.matcher(value.asText("").trim()).matches()) {
                throw invalid(plugin, version, context + "." + field + " must be a restricted JSONPath.");
            }
        }
    }

    private String normalizedMcpEnum(String value) {
        return value == null ? "" : value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
    }

    private Set<String> parseWebhookTargets(MarketplacePluginEntity plugin,
                                            MarketplacePluginVersionEntity version,
                                            JsonNode webhookTargets) {
        if (webhookTargets.isMissingNode() || webhookTargets.isNull()) {
            return Set.of();
        }
        if (!webhookTargets.isArray()) {
            throw invalid(plugin, version, "action plugin webhookTargets must be an array when provided.");
        }
        Set<String> ids = new LinkedHashSet<>();
        for (JsonNode target : webhookTargets) {
            if (!target.isObject()) {
                throw invalid(plugin, version, "each webhook target contribution must be an object.");
            }
            String id = firstText(target, "id");
            if (!StringUtils.hasText(id)) {
                throw invalid(plugin, version, "each webhook target must declare id.");
            }
            if (!ids.add(id.trim())) {
                throw invalid(plugin, version, "duplicate webhook target id: " + id);
            }
            validateSecretContribution(plugin, version, target, "urlSecretRef", "urlSecretRefField", true, "webhook target '" + id + "'");
            validateSecretContribution(plugin, version, target, "signingSecretRef", "signingSecretRefField", false, "webhook target '" + id + "'");
            validateNumberContribution(plugin, version, target, "timeoutMs", "timeoutMsField", false, "webhook target '" + id + "'");
            validateNumberContribution(plugin, version, target, "maxAttempts", "maxAttemptsField", false, "webhook target '" + id + "'");
        }
        return Set.copyOf(ids);
    }

    private void validateActionPostPolicies(MarketplacePluginEntity plugin,
                                            MarketplacePluginVersionEntity version,
                                            JsonNode postPolicies,
                                            Set<String> webhookTargetIds,
                                            String actionId) {
        if (postPolicies.isMissingNode() || postPolicies.isNull()) {
            return;
        }
        if (!postPolicies.isArray()) {
            throw invalid(plugin, version, "action '" + actionId + "' postPolicies must be an array when provided.");
        }
        for (JsonNode postPolicy : postPolicies) {
            if (!postPolicy.isObject()) {
                throw invalid(plugin, version, "action '" + actionId + "' post policy entries must be objects.");
            }
            String type = firstText(postPolicy, "type");
            if (!"webhook".equalsIgnoreCase(type)) {
                throw invalid(plugin, version, "action '" + actionId + "' declares unsupported post policy type: " + type);
            }
            String targetRef = firstText(postPolicy, "targetRef");
            if (!StringUtils.hasText(targetRef)) {
                throw invalid(plugin, version, "action '" + actionId + "' webhook post policy must declare targetRef.");
            }
            if (!webhookTargetIds.contains(targetRef.trim())) {
                throw invalid(plugin, version, "action '" + actionId + "' references unknown webhook target: " + targetRef);
            }
            String eventType = firstText(postPolicy, "eventType");
            if (!StringUtils.hasText(eventType)) {
                throw invalid(plugin, version, "action '" + actionId + "' webhook post policy must declare eventType.");
            }
        }
    }

    private void validateSecretContribution(MarketplacePluginEntity plugin,
                                            MarketplacePluginVersionEntity version,
                                            JsonNode node,
                                            String directField,
                                            String fieldRefField,
                                            boolean required,
                                            String subject) {
        String direct = firstText(node, directField);
        String fieldRef = firstText(node, fieldRefField);
        if (StringUtils.hasText(direct) && StringUtils.hasText(fieldRef)) {
            throw invalid(plugin, version, subject + " may not declare both " + directField + " and " + fieldRefField + ".");
        }
        if (required && !StringUtils.hasText(direct) && !StringUtils.hasText(fieldRef)) {
            throw invalid(plugin, version, subject + " must declare " + directField + " or " + fieldRefField + ".");
        }
    }

    private void validateNumberContribution(MarketplacePluginEntity plugin,
                                            MarketplacePluginVersionEntity version,
                                            JsonNode node,
                                            String directField,
                                            String fieldRefField,
                                            boolean required,
                                            String subject) {
        String fieldRef = firstText(node, fieldRefField);
        JsonNode directNode = node.path(directField);
        boolean hasDirect = !directNode.isMissingNode() && !directNode.isNull();
        if (hasDirect && StringUtils.hasText(fieldRef)) {
            throw invalid(plugin, version, subject + " may not declare both " + directField + " and " + fieldRefField + ".");
        }
        if (required && !hasDirect && !StringUtils.hasText(fieldRef)) {
            throw invalid(plugin, version, subject + " must declare " + directField + " or " + fieldRefField + ".");
        }
        if (hasDirect && (!directNode.canConvertToInt() || directNode.asInt() <= 0)) {
            throw invalid(plugin, version, subject + " field '" + directField + "' must be a positive integer.");
        }
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
            readStringList(shell, "moduleRefs", "enabledModuleIds"),
            readStringList(shell, "cardRefs", "enabledCardIds"),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of()
        );
    }

    private MarketplacePluginContributionSummary parseInferenceContribution(MarketplacePluginEntity plugin,
                                                                            MarketplacePluginVersionEntity version,
                                                                            JsonNode contributions) {
        JsonNode inferenceProfile = contributions.path("inferenceProfile");
        if (!inferenceProfile.isObject()) {
            throw invalid(plugin, version, "inference-profile plugins must declare contributions.inferenceProfile.");
        }
        String profileId = firstText(inferenceProfile, "profileId", "id");
        if (!StringUtils.hasText(profileId)) {
            throw invalid(plugin, version, "inferenceProfile must declare profileId.");
        }
        JsonNode orchestration = inferenceProfile.path("orchestration");
        JsonNode generation = inferenceProfile.path("generation");
        JsonNode embedding = inferenceProfile.path("embedding");
        if (!orchestration.isObject() && !generation.isObject() && !embedding.isObject()) {
            throw invalid(plugin, version, "inferenceProfile must declare at least one of orchestration, generation, or embedding.");
        }
        List<String> endpointRefs = new ArrayList<>();
        List<String> managedServiceRefs = new ArrayList<>();
        collectInferenceEndpointRef(orchestration, endpointRefs, managedServiceRefs);
        collectInferenceEndpointRef(generation, endpointRefs, managedServiceRefs);
        collectInferenceEndpointRef(embedding, endpointRefs, managedServiceRefs);
        return new MarketplacePluginContributionSummary(
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(profileId.trim()),
            List.copyOf(new LinkedHashSet<>(endpointRefs)),
            List.copyOf(new LinkedHashSet<>(managedServiceRefs)),
            null,
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of()
        );
    }

    private MarketplacePluginContributionSummary parseSpecialistContribution(
        MarketplacePluginEntity plugin,
        MarketplacePluginVersionEntity version,
        JsonNode contributions
    ) {
        JsonNode specialist = contributions.path("specialist");
        if (!specialist.isObject()) {
            throw invalid(plugin, version, "specialist plugins must declare contributions.specialist.");
        }
        rejectUnknownFields(plugin, version, specialist, SPECIALIST_CONTRIBUTION_FIELDS, "specialist");
        String contractVersion = specialist.path("contractVersion").asText("").trim();
        if (!DeploymentBehaviorCatalogService.SPECIALIST_BUNDLE_CONTRACT_VERSION.equals(contractVersion)) {
            throw invalid(
                plugin,
                version,
                "specialist contractVersion must be "
                    + DeploymentBehaviorCatalogService.SPECIALIST_BUNDLE_CONTRACT_VERSION + "."
            );
        }
        List<String> compatibleBehaviors = readStringList(specialist.path("compatibleBehaviorTypes"));
        if (compatibleBehaviors.isEmpty()) {
            throw invalid(plugin, version, "specialist compatibleBehaviorTypes must not be empty.");
        }
        List<DeploymentBehaviorSummary> behaviorContracts = compatibleBehaviors.stream()
            .map(value -> {
                try {
                    return deploymentBehaviorCatalogService.requireSummary(value);
                } catch (ResponseStatusException exception) {
                    throw invalid(plugin, version, exception.getReason());
                }
            })
            .toList();

        JsonNode bundleRefsNode = specialist.path("sourceBundleRefs");
        if (!bundleRefsNode.isArray() || bundleRefsNode.isEmpty() || bundleRefsNode.size() > 8) {
            throw invalid(plugin, version, "specialist sourceBundleRefs must contain between one and eight bundles.");
        }
        List<MarketplaceSpecialistBundleRefSummary> bundleRefs = new ArrayList<>();
        Set<String> bundleIds = new LinkedHashSet<>();
        for (JsonNode bundle : bundleRefsNode) {
            if (!bundle.isObject()) {
                throw invalid(plugin, version, "every specialist sourceBundleRefs entry must be an object.");
            }
            rejectUnknownFields(plugin, version, bundle, SPECIALIST_BUNDLE_REF_FIELDS, "specialist sourceBundleRef");
            String bundleId = bundle.path("bundleId").asText("").trim();
            String bundleContract = bundle.path("contractVersion").asText("").trim();
            String contentHash = bundle.path("contentHash").asText("").trim();
            if (!SPECIALIST_REF_PATTERN.matcher(bundleId).matches() || !bundleIds.add(bundleId)) {
                throw invalid(plugin, version, "specialist sourceBundleRefs require unique stable bundleId values.");
            }
            if (!contractVersion.equals(bundleContract)) {
                throw invalid(plugin, version, "specialist source bundle contractVersion must match the contribution contract.");
            }
            if (!CONTENT_HASH_PATTERN.matcher(contentHash).matches()) {
                throw invalid(plugin, version, "specialist source bundle contentHash must be a lowercase sha256 hash.");
            }
            List<String> specialistRefs = readSpecialistRefs(
                plugin,
                version,
                bundle.path("specialistRefs"),
                "specialistRefs",
                true
            );
            List<String> chainRefs = readSpecialistRefs(
                plugin,
                version,
                bundle.path("chainRefs"),
                "chainRefs",
                false
            );
            bundleRefs.add(new MarketplaceSpecialistBundleRefSummary(
                bundleId,
                bundleContract,
                contentHash,
                specialistRefs,
                chainRefs
            ));
        }

        List<String> requiredCapabilities = readStringList(specialist.path("requiredRuntimeCapabilityIds"));
        List<String> requiredMigrations = readStringList(specialist.path("requiredMigrationIds"));
        List<String> requiredSecrets = readStringList(specialist.path("requiredSecretNames"));
        requiredSecrets.forEach(secret -> {
            if (!SECRET_NAME_PATTERN.matcher(secret).matches()) {
                throw invalid(plugin, version, "specialist requiredSecretNames contains an invalid secret name.");
            }
        });
        List<String> verificationPacks = readStringList(specialist.path("verificationPackIds"));
        List<String> unsupportedClaims = readStringList(specialist.path("unsupportedClaims"));
        if (unsupportedClaims.isEmpty()) {
            throw invalid(plugin, version, "specialist unsupportedClaims must state at least one customer-safe boundary.");
        }
        for (DeploymentBehaviorSummary behavior : behaviorContracts) {
            requireSubset(
                plugin,
                version,
                requiredCapabilities,
                behavior.requiredRuntimeCapabilities(),
                "requiredRuntimeCapabilityIds",
                behavior.code()
            );
            requireSubset(
                plugin,
                version,
                requiredMigrations,
                behavior.requiredRuntimeMigrationIds(),
                "requiredMigrationIds",
                behavior.code()
            );
            requireSubset(
                plugin,
                version,
                verificationPacks,
                behavior.baselineVerificationPackIds(),
                "verificationPackIds",
                behavior.code()
            );
            Set<String> expectedBundleIds = behavior.requiredSpecialistBundles().stream()
                .map(com.ai.fabric.platform.backend.deployment.model.DeploymentSpecialistBundleSummary::bundleId)
                .collect(java.util.stream.Collectors.toSet());
            List<String> unsupportedBundles = bundleRefs.stream()
                .map(MarketplaceSpecialistBundleRefSummary::bundleId)
                .filter(bundleId -> !expectedBundleIds.contains(bundleId))
                .toList();
            if (!unsupportedBundles.isEmpty()) {
                throw invalid(
                    plugin,
                    version,
                    "specialist source bundles are not part of behavior " + behavior.code() + ": "
                        + String.join(", ", unsupportedBundles)
                );
            }
            Set<String> declaredBundleIds = bundleRefs.stream()
                .map(MarketplaceSpecialistBundleRefSummary::bundleId)
                .collect(java.util.stream.Collectors.toSet());
            if (!declaredBundleIds.equals(expectedBundleIds)) {
                throw invalid(
                    plugin,
                    version,
                    "specialist source bundles must exactly satisfy behavior " + behavior.code() + "."
                );
            }
            Map<String, com.ai.fabric.platform.backend.deployment.model.DeploymentSpecialistBundleSummary> expectedBundles =
                behavior.requiredSpecialistBundles().stream().collect(java.util.stream.Collectors.toMap(
                    com.ai.fabric.platform.backend.deployment.model.DeploymentSpecialistBundleSummary::bundleId,
                    value -> value
                ));
            for (MarketplaceSpecialistBundleRefSummary declared : bundleRefs) {
                var expectedBundle = expectedBundles.get(declared.bundleId());
                if (expectedBundle == null
                    || !deploymentBehaviorCatalogService.supportsSpecialistBundle(
                        behavior.code(),
                        declared.bundleId(),
                        declared.contractVersion(),
                        declared.contentHash(),
                        declared.specialistRefs(),
                        declared.chainRefs()
                    )) {
                    throw invalid(
                        plugin,
                        version,
                        "specialist source bundle does not match the reviewed behavior contract: " + declared.bundleId()
                    );
                }
            }
        }

        return new MarketplacePluginContributionSummary(
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            contractVersion,
            compatibleBehaviors,
            List.copyOf(bundleRefs),
            requiredCapabilities,
            requiredMigrations,
            requiredSecrets,
            verificationPacks,
            unsupportedClaims
        );
    }

    private List<String> readSpecialistRefs(MarketplacePluginEntity plugin,
                                            MarketplacePluginVersionEntity version,
                                            JsonNode node,
                                            String field,
                                            boolean required) {
        List<String> refs = readStringList(node);
        if (required && refs.isEmpty()) {
            throw invalid(plugin, version, "specialist " + field + " must not be empty.");
        }
        refs.forEach(ref -> {
            if (!SPECIALIST_REF_PATTERN.matcher(ref).matches()) {
                throw invalid(plugin, version, "specialist " + field + " contains an invalid exact resource reference.");
            }
        });
        return refs;
    }

    private void requireSubset(MarketplacePluginEntity plugin,
                               MarketplacePluginVersionEntity version,
                               List<String> values,
                               List<String> allowed,
                               String field,
                               String behaviorType) {
        List<String> unsupported = values.stream().filter(value -> !allowed.contains(value)).toList();
        if (!unsupported.isEmpty()) {
            throw invalid(
                plugin,
                version,
                "specialist " + field + " widens behavior " + behaviorType + ": " + String.join(", ", unsupported)
            );
        }
    }

    private void rejectUnknownFields(MarketplacePluginEntity plugin,
                                     MarketplacePluginVersionEntity version,
                                     JsonNode object,
                                     Set<String> allowed,
                                     String label) {
        object.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) {
                throw invalid(plugin, version, label + " contains unsupported field: " + field);
            }
        });
    }

    private void collectInferenceEndpointRef(JsonNode node,
                                             List<String> endpointRefs,
                                             List<String> managedServiceRefs) {
        if (!node.isObject()) {
            return;
        }
        String provider = firstText(node, "provider", "llmProvider", "embeddingProvider");
        if (!StringUtils.hasText(provider)) {
            return;
        }
        String endpointProfileRef = firstText(node, "endpointProfileRef");
        String managedServiceRef = firstText(node, "managedServiceRef");
        if (StringUtils.hasText(endpointProfileRef) && StringUtils.hasText(managedServiceRef)) {
            throw new ResponseStatusException(
                BAD_REQUEST,
                "Inference profile sections may declare either endpointProfileRef or managedServiceRef, not both."
            );
        }
        if (StringUtils.hasText(endpointProfileRef)) {
            endpointRefs.add(endpointProfileRef.trim());
        }
        if (StringUtils.hasText(managedServiceRef)) {
            managedServiceRefs.add(managedServiceRef.trim());
        }
    }

    private record TemplateBehaviorContribution(
        String type,
        Integer contractVersion,
        List<String> requiredRuntimeCapabilityIds,
        List<String> allowedExecutionExtensions,
        List<String> allowedChannelBindings,
        List<String> verificationPackIds
    ) {
        private static TemplateBehaviorContribution empty() {
            return new TemplateBehaviorContribution(null, null, List.of(), List.of(), List.of(), List.of());
        }
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
            JsonNode sourceConnector = dataset.path("sourceConnector");
            JsonNode documentPolicy = dataset.path("documentPolicy");
            JsonNode customerBackendIngestion = dataset.path("customerBackendIngestion");
            validateCustomerBackendIngestion(
                plugin,
                version,
                datasetId,
                customerBackendIngestion
            );
            String connectorType = null;
            String connectionRefField = null;
            String folderRefField = null;
            if ("EXTERNAL_DOCUMENT_STORAGE".equals(ingestionMode)) {
                validateDocumentDataset(
                    plugin,
                    version,
                    datasetId,
                    entityType,
                    storageScope,
                    sharingScope,
                    updateStrategy,
                    seedDatasetRef,
                    syncConnector,
                    sourceConnector,
                    documentPolicy
                );
                connectorType = normalizeUppercaseValue(sourceConnector.path("connectorType").asText(""));
                connectionRefField = blankToNull(sourceConnector.path("bindingRefField").asText(""));
            } else if ("PACKAGED_SEED".equals(ingestionMode)) {
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
                if ("EXTERNAL_SYNC_HTTP".equals(ingestionMode) && !"HTTP_JSON".equals(connectorType)) {
                    throw invalid(plugin, version, "dataset '" + datasetId + "' must use syncConnector.connectorType=HTTP_JSON for EXTERNAL_SYNC_HTTP.");
                }
                if ("EXTERNAL_SYNC_HTTP".equals(ingestionMode)) {
                    validateHttpSyncDataset(plugin, version, datasetId, entityType, syncConnector);
                    validateHttpIngestionAuthority(
                        plugin,
                        version,
                        datasetId,
                        syncConnector.path("httpSource"),
                        customerBackendIngestion
                    );
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
                syncConnector != null && syncConnector.isObject() ? syncConnector.deepCopy() : null,
                sourceConnector != null && sourceConnector.isObject() ? sourceConnector.deepCopy() : null,
                documentPolicy != null && documentPolicy.isObject() ? documentPolicy.deepCopy() : null,
                customerBackendIngestion != null && customerBackendIngestion.isObject()
                    ? customerBackendIngestion.deepCopy()
                    : null
            ));
        }
        return List.copyOf(datasets);
    }

    private void validateCustomerBackendIngestion(MarketplacePluginEntity plugin,
                                                   MarketplacePluginVersionEntity version,
                                                   String datasetId,
                                                   JsonNode contract) {
        if (contract == null || contract.isMissingNode() || contract.isNull()) {
            return;
        }
        if (!contract.isObject()) {
            throw invalid(
                plugin,
                version,
                "dataset '" + datasetId + "' customerBackendIngestion must be an object."
            );
        }
        String prefix = "dataset '" + datasetId + "' customerBackendIngestion";
        rejectUnknownFields(plugin, version, contract, CUSTOMER_BACKEND_INGESTION_FIELDS, prefix);
        if (!contract.path("enabled").isBoolean()) {
            throw invalid(plugin, version, prefix + ".enabled must be a boolean.");
        }
        JsonNode operations = contract.path("operations");
        if (!operations.isArray()) {
            throw invalid(plugin, version, prefix + ".operations must be an array.");
        }
        if (contract.path("enabled").asBoolean() && operations.isEmpty()) {
            throw invalid(plugin, version, prefix + ".operations must not be empty when enabled.");
        }
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode operation : operations) {
            String normalized = normalizeUppercaseValue(operation.asText(""));
            if (!CUSTOMER_BACKEND_INGESTION_OPERATIONS.contains(normalized)) {
                throw invalid(plugin, version, prefix + " declares unsupported operation: " + operation.asText(""));
            }
            if (!seen.add(normalized)) {
                throw invalid(plugin, version, prefix + " declares duplicate operation: " + normalized);
            }
        }
    }

    private void validateHttpIngestionAuthority(MarketplacePluginEntity plugin,
                                                MarketplacePluginVersionEntity version,
                                                String datasetId,
                                                JsonNode source,
                                                JsonNode customerBackendIngestion) {
        boolean connectorPullEnabled = source.path("enabled").asBoolean(true);
        boolean customerBackendPushEnabled = customerBackendIngestion != null
            && customerBackendIngestion.isObject()
            && customerBackendIngestion.path("enabled").asBoolean(false);
        if (connectorPullEnabled == customerBackendPushEnabled) {
            throw invalid(
                plugin,
                version,
                "HTTP dataset '" + datasetId + "' must declare exactly one active ingestion authority: "
                    + "httpSource.enabled or customerBackendIngestion.enabled."
            );
        }
    }

    private void validateHttpSyncDataset(MarketplacePluginEntity plugin,
                                         MarketplacePluginVersionEntity version,
                                         String datasetId,
                                         String entityType,
                                         JsonNode connector) {
        String prefix = "HTTP dataset '" + datasetId + "' ";
        rejectUnknownFields(plugin, version, connector, HTTP_SYNC_CONNECTOR_FIELDS, prefix + "syncConnector");
        JsonNode profile = connector.path("connectionProfile");
        JsonNode resource = connector.path("protectedResource");
        JsonNode source = connector.path("httpSource");
        if (!profile.isObject() || !resource.isObject() || !source.isObject()) {
            throw invalid(plugin, version, prefix + "must declare connectionProfile, protectedResource, and httpSource objects.");
        }
        rejectUnknownFields(plugin, version, profile, HTTP_CONNECTION_PROFILE_FIELDS, prefix + "connectionProfile");
        rejectUnknownFields(plugin, version, resource, HTTP_PROTECTED_RESOURCE_FIELDS, prefix + "protectedResource");
        rejectUnknownFields(plugin, version, source, HTTP_SOURCE_FIELDS, prefix + "httpSource");
        if (source.has("enabled") && !source.path("enabled").isBoolean()) {
            throw invalid(plugin, version, prefix + "httpSource.enabled must be a boolean.");
        }

        requireIdentifier(plugin, version, firstText(profile, "profileId"), prefix + "connectionProfile.profileId");
        requireText(plugin, version, profile, "environment", prefix);
        requireAbsoluteHttpsUrl(plugin, version, profile.path("baseUrl").asText(""), prefix + "connectionProfile.baseUrl");
        JsonNode allowedHosts = profile.path("allowedHosts");
        if (!allowedHosts.isArray() || allowedHosts.isEmpty()) {
            throw invalid(plugin, version, prefix + "connectionProfile.allowedHosts must be a non-empty array.");
        }
        String baseHost = URI.create(profile.path("baseUrl").asText("")).getHost();
        boolean baseHostAllowed = false;
        for (JsonNode host : allowedHosts) {
            String value = host.asText("").trim();
            if (!StringUtils.hasText(value) || value.contains(":") || value.contains("/")) {
                throw invalid(plugin, version, prefix + "connectionProfile.allowedHosts entries must be host names.");
            }
            baseHostAllowed = baseHostAllowed || value.equalsIgnoreCase(baseHost);
        }
        if (!baseHostAllowed) {
            throw invalid(plugin, version, prefix + "connectionProfile.allowedHosts must contain the base URL host.");
        }
        Set<String> profileGrants = requireCapabilityArray(
            plugin,
            version,
            profile.path("capabilityGrants"),
            prefix + "connectionProfile.capabilityGrants"
        );
        JsonNode correlationHeaders = profile.path("correlationResponseHeaders");
        if (!correlationHeaders.isMissingNode() && !correlationHeaders.isNull()) {
            if (!correlationHeaders.isArray() || correlationHeaders.size() > 20) {
                throw invalid(plugin, version, prefix + "connectionProfile.correlationResponseHeaders must be an array with at most 20 entries.");
            }
            for (JsonNode header : correlationHeaders) {
                if (!header.isTextual()
                    || !header.asText("").trim().matches("[!#$%&'*+.^_`|~0-9A-Za-z-]{1,160}")) {
                    throw invalid(plugin, version, prefix + "connectionProfile.correlationResponseHeaders contains an invalid header name.");
                }
            }
        }
        JsonNode auth = profile.path("auth");
        if (!auth.isObject()) {
            throw invalid(plugin, version, prefix + "connectionProfile.auth is required.");
        }
        rejectUnknownFields(plugin, version, auth, HTTP_AUTH_FIELDS, prefix + "connectionProfile.auth");
        String strategy = normalizeUppercaseValue(auth.path("strategy").asText(""));
        if (!Set.of("API_KEY", "FORM_TOKEN_EXCHANGE").contains(strategy)) {
            throw invalid(plugin, version, prefix + "auth.strategy must be API_KEY or FORM_TOKEN_EXCHANGE.");
        }
        if ("API_KEY".equals(strategy)) {
            requireText(plugin, version, auth, "apiKeyHeader", prefix);
            requireText(plugin, version, auth, "apiKeySecretRefField", prefix);
            requireHttpHeaderName(
                plugin,
                version,
                auth.path("apiKeyHeader").asText(""),
                prefix + "auth.apiKeyHeader"
            );
        } else {
            if (StringUtils.hasText(auth.path("tokenBaseUrl").asText(""))) {
                requireAbsoluteHttpsUrl(
                    plugin,
                    version,
                    auth.path("tokenBaseUrl").asText(""),
                    prefix + "auth.tokenBaseUrl"
                );
                String tokenHost = URI.create(auth.path("tokenBaseUrl").asText("")).getHost();
                boolean tokenHostAllowed = false;
                for (JsonNode host : allowedHosts) {
                    tokenHostAllowed = tokenHostAllowed || host.asText("").trim().equalsIgnoreCase(tokenHost);
                }
                if (!tokenHostAllowed) {
                    throw invalid(
                        plugin,
                        version,
                        prefix + "connectionProfile.allowedHosts must contain the token base URL host."
                    );
                }
            }
            requireRelativePath(plugin, version, auth.path("tokenPath").asText(""), prefix + "auth.tokenPath");
            if (!"POST".equalsIgnoreCase(auth.path("tokenMethod").asText("POST"))) {
                throw invalid(plugin, version, prefix + "FORM_TOKEN_EXCHANGE tokenMethod must be POST.");
            }
            JsonNode credentialFields = auth.path("credentialFields");
            if (!credentialFields.isArray() || credentialFields.isEmpty() || credentialFields.size() > 20) {
                throw invalid(plugin, version, prefix + "FORM_TOKEN_EXCHANGE requires credentialFields.");
            }
            Set<String> credentialNames = new LinkedHashSet<>();
            for (JsonNode credential : credentialFields) {
                if (!credential.isObject()) {
                    throw invalid(plugin, version, prefix + "credentialFields entries must be objects.");
                }
                rejectUnknownFields(plugin, version, credential, Set.of("name", "secretRefField"), prefix + "credentialFields entry");
                String credentialName = firstText(credential, "name");
                requireIdentifier(plugin, version, credentialName, prefix + "credential name");
                if (!credentialNames.add(credentialName)) {
                    throw invalid(plugin, version, prefix + "credentialFields declares duplicate name: " + credentialName);
                }
                requireText(plugin, version, credential, "secretRefField", prefix);
            }
            JsonNode staticFields = auth.path("staticFields");
            if (!staticFields.isMissingNode() && !staticFields.isNull()) {
                if (!staticFields.isObject() || staticFields.size() > 20) {
                    throw invalid(plugin, version, prefix + "auth.staticFields must be an object with at most 20 entries.");
                }
                staticFields.fields().forEachRemaining(entry -> {
                    requireIdentifier(plugin, version, entry.getKey(), prefix + "static field name");
                    if (!entry.getValue().isTextual() || entry.getValue().asText().length() > 500) {
                        throw invalid(plugin, version, prefix + "auth.staticFields values must be strings of at most 500 characters.");
                    }
                    if (credentialNames.contains(entry.getKey())) {
                        throw invalid(plugin, version, prefix + "auth.staticFields must not redefine credential field " + entry.getKey() + ".");
                    }
                });
            }
            requireJsonPointer(plugin, version, auth.path("tokenJsonPointer").asText(""), prefix + "auth.tokenJsonPointer");
            boolean absoluteExpiry = StringUtils.hasText(auth.path("absoluteExpiryJsonPointer").asText(""));
            boolean relativeExpiry = StringUtils.hasText(auth.path("relativeExpiryJsonPointer").asText(""));
            if (absoluteExpiry == relativeExpiry) {
                throw invalid(plugin, version, prefix + "auth must declare exactly one absolute or relative expiry JSON Pointer.");
            }
            requireJsonPointer(
                plugin,
                version,
                absoluteExpiry ? auth.path("absoluteExpiryJsonPointer").asText("") : auth.path("relativeExpiryJsonPointer").asText(""),
                prefix + "auth expiry pointer"
            );
            requireHttpHeaderName(
                plugin,
                version,
                auth.path("authorizationHeader").asText("Authorization"),
                prefix + "auth.authorizationHeader"
            );
            String authorizationScheme = auth.path("authorizationScheme").asText("Bearer").trim();
            if (!authorizationScheme.matches("[A-Za-z][A-Za-z0-9._-]{0,39}")) {
                throw invalid(plugin, version, prefix + "auth.authorizationScheme is invalid.");
            }
            validateOptionalIntegerRange(plugin, version, auth, "expirySkewSeconds", 0, 3600, prefix);
            validateOptionalIntegerRange(plugin, version, auth, "timeoutMs", 100, 120_000, prefix);
        }

        JsonNode ratePolicy = profile.path("ratePolicy");
        if (!ratePolicy.isMissingNode() && !ratePolicy.isNull()) {
            if (!ratePolicy.isObject()) {
                throw invalid(plugin, version, prefix + "connectionProfile.ratePolicy must be an object.");
            }
            rejectUnknownFields(plugin, version, ratePolicy, HTTP_RATE_POLICY_FIELDS, prefix + "connectionProfile.ratePolicy");
            validateOptionalIntegerRange(plugin, version, ratePolicy, "maxConcurrent", 1, 100, prefix);
            validateOptionalIntegerRange(plugin, version, ratePolicy, "minIntervalMs", 0, 60_000, prefix);
            validateOptionalIntegerRange(plugin, version, ratePolicy, "rateLimitedPauseMs", 0, 3_600_000, prefix);
            validateOptionalIntegerRange(plugin, version, ratePolicy, "unavailablePauseMs", 0, 3_600_000, prefix);
            validateOptionalIntegerRange(plugin, version, ratePolicy, "maxAttempts", 1, 5, prefix);
            validateOptionalIntegerRange(plugin, version, ratePolicy, "retryBackoffMs", 0, 30_000, prefix);
            JsonNode retryStatuses = ratePolicy.path("retryStatuses");
            if (!retryStatuses.isMissingNode() && (!retryStatuses.isArray() || retryStatuses.isEmpty())) {
                throw invalid(plugin, version, prefix + "connectionProfile.ratePolicy.retryStatuses must be a non-empty array.");
            }
            for (JsonNode status : retryStatuses) {
                if (!status.canConvertToInt() || status.asInt() < 100 || status.asInt() > 599) {
                    throw invalid(plugin, version, prefix + "connectionProfile.ratePolicy.retryStatuses contains an invalid HTTP status.");
                }
            }
        }

        JsonNode errorMappings = profile.path("errorMappings");
        if (!errorMappings.isMissingNode() && !errorMappings.isNull()) {
            if (!errorMappings.isArray()) {
                throw invalid(plugin, version, prefix + "connectionProfile.errorMappings must be an array.");
            }
            for (JsonNode mapping : errorMappings) {
                if (!mapping.isObject()) {
                    throw invalid(plugin, version, prefix + "connectionProfile.errorMappings entries must be objects.");
                }
                rejectUnknownFields(plugin, version, mapping, HTTP_ERROR_MAPPING_FIELDS, prefix + "connectionProfile.errorMapping");
                if (!mapping.path("status").canConvertToInt()
                    || mapping.path("status").asInt() < 100
                    || mapping.path("status").asInt() > 599) {
                    throw invalid(plugin, version, prefix + "connectionProfile.errorMapping.status must be a valid HTTP status.");
                }
                String errorClass = normalizeUppercaseValue(mapping.path("errorClass").asText(""));
                if (!HTTP_ERROR_CLASSES.contains(errorClass)) {
                    throw invalid(plugin, version, prefix + "connectionProfile.errorMapping.errorClass is unsupported.");
                }
                boolean hasPointer = StringUtils.hasText(mapping.path("bodyJsonPointer").asText(""));
                boolean hasExpectedValue = mapping.has("equalsValue") && mapping.path("equalsValue").isValueNode();
                if (hasPointer != hasExpectedValue) {
                    throw invalid(plugin, version, prefix + "connectionProfile.errorMapping must declare both bodyJsonPointer and equalsValue, or neither.");
                }
                if (hasPointer) {
                    requireJsonPointer(plugin, version, mapping.path("bodyJsonPointer").asText(""), prefix + "connectionProfile.errorMapping.bodyJsonPointer");
                }
            }
        }

        requireIdentifier(plugin, version, firstText(resource, "bindingId"), prefix + "protectedResource.bindingId");
        requireText(plugin, version, resource, "environment", prefix);
        requireIdentifier(plugin, version, firstText(resource, "resourceType"), prefix + "protectedResource.resourceType");
        requireIdentifier(plugin, version, firstText(resource, "resourceIdField"), prefix + "protectedResource.resourceIdField");
        if (StringUtils.hasText(resource.path("displayValueField").asText(""))) {
            requireIdentifier(
                plugin,
                version,
                resource.path("displayValueField").asText(""),
                prefix + "protectedResource.displayValueField"
            );
        }
        if (StringUtils.hasText(resource.path("policyRef").asText(""))) {
            requireIdentifier(plugin, version, resource.path("policyRef").asText(""), prefix + "protectedResource.policyRef");
        }
        if (!resource.path("environment").asText("").trim().equalsIgnoreCase(profile.path("environment").asText("").trim())) {
            throw invalid(plugin, version, prefix + "protectedResource.environment must match connectionProfile.environment.");
        }
        Set<String> resourceGrants = requireCapabilityArray(
            plugin,
            version,
            resource.path("capabilityGrants"),
            prefix + "protectedResource.capabilityGrants"
        );

        requireIdentifier(plugin, version, firstText(source, "sourceId"), prefix + "httpSource.sourceId");
        requireRelativePath(plugin, version, source.path("path").asText(""), prefix + "httpSource.path");
        String method = source.path("method").asText("GET").trim().toUpperCase(Locale.ROOT);
        if (!Set.of("GET", "POST").contains(method)) {
            throw invalid(plugin, version, prefix + "httpSource.method must be GET or POST.");
        }
        JsonNode completeHttpStatuses = source.path("completeHttpStatuses");
        if (!completeHttpStatuses.isMissingNode() && !completeHttpStatuses.isNull()) {
            if (!completeHttpStatuses.isArray() || completeHttpStatuses.isEmpty() || completeHttpStatuses.size() > 10) {
                throw invalid(plugin, version, prefix + "httpSource.completeHttpStatuses must contain between 1 and 10 HTTP statuses.");
            }
            Set<Integer> seenCompleteStatuses = new LinkedHashSet<>();
            for (JsonNode status : completeHttpStatuses) {
                if (!status.canConvertToInt() || status.asInt() < 200 || status.asInt() > 299) {
                    throw invalid(plugin, version, prefix + "httpSource.completeHttpStatuses must contain only 2xx HTTP statuses.");
                }
                if (!seenCompleteStatuses.add(status.asInt())) {
                    throw invalid(plugin, version, prefix + "httpSource.completeHttpStatuses contains a duplicate HTTP status.");
                }
            }
        }
        Set<String> requiredGrants = requireCapabilityArray(
            plugin,
            version,
            source.path("requiredCapabilityGrants"),
            prefix + "httpSource.requiredCapabilityGrants"
        );
        if (!profileGrants.containsAll(requiredGrants) || !resourceGrants.containsAll(requiredGrants)) {
            throw invalid(plugin, version, prefix + "httpSource requires capability grants absent from the profile or protected resource.");
        }
        validateResourcePlacements(
            plugin,
            version,
            source.path("trustedResourcePlacements"),
            source.path("path").asText(""),
            prefix
        );
        rejectProviderAuthHeaderPlacement(
            plugin,
            version,
            source.path("trustedResourcePlacements"),
            "API_KEY".equals(strategy)
                ? auth.path("apiKeyHeader").asText("")
                : auth.path("authorizationHeader").asText("Authorization"),
            prefix
        );
        validateStaticQuery(plugin, version, source.path("query"), prefix + "httpSource.query");
        validateStaticHeaders(plugin, version, source.path("headers"), prefix + "httpSource.headers");
        JsonNode pagination = source.path("pagination");
        if (!pagination.isObject()) {
            throw invalid(plugin, version, prefix + "httpSource.pagination is required.");
        }
        rejectUnknownFields(
            plugin, version, pagination,
            Set.of("strategy", "pageQuery", "sizeQuery", "startPage", "pageSize", "maxPages", "cursorQuery", "nextCursorJsonPointer"),
            prefix + "pagination"
        );
        String paginationStrategy = normalizeUppercaseValue(pagination.path("strategy").asText("NONE"));
        if (!Set.of("NONE", "PAGE_SIZE", "CURSOR").contains(paginationStrategy)) {
            throw invalid(plugin, version, prefix + "pagination.strategy is unsupported.");
        }
        validateOptionalIntegerRange(plugin, version, pagination, "maxPages", 1, 10_000, prefix + "pagination.");
        if ("PAGE_SIZE".equals(paginationStrategy)) {
            requireIdentifier(
                plugin,
                version,
                pagination.path("pageQuery").asText("page"),
                prefix + "pagination.pageQuery"
            );
            requireIdentifier(
                plugin,
                version,
                pagination.path("sizeQuery").asText("pageSize"),
                prefix + "pagination.sizeQuery"
            );
            validateOptionalIntegerRange(plugin, version, pagination, "startPage", 0, 1_000_000, prefix + "pagination.");
            validateOptionalIntegerRange(plugin, version, pagination, "pageSize", 1, 1_000, prefix + "pagination.");
        }
        if ("CURSOR".equals(paginationStrategy)) {
            requireIdentifier(
                plugin,
                version,
                pagination.path("cursorQuery").asText("cursor"),
                prefix + "pagination.cursorQuery"
            );
            requireJsonPointer(plugin, version, pagination.path("nextCursorJsonPointer").asText(""), prefix + "pagination.nextCursorJsonPointer");
        }
        JsonNode mapping = source.path("mapping");
        if (!mapping.isObject()) {
            throw invalid(plugin, version, prefix + "httpSource.mapping is required.");
        }
        rejectUnknownFields(
            plugin, version, mapping,
            Set.of("recordsJsonPointer", "idJsonPointer", "resourceJsonPointer", "contentFields", "entityFields", "metadataFields", "maxRecords", "maxResponseBytes"),
            prefix + "mapping"
        );
        requireJsonPointer(plugin, version, mapping.path("idJsonPointer").asText(""), prefix + "mapping.idJsonPointer");
        if (StringUtils.hasText(mapping.path("recordsJsonPointer").asText(""))) {
            requireJsonPointer(plugin, version, mapping.path("recordsJsonPointer").asText(""), prefix + "mapping.recordsJsonPointer");
        }
        requireJsonPointer(
            plugin,
            version,
            mapping.path("resourceJsonPointer").asText(""),
            prefix + "mapping.resourceJsonPointer"
        );
        if (!mapping.path("contentFields").isObject() || mapping.path("contentFields").isEmpty()) {
            throw invalid(plugin, version, prefix + "mapping.contentFields must be a non-empty object.");
        }
        validatePointerMap(plugin, version, mapping.path("contentFields"), prefix + "mapping.contentFields");
        validatePointerMap(plugin, version, mapping.path("entityFields"), prefix + "mapping.entityFields");
        validatePointerMap(plugin, version, mapping.path("metadataFields"), prefix + "mapping.metadataFields");
        validateOptionalIntegerRange(plugin, version, mapping, "maxRecords", 1, 100_000, prefix + "mapping.");
        validateOptionalIntegerRange(
            plugin,
            version,
            mapping,
            "maxResponseBytes",
            1_024,
            50 * 1_024 * 1_024,
            prefix + "mapping."
        );
        validateOptionalIntegerRange(plugin, version, source, "scheduleSeconds", 10, 86_400, prefix + "httpSource.");

        JsonNode tombstonePolicy = source.path("tombstonePolicy");
        String tombstoneStrategy = "NONE";
        if (!tombstonePolicy.isMissingNode() && !tombstonePolicy.isNull()) {
            if (!tombstonePolicy.isObject()) {
                throw invalid(plugin, version, prefix + "httpSource.tombstonePolicy must be an object.");
            }
            rejectUnknownFields(
                plugin,
                version,
                tombstonePolicy,
                Set.of("strategy", "operationJsonPointer", "deleteValues"),
                prefix + "tombstonePolicy"
            );
            tombstoneStrategy = normalizeUppercaseValue(tombstonePolicy.path("strategy").asText("NONE"));
            if (!Set.of("NONE", "ABSENT_FROM_SNAPSHOT", "FIELD_VALUE", "ABSENT_OR_FIELD_VALUE")
                .contains(tombstoneStrategy)) {
                throw invalid(plugin, version, prefix + "tombstonePolicy.strategy is unsupported.");
            }
            boolean deletesByField = Set.of("FIELD_VALUE", "ABSENT_OR_FIELD_VALUE").contains(tombstoneStrategy);
            if (deletesByField) {
                requireJsonPointer(
                    plugin,
                    version,
                    tombstonePolicy.path("operationJsonPointer").asText(""),
                    prefix + "tombstonePolicy.operationJsonPointer"
                );
                JsonNode deleteValues = tombstonePolicy.path("deleteValues");
                if (!deleteValues.isArray() || deleteValues.isEmpty()) {
                    throw invalid(plugin, version, prefix + "tombstonePolicy.deleteValues must be non-empty.");
                }
                for (JsonNode deleteValue : deleteValues) {
                    if (!deleteValue.isValueNode() || !StringUtils.hasText(deleteValue.asText(""))) {
                        throw invalid(plugin, version, prefix + "tombstonePolicy.deleteValues must contain scalar values.");
                    }
                }
            }
        }
        if ("CURSOR".equals(paginationStrategy)
            && Set.of("ABSENT_FROM_SNAPSHOT", "ABSENT_OR_FIELD_VALUE").contains(tombstoneStrategy)) {
            throw invalid(plugin, version, prefix + "cursor pagination cannot delete records by snapshot absence.");
        }

        JsonNode webhook = connector.path("webhook");
        if (webhook.isObject()) {
            rejectUnknownFields(plugin, version, webhook, HTTP_WEBHOOK_FIELDS, prefix + "webhook");
            requireIdentifier(plugin, version, firstText(webhook, "sourceId"), prefix + "webhook.sourceId");
            String webhookMethod = webhook.path("method").asText("").trim().toUpperCase(Locale.ROOT);
            if (!Set.of("POST", "PUT").contains(webhookMethod)) {
                throw invalid(plugin, version, prefix + "webhook.method must be POST or PUT.");
            }
            JsonNode verification = webhook.path("verification");
            if (!verification.isObject()) {
                throw invalid(plugin, version, prefix + "webhook.verification is required.");
            }
            rejectUnknownFields(
                plugin, version, verification,
                Set.of("strategy", "signatureHeader", "secretRefField", "timestampComponent", "signatureComponent", "replayWindowSeconds"),
                prefix + "webhook.verification"
            );
            if (!"HMAC_SHA256_TIMESTAMP_DOT_RAW_BODY".equals(normalizeUppercaseValue(verification.path("strategy").asText("")))) {
                throw invalid(plugin, version, prefix + "webhook verification strategy is unsupported.");
            }
            requireHttpHeaderName(
                plugin,
                version,
                verification.path("signatureHeader").asText(""),
                prefix + "webhook.verification.signatureHeader"
            );
            requireIdentifier(
                plugin,
                version,
                verification.path("secretRefField").asText(""),
                prefix + "webhook.verification.secretRefField"
            );
            requireIdentifier(
                plugin,
                version,
                verification.path("timestampComponent").asText("t"),
                prefix + "webhook.verification.timestampComponent"
            );
            requireIdentifier(
                plugin,
                version,
                verification.path("signatureComponent").asText("v1"),
                prefix + "webhook.verification.signatureComponent"
            );
            validateOptionalIntegerRange(
                plugin,
                version,
                verification,
                "replayWindowSeconds",
                0,
                86_400,
                prefix + "webhook.verification."
            );
            requireJsonPointer(plugin, version, webhook.path("eventIdJsonPointer").asText(""), prefix + "webhook.eventIdJsonPointer");
            requireJsonPointer(plugin, version, webhook.path("eventTypeJsonPointer").asText(""), prefix + "webhook.eventTypeJsonPointer");
            requireJsonPointer(plugin, version, webhook.path("resourceJsonPointer").asText(""), prefix + "webhook.resourceJsonPointer");
            if (!webhook.path("allowedEventTypes").isArray() || webhook.path("allowedEventTypes").isEmpty()) {
                throw invalid(plugin, version, prefix + "webhook.allowedEventTypes must be non-empty.");
            }
            requireCapabilityArray(
                plugin,
                version,
                webhook.path("allowedEventTypes"),
                prefix + "webhook.allowedEventTypes"
            );
            JsonNode contentTypes = webhook.path("allowedContentTypes");
            if (!contentTypes.isMissingNode() && (!contentTypes.isArray() || contentTypes.isEmpty())) {
                throw invalid(plugin, version, prefix + "webhook.allowedContentTypes must be a non-empty array.");
            }
            for (JsonNode contentType : contentTypes) {
                String value = contentType.asText("").trim().toLowerCase(Locale.ROOT);
                if (!value.matches("[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+")) {
                    throw invalid(plugin, version, prefix + "webhook.allowedContentTypes contains an invalid media type.");
                }
            }
            validateOptionalIntegerRange(plugin, version, webhook, "maxBodyBytes", 1024, 10 * 1024 * 1024, prefix);
            validateOptionalIntegerRange(plugin, version, webhook, "maxReconcileAttempts", 1, 20, prefix);
            validateOptionalIntegerRange(plugin, version, webhook, "retryDelaySeconds", 1, 86_400, prefix);
            validateOptionalBoolean(plugin, version, webhook, "registrationExpected", prefix);
            validateOptionalBoolean(plugin, version, webhook, "manualReplayEnabled", prefix);
            String orderingPolicy = normalizeUppercaseValue(webhook.path("orderingPolicy").asText("RECONCILE_LATEST_STATE"));
            if (!"RECONCILE_LATEST_STATE".equals(orderingPolicy)) {
                throw invalid(plugin, version, prefix + "webhook.orderingPolicy is unsupported.");
            }
        }
    }

    private void validateOptionalBoolean(MarketplacePluginEntity plugin,
                                         MarketplacePluginVersionEntity version,
                                         JsonNode parent,
                                         String field,
                                         String prefix) {
        JsonNode value = parent.path(field);
        if (!value.isMissingNode() && !value.isNull() && !value.isBoolean()) {
            throw invalid(plugin, version, prefix + field + " must be a boolean.");
        }
    }

    private void validateStaticQuery(MarketplacePluginEntity plugin,
                                     MarketplacePluginVersionEntity version,
                                     JsonNode query,
                                     String path) {
        if (query.isMissingNode() || query.isNull()) {
            return;
        }
        if (!query.isObject() || query.size() > 50) {
            throw invalid(plugin, version, path + " must be an object with at most 50 entries.");
        }
        query.fields().forEachRemaining(entry -> {
            requireRequestFieldName(plugin, version, entry.getKey(), path + " field");
            JsonNode value = entry.getValue();
            if (!value.isValueNode() || value.isNull() || value.asText("").length() > 2_000) {
                throw invalid(plugin, version, path + " values must be bounded scalar values.");
            }
        });
    }

    private void validateStaticHeaders(MarketplacePluginEntity plugin,
                                       MarketplacePluginVersionEntity version,
                                       JsonNode headers,
                                       String path) {
        if (headers.isMissingNode() || headers.isNull()) {
            return;
        }
        if (!headers.isObject() || headers.size() > 50) {
            throw invalid(plugin, version, path + " must be an object with at most 50 entries.");
        }
        headers.fields().forEachRemaining(entry -> {
            requireHttpHeaderName(plugin, version, entry.getKey(), path + " field");
            if (!entry.getValue().isTextual() || entry.getValue().asText().length() > 2_000) {
                throw invalid(plugin, version, path + " values must be strings of at most 2000 characters.");
            }
        });
    }

    private void requireRequestFieldName(MarketplacePluginEntity plugin,
                                         MarketplacePluginVersionEntity version,
                                         String value,
                                         String path) {
        if (!StringUtils.hasText(value) || !value.matches("[A-Za-z0-9$][A-Za-z0-9$._-]{0,159}")) {
            throw invalid(plugin, version, path + " is invalid.");
        }
    }

    private Set<String> requireCapabilityArray(MarketplacePluginEntity plugin,
                                               MarketplacePluginVersionEntity version,
                                               JsonNode values,
                                               String path) {
        if (!values.isArray() || values.isEmpty() || values.size() > 100) {
            throw invalid(plugin, version, path + " must be a non-empty array with at most 100 entries.");
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (JsonNode value : values) {
            String identifier = value.isTextual() ? value.asText("").trim() : "";
            if (!identifier.matches("[A-Za-z0-9][A-Za-z0-9:._-]{0,159}") || !normalized.add(identifier)) {
                throw invalid(plugin, version, path + " contains an invalid or duplicate identifier.");
            }
        }
        return Set.copyOf(normalized);
    }

    private void validateOptionalIntegerRange(MarketplacePluginEntity plugin,
                                              MarketplacePluginVersionEntity version,
                                              JsonNode parent,
                                              String field,
                                              int minimum,
                                              int maximum,
                                              String prefix) {
        JsonNode value = parent.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return;
        }
        if (!value.canConvertToInt() || value.asInt() < minimum || value.asInt() > maximum) {
            throw invalid(
                plugin,
                version,
                prefix + field + " must be an integer between " + minimum + " and " + maximum + "."
            );
        }
    }

    private void validateResourcePlacements(MarketplacePluginEntity plugin,
                                            MarketplacePluginVersionEntity version,
                                            JsonNode placements,
                                            String requestPath,
                                            String prefix) {
        if (!placements.isArray() || placements.isEmpty()) {
            throw invalid(plugin, version, prefix + "trustedResourcePlacements must be non-empty.");
        }
        Set<String> pathPlacements = new LinkedHashSet<>();
        for (JsonNode placement : placements) {
            if (!placement.isObject()) {
                throw invalid(plugin, version, prefix + "trustedResourcePlacements entries must be objects.");
            }
            rejectUnknownFields(plugin, version, placement, Set.of("target", "field", "jsonPointer"), prefix + "trustedResourcePlacement");
            String target = normalizeUppercaseValue(placement.path("target").asText(""));
            if (!Set.of("QUERY", "PATH", "HEADER", "BODY").contains(target)) {
                throw invalid(plugin, version, prefix + "trusted resource placement target is unsupported.");
            }
            requireText(plugin, version, placement, "field", prefix);
            if ("BODY".equals(target)) {
                requireJsonPointer(plugin, version, placement.path("jsonPointer").asText(""), prefix + "trustedResourcePlacement.jsonPointer");
            }
            if ("PATH".equals(target)) {
                String field = placement.path("field").asText("").trim();
                if (!requestPath.contains("{" + field + "}")) {
                    throw invalid(plugin, version, prefix + "PATH trusted resource placement must match a path placeholder.");
                }
                pathPlacements.add(field);
            }
        }
        Matcher placeholders = Pattern.compile("\\{([A-Za-z0-9._-]+)}").matcher(requestPath);
        while (placeholders.find()) {
            if (!pathPlacements.contains(placeholders.group(1))) {
                throw invalid(plugin, version, prefix + "contains an unbound protected path placeholder.");
            }
        }
    }

    private void rejectProviderAuthHeaderPlacement(MarketplacePluginEntity plugin,
                                                   MarketplacePluginVersionEntity version,
                                                   JsonNode placements,
                                                   String authenticationHeader,
                                                   String prefix) {
        if (!StringUtils.hasText(authenticationHeader) || !placements.isArray()) {
            return;
        }
        for (JsonNode placement : placements) {
            if ("HEADER".equals(normalizeUppercaseValue(placement.path("target").asText("")))) {
                requireHttpHeaderName(
                    plugin,
                    version,
                    placement.path("field").asText(""),
                    prefix + "trustedResourcePlacement.field"
                );
                if (authenticationHeader.trim().equalsIgnoreCase(placement.path("field").asText("").trim())) {
                    throw invalid(
                        plugin,
                        version,
                        prefix + "trusted resource placement must not target the provider authentication header."
                    );
                }
            }
        }
    }

    private void validatePointerMap(MarketplacePluginEntity plugin,
                                    MarketplacePluginVersionEntity version,
                                    JsonNode map,
                                    String path) {
        if (map == null || map.isMissingNode() || map.isNull()) {
            return;
        }
        if (!map.isObject() || map.size() > 100) {
            throw invalid(plugin, version, path + " must be an object with at most 100 entries.");
        }
        map.fields().forEachRemaining(entry -> {
            requireIdentifier(plugin, version, entry.getKey(), path + " field");
            if (!entry.getValue().isTextual()) {
                throw invalid(plugin, version, path + "." + entry.getKey() + " must be a JSON Pointer string.");
            }
            requireJsonPointer(plugin, version, entry.getValue().asText(""), path + "." + entry.getKey());
        });
    }

    private void requireJsonPointer(MarketplacePluginEntity plugin,
                                    MarketplacePluginVersionEntity version,
                                    String value,
                                    String path) {
        if (!StringUtils.hasText(value) || !value.startsWith("/")) {
            throw invalid(plugin, version, path + " must be an absolute JSON Pointer.");
        }
    }

    private void requireRelativePath(MarketplacePluginEntity plugin,
                                     MarketplacePluginVersionEntity version,
                                     String value,
                                     String path) {
        if (!StringUtils.hasText(value) || !value.startsWith("/") || value.contains("://")) {
            throw invalid(plugin, version, path + " must be a relative path beginning with '/'.");
        }
    }

    private void requireAbsoluteHttpsUrl(MarketplacePluginEntity plugin,
                                         MarketplacePluginVersionEntity version,
                                         String value,
                                         String path) {
        try {
            java.net.URI uri = java.net.URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                || !StringUtils.hasText(uri.getHost())
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
        } catch (Exception ex) {
            throw invalid(
                plugin,
                version,
                path + " must be an absolute HTTPS URL without credentials, query, or fragment."
            );
        }
    }

    private void requireIdentifier(MarketplacePluginEntity plugin,
                                   MarketplacePluginVersionEntity version,
                                   String value,
                                   String path) {
        if (!StringUtils.hasText(value) || !value.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,159}")) {
            throw invalid(plugin, version, path + " is invalid.");
        }
    }

    private void requireHttpHeaderName(MarketplacePluginEntity plugin,
                                       MarketplacePluginVersionEntity version,
                                       String value,
                                       String path) {
        if (!StringUtils.hasText(value)
            || !value.trim().matches("[!#$%&'*+.^_`|~0-9A-Za-z-]{1,160}")) {
            throw invalid(plugin, version, path + " is not a valid HTTP header name.");
        }
    }

    private void requireText(MarketplacePluginEntity plugin,
                             MarketplacePluginVersionEntity version,
                             JsonNode node,
                             String field,
                             String prefix) {
        if (!StringUtils.hasText(node.path(field).asText(""))) {
            throw invalid(plugin, version, prefix + field + " is required.");
        }
    }

    private void validateDocumentDataset(MarketplacePluginEntity plugin,
                                         MarketplacePluginVersionEntity version,
                                         String datasetId,
                                         String entityType,
                                         String storageScope,
                                         String sharingScope,
                                         String updateStrategy,
                                         String seedDatasetRef,
                                         JsonNode syncConnector,
                                         JsonNode sourceConnector,
                                         JsonNode documentPolicy) {
        String prefix = "document dataset '" + datasetId + "' ";
        if (!"document".equals(entityType)) {
            throw invalid(plugin, version, prefix + "must declare entityType=document.");
        }
        if (!"CUSTOMER_MANAGED".equals(storageScope)
            || !"DEPLOYMENT_ONLY".equals(sharingScope)
            || !"VERSIONED_REPLACE".equals(updateStrategy)) {
            throw invalid(plugin, version, prefix
                + "must use storageScope=CUSTOMER_MANAGED, sharingScope=DEPLOYMENT_ONLY, and updateStrategy=VERSIONED_REPLACE.");
        }
        if (StringUtils.hasText(seedDatasetRef) || syncConnector.isObject()) {
            throw invalid(plugin, version, prefix + "must not declare seedDatasetRef or syncConnector.");
        }
        if (!sourceConnector.isObject()) {
            throw invalid(plugin, version, prefix + "must declare sourceConnector.");
        }
        rejectUnknownFields(
            plugin,
            version,
            sourceConnector,
            DOCUMENT_SOURCE_CONNECTOR_FIELDS,
            prefix + "sourceConnector"
        );
        String connectorType = normalizeUppercaseValue(sourceConnector.path("connectorType").asText(""));
        if (!SUPPORTED_DOCUMENT_CONNECTOR_TYPES.contains(connectorType)) {
            throw invalid(plugin, version, prefix + "declares unsupported sourceConnector.connectorType.");
        }
        if (!"CUSTOMER_MANAGED".equals(normalizeUppercaseValue(
            sourceConnector.path("storageOwnership").asText("")
        ))) {
            throw invalid(plugin, version, prefix + "must declare sourceConnector.storageOwnership=CUSTOMER_MANAGED.");
        }
        String bindingRefField = blankToNull(sourceConnector.path("bindingRefField").asText(""));
        if (!StringUtils.hasText(bindingRefField) || bindingRefField.length() > 128) {
            throw invalid(plugin, version, prefix + "must declare a bounded sourceConnector.bindingRefField.");
        }
        if (sourceConnector.path("deleteSourceOnRemoval").asBoolean(false)) {
            throw invalid(plugin, version, prefix + "cannot request source-object deletion.");
        }
        if (!sourceConnector.has("deleteSourceOnRemoval")
            || !sourceConnector.path("deleteSourceOnRemoval").isBoolean()) {
            throw invalid(plugin, version, prefix + "must explicitly declare sourceConnector.deleteSourceOnRemoval=false.");
        }
        JsonNode allowedOperations = sourceConnector.path("allowedOperations");
        if (!allowedOperations.isArray()) {
            throw invalid(plugin, version, prefix + "sourceConnector.allowedOperations must be an array.");
        }
        Set<String> normalizedOperations = new LinkedHashSet<>();
        for (JsonNode operation : allowedOperations) {
            String value = normalizeUppercaseValue(operation.asText(""));
            if (!Set.of("LIST", "READ", "HEAD").contains(value)) {
                throw invalid(plugin, version, prefix + "source connector may request only LIST, READ, and HEAD operations.");
            }
            normalizedOperations.add(value);
        }
        if (!normalizedOperations.equals(Set.of("LIST", "READ", "HEAD"))) {
            throw invalid(plugin, version, prefix + "source connector must declare exactly LIST, READ, and HEAD operations.");
        }
        if (!documentPolicy.isObject()) {
            throw invalid(plugin, version, prefix + "must declare documentPolicy.");
        }
        rejectUnknownFields(
            plugin,
            version,
            documentPolicy,
            DOCUMENT_POLICY_FIELDS,
            prefix + "documentPolicy"
        );
        requireExactStringSet(plugin, version, prefix + "documentPolicy.allowedMediaTypes",
            documentPolicy.path("allowedMediaTypes"), Set.of("text/plain", "application/json"));
        requireExactStringSet(plugin, version, prefix + "documentPolicy.allowedExtensions",
            documentPolicy.path("allowedExtensions"), Set.of(".txt", ".json"));
        JsonNode jsonContentKeys = documentPolicy.path("jsonContentKeys");
        if (!jsonContentKeys.isArray() || jsonContentKeys.isEmpty() || jsonContentKeys.size() > 8) {
            throw invalid(plugin, version, prefix + "documentPolicy.jsonContentKeys must contain between 1 and 8 keys.");
        }
        Set<String> normalizedJsonKeys = new LinkedHashSet<>();
        for (JsonNode key : jsonContentKeys) {
            String value = key.asText("").trim();
            if (!value.matches("[A-Za-z][A-Za-z0-9._-]{0,63}") || !normalizedJsonKeys.add(value)) {
                throw invalid(plugin, version, prefix + "documentPolicy.jsonContentKeys contains an invalid or duplicate key.");
            }
        }
        requirePositiveBound(plugin, version, prefix, documentPolicy, "maxSourceBytes", 1, 10 * 1024 * 1024);
        requirePositiveBound(plugin, version, prefix, documentPolicy, "maxSources", 1, 10_000);
        requirePositiveBound(plugin, version, prefix, documentPolicy, "maxTotalIndexedBytes", 1, 10L * 1024 * 1024 * 1024);
        requirePositiveBound(plugin, version, prefix, documentPolicy, "maxChunksPerSource", 1, 10_000);
        requirePositiveBound(plugin, version, prefix, documentPolicy, "maxChunkCharacters", 50, 100_000);
        requirePositiveBound(plugin, version, prefix, documentPolicy, "maxTotalCharacters", 50, 10_000_000);
        requirePositiveBound(plugin, version, prefix, documentPolicy, "previewMaxChunks", 1, 100);
        requirePositiveBound(plugin, version, prefix, documentPolicy, "previewMaxCharactersPerChunk", 50, 2_000);
        requirePositiveBound(plugin, version, prefix, documentPolicy, "evidenceRetentionDays", 1, 3_650);
        requirePositiveBound(plugin, version, prefix, documentPolicy, "commandRetentionDays", 1, 3_650);
        requirePositiveBound(plugin, version, prefix, documentPolicy, "retentionBatchSize", 1, 1_000);
        JsonNode metadataKeys = documentPolicy.path("allowedMetadataKeys");
        if (!metadataKeys.isArray() || metadataKeys.size() > 16) {
            throw invalid(plugin, version, prefix + "documentPolicy.allowedMetadataKeys must be an array with at most 16 entries.");
        }
        for (JsonNode key : metadataKeys) {
            String value = key.asText("").trim();
            if (!value.matches("[A-Za-z][A-Za-z0-9._-]{0,63}")
                || DOCUMENT_PROTECTED_METADATA_KEYS.contains(value)) {
                throw invalid(plugin, version, prefix + "documentPolicy.allowedMetadataKeys contains an invalid or protected key.");
            }
        }
        if (!documentPolicy.path("initialIndexRequiresConfirmation").isBoolean()
            || !documentPolicy.path("initialIndexRequiresConfirmation").asBoolean()) {
            throw invalid(plugin, version, prefix + "must require explicit initial indexing confirmation in the first release.");
        }
        if (!documentPolicy.path("trustedAutoIndexingAllowed").isBoolean()
            || documentPolicy.path("trustedAutoIndexingAllowed").asBoolean()) {
            throw invalid(plugin, version, prefix + "must disable trusted auto-indexing in the first release.");
        }
    }

    private void requireExactStringSet(MarketplacePluginEntity plugin,
                                       MarketplacePluginVersionEntity version,
                                       String field,
                                       JsonNode node,
                                       Set<String> supported) {
        if (!node.isArray() || node.isEmpty()) {
            throw invalid(plugin, version, field + " must be a non-empty array.");
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (JsonNode entry : node) {
            String value = entry.asText("").trim().toLowerCase(Locale.ROOT);
            if (!supported.contains(value) || !normalized.add(value)) {
                throw invalid(plugin, version, field + " contains an unsupported first-release value.");
            }
        }
        if (!normalized.equals(supported)) {
            throw invalid(plugin, version, field + " must declare the complete first-release value set.");
        }
    }

    private void requirePositiveBound(MarketplacePluginEntity plugin,
                                      MarketplacePluginVersionEntity version,
                                      String prefix,
                                      JsonNode policy,
                                      String field,
                                      long minimum,
                                      long maximum) {
        JsonNode value = policy.path(field);
        if (!value.isIntegralNumber() || value.asLong() < minimum || value.asLong() > maximum) {
            throw invalid(plugin, version, prefix + "documentPolicy." + field
                + " must be between " + minimum + " and " + maximum + ".");
        }
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

        ObjectNode candidate = objectMapper.createObjectNode();
        candidate.putObject("ai-config").put("vector-dimensions", 512);
        candidate.set("ai-entities", entities.deepCopy());
        EntityConfigContractValidation validation =
            entityConfigContractService.validate(
                candidate,
                new EntityConfigValidationContext(false, true)
            );
        if (!validation.valid()) {
            String details = validation.issues().stream()
                .limit(3)
                .map(this::summarizeEntityConfigIssue)
                .collect(java.util.stream.Collectors.joining("; "));
            throw invalid(
                plugin,
                version,
                "contributions.entityConfig must use "
                    + EntityConfigContractService.CONTRACT_VERSION_V04
                    + ": "
                    + details
            );
        }
    }

    private String summarizeEntityConfigIssue(EntityConfigContractIssue issue) {
        if (issue == null) {
            return "ENTITY_CONFIG_INVALID";
        }
        return issue.code() + " at " + issue.path() + ": " + issue.message();
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

    private void validateCapabilityProfiles(MarketplacePluginEntity plugin,
                                            MarketplacePluginVersionEntity version,
                                            JsonNode capabilityProfilesNode) {
        List<String> profiles = normalizeUppercaseValues(capabilityProfilesNode);
        if (!profiles.isEmpty()) {
            throw invalid(
                plugin,
                version,
                "manifest capabilityProfiles are no longer supported; compile only into runtime-backed contracts."
            );
        }
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
            permissionsNode.path("contributesProviders").asBoolean("INFERENCE_PROFILE".equals(pluginType)),
            permissionsNode.path("contributesSpecialists").asBoolean("SPECIALIST".equals(pluginType)),
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
        if (!contributions.inferenceProfileIds().isEmpty() && !permissions.contributesProviders()) {
            throw invalid(plugin, version, "inference profile contributions require permissions.contributesProviders=true.");
        }
        if (!contributions.specialistBundleRefs().isEmpty() && !permissions.contributesSpecialists()) {
            throw invalid(plugin, version, "specialist contributions require permissions.contributesSpecialists=true.");
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
        JsonNode syncConnector,
        JsonNode sourceConnector,
        JsonNode documentPolicy,
        JsonNode customerBackendIngestion
    ) {
    }
}
