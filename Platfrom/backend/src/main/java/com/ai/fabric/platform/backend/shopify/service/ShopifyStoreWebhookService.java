package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.model.RecordShopifyStoreWebhookEventRequest;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreConnectionSummary;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
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
public class ShopifyStoreWebhookService {

    private final ShopifyStoreConnectionRepository repository;
    private final ShopifyStoreConnectionService shopifyStoreConnectionService;
    private final ShopifyStoreSourcePreflightSupport support;
    private final ShopifyStoreVectorizationEventService vectorizationEventService;
    private final PlatformAuditService platformAuditService;

    public ShopifyStoreWebhookService(ShopifyStoreConnectionRepository repository,
                                      ShopifyStoreConnectionService shopifyStoreConnectionService,
                                      ShopifyStoreSourcePreflightSupport support,
                                      ShopifyStoreVectorizationEventService vectorizationEventService,
                                      PlatformAuditService platformAuditService) {
        this.repository = repository;
        this.shopifyStoreConnectionService = shopifyStoreConnectionService;
        this.support = support;
        this.vectorizationEventService = vectorizationEventService;
        this.platformAuditService = platformAuditService;
    }

    @Transactional
    public ShopifyStoreConnectionSummary record(String shopDomain, RecordShopifyStoreWebhookEventRequest request) {
        ShopifyStoreConnectionEntity store = repository.findByShopDomainIgnoreCase(normalizeShopDomain(shopDomain))
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Shopify store connection not found: " + shopDomain));
        if (request == null || !hasText(request.topic())) {
            throw new ResponseStatusException(CONFLICT, "webhook topic is required.");
        }

        Instant now = Instant.now();
        String topic = request.topic().trim();
        String eventType = normalizeText(request.eventType(), "RECEIVED");
        String sourceCategory = normalizeText(request.sourceCategory(), null);
        boolean invalidateSync = Boolean.TRUE.equals(request.invalidateSync());
        String message = normalizeText(request.message(), null);

        ObjectNode details = support.mutableDetails(store.getDetailsJson());
        ObjectNode webhook = details.putObject("webhook");
        webhook.put("topic", topic);
        webhook.put("eventType", eventType);
        webhook.put("receivedAt", now.toString());
        webhook.put("invalidateSync", invalidateSync);
        if (sourceCategory != null) {
            webhook.put("sourceCategory", sourceCategory);
        }
        if (message != null) {
            webhook.put("message", message);
        }

        if (invalidateSync) {
            ObjectNode sync = details.putObject("sync");
            sync.put("status", "NOT_SYNCED");
            sync.put("checkedAt", now.toString());
            sync.put("mode", "INCREMENTAL");
            if (message != null) {
                sync.put("message", message);
            } else {
                sync.put("message", "Shopify webhook received: " + topic);
            }
            if (!sync.has("documentCount")) {
                sync.put("documentCount", 0);
            }
            store.setSyncStatus("NOT_SYNCED");
            if (!"UNINSTALLED".equalsIgnoreCase(store.getInstallStatus())
                && !"BLOCKED".equalsIgnoreCase(store.getOnboardingStatus())) {
                store.setOnboardingStatus("PLATFORM_BOOTSTRAPPED");
            }
        }

        store.setLastWebhookAt(now);
        store.setDetailsJson(support.writeJson(details));
        store.setUpdatedAt(now);
        repository.save(store);
        if (sourceCategory != null
            && ShopifyStoreVectorizationConstants.SOURCE_CATEGORIES.contains(sourceCategory.toLowerCase(Locale.ROOT))) {
            vectorizationEventService.ingest(store, request);
        }

        platformAuditService.record(
            "SHOPIFY_STORE_WEBHOOK_RECORDED",
            "SHOPIFY_STORE_CONNECTION",
            store.getShopDomain(),
            Map.of(
                "shopDomain", store.getShopDomain(),
                "topic", topic,
                "eventType", eventType,
                "sourceCategory", sourceCategory == null ? "" : sourceCategory,
                "invalidateSync", Boolean.toString(invalidateSync)
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

    private String normalizeText(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
