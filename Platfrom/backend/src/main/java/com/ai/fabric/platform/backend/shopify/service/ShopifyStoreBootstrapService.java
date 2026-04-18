package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.ShopifyCompanionBootstrapProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentDraftRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.marketplace.model.CreateDeploymentMarketplaceInstallRequest;
import com.ai.fabric.platform.backend.marketplace.model.CreateMarketplaceTemplateBootstrapRequest;
import com.ai.fabric.platform.backend.marketplace.model.DeploymentMarketplaceInstallSummary;
import com.ai.fabric.platform.backend.marketplace.service.DeploymentMarketplaceInstallService;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceCatalogService;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceTemplateBootstrapService;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.model.BootstrapShopifyStoreRequest;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreBootstrapSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreConnectionSummary;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.model.CreatePlatformConsumerRequest;
import com.ai.fabric.platform.backend.tenant.model.CreatePlatformCustomerRequest;
import com.ai.fabric.platform.backend.tenant.model.PlatformConsumerSummary;
import com.ai.fabric.platform.backend.tenant.model.PlatformCustomerSummary;
import com.ai.fabric.platform.backend.tenant.model.UpdatePlatformConsumerRequest;
import com.ai.fabric.platform.backend.tenant.model.UpdatePlatformConsumerBindingRequest;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerConsumerService;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerTenantService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class ShopifyStoreBootstrapService {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final ShopifyStoreConnectionRepository repository;
    private final PlatformCustomerRepository customerRepository;
    private final PlatformConsumerRepository consumerRepository;
    private final DeploymentRepository deploymentRepository;
    private final PlatformCustomerTenantService platformCustomerTenantService;
    private final PlatformCustomerConsumerService platformCustomerConsumerService;
    private final DeploymentService deploymentService;
    private final MarketplaceTemplateBootstrapService marketplaceTemplateBootstrapService;
    private final DeploymentMarketplaceInstallService deploymentMarketplaceInstallService;
    private final MarketplaceCatalogService marketplaceCatalogService;
    private final ShopifyStoreConnectionService shopifyStoreConnectionService;
    private final ShopifyCompanionBootstrapProperties properties;
    private final PlatformAuditService platformAuditService;

    public ShopifyStoreBootstrapService(ShopifyStoreConnectionRepository repository,
                                        PlatformCustomerRepository customerRepository,
                                        PlatformConsumerRepository consumerRepository,
                                        DeploymentRepository deploymentRepository,
                                        PlatformCustomerTenantService platformCustomerTenantService,
                                        PlatformCustomerConsumerService platformCustomerConsumerService,
                                        DeploymentService deploymentService,
                                        MarketplaceTemplateBootstrapService marketplaceTemplateBootstrapService,
                                        DeploymentMarketplaceInstallService deploymentMarketplaceInstallService,
                                        MarketplaceCatalogService marketplaceCatalogService,
                                        ShopifyStoreConnectionService shopifyStoreConnectionService,
                                        ShopifyCompanionBootstrapProperties properties,
                                        PlatformAuditService platformAuditService) {
        this.repository = repository;
        this.customerRepository = customerRepository;
        this.consumerRepository = consumerRepository;
        this.deploymentRepository = deploymentRepository;
        this.platformCustomerTenantService = platformCustomerTenantService;
        this.platformCustomerConsumerService = platformCustomerConsumerService;
        this.deploymentService = deploymentService;
        this.marketplaceTemplateBootstrapService = marketplaceTemplateBootstrapService;
        this.deploymentMarketplaceInstallService = deploymentMarketplaceInstallService;
        this.marketplaceCatalogService = marketplaceCatalogService;
        this.shopifyStoreConnectionService = shopifyStoreConnectionService;
        this.properties = properties;
        this.platformAuditService = platformAuditService;
    }

    @Transactional
    public ShopifyStoreBootstrapSummary bootstrap(String shopDomain, BootstrapShopifyStoreRequest request) {
        BootstrapShopifyStoreRequest resolvedRequest = request == null
            ? new BootstrapShopifyStoreRequest(null, null, null, null, null, null, null)
            : request;
        ShopifyStoreConnectionEntity store = repository.findByShopDomainIgnoreCase(normalizeRequired(shopDomain))
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Shopify store connection not found: " + shopDomain));

        PlatformCustomerEntity customer = resolveCustomer(store, resolvedRequest);
        boolean createdCustomer = !hasText(store.getCustomerId()) || !store.getCustomerId().equals(customer.getId());

        DeploymentEntity existingDeployment = resolveDeployment(store.getDeploymentId());
        boolean createdDeployment = existingDeployment == null;
        BootstrapDeployment deployment = createdDeployment
            ? createDeployment(store, customer, resolvedRequest)
            : new BootstrapDeployment(
                existingDeployment.getId(),
                existingDeployment.getName(),
                existingDeployment.getEnvironmentName(),
                existingDeployment.getStatus()
            );
        ensureSharedVectorBootstrapDefaults(deployment.id());

        PlatformConsumerEntity existingConsumer = resolveConsumer(store.getConsumerId());
        boolean createdConsumer = existingConsumer == null;
        PlatformConsumerSummary consumer = existingConsumer == null
            ? createConsumer(store, customer, deployment, resolvedRequest)
            : ensureConsumerBinding(existingConsumer, deployment);

        List<String> installedPluginIds = ensureBundle(deployment.id(), createdDeployment, resolvedRequest);

        store.setCustomerId(customer.getId());
        store.setDeploymentId(deployment.id());
        store.setConsumerId(consumer.consumerId());
        store.setOnboardingStatus("PLATFORM_BOOTSTRAPPED");
        store.setUpdatedAt(Instant.now());
        repository.save(store);

        ShopifyStoreConnectionSummary summary = shopifyStoreConnectionService.getConnection(store.getShopDomain());
        platformAuditService.record(
            "SHOPIFY_STORE_BOOTSTRAPPED",
            "SHOPIFY_STORE_CONNECTION",
            store.getShopDomain(),
            Map.of(
                "shopDomain", store.getShopDomain(),
                "customerId", customer.getId(),
                "deploymentId", deployment.id(),
                "consumerId", consumer.consumerId(),
                "createdCustomer", Boolean.toString(createdCustomer),
                "createdDeployment", Boolean.toString(createdDeployment),
                "createdConsumer", Boolean.toString(createdConsumer)
            )
        );

        return new ShopifyStoreBootstrapSummary(
            store.getShopDomain(),
            customer.getId(),
            deployment.id(),
            consumer.consumerId(),
            createdCustomer,
            createdDeployment,
            createdConsumer,
            installedPluginIds,
            summary
        );
    }

    private PlatformCustomerEntity resolveCustomer(ShopifyStoreConnectionEntity store, BootstrapShopifyStoreRequest request) {
        PlatformCustomerEntity existing = hasText(store.getCustomerId())
            ? customerRepository.findById(store.getCustomerId()).orElse(null)
            : null;
        if (existing != null) {
            return existing;
        }
        String customerName = hasText(request.customerName()) ? request.customerName().trim() : defaultCustomerName(store);
        PlatformCustomerSummary created = platformCustomerTenantService.createCustomer(
            new CreatePlatformCustomerRequest(customerName, "Customer boundary for Shopify store " + store.getShopDomain() + ".")
        );
        return customerRepository.findById(created.id())
            .orElseThrow(() -> new IllegalStateException("Created Shopify customer is missing: " + created.id()));
    }

    private BootstrapDeployment createDeployment(ShopifyStoreConnectionEntity store,
                                                 PlatformCustomerEntity customer,
                                                 BootstrapShopifyStoreRequest request) {
        String deploymentName = hasText(request.deploymentName()) ? request.deploymentName().trim() : defaultDeploymentName(store);
        String environment = hasText(request.environment()) ? request.environment().trim() : properties.defaultEnvironment();
        String templatePluginId = hasText(request.templatePluginId()) ? request.templatePluginId().trim() : properties.templatePluginId();
        String templatePluginVersion = hasText(request.templatePluginVersion()) ? request.templatePluginVersion().trim() : properties.templatePluginVersion();
        if (hasText(templatePluginId)) {
            DeploymentSummary summary = marketplaceTemplateBootstrapService.bootstrap(
                templatePluginId,
                new CreateMarketplaceTemplateBootstrapRequest(
                    hasText(templatePluginVersion) ? templatePluginVersion : null,
                    deploymentName,
                    environment,
                    properties.defaultTemplateId(),
                    blankToNull(properties.defaultVectorProvisioningMode()),
                    customer.getId(),
                    null
                )
            );
            return new BootstrapDeployment(summary.id(), summary.name(), summary.environment(), summary.status());
        }
        DeploymentSummary summary = deploymentService.createDeployment(
            new CreateDeploymentRequest(
                deploymentName,
                environment,
                properties.defaultTemplateId(),
                null,
                blankToNull(properties.defaultVectorProvisioningMode()),
                customer.getId(),
                null
            )
        );
        return new BootstrapDeployment(summary.id(), summary.name(), summary.environment(), summary.status());
    }

    private PlatformConsumerSummary createConsumer(ShopifyStoreConnectionEntity store,
                                                   PlatformCustomerEntity customer,
                                                   BootstrapDeployment deployment,
                                                   BootstrapShopifyStoreRequest request) {
        String requestedConsumerId = hasText(request.consumerId()) ? request.consumerId().trim() : defaultConsumerId(store);
        String resolvedConsumerId = nextAvailableConsumerId(customer.getId(), requestedConsumerId);
        return platformCustomerConsumerService.createConsumer(
            customer.getId(),
            new CreatePlatformConsumerRequest(
                resolvedConsumerId,
                defaultConsumerDisplayName(store),
                "Storefront consumer for Shopify store " + store.getShopDomain() + ".",
                deployment.id(),
                "Bootstrap Shopify store " + store.getShopDomain()
            )
        );
    }

    private PlatformConsumerSummary ensureConsumerBinding(PlatformConsumerEntity consumer, BootstrapDeployment deployment) {
        PlatformConsumerSummary summary;
        if (deployment.id().equals(consumer.getBoundDeploymentId())) {
            summary = new PlatformConsumerSummary(
                consumer.getConsumerId(),
                consumer.getCustomerId(),
                consumer.getDisplayName(),
                consumer.getDescription(),
                consumer.getStatus(),
                consumer.getBoundDeploymentId(),
                deployment.name(),
                deployment.environment(),
                deployment.status(),
                consumer.getLastBoundAt(),
                consumer.getCreatedAt(),
                consumer.getUpdatedAt()
            );
        } else {
            summary = platformCustomerConsumerService.updateBinding(
                consumer.getCustomerId(),
                consumer.getConsumerId(),
                new UpdatePlatformConsumerBindingRequest(
                    deployment.id(),
                    "Bootstrap Shopify store binding to deployment " + deployment.id()
                )
            );
        }
        if (!"ACTIVE".equalsIgnoreCase(summary.status())) {
            return platformCustomerConsumerService.updateConsumer(
                summary.customerId(),
                summary.consumerId(),
                new UpdatePlatformConsumerRequest(
                    summary.displayName(),
                    summary.description(),
                    "ACTIVE"
                )
            );
        }
        return summary;
    }

    private List<String> ensureBundle(String deploymentId,
                                      boolean createdDeployment,
                                      BootstrapShopifyStoreRequest request) {
        LinkedHashSet<String> desiredPluginIds = new LinkedHashSet<>();
        if (request.pluginIds() != null) {
            request.pluginIds().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .forEach(desiredPluginIds::add);
        } else {
            properties.defaultPluginIds().forEach(desiredPluginIds::add);
        }

        LinkedHashSet<String> installed = deploymentMarketplaceInstallService.listInstalls(deploymentId).stream()
            .map(DeploymentMarketplaceInstallSummary::pluginId)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        List<String> ensured = new ArrayList<>();
        for (String pluginId : desiredPluginIds) {
            if (installed.contains(pluginId)) {
                ensured.add(pluginId);
                continue;
            }
            String version = marketplaceCatalogService.resolveLatestPublishedVersionLabel(pluginId);
            deploymentMarketplaceInstallService.createInstall(
                deploymentId,
                new CreateDeploymentMarketplaceInstallRequest(
                    pluginId,
                    version,
                    JSON.objectNode(),
                    JSON.objectNode()
                )
            );
            ensured.add(pluginId);
        }
        if (createdDeployment && hasText(blankToNull(properties.templatePluginId()))) {
            ensured.add(0, properties.templatePluginId());
        }
        return ensured;
    }

    private void ensureSharedVectorBootstrapDefaults(String deploymentId) {
        DeploymentDraftResponse draft = deploymentService.getActiveDraftForDeployment(deploymentId);
        ObjectNode providerConfig = ensureObject(draft.providerConfig());
        boolean changed = false;
        changed |= putText(providerConfig, "vectorProvisioningMode", properties.defaultVectorProvisioningMode());
        changed |= putText(providerConfig, "vectorStoragePosture", properties.defaultVectorStoragePosture());
        changed |= putBoolean(providerConfig, "qdrantManagedCollectionsEnabled", properties.defaultQdrantManagedCollectionsEnabled());
        changed |= putText(providerConfig, "qdrantCloudProviderId", properties.defaultQdrantCloudProviderId());
        changed |= putText(providerConfig, "qdrantCloudRegionId", properties.defaultQdrantCloudRegionId());
        if (!changed) {
            return;
        }
        deploymentService.updateDraft(
            draft.id(),
            new UpdateDeploymentDraftRequest(
                null,
                null,
                null,
                providerConfig,
                null,
                null,
                null,
                null,
                null
            )
        );
    }

    private DeploymentEntity resolveDeployment(String deploymentId) {
        return hasText(deploymentId) ? deploymentRepository.findById(deploymentId).orElse(null) : null;
    }

    private PlatformConsumerEntity resolveConsumer(String consumerId) {
        return hasText(consumerId) ? consumerRepository.findByConsumerIdIgnoreCase(consumerId).orElse(null) : null;
    }

    private String nextAvailableConsumerId(String customerId, String requestedConsumerId) {
        String base = sanitizeConsumerId(requestedConsumerId);
        PlatformConsumerEntity existing = consumerRepository.findByConsumerIdIgnoreCase(base).orElse(null);
        if (existing == null || customerId.equals(existing.getCustomerId())) {
            return base;
        }
        for (int suffix = 2; suffix < 1000; suffix++) {
            String candidate = base + "-" + suffix;
            PlatformConsumerEntity collision = consumerRepository.findByConsumerIdIgnoreCase(candidate).orElse(null);
            if (collision == null || customerId.equals(collision.getCustomerId())) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not allocate unique consumerId for Shopify store bootstrap: " + requestedConsumerId);
    }

    private String defaultCustomerName(ShopifyStoreConnectionEntity store) {
        return "Shopify Store " + store.getShopDomain();
    }

    private String defaultDeploymentName(ShopifyStoreConnectionEntity store) {
        return "Shopify Companion " + store.getShopDomain();
    }

    private String defaultConsumerDisplayName(ShopifyStoreConnectionEntity store) {
        return hasText(store.getDisplayName()) ? store.getDisplayName() : "Shopify Storefront " + store.getShopDomain();
    }

    private String defaultConsumerId(ShopifyStoreConnectionEntity store) {
        String base = store.getShopDomain().toLowerCase(Locale.ROOT);
        if (base.endsWith(".myshopify.com")) {
            base = base.substring(0, base.length() - ".myshopify.com".length());
        }
        return sanitizeConsumerId("shopify-" + base);
    }

    private String sanitizeConsumerId(String value) {
        String normalized = value == null ? "shopify-store" : value.trim().toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9-]+", "-")
            .replaceAll("^-+", "")
            .replaceAll("-+$", "")
            .replaceAll("-{2,}", "-");
        if (normalized.isBlank()) {
            return "shopify-store";
        }
        return normalized.length() < 3 ? (normalized + "-id") : normalized;
    }

    private String normalizeRequired(String value) {
        if (!hasText(value)) {
            throw new ResponseStatusException(NOT_FOUND, "shopDomain is required.");
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private ObjectNode ensureObject(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            return objectNode.deepCopy();
        }
        return JSON.objectNode();
    }

    private boolean putText(ObjectNode node, String field, String value) {
        String normalized = blankToNull(value);
        String existing = blankToNull(node.path(field).asText(null));
        if (java.util.Objects.equals(existing, normalized)) {
            return false;
        }
        if (normalized == null) {
            node.remove(field);
        } else {
            node.put(field, normalized);
        }
        return true;
    }

    private boolean putBoolean(ObjectNode node, String field, Boolean value) {
        if (value == null) {
            return false;
        }
        if (!node.path(field).isMissingNode() && node.path(field).isBoolean() && node.path(field).booleanValue() == value) {
            return false;
        }
        node.put(field, value);
        return true;
    }

    private record BootstrapDeployment(
        String id,
        String name,
        String environment,
        String status
    ) {
    }
}
