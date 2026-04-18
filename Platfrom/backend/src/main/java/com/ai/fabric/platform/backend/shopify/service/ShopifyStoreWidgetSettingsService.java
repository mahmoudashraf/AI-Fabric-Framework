package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreConnectionSummary;
import com.ai.fabric.platform.backend.shopify.model.UpdateShopifyStoreWidgetSettingsRequest;
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
public class ShopifyStoreWidgetSettingsService {

    private static final int MAX_LAUNCHER_LABEL_LENGTH = 60;
    private static final int MAX_WELCOME_MESSAGE_LENGTH = 320;
    private static final String DEFAULT_LAUNCHER_LABEL = "Ask the store assistant";
    private static final String DEFAULT_WELCOME_MESSAGE =
        "Store assistant is ready. Ask about products, policies, or collections.";

    private final ShopifyStoreConnectionRepository repository;
    private final ShopifyStoreConnectionService connectionService;
    private final ShopifyStoreSourcePreflightSupport support;
    private final PlatformAuditService auditService;

    public ShopifyStoreWidgetSettingsService(ShopifyStoreConnectionRepository repository,
                                             ShopifyStoreConnectionService connectionService,
                                             ShopifyStoreSourcePreflightSupport support,
                                             PlatformAuditService auditService) {
        this.repository = repository;
        this.connectionService = connectionService;
        this.support = support;
        this.auditService = auditService;
    }

    @Transactional
    public ShopifyStoreConnectionSummary update(String shopDomain, UpdateShopifyStoreWidgetSettingsRequest request) {
        ShopifyStoreConnectionEntity store = repository.findByShopDomainIgnoreCase(normalizeShopDomain(shopDomain))
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Shopify store connection not found: " + shopDomain));
        if (request == null) {
            throw new ResponseStatusException(CONFLICT, "widget settings payload is required.");
        }

        String launcherLabel = normalizeLauncherLabel(request.launcherLabel());
        String welcomeMessage = normalizeWelcomeMessage(request.welcomeMessage());
        Instant now = Instant.now();

        ObjectNode details = support.mutableDetails(store.getDetailsJson());
        ObjectNode widget = details.with("widget");
        ObjectNode settings = widget.putObject("settings");
        settings.put("launcherLabel", launcherLabel);
        settings.put("welcomeMessage", welcomeMessage);
        settings.put("updatedAt", now.toString());
        store.setDetailsJson(support.writeJson(details));
        store.setUpdatedAt(now);
        repository.save(store);

        auditService.record(
            "SHOPIFY_STORE_WIDGET_SETTINGS_UPDATED",
            "SHOPIFY_STORE_CONNECTION",
            store.getShopDomain(),
            Map.of(
                "shopDomain", store.getShopDomain(),
                "launcherLabel", launcherLabel,
                "welcomeMessageLength", Integer.toString(welcomeMessage.length())
            )
        );

        return connectionService.getConnection(store.getShopDomain());
    }

    private String normalizeShopDomain(String value) {
        if (!hasText(value)) {
            throw new ResponseStatusException(CONFLICT, "shopDomain is required.");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeLauncherLabel(String value) {
        String normalized = hasText(value) ? value.trim() : DEFAULT_LAUNCHER_LABEL;
        if (normalized.length() > MAX_LAUNCHER_LABEL_LENGTH) {
            throw new ResponseStatusException(CONFLICT, "launcherLabel exceeds " + MAX_LAUNCHER_LABEL_LENGTH + " characters.");
        }
        return normalized;
    }

    private String normalizeWelcomeMessage(String value) {
        String normalized = hasText(value) ? value.trim() : DEFAULT_WELCOME_MESSAGE;
        if (normalized.length() > MAX_WELCOME_MESSAGE_LENGTH) {
            throw new ResponseStatusException(CONFLICT, "welcomeMessage exceeds " + MAX_WELCOME_MESSAGE_LENGTH + " characters.");
        }
        return normalized;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
