package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.model.RecordShopifyStoreSyncStatusRequest;
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
public class ShopifyStoreSyncService {

    private final ShopifyStoreConnectionRepository repository;
    private final ShopifyStoreConnectionService shopifyStoreConnectionService;
    private final PlatformAuditService platformAuditService;
    private final ShopifyStoreSourcePreflightSupport support;

    public ShopifyStoreSyncService(ShopifyStoreConnectionRepository repository,
                                   ShopifyStoreConnectionService shopifyStoreConnectionService,
                                   PlatformAuditService platformAuditService,
                                   ShopifyStoreSourcePreflightSupport support) {
        this.repository = repository;
        this.shopifyStoreConnectionService = shopifyStoreConnectionService;
        this.platformAuditService = platformAuditService;
        this.support = support;
    }

    @Transactional
    public ShopifyStoreConnectionSummary record(String shopDomain, RecordShopifyStoreSyncStatusRequest request) {
        ShopifyStoreConnectionEntity store = repository.findByShopDomainIgnoreCase(normalizeShopDomain(shopDomain))
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Shopify store connection not found: " + shopDomain));
        if (request == null || !hasText(request.status())) {
            throw new ResponseStatusException(CONFLICT, "sync status is required.");
        }

        Instant now = Instant.now();
        String status = normalizeStatus(request.status());
        ObjectNode details = support.mutableDetails(store.getDetailsJson());
        ObjectNode sync = details.putObject("sync");
        sync.put("status", status);
        sync.put("checkedAt", now.toString());
        if (hasText(request.mode())) {
            sync.put("mode", request.mode().trim().toUpperCase(Locale.ROOT));
        }
        sync.put("documentCount", Math.max(request.documentCount() == null ? 0 : request.documentCount(), 0));
        if (hasText(request.message())) {
            sync.put("message", request.message().trim());
        }

        store.setSyncStatus(status);
        store.setLastSyncAt(now);
        if ("FAILED".equals(status)) {
            store.setOnboardingStatus("BLOCKED");
        }
        store.setDetailsJson(support.writeJson(details));
        store.setUpdatedAt(now);
        repository.save(store);

        platformAuditService.record(
            "SHOPIFY_STORE_SYNC_RECORDED",
            "SHOPIFY_STORE_CONNECTION",
            store.getShopDomain(),
            Map.of(
                "shopDomain", store.getShopDomain(),
                "syncStatus", status,
                "documentCount", Integer.toString(Math.max(request.documentCount() == null ? 0 : request.documentCount(), 0))
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

    private String normalizeStatus(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
