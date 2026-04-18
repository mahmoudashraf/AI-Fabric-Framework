package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentReleaseSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVersionSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.productservice.entity.PlatformManagedProductServiceEntity;
import com.ai.fabric.platform.backend.productservice.service.PlatformManagedProductServiceService;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreCapabilitySummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreBindingInspectionSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreConnectionSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreLinkedConsumerSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreLinkedCustomerSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreLinkedDeploymentSummary;
import com.ai.fabric.platform.backend.shopify.model.UpsertShopifyStoreConnectionRequest;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class ShopifyStoreConnectionService {

    private final ShopifyStoreConnectionRepository repository;
    private final PlatformManagedProductServiceService productServiceService;
    private final PlatformCustomerRepository customerRepository;
    private final DeploymentRepository deploymentRepository;
    private final DeploymentVersionRepository deploymentVersionRepository;
    private final DeploymentReleaseRepository deploymentReleaseRepository;
    private final PlatformConsumerRepository consumerRepository;
    private final PlatformAuditService platformAuditService;
    private final ShopifyStoreSourcePreflightSupport sourcePreflightSupport;
    private final ShopifyStoreReadinessEvaluator readinessEvaluator;

    public ShopifyStoreConnectionService(ShopifyStoreConnectionRepository repository,
                                         PlatformManagedProductServiceService productServiceService,
                                         PlatformCustomerRepository customerRepository,
                                         DeploymentRepository deploymentRepository,
                                         DeploymentVersionRepository deploymentVersionRepository,
                                         DeploymentReleaseRepository deploymentReleaseRepository,
                                         PlatformConsumerRepository consumerRepository,
                                         PlatformAuditService platformAuditService,
                                         ShopifyStoreSourcePreflightSupport sourcePreflightSupport,
                                         ShopifyStoreReadinessEvaluator readinessEvaluator) {
        this.repository = repository;
        this.productServiceService = productServiceService;
        this.customerRepository = customerRepository;
        this.deploymentRepository = deploymentRepository;
        this.deploymentVersionRepository = deploymentVersionRepository;
        this.deploymentReleaseRepository = deploymentReleaseRepository;
        this.consumerRepository = consumerRepository;
        this.platformAuditService = platformAuditService;
        this.sourcePreflightSupport = sourcePreflightSupport;
        this.readinessEvaluator = readinessEvaluator;
    }

    public List<ShopifyStoreConnectionSummary> listConnections() {
        PlatformPrincipal principal = PlatformSecurityContext.currentPrincipal();
        List<ShopifyStoreConnectionEntity> entities;
        if (principal != null && principal.role() == PlatformRole.PLATFORM_PRODUCT_SERVICE) {
            PlatformManagedProductServiceEntity service = productServiceService.requireService(principal.actorId());
            entities = repository.findAllByProductServiceIdOrderByShopDomainAsc(service.getId());
        } else {
            entities = repository.findAllByOrderByCreatedAtDesc();
        }
        return entities.stream()
            .map(this::toSummary)
            .toList();
    }

    public ShopifyStoreConnectionSummary getConnection(String shopDomain) {
        return toSummary(requireConnection(shopDomain));
    }

    public ShopifyStoreBindingInspectionSummary inspectBinding(String shopDomain) {
        ShopifyStoreConnectionEntity entity = requireConnection(shopDomain);
        ResolvedStoreContext context = resolveStoreContext(entity);
        return new ShopifyStoreBindingInspectionSummary(
            entity.getShopDomain(),
            context.productService().getServiceRef(),
            toLinkedCustomerSummary(context.customer()),
            toLinkedDeploymentSummary(context.deployment()),
            toLinkedConsumerSummary(context.consumer(), context.deployment()),
            toVersionSummary(context.latestVersion()),
            toReleaseSummary(context.latestRelease()),
            buildBindingWarnings(entity, context.customer(), context.deployment(), context.consumer())
        );
    }

    @Transactional
    public void deleteConnection(String shopDomain, boolean force) {
        ShopifyStoreConnectionEntity entity = requireConnection(shopDomain);
        if (!force && (hasText(entity.getDeploymentId()) || hasText(entity.getConsumerId()) || "LIVE".equalsIgnoreCase(entity.getOnboardingStatus()))) {
            throw new ResponseStatusException(CONFLICT,
                "Shopify store mapping still has active platform bindings. Re-run deletion with force=true after reviewing the linked customer, deployment, and consumer.");
        }
        repository.delete(entity);
        platformAuditService.record(
            "SHOPIFY_STORE_CONNECTION_DELETED",
            "SHOPIFY_STORE_CONNECTION",
            entity.getShopDomain(),
            java.util.Map.of(
                "shopDomain", entity.getShopDomain(),
                "force", Boolean.toString(force),
                "customerId", entity.getCustomerId() == null ? "" : entity.getCustomerId(),
                "deploymentId", entity.getDeploymentId() == null ? "" : entity.getDeploymentId(),
                "consumerId", entity.getConsumerId() == null ? "" : entity.getConsumerId()
            )
        );
    }

    @Transactional
    public ShopifyStoreConnectionSummary upsertConnection(UpsertShopifyStoreConnectionRequest request) {
        String normalizedShopDomain = normalizeShopDomain(request.shopDomain());
        PlatformManagedProductServiceEntity productService = productServiceService.requireService(request.productServiceRef());
        if (!"SHOPIFY".equalsIgnoreCase(productService.getProductFamily())
            && !"SHOPIFY_BRIDGE_SERVICE".equalsIgnoreCase(productService.getServiceKind())) {
            throw new ResponseStatusException(CONFLICT, "Shopify store connections require a Shopify managed product service.");
        }

        PlatformCustomerEntity customer = resolveCustomer(request.customerId(), request.deploymentId(), request.consumerId());
        DeploymentEntity deployment = resolveDeployment(request.deploymentId());
        PlatformConsumerEntity consumer = resolveConsumer(request.consumerId());

        String resolvedCustomerId = resolveCustomerId(customer, deployment, consumer);
        if (deployment != null && resolvedCustomerId != null && !resolvedCustomerId.equals(deployment.getCustomerId())) {
            throw new ResponseStatusException(CONFLICT, "deploymentId does not belong to customerId.");
        }
        if (consumer != null && resolvedCustomerId != null && !resolvedCustomerId.equals(consumer.getCustomerId())) {
            throw new ResponseStatusException(CONFLICT, "consumerId does not belong to customerId.");
        }
        if (consumer != null && deployment != null && consumer.getBoundDeploymentId() != null
            && !consumer.getBoundDeploymentId().equals(deployment.getId())) {
            throw new ResponseStatusException(CONFLICT, "consumerId is bound to a different deployment.");
        }

        ShopifyStoreConnectionEntity entity = repository.findByShopDomainIgnoreCase(normalizedShopDomain)
            .orElseGet(ShopifyStoreConnectionEntity::new);
        boolean created = entity.getId() == null;
        if (created) {
            entity.setId(generateId("shp"));
            entity.setCreatedAt(Instant.now());
        }
        entity.setShopDomain(normalizedShopDomain);
        entity.setDisplayName(trimToNull(request.displayName()));
        entity.setProductServiceId(productService.getId());
        entity.setCustomerId(resolvedCustomerId);
        entity.setDeploymentId(deployment == null ? null : deployment.getId());
        entity.setConsumerId(consumer == null ? null : consumer.getConsumerId());
        entity.setInstallStatus(normalizeStatus(request.installStatus(), "INSTALLED"));
        entity.setSyncStatus(normalizeStatus(request.syncStatus(), "NOT_SYNCED"));
        entity.setSourceReadinessStatus(normalizeStatus(request.sourceReadinessStatus(), "NOT_RUN"));
        entity.setWidgetStatus(normalizeStatus(request.widgetStatus(), "NOT_ENABLED"));
        entity.setOnboardingStatus(normalizeStatus(request.onboardingStatus(), "NOT_STARTED"));
        entity.setProductsEnabled(request.productsEnabled() == null || request.productsEnabled());
        entity.setCollectionsEnabled(request.collectionsEnabled() == null || request.collectionsEnabled());
        entity.setPagesEnabled(request.pagesEnabled() == null || request.pagesEnabled());
        entity.setPoliciesEnabled(request.policiesEnabled() == null || request.policiesEnabled());
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);

        platformAuditService.record(
            created ? "SHOPIFY_STORE_CONNECTION_CREATED" : "SHOPIFY_STORE_CONNECTION_UPDATED",
            "SHOPIFY_STORE_CONNECTION",
            entity.getShopDomain(),
            java.util.Map.of(
                "shopDomain", entity.getShopDomain(),
                "serviceRef", productService.getServiceRef(),
                "customerId", resolvedCustomerId == null ? "" : resolvedCustomerId,
                "deploymentId", entity.getDeploymentId() == null ? "" : entity.getDeploymentId(),
                "consumerId", entity.getConsumerId() == null ? "" : entity.getConsumerId(),
                "onboardingStatus", entity.getOnboardingStatus()
            )
        );

        return toSummary(entity);
    }

    private ShopifyStoreConnectionEntity requireConnection(String shopDomain) {
        return repository.findByShopDomainIgnoreCase(normalizeShopDomain(shopDomain))
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Shopify store connection not found: " + shopDomain));
    }

    private ShopifyStoreConnectionSummary toSummary(ShopifyStoreConnectionEntity entity) {
        ResolvedStoreContext context = resolveStoreContext(entity);
        var credentials = sourcePreflightSupport.summarizeCredentials(entity.getDetailsJson());
        var sourcePreflight = sourcePreflightSupport.summarize(entity.getDetailsJson());
        var syncDetail = sourcePreflightSupport.summarizeSync(entity.getDetailsJson());
        var webhookDetail = sourcePreflightSupport.summarizeWebhook(entity.getDetailsJson());
        var widgetDetail = sourcePreflightSupport.summarizeWidget(entity.getDetailsJson());
        var capabilities = summarizeCapabilities(context.latestVersion());
        var latestReleaseSummary = toReleaseSummary(context.latestRelease());
        return new ShopifyStoreConnectionSummary(
            entity.getId(),
            entity.getShopDomain(),
            entity.getDisplayName(),
            context.productService().getId(),
            context.productService().getServiceRef(),
            context.productService().getDisplayName(),
            entity.getCustomerId(),
            context.customer() == null ? null : context.customer().getName(),
            entity.getDeploymentId(),
            context.deployment() == null ? null : context.deployment().getName(),
            context.deployment() == null ? null : context.deployment().getStatus(),
            entity.getConsumerId(),
            context.consumer() == null ? null : context.consumer().getDisplayName(),
            entity.getInstallStatus(),
            entity.getSyncStatus(),
            entity.getSourceReadinessStatus(),
            entity.getWidgetStatus(),
            entity.getOnboardingStatus(),
            entity.isProductsEnabled(),
            entity.isCollectionsEnabled(),
            entity.isPagesEnabled(),
            entity.isPoliciesEnabled(),
            credentials,
            sourcePreflight,
            syncDetail,
            webhookDetail,
            widgetDetail,
            capabilities,
            readinessEvaluator.evaluate(entity, credentials, sourcePreflight, syncDetail, widgetDetail, latestReleaseSummary),
            toVersionSummary(context.latestVersion()),
            latestReleaseSummary,
            entity.getLastSourcePreflightAt(),
            entity.getLastSyncAt(),
            entity.getLastWebhookAt(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    private ResolvedStoreContext resolveStoreContext(ShopifyStoreConnectionEntity entity) {
        PlatformManagedProductServiceEntity productService = productServiceService.requireServiceById(entity.getProductServiceId());
        PlatformCustomerEntity customer = entity.getCustomerId() == null ? null : customerRepository.findById(entity.getCustomerId()).orElse(null);
        DeploymentEntity deployment = entity.getDeploymentId() == null ? null : deploymentRepository.findById(entity.getDeploymentId()).orElse(null);
        DeploymentVersionEntity latestVersion = deployment == null
            ? null
            : deploymentVersionRepository.findByDeploymentIdOrderByPublishedAtDesc(deployment.getId()).stream().findFirst().orElse(null);
        DeploymentReleaseEntity latestRelease = deployment == null
            ? null
            : deploymentReleaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc(deployment.getId()).orElse(null);
        PlatformConsumerEntity consumer = entity.getConsumerId() == null ? null : consumerRepository.findByConsumerIdIgnoreCase(entity.getConsumerId()).orElse(null);
        return new ResolvedStoreContext(productService, customer, deployment, latestVersion, latestRelease, consumer);
    }

    private ShopifyStoreCapabilitySummary summarizeCapabilities(DeploymentVersionEntity version) {
        if (version == null) {
            return null;
        }
        Set<String> actionNames = textSet(sourcePreflightSupport.readJsonNode(version.getActionsConfigJson()), "actions", "name");
        Set<String> knowledgeSourceIds = textSet(sourcePreflightSupport.readJsonNode(version.getKnowledgeSourceConfigJson()), "sources", "id");
        Set<String> shellModuleIds = textSet(sourcePreflightSupport.readJsonNode(version.getShellConfigJson()), "modules", "id");
        Set<String> marketplaceDatasetIds = textSet(sourcePreflightSupport.readJsonNode(version.getMarketplaceDatasetConfigJson()), "datasets", "datasetId");
        return new ShopifyStoreCapabilitySummary(
            actionNames.size(),
            knowledgeSourceIds.size(),
            shellModuleIds.size(),
            marketplaceDatasetIds.size(),
            List.copyOf(actionNames),
            List.copyOf(knowledgeSourceIds),
            List.copyOf(shellModuleIds),
            List.copyOf(marketplaceDatasetIds)
        );
    }

    private Set<String> textSet(com.fasterxml.jackson.databind.JsonNode root, String arrayField, String valueField) {
        if (root == null || !root.path(arrayField).isArray()) {
            return Set.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (com.fasterxml.jackson.databind.JsonNode node : root.path(arrayField)) {
            String value = node.path(valueField).asText("").trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values;
    }

    private DeploymentVersionSummary toVersionSummary(DeploymentVersionEntity version) {
        if (version == null) {
            return null;
        }
        return new DeploymentVersionSummary(
            version.getId(),
            version.getDeploymentId(),
            version.getSourceDraftId(),
            version.getVersionLabel(),
            version.getStatus(),
            version.getConfigHash(),
            version.isReindexRequired(),
            version.getPublishedAt()
        );
    }

    private DeploymentReleaseSummary toReleaseSummary(DeploymentReleaseEntity release) {
        if (release == null) {
            return null;
        }
        return new DeploymentReleaseSummary(
            release.getId(),
            release.getDeploymentId(),
            release.getDeploymentVersionId(),
            release.getStatus(),
            release.getVerificationStatus(),
            release.getProvisioningStatus(),
            release.getProvisioningTarget(),
            release.getCurrentStepKey(),
            release.getCurrentStepDescription(),
            release.getErrorMessage(),
            release.getVerificationRunId(),
            sourcePreflightSupport.readJsonNode(release.getProvisioningDetailsJson()),
            release.getCreatedAt(),
            release.getAppliedAt(),
            release.getUpdatedAt()
        );
    }

    private ShopifyStoreLinkedCustomerSummary toLinkedCustomerSummary(PlatformCustomerEntity customer) {
        if (customer == null) {
            return null;
        }
        return new ShopifyStoreLinkedCustomerSummary(
            customer.getId(),
            customer.getName(),
            customer.getSlug(),
            customer.getStatus(),
            customer.isPlatformManaged(),
            customer.getCreatedAt(),
            customer.getUpdatedAt()
        );
    }

    private ShopifyStoreLinkedDeploymentSummary toLinkedDeploymentSummary(DeploymentEntity deployment) {
        if (deployment == null) {
            return null;
        }
        return new ShopifyStoreLinkedDeploymentSummary(
            deployment.getId(),
            deployment.getName(),
            deployment.getEnvironmentName(),
            deployment.getTemplateId(),
            deployment.getStatus(),
            deployment.getCustomerId(),
            deployment.getTenantId(),
            deployment.getActiveVersionId(),
            deployment.getRuntimeBaseUrl(),
            deployment.getConnectorBaseUrl(),
            deployment.isApprovalRequiredForApply(),
            deployment.isApprovalRequiredForDelete(),
            deployment.getCreatedAt(),
            deployment.getUpdatedAt()
        );
    }

    private ShopifyStoreLinkedConsumerSummary toLinkedConsumerSummary(PlatformConsumerEntity consumer,
                                                                     DeploymentEntity deployment) {
        if (consumer == null) {
            return null;
        }
        return new ShopifyStoreLinkedConsumerSummary(
            consumer.getConsumerId(),
            consumer.getCustomerId(),
            consumer.getDisplayName(),
            consumer.getStatus(),
            consumer.getBoundDeploymentId(),
            deployment == null ? null : deployment.getName(),
            deployment == null ? null : deployment.getEnvironmentName(),
            deployment == null ? null : deployment.getStatus(),
            consumer.getLastBoundAt(),
            consumer.getCreatedAt(),
            consumer.getUpdatedAt()
        );
    }

    private List<String> buildBindingWarnings(ShopifyStoreConnectionEntity entity,
                                              PlatformCustomerEntity customer,
                                              DeploymentEntity deployment,
                                              PlatformConsumerEntity consumer) {
        List<String> warnings = new java.util.ArrayList<>();
        if (!hasText(entity.getCustomerId())) {
            warnings.add("No platform customer is currently bound.");
        } else if (customer == null) {
            warnings.add("Bound customer " + entity.getCustomerId() + " no longer exists.");
        }

        if (!hasText(entity.getDeploymentId())) {
            warnings.add("No deployment is currently bound.");
        } else if (deployment == null) {
            warnings.add("Bound deployment " + entity.getDeploymentId() + " no longer exists.");
        }

        if (!hasText(entity.getConsumerId())) {
            warnings.add("No consumerId is currently bound.");
        } else if (consumer == null) {
            warnings.add("Bound consumer " + entity.getConsumerId() + " no longer exists.");
        }

        if (customer != null && deployment != null && !customer.getId().equals(deployment.getCustomerId())) {
            warnings.add("Bound deployment belongs to a different customer.");
        }
        if (customer != null && consumer != null && !customer.getId().equals(consumer.getCustomerId())) {
            warnings.add("Bound consumer belongs to a different customer.");
        }
        if (deployment != null && consumer != null && hasText(consumer.getBoundDeploymentId())
            && !deployment.getId().equals(consumer.getBoundDeploymentId())) {
            warnings.add("Bound consumer points to a different deployment.");
        }
        return List.copyOf(warnings);
    }

    private PlatformCustomerEntity resolveCustomer(String customerId, String deploymentId, String consumerId) {
        if (hasText(customerId)) {
            return customerRepository.findById(customerId.trim())
                .orElseThrow(() -> new ResponseStatusException(CONFLICT, "Customer not found: " + customerId));
        }
        if (hasText(deploymentId)) {
            return deploymentRepository.findById(deploymentId.trim())
                .flatMap(deployment -> customerRepository.findById(deployment.getCustomerId()))
                .orElse(null);
        }
        if (hasText(consumerId)) {
            return consumerRepository.findByConsumerIdIgnoreCase(consumerId.trim())
                .flatMap(consumer -> customerRepository.findById(consumer.getCustomerId()))
                .orElse(null);
        }
        return null;
    }

    private DeploymentEntity resolveDeployment(String deploymentId) {
        if (!hasText(deploymentId)) {
            return null;
        }
        return deploymentRepository.findById(deploymentId.trim())
            .orElseThrow(() -> new ResponseStatusException(CONFLICT, "Deployment not found: " + deploymentId));
    }

    private PlatformConsumerEntity resolveConsumer(String consumerId) {
        if (!hasText(consumerId)) {
            return null;
        }
        return consumerRepository.findByConsumerIdIgnoreCase(consumerId.trim())
            .orElseThrow(() -> new ResponseStatusException(CONFLICT, "Consumer not found: " + consumerId));
    }

    private String resolveCustomerId(PlatformCustomerEntity customer,
                                     DeploymentEntity deployment,
                                     PlatformConsumerEntity consumer) {
        if (customer != null) {
            return customer.getId();
        }
        if (deployment != null) {
            return deployment.getCustomerId();
        }
        if (consumer != null) {
            return consumer.getCustomerId();
        }
        return null;
    }

    private String normalizeShopDomain(String value) {
        if (!hasText(value)) {
            throw new ResponseStatusException(CONFLICT, "shopDomain is required.");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9.-]{1,253}[a-z0-9]")) {
            throw new ResponseStatusException(CONFLICT, "shopDomain must be a valid hostname.");
        }
        return normalized;
    }

    private String normalizeStatus(String value, String fallback) {
        return hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private String generateId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private record ResolvedStoreContext(
        PlatformManagedProductServiceEntity productService,
        PlatformCustomerEntity customer,
        DeploymentEntity deployment,
        DeploymentVersionEntity latestVersion,
        DeploymentReleaseEntity latestRelease,
        PlatformConsumerEntity consumer
    ) {
    }
}
