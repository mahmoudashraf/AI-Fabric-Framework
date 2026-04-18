package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.model.RecordShopifyStoreWidgetStatusRequest;
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
public class ShopifyStoreWidgetService {

    private final ShopifyStoreConnectionRepository repository;
    private final ShopifyStoreConnectionService shopifyStoreConnectionService;
    private final PlatformAuditService platformAuditService;
    private final ShopifyStoreSourcePreflightSupport support;

    public ShopifyStoreWidgetService(ShopifyStoreConnectionRepository repository,
                                     ShopifyStoreConnectionService shopifyStoreConnectionService,
                                     PlatformAuditService platformAuditService,
                                     ShopifyStoreSourcePreflightSupport support) {
        this.repository = repository;
        this.shopifyStoreConnectionService = shopifyStoreConnectionService;
        this.platformAuditService = platformAuditService;
        this.support = support;
    }

    @Transactional
    public ShopifyStoreConnectionSummary record(String shopDomain, RecordShopifyStoreWidgetStatusRequest request) {
        ShopifyStoreConnectionEntity store = repository.findByShopDomainIgnoreCase(normalizeShopDomain(shopDomain))
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Shopify store connection not found: " + shopDomain));
        if (request == null || !hasText(request.status())) {
            throw new ResponseStatusException(CONFLICT, "widget status is required.");
        }

        Instant now = Instant.now();
        String status = normalizeStatus(request.status());
        ObjectNode details = support.mutableDetails(store.getDetailsJson());
        ObjectNode widget = details.putObject("widget");
        widget.put("status", status);
        widget.put("checkedAt", now.toString());
        if (hasText(request.channel())) {
            widget.put("channel", request.channel().trim().toUpperCase(Locale.ROOT));
        }
        if (hasText(request.message())) {
            widget.put("message", request.message().trim());
        }

        store.setWidgetStatus(status);
        if ("ENABLED".equals(status)
            && "SYNCED".equalsIgnoreCase(store.getSyncStatus())
            && "READY".equalsIgnoreCase(store.getSourceReadinessStatus())) {
            store.setOnboardingStatus("LIVE");
        } else if ("FAILED".equals(status)) {
            store.setOnboardingStatus("BLOCKED");
        }
        store.setDetailsJson(support.writeJson(details));
        store.setUpdatedAt(now);
        repository.save(store);

        platformAuditService.record(
            "SHOPIFY_STORE_WIDGET_STATUS_RECORDED",
            "SHOPIFY_STORE_CONNECTION",
            store.getShopDomain(),
            Map.of(
                "shopDomain", store.getShopDomain(),
                "widgetStatus", status,
                "channel", hasText(request.channel()) ? request.channel().trim().toUpperCase(Locale.ROOT) : ""
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
