package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreConnectionSummary;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.model.UpdatePlatformConsumerBindingRequest;
import com.ai.fabric.platform.backend.tenant.model.UpdatePlatformConsumerRequest;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerConsumerService;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class ShopifyStoreUninstallService {

    private final ShopifyStoreConnectionRepository repository;
    private final ShopifyStoreConnectionService shopifyStoreConnectionService;
    private final ShopifyStoreSourcePreflightSupport support;
    private final ShopifyStoreCredentialService shopifyStoreCredentialService;
    private final ShopifyStoreDocumentSyncService shopifyStoreDocumentSyncService;
    private final PlatformConsumerRepository consumerRepository;
    private final PlatformCustomerConsumerService platformCustomerConsumerService;
    private final PlatformAuditService platformAuditService;

    public ShopifyStoreUninstallService(ShopifyStoreConnectionRepository repository,
                                        ShopifyStoreConnectionService shopifyStoreConnectionService,
                                        ShopifyStoreSourcePreflightSupport support,
                                        ShopifyStoreCredentialService shopifyStoreCredentialService,
                                        ShopifyStoreDocumentSyncService shopifyStoreDocumentSyncService,
                                        PlatformConsumerRepository consumerRepository,
                                        PlatformCustomerConsumerService platformCustomerConsumerService,
                                        PlatformAuditService platformAuditService) {
        this.repository = repository;
        this.shopifyStoreConnectionService = shopifyStoreConnectionService;
        this.support = support;
        this.shopifyStoreCredentialService = shopifyStoreCredentialService;
        this.shopifyStoreDocumentSyncService = shopifyStoreDocumentSyncService;
        this.consumerRepository = consumerRepository;
        this.platformCustomerConsumerService = platformCustomerConsumerService;
        this.platformAuditService = platformAuditService;
    }

    @Transactional
    public ShopifyStoreConnectionSummary markUninstalled(String shopDomain, String reason) {
        ShopifyStoreConnectionEntity store = repository.findByShopDomainIgnoreCase(normalizeShopDomain(shopDomain))
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Shopify store connection not found: " + shopDomain));

        Instant now = Instant.now();
        String resolvedReason = hasText(reason) ? reason.trim() : "Shopify app uninstall cleanup.";
        PlatformConsumerEntity consumer = hasText(store.getConsumerId())
            ? consumerRepository.findByConsumerIdIgnoreCase(store.getConsumerId()).orElse(null)
            : null;
        CleanupStatus credentialCleanup = clearCredentialsSafely(store.getShopDomain());
        ShopifyStoreDocumentSyncService.DocumentCleanupResult documentCleanup =
            shopifyStoreDocumentSyncService.cleanupTrackedDocuments(store, resolvedReason);

        boolean consumerUnbound = false;
        boolean consumerDisabled = false;
        if (consumer != null && hasText(consumer.getBoundDeploymentId())) {
            platformCustomerConsumerService.updateBinding(
                consumer.getCustomerId(),
                consumer.getConsumerId(),
                new UpdatePlatformConsumerBindingRequest(null, resolvedReason)
            );
            consumerUnbound = true;
        }
        if (consumer != null && !"DISABLED".equalsIgnoreCase(consumer.getStatus())) {
            platformCustomerConsumerService.updateConsumer(
                consumer.getCustomerId(),
                consumer.getConsumerId(),
                new UpdatePlatformConsumerRequest(
                    consumer.getDisplayName(),
                    consumer.getDescription(),
                    "DISABLED"
                )
            );
            consumerDisabled = true;
        }

        ObjectNode details = support.mutableDetails(store.getDetailsJson());
        ObjectNode uninstall = details.putObject("uninstall");
        uninstall.put("status", "UNINSTALLED");
        uninstall.put("recordedAt", now.toString());
        uninstall.put("reason", resolvedReason);
        uninstall.put("consumerUnbound", consumerUnbound);
        uninstall.put("consumerDisabled", consumerDisabled);
        uninstall.put("credentialCleanupStatus", credentialCleanup.status());
        uninstall.put("credentialCleanupMessage", credentialCleanup.message());
        uninstall.put("documentCleanupStatus", documentCleanup.status());
        uninstall.put("documentCleanupMessage", documentCleanup.message());
        uninstall.put("clearedDocumentCount", documentCleanup.deletedDocumentCount());

        ObjectNode widget = details.with("widget");
        widget.put("status", "NOT_ENABLED");
        widget.put("checkedAt", now.toString());
        widget.put("activationMode", "SHOPIFY_APP_UNINSTALLED");
        widget.put("message", "Storefront widget disabled because the Shopify app was uninstalled.");

        store.setInstallStatus("UNINSTALLED");
        store.setSyncStatus("FAILED".equalsIgnoreCase(documentCleanup.status()) ? "FAILED" : "NOT_SYNCED");
        store.setSourceReadinessStatus("NOT_RUN");
        store.setWidgetStatus("NOT_ENABLED");
        store.setOnboardingStatus("BLOCKED");
        store.setLastWebhookAt(now);
        store.setDetailsJson(support.writeJson(details));
        store.setUpdatedAt(now);
        repository.save(store);

        platformAuditService.record(
            "SHOPIFY_STORE_UNINSTALLED",
            "SHOPIFY_STORE_CONNECTION",
            store.getShopDomain(),
            Map.of(
                "shopDomain", store.getShopDomain(),
                "consumerId", blankToEmpty(store.getConsumerId()),
                "deploymentId", blankToEmpty(store.getDeploymentId()),
                "credentialCleanupStatus", credentialCleanup.status(),
                "documentCleanupStatus", documentCleanup.status(),
                "clearedDocumentCount", Integer.toString(documentCleanup.deletedDocumentCount()),
                "consumerUnbound", Boolean.toString(consumerUnbound),
                "consumerDisabled", Boolean.toString(consumerDisabled),
                "reason", resolvedReason
            )
        );

        return shopifyStoreConnectionService.getConnection(store.getShopDomain());
    }

    private String normalizeShopDomain(String shopDomain) {
        if (!hasText(shopDomain)) {
            throw new ResponseStatusException(CONFLICT, "shopDomain is required.");
        }
        return shopDomain.trim().toLowerCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private CleanupStatus clearCredentialsSafely(String shopDomain) {
        try {
            shopifyStoreCredentialService.clear(shopDomain);
            return new CleanupStatus("CLEARED", "Shopify access credentials were cleared.");
        } catch (RuntimeException ex) {
            return new CleanupStatus("FAILED", firstMessage(ex, "Shopify access credentials could not be cleared."));
        }
    }

    private String firstMessage(RuntimeException ex, String fallback) {
        if (ex instanceof ResponseStatusException responseStatusException && hasText(responseStatusException.getReason())) {
            return responseStatusException.getReason().trim();
        }
        return hasText(ex.getMessage()) ? ex.getMessage().trim() : fallback;
    }

    private record CleanupStatus(String status, String message) {
    }
}
