package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.ShopifyCompanionBootstrapProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.marketplace.model.CreateDeploymentMarketplaceInstallRequest;
import com.ai.fabric.platform.backend.marketplace.model.DeploymentMarketplaceInstallSummary;
import com.ai.fabric.platform.backend.marketplace.model.UpdateDeploymentMarketplaceInstallRequest;
import com.ai.fabric.platform.backend.marketplace.service.DeploymentMarketplaceInstallService;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceCatalogService;
import com.ai.fabric.platform.backend.productservice.entity.PlatformManagedProductServiceEntity;
import com.ai.fabric.platform.backend.productservice.repository.PlatformManagedProductServiceRepository;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreVectorizationAutomationSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreVectorizationEventSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreVectorizationIndexedFieldSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreVectorizationPolicySummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreVectorizationRunSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreVectorizationSelectedEntitiesRequest;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreVectorizationSummary;
import com.ai.fabric.platform.backend.shopify.model.UpdateShopifyStoreVectorizationPolicyRequest;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.ai.fabric.platform.backend.vectorization.model.CreateVectorizationRunRequest;
import com.ai.fabric.platform.backend.vectorization.model.UpsertVectorizationPlanRequest;
import com.ai.fabric.platform.backend.vectorization.model.UpsertVectorizationSourceConnectionRequest;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationOverviewSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunSummary;
import com.ai.fabric.platform.backend.vectorization.service.VectorizationService;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class ShopifyStoreVectorizationService {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final String BRIDGE_ADMIN_AUTH_HEADER_NAME = "X-BRIDGE-API-KEY";

    private final ShopifyStoreConnectionRepository repository;
    private final DeploymentRepository deploymentRepository;
    private final DeploymentReleaseRepository deploymentReleaseRepository;
    private final DeploymentService deploymentService;
    private final DeploymentMarketplaceInstallService deploymentMarketplaceInstallService;
    private final MarketplaceCatalogService marketplaceCatalogService;
    private final PlatformManagedProductServiceRepository productServiceRepository;
    private final VectorizationService vectorizationService;
    private final ShopifyCompanionBootstrapProperties properties;
    private final ShopifyStoreVectorizationPolicyService policyService;
    private final ShopifyStoreVectorizationFieldCatalogService fieldCatalogService;
    private final ShopifyStoreVectorizationEventService eventService;
    private final ShopifyBridgeAdminClient bridgeAdminClient;
    private final PlatformAuditService platformAuditService;

    public ShopifyStoreVectorizationService(ShopifyStoreConnectionRepository repository,
                                            DeploymentRepository deploymentRepository,
                                            DeploymentReleaseRepository deploymentReleaseRepository,
                                            DeploymentService deploymentService,
                                            DeploymentMarketplaceInstallService deploymentMarketplaceInstallService,
                                            MarketplaceCatalogService marketplaceCatalogService,
                                            PlatformManagedProductServiceRepository productServiceRepository,
                                            VectorizationService vectorizationService,
                                            ShopifyCompanionBootstrapProperties properties,
                                            ShopifyStoreVectorizationPolicyService policyService,
                                            ShopifyStoreVectorizationFieldCatalogService fieldCatalogService,
                                            ShopifyStoreVectorizationEventService eventService,
                                            ShopifyBridgeAdminClient bridgeAdminClient,
                                            PlatformAuditService platformAuditService) {
        this.repository = repository;
        this.deploymentRepository = deploymentRepository;
        this.deploymentReleaseRepository = deploymentReleaseRepository;
        this.deploymentService = deploymentService;
        this.deploymentMarketplaceInstallService = deploymentMarketplaceInstallService;
        this.marketplaceCatalogService = marketplaceCatalogService;
        this.productServiceRepository = productServiceRepository;
        this.vectorizationService = vectorizationService;
        this.properties = properties;
        this.policyService = policyService;
        this.fieldCatalogService = fieldCatalogService;
        this.eventService = eventService;
        this.bridgeAdminClient = bridgeAdminClient;
        this.platformAuditService = platformAuditService;
    }

    @Transactional(readOnly = true)
    public ShopifyStoreVectorizationSummary getSummary(String shopDomain) {
        ShopifyStoreConnectionEntity store = requireStore(shopDomain);
        return buildSummary(store);
    }

    @Transactional
    public ShopifyStoreVectorizationSummary reconcile(String shopDomain) {
        return reconcile(shopDomain, false);
    }

    @Transactional
    public ShopifyStoreVectorizationSummary reconcileForTrustedCaller(String shopDomain) {
        return reconcile(shopDomain, true);
    }

    private ShopifyStoreVectorizationSummary reconcile(String shopDomain, boolean trustedCaller) {
        ShopifyStoreConnectionEntity store = requireStore(shopDomain);
        DeploymentEntity deployment = requireDeployment(store);
        LinkedHashSet<String> desiredPluginIds = ShopifyCompanionPluginSelection.desiredManagedPluginIds(properties, store);
        Map<String, DeploymentMarketplaceInstallSummary> installsByPluginId = installsByPluginId(deployment);

        LinkedHashSet<String> managedDataPluginIds = ShopifyCompanionPluginSelection.managedDataPluginIds();
        for (String pluginId : desiredPluginIds) {
            DeploymentMarketplaceInstallSummary current = installsByPluginId.get(normalizePluginId(pluginId));
            String latestVersion = marketplaceCatalogService.resolveLatestPublishedVersionLabel(pluginId);
            if (current == null) {
                CreateDeploymentMarketplaceInstallRequest request =
                    new CreateDeploymentMarketplaceInstallRequest(pluginId, latestVersion, JSON.objectNode(), JSON.objectNode());
                if (trustedCaller) {
                    deploymentMarketplaceInstallService.createInstallForTrustedCaller(deployment, request);
                } else {
                    deploymentMarketplaceInstallService.createInstall(deployment.getId(), request);
                }
                continue;
            }
            boolean versionDrift = latestVersion != null && !latestVersion.isBlank() && !latestVersion.equals(current.pluginVersion());
            boolean disabled = !"ENABLED".equalsIgnoreCase(current.status());
            if (versionDrift || disabled) {
                UpdateDeploymentMarketplaceInstallRequest request = new UpdateDeploymentMarketplaceInstallRequest(
                    versionDrift ? latestVersion : null,
                    "ENABLED",
                    current.config(),
                    current.secretRefs()
                );
                if (trustedCaller) {
                    deploymentMarketplaceInstallService.updateInstallForTrustedCaller(deployment, current.id(), request);
                } else {
                    deploymentMarketplaceInstallService.updateInstall(deployment.getId(), current.id(), request);
                }
            }
        }

        for (DeploymentMarketplaceInstallSummary install : installsByPluginId.values()) {
            String pluginId = normalizePluginId(install.pluginId());
            if (!managedDataPluginIds.contains(pluginId)) {
                continue;
            }
            if (desiredPluginIds.contains(pluginId)) {
                continue;
            }
            if (trustedCaller) {
                deploymentMarketplaceInstallService.deleteInstallForTrustedCaller(deployment, install.id());
            } else {
                deploymentMarketplaceInstallService.deleteInstall(deployment.getId(), install.id());
            }
        }

        VectorizationOverviewSummary overview = reconcileVectorizationOverview(store, deployment);
        ensureDeploymentRunnerSupport(deployment, overview, trustedCaller);
        platformAuditService.record(
            "SHOPIFY_STORE_VECTORIZATION_RECONCILED",
            "SHOPIFY_STORE_CONNECTION",
            store.getShopDomain(),
            Map.of(
                "shopDomain", store.getShopDomain(),
                "deploymentId", deployment.getId(),
                "selectedCategories", String.join(",", ShopifyCompanionPluginSelection.selectedCategories(store)),
                "selectedEntityTypes", String.join(",", ShopifyCompanionPluginSelection.selectedEntityTypes(store))
            )
        );
        return buildSummary(store);
    }

    @Transactional
    public ShopifyStoreVectorizationSummary vectorizeNow(String shopDomain) {
        return indexAllEnabledData(shopDomain);
    }

    @Transactional
    public ShopifyStoreVectorizationSummary indexAllEnabledData(String shopDomain) {
        ShopifyStoreConnectionEntity store = requireStore(shopDomain);
        return runManualIndexAction(
            store,
            ShopifyCompanionPluginSelection.selectedEntityTypes(store),
            false,
            "SHOPIFY_ADMIN_INDEX_ALL",
            "Shopify store admin requested indexing for all enabled data."
        );
    }

    @Transactional
    public ShopifyStoreVectorizationSummary reindexAllEnabledData(String shopDomain) {
        ShopifyStoreConnectionEntity store = requireStore(shopDomain);
        return runManualIndexAction(
            store,
            ShopifyCompanionPluginSelection.selectedEntityTypes(store),
            true,
            "SHOPIFY_ADMIN_REINDEX_ALL",
            "Shopify store admin requested reindex for all enabled data."
        );
    }

    @Transactional
    public ShopifyStoreVectorizationSummary reindexSelectedEntityTypes(String shopDomain,
                                                                       ShopifyStoreVectorizationSelectedEntitiesRequest request) {
        ShopifyStoreConnectionEntity store = requireStore(shopDomain);
        List<String> entityTypes = normalizeRequestedEntityTypes(
            request == null ? null : request.entityTypes(),
            ShopifyCompanionPluginSelection.selectedEntityTypes(store)
        );
        return runManualIndexAction(
            store,
            entityTypes,
            true,
            "SHOPIFY_ADMIN_REINDEX_SELECTED",
            "Shopify store admin requested reindex for selected entity families."
        );
    }

    @Transactional
    public ShopifyStoreVectorizationSummary updatePolicy(String shopDomain,
                                                         UpdateShopifyStoreVectorizationPolicyRequest request) {
        ShopifyStoreConnectionEntity store = requireStore(shopDomain);
        VectorizationOverviewSummary overview = resolveOverview(store);
        policyService.update(store, overview, request);
        platformAuditService.record(
            "SHOPIFY_STORE_VECTORIZATION_POLICY_UPDATED_FROM_ADMIN",
            "SHOPIFY_STORE_CONNECTION",
            store.getShopDomain(),
            Map.of(
                "shopDomain", store.getShopDomain(),
                "deploymentId", blankToEmpty(store.getDeploymentId()),
                "requestedBy", PlatformSecurityContext.actorIdOrSystem()
            )
        );
        return buildSummary(store);
    }

    @Transactional(readOnly = true)
    public List<ShopifyStoreVectorizationEventSummary> recentEvents(String shopDomain, int limit) {
        ShopifyStoreConnectionEntity store = requireStore(shopDomain);
        return eventService.listRecent(store.getShopDomain(), limit);
    }

    @Transactional
    public ShopifyStoreVectorizationSummary replayEvent(String shopDomain, String eventId) {
        ShopifyStoreConnectionEntity store = requireStore(shopDomain);
        eventService.replay(store.getShopDomain(), eventId);
        platformAuditService.record(
            "SHOPIFY_STORE_VECTORIZATION_EVENT_REPLAYED",
            "SHOPIFY_STORE_CONNECTION",
            store.getShopDomain(),
            Map.of(
                "shopDomain", store.getShopDomain(),
                "eventId", eventId,
                "requestedBy", PlatformSecurityContext.actorIdOrSystem()
            )
        );
        return buildSummary(store);
    }

    @Transactional
    public ShopifyStoreVectorizationSummary retryLastFailedAutoRun(String shopDomain) {
        ShopifyStoreConnectionEntity store = requireStore(shopDomain);
        ShopifyStoreVectorizationSummary summary = buildSummary(store);
        ShopifyStoreVectorizationAutomationSummary automation = summary.automation();
        if (automation == null || automation.lastAutoRunId() == null || automation.lastFailedAutoIndexAt() == null) {
            throw new ResponseStatusException(CONFLICT, "No failed Shopify live auto indexing run is available to retry.");
        }
        int replayed = eventService.replayRunEvents(store.getShopDomain(), automation.lastAutoRunId());
        if (replayed <= 0) {
            throw new ResponseStatusException(CONFLICT, "No Shopify live update events were available to replay for the last failed auto indexing run.");
        }
        platformAuditService.record(
            "SHOPIFY_STORE_VECTORIZATION_AUTO_RUN_REQUEUED",
            "SHOPIFY_STORE_CONNECTION",
            store.getShopDomain(),
            Map.of(
                "shopDomain", store.getShopDomain(),
                "deploymentId", blankToEmpty(store.getDeploymentId()),
                "runId", automation.lastAutoRunId(),
                "replayedEvents", Integer.toString(replayed),
                "requestedBy", PlatformSecurityContext.actorIdOrSystem()
            )
        );
        return buildSummary(store);
    }

    private VectorizationOverviewSummary reconcileVectorizationOverview(ShopifyStoreConnectionEntity store, DeploymentEntity deployment) {
        PlatformManagedProductServiceEntity productService = requireProductService(store);
        UpsertVectorizationSourceConnectionRequest connectionRequest = buildConnectionRequest(store, productService);
        var connection = vectorizationService.upsertSourceConnection(deployment.getId(), connectionRequest);
        vectorizationService.upsertPlan(
            deployment.getId(),
            new UpsertVectorizationPlanRequest(
                "Shopify store vectorization",
                "PLATFORM_MANAGED_AUTO",
                connection.id(),
                buildEntityScope(store),
                buildMappingConfig(store),
                buildExecutionConfig(store)
            )
        );
        return vectorizationService.getOverviewForTrustedCaller(deployment);
    }

    private UpsertVectorizationSourceConnectionRequest buildConnectionRequest(ShopifyStoreConnectionEntity store,
                                                                             PlatformManagedProductServiceEntity productService) {
        String bridgeBaseUrl = trimRequired(productService.getBaseUrl(), "Shopify Bridge base URL is not configured for vectorization.");
        String bridgeSharedSecretRef = trimRequired(
            productService.getSecretName(),
            "Shopify Bridge admin API secret is not configured for vectorization."
        );
        ObjectNode connectionConfig = JSON.objectNode();
        connectionConfig.put("baseUrl", trimTrailingSlash(bridgeBaseUrl));
        connectionConfig.put("authHeaderName", BRIDGE_ADMIN_AUTH_HEADER_NAME);
        connectionConfig.set("datasets", buildDatasetConfig(store));

        ObjectNode secretReferences = JSON.objectNode();
        ObjectNode platformSecretRefs = secretReferences.putObject("platformSecretRefs");
        platformSecretRefs.put("apiKey", bridgeSharedSecretRef);

        ObjectNode discoverySummary = JSON.objectNode();
        discoverySummary.put("selectedCategoryCount", ShopifyCompanionPluginSelection.selectedCategories(store).size());
        discoverySummary.put("selectedEntityTypeCount", ShopifyCompanionPluginSelection.selectedEntityTypes(store).size());
        discoverySummary.set("selectedCategories", jsonArray(ShopifyCompanionPluginSelection.selectedCategories(store)));
        discoverySummary.set("selectedEntityTypes", jsonArray(ShopifyCompanionPluginSelection.selectedEntityTypes(store)));

        return new UpsertVectorizationSourceConnectionRequest(
            "Shopify store " + store.getShopDomain(),
            "REST_API",
            "API_KEY",
            connectionConfig,
            secretReferences,
            discoverySummary
        );
    }

    private ObjectNode buildDatasetConfig(ShopifyStoreConnectionEntity store) {
        ObjectNode datasets = JSON.objectNode();

        ObjectNode product = datasets.putObject("product");
        product.put("path", "/api/admin/stores/" + store.getShopDomain() + "/vectorization-source/product");
        product.put("paginationMode", "CURSOR");
        product.put("cursorParam", "cursor");
        product.put("pageSizeParam", "limit");
        product.put("pageSize", 50);
        product.put("itemsPath", "items");
        product.put("nextCursorPath", "nextCursor");
        product.put("hasMorePath", "hasMore");
        product.put("countPath", "totalCount");

        ObjectNode supportPolicy = datasets.putObject("support-policy");
        supportPolicy.put("path", "/api/admin/stores/" + store.getShopDomain() + "/vectorization-source/support-policy");
        supportPolicy.put("paginationMode", "CURSOR");
        supportPolicy.put("cursorParam", "cursor");
        supportPolicy.put("pageSizeParam", "limit");
        supportPolicy.put("pageSize", 50);
        supportPolicy.put("itemsPath", "items");
        supportPolicy.put("nextCursorPath", "nextCursor");
        supportPolicy.put("hasMorePath", "hasMore");
        supportPolicy.put("countPath", "totalCount");

        return datasets;
    }

    private ObjectNode buildMappingConfig(ShopifyStoreConnectionEntity store) {
        ObjectNode mapping = JSON.objectNode();
        ObjectNode sourceCategories = mapping.putObject("sourceCategories");
        sourceCategories.put("productsEnabled", store.isProductsEnabled());
        sourceCategories.put("collectionsEnabled", store.isCollectionsEnabled());
        sourceCategories.put("pagesEnabled", store.isPagesEnabled());
        sourceCategories.put("policiesEnabled", store.isPoliciesEnabled());
        sourceCategories.put("articlesEnabled", store.isArticlesEnabled());
        sourceCategories.put("metaobjectsEnabled", store.isMetaobjectsEnabled());
        ObjectNode entityMappings = mapping.putObject("entityMappings");

        ObjectNode productMapping = entityMappings.putObject("product");
        productMapping.put("dataset", "product");
        productMapping.put("recordIdField", "id");
        productMapping.put("recordVersionField", "updatedAt");
        ObjectNode productFields = productMapping.putObject("entityFieldMappings");
        productFields.put("name", "title");
        productFields.put("description", "content");
        productFields.put("category", "sourceCategory");
        ObjectNode productMetadata = productMapping.putObject("metadataFieldMappings");
        productMetadata.put("sourceCategory", "sourceCategory");
        productMetadata.put("documentType", "documentType");
        productMetadata.put("storefrontUrl", "storefrontUrl");
        productMetadata.put("handle", "handle");
        productMetadata.put("vendor", "vendor");
        productMetadata.put("productType", "productType");
        productMetadata.put("updatedAt", "updatedAt");

        ObjectNode supportPolicyMapping = entityMappings.putObject("support-policy");
        supportPolicyMapping.put("dataset", "support-policy");
        supportPolicyMapping.put("recordIdField", "id");
        supportPolicyMapping.put("recordVersionField", "updatedAt");
        ObjectNode supportPolicyFields = supportPolicyMapping.putObject("entityFieldMappings");
        supportPolicyFields.put("name", "title");
        supportPolicyFields.put("description", "content");
        supportPolicyFields.put("category", "sourceCategory");
        ObjectNode supportPolicyMetadata = supportPolicyMapping.putObject("metadataFieldMappings");
        supportPolicyMetadata.put("sourceCategory", "sourceCategory");
        supportPolicyMetadata.put("documentType", "documentType");
        supportPolicyMetadata.put("policyType", "policyType");
        supportPolicyMetadata.put("metaobjectType", "metaobjectType");
        supportPolicyMetadata.put("definitionName", "definitionName");
        supportPolicyMetadata.put("storefrontUrl", "storefrontUrl");
        supportPolicyMetadata.put("updatedAt", "updatedAt");

        ObjectNode datasets = mapping.putObject("datasets");
        if (ShopifyCompanionPluginSelection.requiresCatalogData(store)) {
            datasets.put("product", ShopifyCompanionPluginSelection.DATA_CATALOG_PLUGIN_ID + "/shopify-catalog");
        }
        if (ShopifyCompanionPluginSelection.requiresPoliciesData(store)) {
            datasets.put("support-policy", ShopifyCompanionPluginSelection.DATA_POLICIES_PLUGIN_ID + "/shopify-policies");
        }
        return mapping;
    }

    private ObjectNode buildExecutionConfig(ShopifyStoreConnectionEntity store) {
        ObjectNode execution = JSON.objectNode();
        execution.put("triggerMode", "SHOPIFY_ADMIN_MANUAL");
        execution.put("selectedCategoryCount", ShopifyCompanionPluginSelection.selectedCategories(store).size());
        execution.put("fullRefreshRequired", true);
        execution.put("batchSize", 50);
        return execution;
    }

    private ShopifyStoreVectorizationSummary buildSummary(ShopifyStoreConnectionEntity store) {
        DeploymentEntity deployment = resolveDeployment(store);
        Map<String, DeploymentMarketplaceInstallSummary> installsByPluginId = deployment == null
            ? Map.of()
            : installsByPluginId(deployment);
        VectorizationOverviewSummary overview = deployment == null ? null : vectorizationService.getOverviewForTrustedCaller(deployment);
        return summarize(store, deployment, installsByPluginId, overview);
    }

    private ShopifyStoreVectorizationSummary summarize(ShopifyStoreConnectionEntity store,
                                                       DeploymentEntity deployment,
                                                       Map<String, DeploymentMarketplaceInstallSummary> installsByPluginId,
                                                       VectorizationOverviewSummary overview) {
        ShopifyStoreVectorizationPolicySummary policySummary = policyService.getSummary(store, overview);
        List<ShopifyStoreVectorizationIndexedFieldSummary> effectiveIndexedFields = fieldCatalogService.effectiveFields(overview);
        ShopifyStoreVectorizationAutomationSummary automation = eventService.summarizeAutomation(store.getShopDomain());
        List<ShopifyStoreVectorizationEventSummary> recentEvents = eventService.listRecent(store.getShopDomain(), 20);

        List<String> selectedCategories = ShopifyCompanionPluginSelection.selectedCategories(store);
        List<String> selectedEntityTypes = ShopifyCompanionPluginSelection.selectedEntityTypes(store);
        List<String> requiredPluginIds = List.copyOf(ShopifyCompanionPluginSelection.desiredManagedPluginIds(properties, store));
        List<String> installedPluginIds = installsByPluginId.values().stream()
            .filter(install -> "ENABLED".equalsIgnoreCase(install.status()))
            .map(DeploymentMarketplaceInstallSummary::pluginId)
            .toList();
        List<String> missingPluginIds = requiredPluginIds.stream()
            .filter(pluginId -> installsByPluginId.values().stream().noneMatch(install ->
                pluginId.equalsIgnoreCase(install.pluginId()) && "ENABLED".equalsIgnoreCase(install.status())))
            .toList();
        List<String> disabledPluginIds = installsByPluginId.values().stream()
            .filter(install -> !"ENABLED".equalsIgnoreCase(install.status()))
            .map(DeploymentMarketplaceInstallSummary::pluginId)
            .filter(pluginId -> requiredPluginIds.stream().anyMatch(pluginId::equalsIgnoreCase))
            .toList();

        boolean connectionConfigured = overview != null && overview.sourceConnection() != null;
        boolean planConfigured = overview != null && overview.plan() != null && overview.plan().activeRevision() != null;
        boolean runnerConfigured = overview != null && overview.runner() != null;
        DeploymentReleaseEntity latestRelease = deployment == null ? null : latestRelease(deployment.getId());
        boolean deploymentApplyInProgress = latestRelease != null && isReleaseInProgress(latestRelease);
        boolean reconciliationRequired = !missingPluginIds.isEmpty()
            || !disabledPluginIds.isEmpty()
            || !connectionConfigured
            || !planConfigured
            || (requiresPlatformManagedRunner(overview) && !runnerConfigured);
        boolean readyToRun = deployment != null
            && !selectedEntityTypes.isEmpty()
            && missingPluginIds.isEmpty()
            && disabledPluginIds.isEmpty()
            && connectionConfigured
            && planConfigured
            && runnerConfigured
            && !deploymentApplyInProgress;

        List<String> blockingReasons = new ArrayList<>();
        if (deployment == null) {
            blockingReasons.add("Shopify store is not bootstrapped to a platform deployment yet.");
        }
        if (selectedEntityTypes.isEmpty()) {
            blockingReasons.add("At least one Shopify source category must be enabled before indexing can run.");
        }
        if (!missingPluginIds.isEmpty()) {
            blockingReasons.add("Deployment is missing required Shopify data plugins: " + String.join(", ", missingPluginIds));
        }
        if (!disabledPluginIds.isEmpty()) {
            blockingReasons.add("Deployment has disabled Shopify data plugins that must be re-enabled: " + String.join(", ", disabledPluginIds));
        }
        if (!connectionConfigured) {
            blockingReasons.add("Deployment vectorization source connection is not configured yet.");
        }
        if (!planConfigured) {
            blockingReasons.add("Deployment vectorization plan is not configured yet.");
        }
        if (planConfigured && requiresPlatformManagedRunner(overview) && !runnerConfigured) {
            if (deploymentApplyInProgress) {
                blockingReasons.add("Deployment is applying vectorization runner support. Wait for the current release to finish before starting indexing.");
            } else {
                blockingReasons.add("Deployment vectorization runner is not provisioned on the current release yet. Reconcile deployment support to apply the current version with runner support.");
            }
        }
        if (deploymentApplyInProgress) {
            blockingReasons.add("Deployment currently has an apply in progress. Wait for it to complete before starting indexing.");
        }

        return new ShopifyStoreVectorizationSummary(
            store.getShopDomain(),
            deployment == null ? null : deployment.getId(),
            deployment != null,
            selectedCategories,
            selectedEntityTypes,
            requiredPluginIds,
            installedPluginIds,
            missingPluginIds,
            disabledPluginIds,
            reconciliationRequired,
            connectionConfigured,
            overview == null || overview.sourceConnection() == null ? null : overview.sourceConnection().id(),
            overview == null || overview.sourceConnection() == null ? null : overview.sourceConnection().status(),
            overview == null || overview.sourceConnection() == null ? null : overview.sourceConnection().adapterType(),
            planConfigured,
            overview == null || overview.plan() == null ? null : overview.plan().id(),
            overview == null || overview.plan() == null ? null : overview.plan().status(),
            runnerConfigured,
            overview == null || overview.runner() == null ? null : overview.runner().registrationId(),
            overview == null || overview.runner() == null ? null : overview.runner().registrationStatus(),
            deploymentApplyInProgress,
            latestRelease == null ? null : latestRelease.getStatus(),
            overview == null || overview.plan() == null ? null : overview.plan().runnerMode(),
            overview == null || overview.plan() == null ? null : overview.plan().syncState(),
            readyToRun,
            List.copyOf(blockingReasons),
            summarizeLastRun(overview),
            policySummary,
            effectiveIndexedFields,
            automation,
            recentEvents
        );
    }

    private ShopifyStoreVectorizationRunSummary summarizeLastRun(VectorizationOverviewSummary overview) {
        if (overview == null || overview.recentRuns() == null || overview.recentRuns().isEmpty()) {
            return null;
        }
        VectorizationRunSummary run = overview.recentRuns().get(0);
        return new ShopifyStoreVectorizationRunSummary(
            run.id(),
            run.reason(),
            run.status(),
            run.requestedStatus(),
            run.entityScope(),
            run.createdAt(),
            run.startedAt(),
            run.completedAt(),
            run.updatedAt()
        );
    }

    private ShopifyStoreVectorizationSummary runManualIndexAction(ShopifyStoreConnectionEntity store,
                                                                 List<String> requestedEntityTypes,
                                                                 boolean reindex,
                                                                 String triggerMode,
                                                                 String note) {
        DeploymentEntity deployment = requireDeployment(store);
        ShopifyStoreVectorizationSummary reconciled = reconcile(store.getShopDomain());
        if (!reconciled.readyToRun()) {
            throw new ResponseStatusException(CONFLICT, String.join(" ", reconciled.blockingReasons()));
        }
        List<String> entityTypes = normalizeRequestedEntityTypes(requestedEntityTypes, reconciled.selectedEntityTypes());
        bridgeAdminClient.runSync(store);
        vectorizationService.createRunForTrustedCaller(
            deployment.getId(),
            new CreateVectorizationRunRequest(
                resolveManualRunReason(reconciled, reindex),
                entityTypes,
                note,
                buildManualExecutionOverrides(store, entityTypes, triggerMode, note)
            ),
            PlatformSecurityContext.actorIdOrSystem()
        );
        platformAuditService.record(
            "SHOPIFY_STORE_VECTORIZATION_MANUAL_RUN_TRIGGERED",
            "SHOPIFY_STORE_CONNECTION",
            store.getShopDomain(),
            Map.of(
                "shopDomain", store.getShopDomain(),
                "deploymentId", deployment.getId(),
                "entityTypes", String.join(",", entityTypes),
                "triggerMode", triggerMode,
                "requestedBy", PlatformSecurityContext.actorIdOrSystem()
            )
        );
        return buildSummary(store);
    }

    private String resolveManualRunReason(ShopifyStoreVectorizationSummary summary, boolean reindex) {
        if (reindex) {
            return "REINDEX";
        }
        return summary.lastRun() == null ? "BOOTSTRAP" : "REFRESH";
    }

    private ObjectNode buildManualExecutionOverrides(ShopifyStoreConnectionEntity store,
                                                     List<String> entityTypes,
                                                     String triggerMode,
                                                     String note) {
        ObjectNode overrides = JSON.objectNode();
        overrides.put("triggerMode", triggerMode);
        overrides.put("shopDomain", store.getShopDomain());
        overrides.put("requestedBy", PlatformSecurityContext.actorIdOrSystem());
        overrides.put("sourceSelectionMode", "SHOPIFY_COMPANION_BOUNDED_ADMIN");
        overrides.set("entityTypes", jsonArray(entityTypes));
        overrides.set("sourceCategories", jsonArray(sourceCategoriesForEntityTypes(store, entityTypes)));
        if (note != null && !note.isBlank()) {
            overrides.put("note", note);
        }
        return overrides;
    }

    private List<String> sourceCategoriesForEntityTypes(ShopifyStoreConnectionEntity store, List<String> entityTypes) {
        LinkedHashSet<String> categories = new LinkedHashSet<>();
        if (entityTypes.contains(ShopifyCompanionPluginSelection.ENTITY_TYPE_PRODUCT)) {
            if (store.isProductsEnabled()) {
                categories.add(ShopifyStoreVectorizationConstants.SOURCE_PRODUCTS);
            }
            if (store.isCollectionsEnabled()) {
                categories.add(ShopifyStoreVectorizationConstants.SOURCE_COLLECTIONS);
            }
        }
        if (entityTypes.contains(ShopifyCompanionPluginSelection.ENTITY_TYPE_SUPPORT_POLICY)) {
            if (store.isPagesEnabled()) {
                categories.add(ShopifyStoreVectorizationConstants.SOURCE_PAGES);
            }
            if (store.isPoliciesEnabled()) {
                categories.add(ShopifyStoreVectorizationConstants.SOURCE_POLICIES);
            }
        }
        return List.copyOf(categories);
    }

    private List<String> normalizeRequestedEntityTypes(List<String> requested, List<String> allowed) {
        LinkedHashSet<String> allowedSet = new LinkedHashSet<>(allowed == null ? List.of() : allowed);
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        for (String value : requested == null ? List.<String>of() : requested) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String normalized = ShopifyStoreVectorizationConstants.normalizeEntityType(value);
            if (!allowedSet.contains(normalized)) {
                throw new ResponseStatusException(CONFLICT, "Shopify vectorization entity type is not enabled for this store: " + normalized);
            }
            selected.add(normalized);
        }
        if (selected.isEmpty()) {
            throw new ResponseStatusException(CONFLICT, "At least one enabled Shopify vectorization entity type is required.");
        }
        return List.copyOf(selected);
    }

    private Map<String, DeploymentMarketplaceInstallSummary> installsByPluginId(DeploymentEntity deployment) {
        LinkedHashMap<String, DeploymentMarketplaceInstallSummary> installs = new LinkedHashMap<>();
        for (DeploymentMarketplaceInstallSummary install : deploymentMarketplaceInstallService.listInstallsForTrustedCaller(deployment)) {
            installs.put(normalizePluginId(install.pluginId()), install);
        }
        return installs;
    }

    private void ensureDeploymentRunnerSupport(DeploymentEntity deployment,
                                               VectorizationOverviewSummary overview,
                                               boolean trustedCaller) {
        if (deployment == null || overview == null || !requiresPlatformManagedRunner(overview)) {
            return;
        }
        if (overview.runner() != null) {
            return;
        }
        if (deployment.getActiveVersionId() == null || deployment.getActiveVersionId().isBlank()) {
            return;
        }
        DeploymentReleaseEntity latestRelease = latestRelease(deployment.getId());
        if (latestRelease != null && isReleaseInProgress(latestRelease)) {
            return;
        }
        if (trustedCaller) {
            deploymentService.applyVersionForTrustedCaller(deployment.getId(), deployment.getActiveVersionId());
        } else {
            deploymentService.applyVersion(deployment.getId(), deployment.getActiveVersionId());
        }
    }

    private boolean requiresPlatformManagedRunner(VectorizationOverviewSummary overview) {
        return overview != null
            && overview.plan() != null
            && "PLATFORM_MANAGED_AUTO".equalsIgnoreCase(overview.plan().runnerMode());
    }

    private DeploymentReleaseEntity latestRelease(String deploymentId) {
        return deploymentReleaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc(deploymentId).orElse(null);
    }

    private boolean isReleaseInProgress(DeploymentReleaseEntity release) {
        if (release == null) {
            return false;
        }
        return switch (normalizeStatus(release.getStatus())) {
            case "APPLY_REQUESTED", "PRE_APPLY_VERIFYING", "PROVISIONING", "VERIFYING" -> true;
            default -> "QUEUED".equalsIgnoreCase(release.getProvisioningStatus())
                || "RUNNING".equalsIgnoreCase(release.getProvisioningStatus())
                || "AWAITING_CONFIRMATION".equalsIgnoreCase(release.getProvisioningStatus())
                || "RUNNING".equalsIgnoreCase(release.getVerificationStatus());
        };
    }

    private String normalizeStatus(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private ShopifyStoreConnectionEntity requireStore(String shopDomain) {
        return repository.findByShopDomainIgnoreCase(normalizeRequired(shopDomain))
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Shopify store connection not found: " + shopDomain));
    }

    private DeploymentEntity requireDeployment(ShopifyStoreConnectionEntity store) {
        DeploymentEntity deployment = resolveDeployment(store);
        if (deployment == null) {
            throw new ResponseStatusException(CONFLICT, "Shopify store must be bootstrapped to a deployment before vectorization can be configured.");
        }
        return deployment;
    }

    private PlatformManagedProductServiceEntity requireProductService(ShopifyStoreConnectionEntity store) {
        String productServiceId = blankToNull(store == null ? null : store.getProductServiceId());
        if (productServiceId == null) {
            throw new ResponseStatusException(CONFLICT, "Shopify store must be bound to a Shopify Bridge service before vectorization can be configured.");
        }
        return productServiceRepository.findById(productServiceId)
            .orElseThrow(() -> new ResponseStatusException(
                CONFLICT,
                "Shopify Bridge service binding could not be resolved for vectorization."
            ));
    }

    private DeploymentEntity resolveDeployment(ShopifyStoreConnectionEntity store) {
        if (store == null || store.getDeploymentId() == null || store.getDeploymentId().isBlank()) {
            return null;
        }
        return deploymentRepository.findById(store.getDeploymentId()).orElse(null);
    }

    private VectorizationOverviewSummary resolveOverview(ShopifyStoreConnectionEntity store) {
        DeploymentEntity deployment = requireDeployment(store);
        return vectorizationService.getOverviewForTrustedCaller(deployment);
    }

    private com.fasterxml.jackson.databind.node.ArrayNode jsonArray(List<String> values) {
        var array = JSON.arrayNode();
        values.forEach(array::add);
        return array;
    }

    private com.fasterxml.jackson.databind.node.ArrayNode buildEntityScope(ShopifyStoreConnectionEntity store) {
        var scope = JSON.arrayNode();
        ShopifyCompanionPluginSelection.selectedEntityTypes(store).forEach(scope::add);
        return scope;
    }

    private String normalizePluginId(String pluginId) {
        return ShopifyCompanionPluginSelection.canonicalizePluginId(pluginId).toLowerCase(Locale.ROOT);
    }

    private String normalizeRequired(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(NOT_FOUND, "shopDomain is required.");
        }
        return value.trim();
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String trimRequired(String value, String message) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new ResponseStatusException(CONFLICT, message);
        }
        return normalized;
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
