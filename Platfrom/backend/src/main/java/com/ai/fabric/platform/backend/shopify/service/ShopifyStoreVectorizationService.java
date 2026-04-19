package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.ShopifyCompanionBootstrapProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.marketplace.model.CreateDeploymentMarketplaceInstallRequest;
import com.ai.fabric.platform.backend.marketplace.model.DeploymentMarketplaceInstallSummary;
import com.ai.fabric.platform.backend.marketplace.model.UpdateDeploymentMarketplaceInstallRequest;
import com.ai.fabric.platform.backend.marketplace.service.DeploymentMarketplaceInstallService;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceCatalogService;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreVectorizationRunSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreVectorizationSummary;
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

    private final ShopifyStoreConnectionRepository repository;
    private final DeploymentRepository deploymentRepository;
    private final DeploymentMarketplaceInstallService deploymentMarketplaceInstallService;
    private final MarketplaceCatalogService marketplaceCatalogService;
    private final VectorizationService vectorizationService;
    private final ShopifyCompanionBootstrapProperties properties;
    private final PlatformAuditService platformAuditService;

    public ShopifyStoreVectorizationService(ShopifyStoreConnectionRepository repository,
                                            DeploymentRepository deploymentRepository,
                                            DeploymentMarketplaceInstallService deploymentMarketplaceInstallService,
                                            MarketplaceCatalogService marketplaceCatalogService,
                                            VectorizationService vectorizationService,
                                            ShopifyCompanionBootstrapProperties properties,
                                            PlatformAuditService platformAuditService) {
        this.repository = repository;
        this.deploymentRepository = deploymentRepository;
        this.deploymentMarketplaceInstallService = deploymentMarketplaceInstallService;
        this.marketplaceCatalogService = marketplaceCatalogService;
        this.vectorizationService = vectorizationService;
        this.properties = properties;
        this.platformAuditService = platformAuditService;
    }

    @Transactional(readOnly = true)
    public ShopifyStoreVectorizationSummary getSummary(String shopDomain) {
        ShopifyStoreConnectionEntity store = requireStore(shopDomain);
        return buildSummary(store);
    }

    @Transactional
    public ShopifyStoreVectorizationSummary reconcile(String shopDomain) {
        ShopifyStoreConnectionEntity store = requireStore(shopDomain);
        DeploymentEntity deployment = requireDeployment(store);
        LinkedHashSet<String> desiredPluginIds = ShopifyCompanionPluginSelection.desiredManagedPluginIds(properties, store);
        Map<String, DeploymentMarketplaceInstallSummary> installsByPluginId = installsByPluginId(deployment.getId());

        LinkedHashSet<String> managedDataPluginIds = ShopifyCompanionPluginSelection.managedDataPluginIds();
        for (String pluginId : desiredPluginIds) {
            DeploymentMarketplaceInstallSummary current = installsByPluginId.get(normalizePluginId(pluginId));
            String latestVersion = marketplaceCatalogService.resolveLatestPublishedVersionLabel(pluginId);
            if (current == null) {
                deploymentMarketplaceInstallService.createInstall(
                    deployment.getId(),
                    new CreateDeploymentMarketplaceInstallRequest(pluginId, latestVersion, JSON.objectNode(), JSON.objectNode())
                );
                continue;
            }
            boolean versionDrift = latestVersion != null && !latestVersion.isBlank() && !latestVersion.equals(current.pluginVersion());
            boolean disabled = !"ENABLED".equalsIgnoreCase(current.status());
            if (versionDrift || disabled) {
                deploymentMarketplaceInstallService.updateInstall(
                    deployment.getId(),
                    current.id(),
                    new UpdateDeploymentMarketplaceInstallRequest(
                        versionDrift ? latestVersion : null,
                        "ENABLED",
                        current.config(),
                        current.secretRefs()
                    )
                );
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
            deploymentMarketplaceInstallService.deleteInstall(deployment.getId(), install.id());
        }

        VectorizationOverviewSummary overview = reconcileVectorizationOverview(store, deployment);
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
        return summarize(store, deployment, installsByPluginId(deployment.getId()), overview);
    }

    @Transactional
    public ShopifyStoreVectorizationSummary vectorizeNow(String shopDomain) {
        ShopifyStoreConnectionEntity store = requireStore(shopDomain);
        DeploymentEntity deployment = requireDeployment(store);
        ShopifyStoreVectorizationSummary reconciled = reconcile(shopDomain);
        if (!reconciled.readyToRun()) {
            throw new ResponseStatusException(CONFLICT, String.join(" ", reconciled.blockingReasons()));
        }
        vectorizationService.createRun(
            deployment.getId(),
            new CreateVectorizationRunRequest(
                reconciled.lastRun() == null ? "BOOTSTRAP" : "REFRESH",
                reconciled.selectedEntityTypes(),
                "Shopify store admin requested vectorization for current enabled source categories.",
                JSON.objectNode()
            )
        );
        platformAuditService.record(
            "SHOPIFY_STORE_VECTORIZATION_RUN_TRIGGERED",
            "SHOPIFY_STORE_CONNECTION",
            store.getShopDomain(),
            Map.of(
                "shopDomain", store.getShopDomain(),
                "deploymentId", deployment.getId(),
                "selectedEntityTypes", String.join(",", reconciled.selectedEntityTypes())
            )
        );
        return buildSummary(store);
    }

    private VectorizationOverviewSummary reconcileVectorizationOverview(ShopifyStoreConnectionEntity store, DeploymentEntity deployment) {
        UpsertVectorizationSourceConnectionRequest connectionRequest = buildConnectionRequest(store);
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

    private UpsertVectorizationSourceConnectionRequest buildConnectionRequest(ShopifyStoreConnectionEntity store) {
        ObjectNode connectionConfig = JSON.objectNode();
        connectionConfig.put("shopDomain", store.getShopDomain());
        connectionConfig.put("productServiceId", blankToEmpty(store.getProductServiceId()));
        connectionConfig.put("deploymentId", blankToEmpty(store.getDeploymentId()));
        connectionConfig.put("consumerId", blankToEmpty(store.getConsumerId()));

        ObjectNode discoverySummary = JSON.objectNode();
        discoverySummary.put("selectedCategoryCount", ShopifyCompanionPluginSelection.selectedCategories(store).size());
        discoverySummary.put("selectedEntityTypeCount", ShopifyCompanionPluginSelection.selectedEntityTypes(store).size());
        discoverySummary.set("selectedCategories", jsonArray(ShopifyCompanionPluginSelection.selectedCategories(store)));
        discoverySummary.set("selectedEntityTypes", jsonArray(ShopifyCompanionPluginSelection.selectedEntityTypes(store)));

        return new UpsertVectorizationSourceConnectionRequest(
            "Shopify store " + store.getShopDomain(),
            "shopify-store",
            "private_runtime_backend_mediated",
            connectionConfig,
            JSON.objectNode(),
            discoverySummary
        );
    }

    private ObjectNode buildMappingConfig(ShopifyStoreConnectionEntity store) {
        ObjectNode mapping = JSON.objectNode();
        ObjectNode sourceCategories = mapping.putObject("sourceCategories");
        sourceCategories.put("productsEnabled", store.isProductsEnabled());
        sourceCategories.put("collectionsEnabled", store.isCollectionsEnabled());
        sourceCategories.put("pagesEnabled", store.isPagesEnabled());
        sourceCategories.put("policiesEnabled", store.isPoliciesEnabled());
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
        return execution;
    }

    private ShopifyStoreVectorizationSummary buildSummary(ShopifyStoreConnectionEntity store) {
        DeploymentEntity deployment = resolveDeployment(store);
        Map<String, DeploymentMarketplaceInstallSummary> installsByPluginId = deployment == null
            ? Map.of()
            : installsByPluginId(deployment.getId());
        VectorizationOverviewSummary overview = deployment == null ? null : vectorizationService.getOverviewForTrustedCaller(deployment);
        return summarize(store, deployment, installsByPluginId, overview);
    }

    private ShopifyStoreVectorizationSummary summarize(ShopifyStoreConnectionEntity store,
                                                       DeploymentEntity deployment,
                                                       Map<String, DeploymentMarketplaceInstallSummary> installsByPluginId,
                                                       VectorizationOverviewSummary overview) {
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
        boolean readyToRun = deployment != null
            && !selectedEntityTypes.isEmpty()
            && missingPluginIds.isEmpty()
            && disabledPluginIds.isEmpty()
            && connectionConfigured
            && planConfigured;

        List<String> blockingReasons = new ArrayList<>();
        if (deployment == null) {
            blockingReasons.add("Shopify store is not bootstrapped to a platform deployment yet.");
        }
        if (selectedEntityTypes.isEmpty()) {
            blockingReasons.add("At least one Shopify source category must be enabled before vectorization can run.");
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
            !missingPluginIds.isEmpty() || !disabledPluginIds.isEmpty(),
            connectionConfigured,
            overview == null || overview.sourceConnection() == null ? null : overview.sourceConnection().id(),
            overview == null || overview.sourceConnection() == null ? null : overview.sourceConnection().status(),
            overview == null || overview.sourceConnection() == null ? null : overview.sourceConnection().adapterType(),
            planConfigured,
            overview == null || overview.plan() == null ? null : overview.plan().id(),
            overview == null || overview.plan() == null ? null : overview.plan().status(),
            overview == null || overview.plan() == null ? null : overview.plan().runnerMode(),
            overview == null || overview.plan() == null ? null : overview.plan().syncState(),
            readyToRun,
            List.copyOf(blockingReasons),
            summarizeLastRun(overview)
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

    private Map<String, DeploymentMarketplaceInstallSummary> installsByPluginId(String deploymentId) {
        LinkedHashMap<String, DeploymentMarketplaceInstallSummary> installs = new LinkedHashMap<>();
        for (DeploymentMarketplaceInstallSummary install : deploymentMarketplaceInstallService.listInstalls(deploymentId)) {
            installs.put(normalizePluginId(install.pluginId()), install);
        }
        return installs;
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

    private DeploymentEntity resolveDeployment(ShopifyStoreConnectionEntity store) {
        if (store == null || store.getDeploymentId() == null || store.getDeploymentId().isBlank()) {
            return null;
        }
        return deploymentRepository.findById(store.getDeploymentId()).orElse(null);
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
}
