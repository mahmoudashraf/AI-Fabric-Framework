package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.config.ShopifyCompanionBootstrapProperties;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

final class ShopifyCompanionPluginSelection {

    static final String ACTION_READ_PLUGIN_ID = "mkp-action-shopify-companion-read";
    static final String DATA_CATALOG_PLUGIN_ID = "mkp-data-shopify-catalog";
    static final String DATA_POLICIES_PLUGIN_ID = "mkp-data-shopify-policies";
    static final String INFERENCE_SHARED_PLUGIN_ID = "mkp-inference-shared-embeddings";
    static final String LEGACY_INFERENCE_PLUGIN_ID = "mkp-inference-shopify-companion-default";
    static final String ENTITY_TYPE_PRODUCT = "product";
    static final String ENTITY_TYPE_SUPPORT_POLICY = "support-policy";

    private ShopifyCompanionPluginSelection() {
    }

    static String canonicalizePluginId(String pluginId) {
        if (pluginId == null || pluginId.isBlank()) {
            return "";
        }
        String normalized = pluginId.trim();
        return LEGACY_INFERENCE_PLUGIN_ID.equalsIgnoreCase(normalized)
            ? INFERENCE_SHARED_PLUGIN_ID
            : normalized;
    }

    static boolean requiresCatalogData(ShopifyStoreConnectionEntity store) {
        return store != null && (store.isProductsEnabled() || store.isCollectionsEnabled());
    }

    static boolean requiresPoliciesData(ShopifyStoreConnectionEntity store) {
        return store != null && (store.isPagesEnabled() || store.isPoliciesEnabled() || store.isArticlesEnabled() || store.isMetaobjectsEnabled());
    }

    static List<String> selectedCategories(ShopifyStoreConnectionEntity store) {
        List<String> categories = new ArrayList<>();
        if (store == null) {
            return categories;
        }
        if (store.isProductsEnabled()) {
            categories.add("products");
        }
        if (store.isCollectionsEnabled()) {
            categories.add("collections");
        }
        if (store.isPagesEnabled()) {
            categories.add("pages");
        }
        if (store.isPoliciesEnabled()) {
            categories.add("policies");
        }
        if (store.isArticlesEnabled()) {
            categories.add("articles");
        }
        if (store.isMetaobjectsEnabled()) {
            categories.add("metaobjects");
        }
        return List.copyOf(categories);
    }

    static List<String> selectedEntityTypes(ShopifyStoreConnectionEntity store) {
        List<String> entityTypes = new ArrayList<>();
        if (requiresCatalogData(store)) {
            entityTypes.add(ENTITY_TYPE_PRODUCT);
        }
        if (requiresPoliciesData(store)) {
            entityTypes.add(ENTITY_TYPE_SUPPORT_POLICY);
        }
        return List.copyOf(entityTypes);
    }

    static LinkedHashSet<String> desiredManagedPluginIds(ShopifyCompanionBootstrapProperties properties,
                                                         ShopifyStoreConnectionEntity store) {
        LinkedHashSet<String> desiredPluginIds = new LinkedHashSet<>();
        properties.defaultPluginIds().stream()
            .map(ShopifyCompanionPluginSelection::canonicalizePluginId)
            .filter(pluginId -> !pluginId.isBlank())
            .filter(pluginId -> !DATA_CATALOG_PLUGIN_ID.equalsIgnoreCase(pluginId))
            .filter(pluginId -> !DATA_POLICIES_PLUGIN_ID.equalsIgnoreCase(pluginId))
            .forEach(desiredPluginIds::add);
        if (requiresCatalogData(store)) {
            desiredPluginIds.add(DATA_CATALOG_PLUGIN_ID);
        }
        if (requiresPoliciesData(store)) {
            desiredPluginIds.add(DATA_POLICIES_PLUGIN_ID);
        }
        return desiredPluginIds;
    }

    static LinkedHashSet<String> desiredVectorizationPluginIds(ShopifyCompanionBootstrapProperties properties,
                                                               ShopifyStoreConnectionEntity store) {
        LinkedHashSet<String> desiredPluginIds = desiredManagedPluginIds(properties, store);
        desiredPluginIds.removeIf(ShopifyCompanionPluginSelection::isInferencePluginId);
        return desiredPluginIds;
    }

    static boolean isInferencePluginId(String pluginId) {
        return pluginId != null && pluginId.trim().toLowerCase(java.util.Locale.ROOT).startsWith("mkp-inference-");
    }

    static LinkedHashSet<String> managedDataPluginIds() {
        LinkedHashSet<String> pluginIds = new LinkedHashSet<>();
        pluginIds.add(DATA_CATALOG_PLUGIN_ID);
        pluginIds.add(DATA_POLICIES_PLUGIN_ID);
        return pluginIds;
    }
}
