package com.ai.fabric.platform.backend.shopify.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class ShopifyCompanionActionCatalog {

    static final String ACTION_PLUGIN_ID = "mkp-action-shopify-companion-read";

    private static final List<String> DEFAULT_ACTION_IDS = List.of(
        "list_products",
        "search_products",
        "get_product_details",
        "check_availability",
        "get_policy",
        "add_product_to_cart",
        "add_to_cart",
        "update_cart_quantity"
    );

    private ShopifyCompanionActionCatalog() {
    }

    static Set<String> routeActionIds(JsonNode actionsConfig) {
        LinkedHashSet<String> actionIds = new LinkedHashSet<>();
        if (actionsConfig != null && actionsConfig.path("actions").isArray()) {
            for (JsonNode action : actionsConfig.path("actions")) {
                String actionId = blankToNull(action.path("name").asText(null));
                if (actionId != null && isShopifyCompanionAction(action, actionId)) {
                    actionIds.add(actionId);
                }
            }
        }
        actionIds.addAll(DEFAULT_ACTION_IDS);
        return actionIds;
    }

    private static boolean isShopifyCompanionAction(JsonNode action, String actionId) {
        if (DEFAULT_ACTION_IDS.contains(actionId)) {
            return true;
        }
        String pluginId = blankToNull(action.path("marketplacePluginId").asText(null));
        if (ACTION_PLUGIN_ID.equals(pluginId)) {
            return true;
        }
        String category = blankToNull(action.path("category").asText(null));
        return "shopify-companion".equals(normalize(category));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
