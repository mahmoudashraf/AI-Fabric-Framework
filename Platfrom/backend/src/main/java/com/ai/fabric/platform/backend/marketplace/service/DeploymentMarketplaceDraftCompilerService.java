package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DraftValidationIssue;
import com.ai.fabric.platform.backend.deployment.model.DraftValidationResponse;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentDraftRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentDraftValidationService;
import com.ai.fabric.platform.backend.deployment.service.ManagedDeploymentProfileCatalog;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.marketplace.entity.DeploymentMarketplacePluginInstallEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.ai.fabric.platform.backend.marketplace.entity.PlatformManagedInferenceEndpointEntity;
import com.ai.fabric.platform.backend.marketplace.service.PlatformManagedInferenceServiceService;
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
import java.util.Locale;
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
    private static final String DEFAULT_MARKETPLACE_INFERENCE_CONTRACT_VERSION = "MARKETPLACE_INFERENCE_PROVIDER_CONFIG_V1";
    private static final String MARKETPLACE_INFERENCE_FIELD = "marketplaceInference";
    private static final Set<String> GREENFIELD_SHOPIFY_MCP_ACTION_PLUGIN_IDS = Set.of(
        "mkp-action-shopify-storefront-read-mcp",
        "mkp-action-shopify-cart-mcp",
        "mkp-action-shopify-customer-account-mcp",
        "mkp-action-shopify-checkout-mcp"
    );
    private static final Set<String> GREENFIELD_SHOPIFY_ACTION_IDS = Set.of(
        "shopify_search_catalog",
        "shopify_lookup_catalog",
        "shopify_get_product",
        "shopify_get_product_details",
        "shopify_search_policies",
        "shopify_get_cart",
        "shopify_update_cart",
        "shopify_get_most_recent_order_status",
        "shopify_get_order_status",
        "shopify_get_store_credit_balances",
        "shopify_request_return",
        "shopify_create_checkout",
        "shopify_get_checkout",
        "shopify_update_checkout",
        "shopify_complete_checkout",
        "shopify_cancel_checkout"
    );

    private final DeploymentService deploymentService;
    private final DeploymentDraftValidationService deploymentDraftValidationService;
    private final DeploymentRepository deploymentRepository;
    private final DeploymentMarketplacePluginInstallRepository installRepository;
    private final MarketplaceCatalogService marketplaceCatalogService;
    private final MarketplaceManifestService marketplaceManifestService;
    private final MarketplaceEntitlementService marketplaceEntitlementService;
    private final MarketplaceDatasetHandleResolver marketplaceDatasetHandleResolver;
    private final PlatformManagedInferenceEndpointService platformManagedInferenceEndpointService;
    private final PlatformManagedInferenceServiceService platformManagedInferenceServiceService;
    private final ObjectMapper objectMapper;

    public DeploymentMarketplaceDraftCompilerService(DeploymentService deploymentService,
                                                     DeploymentDraftValidationService deploymentDraftValidationService,
                                                     DeploymentRepository deploymentRepository,
                                                     DeploymentMarketplacePluginInstallRepository installRepository,
                                                     MarketplaceCatalogService marketplaceCatalogService,
                                                     MarketplaceManifestService marketplaceManifestService,
                                                     MarketplaceEntitlementService marketplaceEntitlementService,
                                                     MarketplaceDatasetHandleResolver marketplaceDatasetHandleResolver,
                                                     PlatformManagedInferenceEndpointService platformManagedInferenceEndpointService,
                                                     PlatformManagedInferenceServiceService platformManagedInferenceServiceService,
                                                     ObjectMapper objectMapper) {
        this.deploymentService = deploymentService;
        this.deploymentDraftValidationService = deploymentDraftValidationService;
        this.deploymentRepository = deploymentRepository;
        this.installRepository = installRepository;
        this.marketplaceCatalogService = marketplaceCatalogService;
        this.marketplaceManifestService = marketplaceManifestService;
        this.marketplaceEntitlementService = marketplaceEntitlementService;
        this.marketplaceDatasetHandleResolver = marketplaceDatasetHandleResolver;
        this.platformManagedInferenceEndpointService = platformManagedInferenceEndpointService;
        this.platformManagedInferenceServiceService = platformManagedInferenceServiceService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DeploymentDraftResponse syncDeploymentDraft(String deploymentId) {
        return syncDeploymentDraft(deploymentId, false);
    }

    @Transactional
    public DeploymentDraftResponse syncDeploymentDraftForTrustedCaller(String deploymentId) {
        return syncDeploymentDraft(deploymentId, true);
    }

    private DeploymentDraftResponse syncDeploymentDraft(String deploymentId, boolean trustedCaller) {
        DeploymentDraftResponse draft = trustedCaller
            ? deploymentService.getActiveDraftForDeploymentForTrustedCaller(deploymentId)
            : deploymentService.getActiveDraftForDeployment(deploymentId);
        ObjectNode actionsRoot = ensureObject(draft.actionsConfig());
        ObjectNode entityRoot = normalizeEntityRoot(draft.entityConfig());
        ObjectNode routingRoot = ensureObject(draft.routingConfig());
        ObjectNode knowledgeSourceRoot = normalizeKnowledgeSourceRoot(draft.knowledgeSourceConfig());
        ObjectNode shellRoot = normalizeShellRoot(draft.shellConfig());
        ObjectNode marketplaceDatasetRoot = normalizeMarketplaceDatasetRoot(draft.marketplaceDatasetConfig());
        ObjectNode providerRoot = normalizeProviderRoot(draft.providerConfig());
        DeploymentEntity deployment = deploymentRepository.findById(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(CONFLICT, "Deployment not found: " + deploymentId));

        List<DeploymentMarketplacePluginInstallEntity> installs =
            installRepository.findByDeploymentIdOrderByUpdatedAtDesc(deploymentId);

        stripMarketplaceManagedActions(actionsRoot);
        stripGreenfieldShopifyLegacyActions(actionsRoot, installs);
        stripMarketplaceManagedEntities(entityRoot);
        stripMarketplaceManagedKnowledgeSources(knowledgeSourceRoot);
        stripMarketplaceManagedShell(shellRoot);
        stripMarketplaceManagedDatasets(marketplaceDatasetRoot);
        stripMarketplaceManagedInference(providerRoot);

        Set<String> existingActionNames = actionNames(actionsRoot.path("actions"));
        Set<String> existingEntityTypes = entityTypes(entityRoot.path("ai-entities"));
        Set<String> existingKnowledgeSourceIds = knowledgeSourceIds(knowledgeSourceRoot.path("sources"));
        String activeInferencePluginId = null;

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
                case "INFERENCE_PROFILE" -> {
                    if (activeInferencePluginId != null && !activeInferencePluginId.equals(plugin.getId())) {
                        throw new ResponseStatusException(
                            CONFLICT,
                            "Only one enabled inference-profile plugin may compile into a deployment at a time. Found "
                                + activeInferencePluginId + " and " + plugin.getId() + "."
                        );
                    }
                    activeInferencePluginId = plugin.getId();
                    applyInferenceProfile(providerRoot, deployment, install, plugin, version, parsed);
                }
                default -> throw new ResponseStatusException(
                    CONFLICT,
                    "Unsupported marketplace plugin type during draft compilation: " + parsed.pluginType()
                );
            }
        }

        synchronizeEntityVectorDimensions(entityRoot, providerRoot);
        boolean routingChanged = pruneRoutesWithoutActions(routingRoot, actionNames(actionsRoot.path("actions")));
        UpdateDeploymentDraftRequest updateRequest = new UpdateDeploymentDraftRequest(
            actionsRoot,
            entityRoot,
            routingChanged ? routingRoot : null,
            providerRoot,
            null,
            null,
            knowledgeSourceRoot,
            shellRoot,
            marketplaceDatasetRoot
        );
        DeploymentDraftResponse updated = trustedCaller
            ? deploymentService.updateDraftForTrustedCaller(draft.id(), updateRequest)
            : deploymentService.updateDraft(draft.id(), updateRequest);
        DraftValidationResponse validation = deploymentDraftValidationService.validate(asDraftEntity(updated));
        if (!validation.publishReady()) {
            throw new ResponseStatusException(
                CONFLICT,
                "Marketplace install compilation produced an invalid draft: " + summarizeIssues(validation.issues())
            );
        }
        return updated;
    }

    private void applyInferenceProfile(ObjectNode providerRoot,
                                       DeploymentEntity deployment,
                                       DeploymentMarketplacePluginInstallEntity install,
                                       MarketplacePluginEntity plugin,
                                       MarketplacePluginVersionEntity version,
                                       MarketplaceManifestService.ParsedMarketplaceManifest parsed) {
        JsonNode inferenceProfile = parsed.manifest().path("contributions").path("inferenceProfile");
        if (!inferenceProfile.isObject()) {
            throw new ResponseStatusException(CONFLICT, "Inference profile contribution is missing for plugin: " + plugin.getId());
        }
        JsonNode installConfig = readJson(install.getConfigJson());
        JsonNode installSecretRefs = readJson(install.getSecretRefsJson());
        LinkedHashSet<String> managedFields = new LinkedHashSet<>();
        LinkedHashSet<String> endpointProfileRefs = new LinkedHashSet<>();
        LinkedHashSet<String> managedServiceRefs = new LinkedHashSet<>();

        applyInferenceLlmSection(
            providerRoot,
            "orchestration",
            inferenceProfile.path("orchestration"),
            installConfig,
            installSecretRefs,
            managedFields,
            endpointProfileRefs,
            managedServiceRefs
        );
        applyInferenceLlmSection(
            providerRoot,
            "generation",
            inferenceProfile.path("generation"),
            installConfig,
            installSecretRefs,
            managedFields,
            endpointProfileRefs,
            managedServiceRefs
        );
        applyInferenceEmbeddingSection(
            providerRoot,
            deployment,
            install,
            inferenceProfile.path("embedding"),
            installConfig,
            installSecretRefs,
            managedFields,
            endpointProfileRefs,
            managedServiceRefs
        );

        String generationProvider = text(providerRoot, "generationLlmProvider");
        String orchestrationProvider = text(providerRoot, "orchestrationLlmProvider");
        String rootLlmProvider = hasText(generationProvider) ? generationProvider : orchestrationProvider;
        if (hasText(rootLlmProvider)) {
            providerRoot.put("llmProvider", rootLlmProvider);
            managedFields.add("llmProvider");
        }

        ObjectNode metadata = ensureObjectNode(providerRoot, MARKETPLACE_INFERENCE_FIELD);
        metadata.put("contractVersion", DEFAULT_MARKETPLACE_INFERENCE_CONTRACT_VERSION);
        metadata.set("profileIds", toStringArray(readStringList(inferenceProfile, "profileId", "id")));
        metadata.set("endpointProfileRefs", toStringArray(endpointProfileRefs));
        metadata.set("managedServiceRefs", toStringArray(managedServiceRefs));
        metadata.set("installIds", toStringArray(List.of(install.getId())));
        metadata.set("managedFields", toStringArray(managedFields));
        metadata.put(MARKETPLACE_MANAGED_FIELD, true);
        metadata.put(MARKETPLACE_PLUGIN_ID_FIELD, plugin.getId());
        metadata.put(MARKETPLACE_INSTALL_ID_FIELD, install.getId());
        metadata.put(MARKETPLACE_PLUGIN_VERSION_FIELD, version.getVersion());
    }

    private void applyInferenceLlmSection(ObjectNode providerRoot,
                                          String purpose,
                                          JsonNode section,
                                          JsonNode installConfig,
                                          JsonNode installSecretRefs,
                                          Set<String> managedFields,
                                          Set<String> endpointProfileRefs,
                                          Set<String> managedServiceRefs) {
        if (!section.isObject()) {
            return;
        }
        String provider = lower(firstText(section, "provider", "llmProvider"));
        if (!hasText(provider)) {
            throw new ResponseStatusException(CONFLICT, "Inference profile " + purpose + " section must declare provider.");
        }
        String endpointProfileRef = configuredText(section, installConfig, "endpointProfileRef", "endpointProfileRefField");
        String managedServiceRef = configuredText(section, installConfig, "managedServiceRef", "managedServiceRefField");
        String baseUrl = configuredText(section, installConfig, "baseUrl", "baseUrlField");
        String deploymentName = configuredText(section, installConfig, "deploymentName", "deploymentNameField");
        String apiVersion = configuredText(section, installConfig, "apiVersion", "apiVersionField");
        String apiKeySecretRef = configuredText(section, installSecretRefs, "apiKeySecretRef", "apiKeySecretRefField");

        if (hasText(endpointProfileRef) && hasText(managedServiceRef)) {
            throw new ResponseStatusException(
                CONFLICT,
                "Inference profile " + purpose + " section may not declare both endpointProfileRef and managedServiceRef."
            );
        }
        if (hasText(managedServiceRef)) {
            PlatformManagedInferenceEndpointEntity endpoint = platformManagedInferenceServiceService
                .requireActiveEndpointForService(managedServiceRef, purpose, provider);
            endpointProfileRef = endpoint.getProfileRef();
            baseUrl = hasText(baseUrl) ? baseUrl : blankToNull(endpoint.getBaseUrl());
            deploymentName = hasText(deploymentName) ? deploymentName : blankToNull(endpoint.getDeploymentName());
            apiVersion = hasText(apiVersion) ? apiVersion : blankToNull(endpoint.getApiVersion());
            apiKeySecretRef = hasText(apiKeySecretRef) ? apiKeySecretRef : blankToNull(endpoint.getSecretName());
            endpointProfileRefs.add(endpointProfileRef);
            managedServiceRefs.add(managedServiceRef);
        } else if (hasText(endpointProfileRef)) {
            PlatformManagedInferenceEndpointEntity endpoint = platformManagedInferenceEndpointService.requireActive(endpointProfileRef);
            String endpointProvider = lower(endpoint.getProviderType());
            if (hasText(endpointProvider) && !provider.equals(endpointProvider)) {
                throw new ResponseStatusException(
                    CONFLICT,
                    "Inference profile endpoint '" + endpointProfileRef + "' uses provider '" + endpoint.getProviderType()
                        + "' but the " + purpose + " section selected '" + provider + "'."
                );
            }
            baseUrl = hasText(baseUrl) ? baseUrl : blankToNull(endpoint.getBaseUrl());
            deploymentName = hasText(deploymentName) ? deploymentName : blankToNull(endpoint.getDeploymentName());
            apiVersion = hasText(apiVersion) ? apiVersion : blankToNull(endpoint.getApiVersion());
            apiKeySecretRef = hasText(apiKeySecretRef) ? apiKeySecretRef : blankToNull(endpoint.getSecretName());
            endpointProfileRefs.add(endpointProfileRef);
        }

        String prefix = purpose.trim().toLowerCase(Locale.ROOT);
        putManaged(providerRoot, prefix + "LlmProvider", provider, managedFields);
        putManaged(providerRoot, prefix + "ManagedServiceRef", managedServiceRef, managedFields);
        putManaged(providerRoot, prefix + "EndpointProfile", endpointProfileRef, managedFields);
        putManaged(providerRoot, prefix + "BaseUrl", baseUrl, managedFields);
        putManaged(providerRoot, prefix + "DeploymentName", deploymentName, managedFields);
        putManaged(providerRoot, prefix + "ApiVersion", apiVersion, managedFields);
        putManaged(providerRoot, prefix + "ApiKeySecretRef", apiKeySecretRef, managedFields);
        putManaged(providerRoot, prefix + "Model", configuredText(section, installConfig, "model", "modelField"), managedFields);
        putManaged(providerRoot, prefix + "MaxTokens", configuredInt(section, installConfig, "maxTokens", "maxTokensField"), managedFields);
        putManaged(providerRoot, prefix + "Temperature", configuredDouble(section, installConfig, "temperature", "temperatureField"), managedFields);
        putManaged(providerRoot, prefix + "Timeout", configuredInt(section, installConfig, "timeout", "timeoutField"), managedFields);

        seedGlobalLlmProviderDefaults(
            providerRoot,
            provider,
            configuredText(section, installConfig, "model", "modelField"),
            baseUrl,
            deploymentName,
            apiVersion,
            managedFields
        );
    }

    private void applyInferenceEmbeddingSection(ObjectNode providerRoot,
                                                DeploymentEntity deployment,
                                                DeploymentMarketplacePluginInstallEntity install,
                                                JsonNode section,
                                                JsonNode installConfig,
                                                JsonNode installSecretRefs,
                                                Set<String> managedFields,
                                                Set<String> endpointProfileRefs,
                                                Set<String> managedServiceRefs) {
        if (!section.isObject()) {
            return;
        }
        String provider = lower(firstText(section, "provider", "embeddingProvider"));
        if (!hasText(provider)) {
            throw new ResponseStatusException(CONFLICT, "Inference profile embedding section must declare provider.");
        }
        String endpointProfileRef = configuredText(section, installConfig, "endpointProfileRef", "endpointProfileRefField");
        String managedServiceRef = configuredText(section, installConfig, "managedServiceRef", "managedServiceRefField");
        String serviceMode = configuredText(section, installConfig, "serviceMode", "serviceModeField");
        String baseUrl = configuredText(section, installConfig, "baseUrl", "baseUrlField");
        String deploymentName = configuredText(section, installConfig, "deploymentName", "deploymentNameField");
        String apiVersion = configuredText(section, installConfig, "apiVersion", "apiVersionField");
        String apiKeySecretRef = configuredText(section, installSecretRefs, "apiKeySecretRef", "apiKeySecretRefField");

        if (hasText(endpointProfileRef) && hasText(managedServiceRef)) {
            throw new ResponseStatusException(
                CONFLICT,
                "Inference profile embedding section may not declare both endpointProfileRef and managedServiceRef."
            );
        }
        if (ManagedDeploymentProfileCatalog.INFERENCE_SERVICE_MODE_DEPLOYMENT_DEDICATED_SERVICE.equals(serviceMode)) {
            if (!hasText(managedServiceRef)) {
                managedServiceRef = pluginScopedDedicatedEmbeddingServiceRef(deployment, install);
            }
            endpointProfileRef = hasText(endpointProfileRef)
                ? endpointProfileRef
                : dedicatedEmbeddingEndpointProfileRef(deployment);
            managedServiceRefs.add(managedServiceRef);
        } else if (hasText(managedServiceRef)) {
            PlatformManagedInferenceEndpointEntity endpoint = platformManagedInferenceServiceService
                .requireActiveEndpointForService(managedServiceRef, "EMBEDDING", provider);
            endpointProfileRef = endpoint.getProfileRef();
            baseUrl = hasText(baseUrl) ? baseUrl : blankToNull(endpoint.getBaseUrl());
            deploymentName = hasText(deploymentName) ? deploymentName : blankToNull(endpoint.getDeploymentName());
            apiVersion = hasText(apiVersion) ? apiVersion : blankToNull(endpoint.getApiVersion());
            apiKeySecretRef = hasText(apiKeySecretRef) ? apiKeySecretRef : blankToNull(endpoint.getSecretName());
            endpointProfileRefs.add(endpointProfileRef);
            managedServiceRefs.add(managedServiceRef);
        } else if (hasText(endpointProfileRef)) {
            PlatformManagedInferenceEndpointEntity endpoint = platformManagedInferenceEndpointService.requireActive(endpointProfileRef);
            String endpointProvider = lower(endpoint.getProviderType());
            if (hasText(endpointProvider) && !provider.equals(endpointProvider)) {
                throw new ResponseStatusException(
                    CONFLICT,
                    "Inference profile endpoint '" + endpointProfileRef + "' uses provider '" + endpoint.getProviderType()
                        + "' but the embedding section selected '" + provider + "'."
                );
            }
            baseUrl = hasText(baseUrl) ? baseUrl : blankToNull(endpoint.getBaseUrl());
            deploymentName = hasText(deploymentName) ? deploymentName : blankToNull(endpoint.getDeploymentName());
            apiVersion = hasText(apiVersion) ? apiVersion : blankToNull(endpoint.getApiVersion());
            apiKeySecretRef = hasText(apiKeySecretRef) ? apiKeySecretRef : blankToNull(endpoint.getSecretName());
            endpointProfileRefs.add(endpointProfileRef);
        }

        putManaged(providerRoot, "embeddingProvider", provider, managedFields);
        putManaged(providerRoot, "embeddingServiceMode", serviceMode, managedFields);
        putManaged(providerRoot, "embeddingManagedServiceRef", managedServiceRef, managedFields);
        putManaged(providerRoot, "embeddingEndpointProfile", endpointProfileRef, managedFields);
        putManaged(providerRoot, "embeddingApiKeySecretRef", apiKeySecretRef, managedFields);
        putManaged(providerRoot, "embeddingBaseUrl", baseUrl, managedFields);
        putManaged(providerRoot, "embeddingDeploymentName", deploymentName, managedFields);
        putManaged(providerRoot, "embeddingApiVersion", apiVersion, managedFields);

        switch (provider) {
            case ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_ONNX -> {
                putManaged(providerRoot, "onnxModelAlias", configuredText(section, installConfig, "modelAlias", "modelAliasField", "model", "modelField"), managedFields);
                putManaged(providerRoot, "onnxMaxSequenceLength", configuredInt(section, installConfig, "maxSequenceLength", "maxSequenceLengthField"), managedFields);
                putManaged(providerRoot, "onnxUseGpu", configuredBoolean(section, installConfig, "useGpu", "useGpuField"), managedFields);
            }
            case ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_OPENAI -> {
                putManaged(providerRoot, "openaiEmbeddingBaseUrl", baseUrl, managedFields);
                putManaged(providerRoot, "openaiEmbeddingModel", configuredText(section, installConfig, "model", "modelField"), managedFields);
                putManaged(
                    providerRoot,
                    "openaiEmbeddingDimensions",
                    configuredClampedOpenAiEmbeddingDimensions(providerRoot, section, installConfig),
                    managedFields
                );
            }
            case ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_AZURE -> {
                putManaged(providerRoot, "azureEmbeddingEndpoint", baseUrl, managedFields);
                putManaged(providerRoot, "azureEmbeddingDeploymentName", configuredText(section, installConfig, "model", "modelField", "deploymentName", "deploymentNameField"), managedFields);
                putManaged(providerRoot, "azureEmbeddingApiVersion", apiVersion, managedFields);
            }
            case ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_COHERE -> {
                putManaged(providerRoot, "cohereBaseUrl", baseUrl, managedFields);
                putManaged(providerRoot, "cohereEmbeddingModel", configuredText(section, installConfig, "model", "modelField"), managedFields);
            }
            case ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_GEMINI -> {
                putManaged(providerRoot, "geminiBaseUrl", baseUrl, managedFields);
                putManaged(providerRoot, "geminiEmbeddingModel", configuredText(section, installConfig, "model", "modelField"), managedFields);
            }
            default -> throw new ResponseStatusException(CONFLICT, "Unsupported embedding provider in inference profile: " + provider);
        }
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
        ArrayNode webhookTargets = ensureArray(actionsRoot, "webhookTargets");
        ArrayNode mcpServers = ensureArray(actionsRoot, "mcpServers");
        JsonNode installConfig = readJson(install.getConfigJson());
        JsonNode installSecretRefs = readJson(install.getSecretRefsJson());
        JsonNode webhookTargetEntries = parsed.manifest().path("contributions").path("webhookTargets");
        for (JsonNode targetEntry : iterable(webhookTargetEntries)) {
            if (!targetEntry.isObject()) {
                continue;
            }
            String targetId = text(targetEntry, "id");
            if (!StringUtils.hasText(targetId)) {
                continue;
            }
            if (hasWebhookTarget(webhookTargets, targetId)) {
                throw new ResponseStatusException(
                    CONFLICT,
                    "Marketplace webhook target conflicts with an existing deployment target: " + targetId
                );
            }
            ObjectNode compiledTarget = objectMapper.createObjectNode();
            compiledTarget.put("id", targetId);
            String urlSecretRef = configuredText(targetEntry, installSecretRefs, "urlSecretRef", "urlSecretRefField");
            if (!hasText(urlSecretRef)) {
                throw new ResponseStatusException(CONFLICT, "Webhook target '" + targetId + "' is missing urlSecretRef.");
            }
            compiledTarget.put("urlSecretRef", urlSecretRef);
            String signingSecretRef = configuredText(targetEntry, installSecretRefs, "signingSecretRef", "signingSecretRefField");
            if (hasText(signingSecretRef)) {
                compiledTarget.put("signingSecretRef", signingSecretRef);
            }
            Integer timeoutMs = configuredInt(targetEntry, installConfig, "timeoutMs", "timeoutMsField");
            if (timeoutMs != null) {
                compiledTarget.put("timeoutMs", timeoutMs);
            }
            Integer maxAttempts = configuredInt(targetEntry, installConfig, "maxAttempts", "maxAttemptsField");
            if (maxAttempts != null) {
                compiledTarget.put("maxAttempts", maxAttempts);
            }
            applyMarketplaceProvenance(compiledTarget, install, plugin, version);
            webhookTargets.add(compiledTarget);
        }

        JsonNode mcpServerEntries = parsed.manifest().path("contributions").path("mcpServers");
        for (JsonNode serverEntry : iterable(mcpServerEntries)) {
            if (!serverEntry.isObject()) {
                continue;
            }
            String serverRef = text(serverEntry, "serverRef", "id");
            if (!StringUtils.hasText(serverRef)) {
                continue;
            }
            if (hasMcpServer(mcpServers, serverRef)) {
                throw new ResponseStatusException(
                    CONFLICT,
                    "Marketplace MCP server conflicts with an existing deployment MCP server: " + serverRef
                );
            }
            mcpServers.add(compileMcpServerContribution(serverEntry, installConfig, installSecretRefs, install, plugin, version));
        }

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
                if (!replaceGreenfieldShopifyActionConflict(actions, existingActionNames, plugin.getId(), actionName)) {
                    throw new ResponseStatusException(
                        CONFLICT,
                        "Marketplace action conflicts with an existing deployment action: " + actionName
                    );
                }
            }
            actions.add(compileActionContribution(actionEntry, install, plugin, version));
        }

        applyShellContribution(
            shellRoot,
            parsed.manifest().path("contributions").path("shell"),
            install,
            plugin,
            version
        );
    }

    ObjectNode compileActionContribution(JsonNode actionEntry,
                                         DeploymentMarketplacePluginInstallEntity install,
                                         MarketplacePluginEntity plugin,
                                         MarketplacePluginVersionEntity version) {
        String actionName = text(actionEntry, "actionId", "id");
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
        if (StringUtils.hasText(actionEntry.path("adapterType").asText(""))) {
            compiled.put("adapterType", actionEntry.path("adapterType").asText("").trim());
        }
        if (actionEntry.path("execution").isObject()) {
            compiled.set("execution", actionEntry.path("execution").deepCopy());
            if (!StringUtils.hasText(compiled.path("adapterType").asText(""))
                && StringUtils.hasText(actionEntry.path("execution").path("adapterType").asText(""))) {
                compiled.put("adapterType", actionEntry.path("execution").path("adapterType").asText("").trim());
            }
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
        if (actionEntry.path("groundingEligible").isBoolean()) {
            compiled.put("groundingEligible", actionEntry.path("groundingEligible").asBoolean());
        }
        if (actionEntry.path("readActionResolutionEligible").isBoolean()) {
            compiled.put(
                "readActionResolutionEligible",
                actionEntry.path("readActionResolutionEligible").asBoolean()
            );
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
        if (actionEntry.path("postPolicies").isArray()) {
            compiled.set("postPolicies", actionEntry.path("postPolicies").deepCopy());
        }
        if (actionEntry.path("llmFacts").isObject()) {
            compiled.set("llmFacts", actionEntry.path("llmFacts").deepCopy());
        }
        applyMarketplaceProvenance(compiled, install, plugin, version);
        return compiled;
    }

    ObjectNode compileMcpServerContribution(JsonNode serverEntry,
                                            JsonNode installConfig,
                                            JsonNode installSecretRefs,
                                            DeploymentMarketplacePluginInstallEntity install,
                                            MarketplacePluginEntity plugin,
                                            MarketplacePluginVersionEntity version) {
        ObjectNode compiled = objectMapper.createObjectNode();
        compiled.put("serverRef", text(serverEntry, "serverRef", "id"));
        String transport = text(serverEntry, "transport", "transportType");
        if (hasText(transport)) {
            compiled.put("transport", transport.trim());
        }
        String endpointUrl = configuredText(serverEntry, installConfig, "endpointUrl", "endpointUrlField", "url", "urlField");
        if (!hasText(endpointUrl)) {
            endpointUrl = configuredText(serverEntry, installConfig, "discoveryUrl", "discoveryUrlField");
        }
        if (hasText(endpointUrl)) {
            compiled.put("endpointUrl", endpointUrl);
        }
        String endpointUrlTemplate = text(serverEntry, "endpointUrlTemplate", "urlTemplate", "discoveryUrlTemplate");
        if (hasText(endpointUrlTemplate)) {
            compiled.put("endpointUrlTemplate", endpointUrlTemplate);
        }
        copyIfText(serverEntry, compiled, "authProfileRef", "authProfileRef");
        if (serverEntry.path("allowedTools").isArray()) {
            compiled.set("allowedTools", serverEntry.path("allowedTools").deepCopy());
        }
        if (serverEntry.path("verification").isObject()) {
            compiled.set("verification", serverEntry.path("verification").deepCopy());
        }
        if (serverEntry.path("auth").isObject()) {
            compiled.set("auth", compileMcpServerAuth(serverEntry.path("auth"), installSecretRefs));
        }
        applyMarketplaceProvenance(compiled, install, plugin, version);
        return compiled;
    }

    private ObjectNode compileMcpServerAuth(JsonNode authEntry, JsonNode installSecretRefs) {
        ObjectNode auth = objectMapper.createObjectNode();
        copyIfText(authEntry, auth, "mode", "mode", "authMode");
        copyIfText(authEntry, auth, "headerName", "headerName");
        copyIfText(authEntry, auth, "tokenUrl", "tokenUrl");
        copyIfText(authEntry, auth, "audience", "audience");
        copyIfText(authEntry, auth, "clientId", "clientId");
        String secretRef = configuredText(authEntry, installSecretRefs, "secretRef", "secretRefField");
        if (!hasText(secretRef)) {
            secretRef = configuredText(authEntry, installSecretRefs, "valueSecretRef", "valueSecretRefField");
        }
        if (hasText(secretRef)) {
            auth.put("secretRef", secretRef);
        }
        String tokenSecretRef = configuredText(authEntry, installSecretRefs, "tokenSecretRef", "tokenSecretRefField");
        if (hasText(tokenSecretRef)) {
            auth.put("tokenSecretRef", tokenSecretRef);
        }
        String clientSecretRef = configuredText(authEntry, installSecretRefs, "clientSecretRef", "clientSecretRefField");
        if (hasText(clientSecretRef)) {
            auth.put("clientSecretRef", clientSecretRef);
        }
        if (authEntry.path("scopes").isArray()) {
            auth.set("scopes", authEntry.path("scopes").deepCopy());
        }
        return auth;
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
        ArrayNode webhookTargets = ensureArray(actionsRoot, "webhookTargets");
        removeMarketplaceManagedEntries(webhookTargets);
        ArrayNode mcpServers = ensureArray(actionsRoot, "mcpServers");
        removeMarketplaceManagedEntries(mcpServers);
    }

    boolean stripGreenfieldShopifyLegacyActions(ObjectNode actionsRoot,
                                                List<DeploymentMarketplacePluginInstallEntity> installs) {
        if (!hasEnabledGreenfieldShopifyMcpActionPlugin(installs)) {
            return false;
        }
        ArrayNode actions = ensureArray(actionsRoot, "actions");
        boolean changed = false;
        for (int index = actions.size() - 1; index >= 0; index--) {
            JsonNode action = actions.get(index);
            String actionName = action == null ? "" : action.path("name").asText("").trim();
            if (GREENFIELD_SHOPIFY_ACTION_IDS.contains(actionName)) {
                actions.remove(index);
                changed = true;
            }
        }
        return changed;
    }

    boolean replaceGreenfieldShopifyActionConflict(ArrayNode actions,
                                                   Set<String> existingActionNames,
                                                   String pluginId,
                                                   String actionName) {
        if (!GREENFIELD_SHOPIFY_MCP_ACTION_PLUGIN_IDS.contains(pluginId)
            || !GREENFIELD_SHOPIFY_ACTION_IDS.contains(actionName)) {
            return false;
        }
        removeActionsByName(actions, actionName);
        existingActionNames.remove(actionName);
        existingActionNames.add(actionName);
        return true;
    }

    private void removeActionsByName(ArrayNode actions, String actionName) {
        if (actions == null || !hasText(actionName)) {
            return;
        }
        for (int index = actions.size() - 1; index >= 0; index--) {
            JsonNode action = actions.get(index);
            if (action != null && actionName.equals(action.path("name").asText("").trim())) {
                actions.remove(index);
            }
        }
    }

    private boolean hasEnabledGreenfieldShopifyMcpActionPlugin(List<DeploymentMarketplacePluginInstallEntity> installs) {
        if (installs == null || installs.isEmpty()) {
            return false;
        }
        for (DeploymentMarketplacePluginInstallEntity install : installs) {
            if (install != null
                && isEnabledForCompilation(install.getStatus())
                && GREENFIELD_SHOPIFY_MCP_ACTION_PLUGIN_IDS.contains(install.getPluginId())) {
                return true;
            }
        }
        return false;
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

    static boolean pruneRoutesWithoutActions(ObjectNode routingRoot, Set<String> actionNames) {
        if (routingRoot == null || !(routingRoot.path("actions") instanceof ObjectNode routes)) {
            return false;
        }
        Set<String> validActionNames = actionNames == null ? Set.of() : actionNames;
        List<String> staleRoutes = new ArrayList<>();
        routes.fieldNames().forEachRemaining(routeName -> {
            if (!validActionNames.contains(routeName)) {
                staleRoutes.add(routeName);
            }
        });
        staleRoutes.forEach(routes::remove);
        return !staleRoutes.isEmpty();
    }

    private void stripMarketplaceManagedInference(ObjectNode providerRoot) {
        JsonNode metadataNode = providerRoot.path(MARKETPLACE_INFERENCE_FIELD);
        if (metadataNode.isObject()) {
            for (JsonNode field : iterable(metadataNode.path("managedFields"))) {
                String fieldName = field.asText("").trim();
                if (hasText(fieldName)) {
                    providerRoot.remove(fieldName);
                }
            }
        }
        providerRoot.remove(MARKETPLACE_INFERENCE_FIELD);
    }

    private void removeMarketplaceManagedEntries(ArrayNode array) {
        for (int index = array.size() - 1; index >= 0; index--) {
            if (isMarketplaceManaged(array.get(index))) {
                array.remove(index);
            }
        }
    }

    private boolean hasWebhookTarget(ArrayNode targets, String targetId) {
        if (targets == null || !hasText(targetId)) {
            return false;
        }
        for (JsonNode target : targets) {
            if (target != null && target.isObject() && targetId.trim().equalsIgnoreCase(target.path("id").asText(""))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasMcpServer(ArrayNode servers, String serverRef) {
        if (servers == null || !hasText(serverRef)) {
            return false;
        }
        for (JsonNode server : servers) {
            if (server != null
                && server.isObject()
                && serverRef.trim().equalsIgnoreCase(server.path("serverRef").asText(""))) {
                return true;
            }
        }
        return false;
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

    private void synchronizeEntityVectorDimensions(ObjectNode entityRoot, ObjectNode providerRoot) {
        if (!providerRoot.path(MARKETPLACE_INFERENCE_FIELD).path(MARKETPLACE_MANAGED_FIELD).asBoolean(false)) {
            return;
        }
        int dimensions = resolvedManagedEmbeddingDimensions(providerRoot);
        if (dimensions <= 0) {
            return;
        }
        ensureObjectNode(entityRoot, "ai-config").put("vector-dimensions", dimensions);
    }

    private int resolvedManagedEmbeddingDimensions(JsonNode providerRoot) {
        String embeddingProvider = ManagedDeploymentProfileCatalog.resolveEmbeddingProvider(providerRoot);
        String vectorStrategy = ManagedDeploymentProfileCatalog.resolveVectorStrategy(providerRoot);
        if (ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_OPENAI.equals(embeddingProvider)) {
            int configured = ManagedDeploymentProfileCatalog.configuredOpenAiEmbeddingDimensions(providerRoot);
            return configured > 0
                ? ManagedDeploymentProfileCatalog.clampVectorDimensions(configured, vectorStrategy)
                : ManagedDeploymentProfileCatalog.defaultVectorDimensions(embeddingProvider, vectorStrategy);
        }
        return 0;
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

    private ObjectNode normalizeProviderRoot(JsonNode candidate) {
        return ensureObject(candidate);
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

    private String configuredText(JsonNode section,
                                  JsonNode installValues,
                                  String directField,
                                  String fieldReferenceField) {
        String fieldRef = text(section, fieldReferenceField);
        if (hasText(fieldRef) && installValues != null) {
            String configured = installValues.path(fieldRef).asText("").trim();
            if (hasText(configured)) {
                return configured;
            }
        }
        String direct = text(section, directField);
        return hasText(direct) ? direct : null;
    }

    private String configuredText(JsonNode section,
                                  JsonNode installValues,
                                  String directFieldOne,
                                  String fieldReferenceFieldOne,
                                  String directFieldTwo,
                                  String fieldReferenceFieldTwo) {
        String value = configuredText(section, installValues, directFieldOne, fieldReferenceFieldOne);
        return hasText(value) ? value : configuredText(section, installValues, directFieldTwo, fieldReferenceFieldTwo);
    }

    private Integer configuredInt(JsonNode section,
                                  JsonNode installValues,
                                  String directField,
                                  String fieldReferenceField) {
        String fieldRef = text(section, fieldReferenceField);
        if (hasText(fieldRef) && installValues != null && installValues.path(fieldRef).isNumber()) {
            return installValues.path(fieldRef).asInt();
        }
        if (section.path(directField).isInt() || section.path(directField).isLong()) {
            return section.path(directField).asInt();
        }
        return null;
    }

    private Integer configuredClampedOpenAiEmbeddingDimensions(ObjectNode providerRoot,
                                                               JsonNode section,
                                                               JsonNode installValues) {
        Integer configuredDimensions = configuredInt(section, installValues, "dimensions", "dimensionsField");
        if (configuredDimensions == null || configuredDimensions <= 0) {
            return configuredDimensions;
        }
        return ManagedDeploymentProfileCatalog.clampVectorDimensions(
            configuredDimensions,
            ManagedDeploymentProfileCatalog.resolveVectorStrategy(providerRoot)
        );
    }

    private Double configuredDouble(JsonNode section,
                                    JsonNode installValues,
                                    String directField,
                                    String fieldReferenceField) {
        String fieldRef = text(section, fieldReferenceField);
        if (hasText(fieldRef) && installValues != null && installValues.path(fieldRef).isNumber()) {
            return installValues.path(fieldRef).asDouble();
        }
        if (section.path(directField).isNumber()) {
            return section.path(directField).asDouble();
        }
        return null;
    }

    private Boolean configuredBoolean(JsonNode section,
                                      JsonNode installValues,
                                      String directField,
                                      String fieldReferenceField) {
        String fieldRef = text(section, fieldReferenceField);
        if (hasText(fieldRef) && installValues != null && installValues.path(fieldRef).isBoolean()) {
            return installValues.path(fieldRef).asBoolean();
        }
        if (section.path(directField).isBoolean()) {
            return section.path(directField).asBoolean();
        }
        return null;
    }

    private void seedGlobalLlmProviderDefaults(ObjectNode providerRoot,
                                               String provider,
                                               String model,
                                               String baseUrl,
                                               String deploymentName,
                                               String apiVersion,
                                               Set<String> managedFields) {
        switch (provider) {
            case ManagedDeploymentProfileCatalog.LLM_PROVIDER_OPENAI -> {
                putManaged(providerRoot, "openaiBaseUrl", baseUrl, managedFields);
                putManaged(providerRoot, "openaiModel", model, managedFields);
            }
            case ManagedDeploymentProfileCatalog.LLM_PROVIDER_AZURE -> {
                putManaged(providerRoot, "azureEndpoint", baseUrl, managedFields);
                putManaged(providerRoot, "azureDeploymentName", deploymentName, managedFields);
                putManaged(providerRoot, "azureApiVersion", apiVersion, managedFields);
            }
            case ManagedDeploymentProfileCatalog.LLM_PROVIDER_ANTHROPIC -> {
                putManaged(providerRoot, "anthropicBaseUrl", baseUrl, managedFields);
                putManaged(providerRoot, "anthropicModel", model, managedFields);
            }
            case ManagedDeploymentProfileCatalog.LLM_PROVIDER_COHERE -> {
                putManaged(providerRoot, "cohereBaseUrl", baseUrl, managedFields);
                putManaged(providerRoot, "cohereModel", model, managedFields);
            }
            case ManagedDeploymentProfileCatalog.LLM_PROVIDER_GEMINI -> {
                putManaged(providerRoot, "geminiBaseUrl", baseUrl, managedFields);
                putManaged(providerRoot, "geminiModel", model, managedFields);
            }
            default -> {
            }
        }
    }

    private void putManaged(ObjectNode root, String fieldName, String value, Set<String> managedFields) {
        if (managedFields != null) {
            managedFields.add(fieldName);
        }
        if (hasText(value)) {
            root.put(fieldName, value.trim());
        } else {
            root.remove(fieldName);
        }
    }

    private void putManaged(ObjectNode root, String fieldName, Integer value, Set<String> managedFields) {
        if (managedFields != null) {
            managedFields.add(fieldName);
        }
        if (value != null) {
            root.put(fieldName, value);
        } else {
            root.remove(fieldName);
        }
    }

    private void putManaged(ObjectNode root, String fieldName, Double value, Set<String> managedFields) {
        if (managedFields != null) {
            managedFields.add(fieldName);
        }
        if (value != null) {
            root.put(fieldName, value);
        } else {
            root.remove(fieldName);
        }
    }

    private void putManaged(ObjectNode root, String fieldName, Boolean value, Set<String> managedFields) {
        if (managedFields != null) {
            managedFields.add(fieldName);
        }
        if (value != null) {
            root.put(fieldName, value);
        } else {
            root.remove(fieldName);
        }
    }

    private ArrayNode toStringArray(Iterable<String> values) {
        ArrayNode array = objectMapper.createArrayNode();
        for (String value : values) {
            if (hasText(value)) {
                array.add(value.trim());
            }
        }
        return array;
    }

    private List<String> readStringList(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode candidate = node.path(fieldName);
            if (candidate.isArray()) {
                List<String> values = new ArrayList<>();
                for (JsonNode item : candidate) {
                    String value = item.asText("").trim();
                    if (hasText(value)) {
                        values.add(value);
                    }
                }
                return List.copyOf(values);
            }
        }
        String text = text(node, fieldNames);
        return hasText(text) ? List.of(text) : List.of();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String firstText(JsonNode node, String... fieldNames) {
        return text(node, fieldNames);
    }

    private String lower(String value) {
        return hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

    private String capitalize(String value) {
        if (!hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        return Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1);
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

    private String dedicatedEmbeddingEndpointProfileRef(DeploymentEntity deployment) {
        return "dep-" + deployment.getId() + "-embedding-worker";
    }

    private String pluginScopedDedicatedEmbeddingServiceRef(DeploymentEntity deployment,
                                                            DeploymentMarketplacePluginInstallEntity install) {
        return "dedicated-embedding-" + deployment.getId() + "-" + install.getId();
    }

    private String summarizeIssues(List<DraftValidationIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return "unknown validation failure";
        }
        String summary = issues.stream()
            .filter(Objects::nonNull)
            .filter(issue -> "ERROR".equalsIgnoreCase(issue.severity()))
            .map(issue -> issue.code() + " at " + issue.path())
            .limit(5)
            .reduce((left, right) -> left + "; " + right)
            .orElse("unknown validation failure");
        if (requiresProviderBaselineGuidance(issues)) {
            return summary
                + ". This deployment draft is missing provider configuration. Open the deployment Providers screen and set llmProvider and embeddingProvider before installing this plugin, or install an inference-profile plugin first.";
        }
        return summary;
    }

    private boolean requiresProviderBaselineGuidance(List<DraftValidationIssue> issues) {
        boolean missingLlmProvider = false;
        boolean missingEmbeddingProvider = false;
        for (DraftValidationIssue issue : issues) {
            if (issue == null || !"ERROR".equalsIgnoreCase(issue.severity())) {
                continue;
            }
            if (!"REQUIRED_VALUE_MISSING".equalsIgnoreCase(issue.code())) {
                continue;
            }
            if ("$.llmProvider".equals(issue.path())) {
                missingLlmProvider = true;
            }
            if ("$.embeddingProvider".equals(issue.path())) {
                missingEmbeddingProvider = true;
            }
        }
        return missingLlmProvider || missingEmbeddingProvider;
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
