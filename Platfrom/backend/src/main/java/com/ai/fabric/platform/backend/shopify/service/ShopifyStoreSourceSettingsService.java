package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreConnectionSummary;
import com.ai.fabric.platform.backend.shopify.model.UpdateShopifyStoreSourceSettingsRequest;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class ShopifyStoreSourceSettingsService {

    private final ShopifyStoreConnectionRepository repository;
    private final ShopifyStoreConnectionService connectionService;
    private final ShopifyStoreSourcePreflightSupport support;
    private final PlatformAuditService auditService;

    public ShopifyStoreSourceSettingsService(ShopifyStoreConnectionRepository repository,
                                             ShopifyStoreConnectionService connectionService,
                                             ShopifyStoreSourcePreflightSupport support,
                                             PlatformAuditService auditService) {
        this.repository = repository;
        this.connectionService = connectionService;
        this.support = support;
        this.auditService = auditService;
    }

    @Transactional
    public ShopifyStoreConnectionSummary update(String shopDomain, UpdateShopifyStoreSourceSettingsRequest request) {
        ShopifyStoreConnectionEntity store = repository.findByShopDomainIgnoreCase(normalizeShopDomain(shopDomain))
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Shopify store connection not found: " + shopDomain));
        if (request == null) {
            throw new ResponseStatusException(CONFLICT, "source settings payload is required.");
        }
        if (request.productsEnabled() == null
            && request.collectionsEnabled() == null
            && request.pagesEnabled() == null
            && request.policiesEnabled() == null
            && request.articlesEnabled() == null
            && request.metaobjectsEnabled() == null) {
            throw new ResponseStatusException(CONFLICT, "At least one source category setting is required.");
        }

        boolean productsEnabled = resolve(request.productsEnabled(), store.isProductsEnabled());
        boolean collectionsEnabled = resolve(request.collectionsEnabled(), store.isCollectionsEnabled());
        boolean pagesEnabled = resolve(request.pagesEnabled(), store.isPagesEnabled());
        boolean policiesEnabled = resolve(request.policiesEnabled(), store.isPoliciesEnabled());
        boolean articlesEnabled = resolve(request.articlesEnabled(), store.isArticlesEnabled());
        boolean metaobjectsEnabled = resolve(request.metaobjectsEnabled(), store.isMetaobjectsEnabled());
        List<String> enabledCategories = enabledCategories(
            productsEnabled,
            collectionsEnabled,
            pagesEnabled,
            policiesEnabled,
            articlesEnabled,
            metaobjectsEnabled
        );
        if (enabledCategories.isEmpty()) {
            throw new ResponseStatusException(CONFLICT, "At least one Shopify source category must remain enabled.");
        }

        boolean changed = store.isProductsEnabled() != productsEnabled
            || store.isCollectionsEnabled() != collectionsEnabled
            || store.isPagesEnabled() != pagesEnabled
            || store.isPoliciesEnabled() != policiesEnabled
            || store.isArticlesEnabled() != articlesEnabled
            || store.isMetaobjectsEnabled() != metaobjectsEnabled;
        Instant now = Instant.now();

        store.setProductsEnabled(productsEnabled);
        store.setCollectionsEnabled(collectionsEnabled);
        store.setPagesEnabled(pagesEnabled);
        store.setPoliciesEnabled(policiesEnabled);
        store.setArticlesEnabled(articlesEnabled);
        store.setMetaobjectsEnabled(metaobjectsEnabled);
        if (changed) {
            store.setSourceReadinessStatus("NOT_RUN");
            store.setSyncStatus("NOT_SYNCED");
            store.setLastSourcePreflightAt(null);
        }
        store.setUpdatedAt(now);

        ObjectNode details = support.mutableDetails(store.getDetailsJson());
        ObjectNode sourceSettings = details.putObject("sourceSettings");
        ArrayNode enabledNode = sourceSettings.putArray("enabledCategories");
        enabledCategories.forEach(enabledNode::add);
        sourceSettings.put("productsEnabled", productsEnabled);
        sourceSettings.put("collectionsEnabled", collectionsEnabled);
        sourceSettings.put("pagesEnabled", pagesEnabled);
        sourceSettings.put("policiesEnabled", policiesEnabled);
        sourceSettings.put("articlesEnabled", articlesEnabled);
        sourceSettings.put("metaobjectsEnabled", metaobjectsEnabled);
        sourceSettings.put("readinessInvalidated", changed);
        sourceSettings.put("updatedAt", now.toString());
        store.setDetailsJson(support.writeJson(details));
        repository.save(store);

        auditService.record(
            "SHOPIFY_STORE_SOURCE_SETTINGS_UPDATED",
            "SHOPIFY_STORE_CONNECTION",
            store.getShopDomain(),
            Map.of(
                "shopDomain", store.getShopDomain(),
                "enabledCategories", String.join(",", enabledCategories),
                "readinessInvalidated", Boolean.toString(changed),
                "productsEnabled", Boolean.toString(productsEnabled),
                "collectionsEnabled", Boolean.toString(collectionsEnabled),
                "pagesEnabled", Boolean.toString(pagesEnabled),
                "policiesEnabled", Boolean.toString(policiesEnabled),
                "articlesEnabled", Boolean.toString(articlesEnabled),
                "metaobjectsEnabled", Boolean.toString(metaobjectsEnabled)
            )
        );

        return connectionService.getConnection(store.getShopDomain());
    }

    private String normalizeShopDomain(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(CONFLICT, "shopDomain is required.");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean resolve(Boolean requested, boolean current) {
        return requested == null ? current : requested;
    }

    private List<String> enabledCategories(boolean productsEnabled,
                                           boolean collectionsEnabled,
                                           boolean pagesEnabled,
                                           boolean policiesEnabled,
                                           boolean articlesEnabled,
                                           boolean metaobjectsEnabled) {
        java.util.ArrayList<String> categories = new java.util.ArrayList<>();
        if (productsEnabled) {
            categories.add("products");
        }
        if (collectionsEnabled) {
            categories.add("collections");
        }
        if (pagesEnabled) {
            categories.add("pages");
        }
        if (policiesEnabled) {
            categories.add("policies");
        }
        if (articlesEnabled) {
            categories.add("articles");
        }
        if (metaobjectsEnabled) {
            categories.add("metaobjects");
        }
        return List.copyOf(categories);
    }
}
