package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.productservice.entity.PlatformManagedProductServiceEntity;
import com.ai.fabric.platform.backend.productservice.service.PlatformManagedProductServiceService;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreConnectionSummary;
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
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class ShopifyStoreConnectionService {

    private final ShopifyStoreConnectionRepository repository;
    private final PlatformManagedProductServiceService productServiceService;
    private final PlatformCustomerRepository customerRepository;
    private final DeploymentRepository deploymentRepository;
    private final PlatformConsumerRepository consumerRepository;
    private final PlatformAuditService platformAuditService;

    public ShopifyStoreConnectionService(ShopifyStoreConnectionRepository repository,
                                         PlatformManagedProductServiceService productServiceService,
                                         PlatformCustomerRepository customerRepository,
                                         DeploymentRepository deploymentRepository,
                                         PlatformConsumerRepository consumerRepository,
                                         PlatformAuditService platformAuditService) {
        this.repository = repository;
        this.productServiceService = productServiceService;
        this.customerRepository = customerRepository;
        this.deploymentRepository = deploymentRepository;
        this.consumerRepository = consumerRepository;
        this.platformAuditService = platformAuditService;
    }

    public List<ShopifyStoreConnectionSummary> listConnections() {
        return repository.findAllByOrderByCreatedAtDesc().stream()
            .map(this::toSummary)
            .toList();
    }

    public ShopifyStoreConnectionSummary getConnection(String shopDomain) {
        return toSummary(requireConnection(shopDomain));
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
                "consumerId", entity.getConsumerId() == null ? "" : entity.getConsumerId()
            )
        );

        return toSummary(entity);
    }

    private ShopifyStoreConnectionEntity requireConnection(String shopDomain) {
        return repository.findByShopDomainIgnoreCase(normalizeShopDomain(shopDomain))
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Shopify store connection not found: " + shopDomain));
    }

    private ShopifyStoreConnectionSummary toSummary(ShopifyStoreConnectionEntity entity) {
        PlatformManagedProductServiceEntity productService = productServiceService.requireServiceById(entity.getProductServiceId());
        PlatformCustomerEntity customer = entity.getCustomerId() == null ? null : customerRepository.findById(entity.getCustomerId()).orElse(null);
        DeploymentEntity deployment = entity.getDeploymentId() == null ? null : deploymentRepository.findById(entity.getDeploymentId()).orElse(null);
        PlatformConsumerEntity consumer = entity.getConsumerId() == null ? null : consumerRepository.findByConsumerIdIgnoreCase(entity.getConsumerId()).orElse(null);
        return new ShopifyStoreConnectionSummary(
            entity.getId(),
            entity.getShopDomain(),
            entity.getDisplayName(),
            productService.getId(),
            productService.getServiceRef(),
            productService.getDisplayName(),
            entity.getCustomerId(),
            customer == null ? null : customer.getName(),
            entity.getDeploymentId(),
            deployment == null ? null : deployment.getName(),
            deployment == null ? null : deployment.getStatus(),
            entity.getConsumerId(),
            consumer == null ? null : consumer.getDisplayName(),
            entity.getInstallStatus(),
            entity.getSyncStatus(),
            entity.getSourceReadinessStatus(),
            entity.getWidgetStatus(),
            entity.getLastSourcePreflightAt(),
            entity.getLastSyncAt(),
            entity.getLastWebhookAt(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
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
}
