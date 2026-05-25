package com.ai.fabric.platform.backend.shopify.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class ShopifyCompanionActionCatalog {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final Set<String> DISABLED_ACTION_IDS = Set.of(
        "relationship_query"
    );

    private static final List<String> DEFAULT_ACTION_IDS = List.of(
        "shopify_search_catalog",
        "shopify_get_product_details",
        "shopify_search_policies",
        "shopify_get_cart",
        "shopify_update_cart"
    );

    private static final Set<String> PRODUCT_FILTER_ACTION_IDS = Set.of(
        "list_products",
        "search_products"
    );

    private static final List<String> PRODUCT_FIELDS = List.of(
        "title",
        "handle",
        "vendor",
        "productType",
        "available",
        "price",
        "primarySku",
        "inventoryQuantity",
        "storefrontUrl",
        "reviewSignalsPresent",
        "reviewAverage",
        "reviewCount"
    );

    private static final List<String> POLICY_FIELDS = List.of(
        "id",
        "title",
        "type",
        "body",
        "url",
        "updatedAt"
    );

    private ShopifyCompanionActionCatalog() {
    }

    static boolean ensureLlmFactsDefaults(ObjectNode actionsConfig) {
        if (actionsConfig == null || !actionsConfig.path("actions").isArray()) {
            return false;
        }
        ArrayNode actions = (ArrayNode) actionsConfig.path("actions");
        boolean changed = false;
        for (int i = actions.size() - 1; i >= 0; i--) {
            JsonNode action = actions.get(i);
            String actionId = firstNonBlank(
                action.path("name").asText(null),
                action.path("actionId").asText(null),
                action.path("id").asText(null)
            );
            if (isDisabledAction(actionId)) {
                actions.remove(i);
                changed = true;
            }
        }
        for (JsonNode action : actions) {
            if (!(action instanceof ObjectNode actionNode)) {
                continue;
            }
            String actionId = firstNonBlank(
                action.path("name").asText(null),
                action.path("actionId").asText(null),
                action.path("id").asText(null)
            );
            if (actionId == null || !isShopifyCompanionAction(action, actionId) || !isReadAction(action)) {
                continue;
            }
            changed |= ensureProductFilterParams(actionNode, actionId);
            if (hasConfiguredLlmFacts(action.path("llmFacts"))) {
                continue;
            }
            ObjectNode llmFacts = defaultLlmFacts(actionId);
            if (llmFacts == null) {
                continue;
            }
            actionNode.set("llmFacts", llmFacts);
            changed = true;
        }
        return changed;
    }

    static Set<String> routeActionIds(JsonNode actionsConfig) {
        LinkedHashSet<String> actionIds = new LinkedHashSet<>();
        if (actionsConfig != null && actionsConfig.path("actions").isArray()) {
            for (JsonNode action : actionsConfig.path("actions")) {
                String actionId = firstNonBlank(
                    action.path("name").asText(null),
                    action.path("actionId").asText(null),
                    action.path("id").asText(null)
                );
                if (actionId != null && !isDisabledAction(actionId) && isShopifyCompanionAction(action, actionId)) {
                    actionIds.add(actionId);
                }
            }
        }
        actionIds.addAll(DEFAULT_ACTION_IDS);
        return actionIds;
    }

    private static boolean isReadAction(JsonNode action) {
        if (action == null || !action.isObject()) {
            return false;
        }
        if (action.path("readOnly").isBoolean()) {
            return action.path("readOnly").asBoolean();
        }
        String accessMode = blankToNull(action.path("accessMode").asText(null));
        return accessMode == null || "READ".equalsIgnoreCase(accessMode);
    }

    private static boolean hasConfiguredLlmFacts(JsonNode llmFacts) {
        if (llmFacts == null || !llmFacts.isObject()) {
            return false;
        }
        return llmFacts.path("copyFields").isArray() && !llmFacts.path("copyFields").isEmpty()
            || llmFacts.path("lists").isArray() && !llmFacts.path("lists").isEmpty()
            || llmFacts.path("objects").isArray() && !llmFacts.path("objects").isEmpty();
    }

    private static boolean ensureProductFilterParams(ObjectNode action, String actionId) {
        if (action == null || !PRODUCT_FILTER_ACTION_IDS.contains(normalize(actionId))) {
            return false;
        }
        ArrayNode params;
        if (action.path("params").isArray()) {
            params = (ArrayNode) action.path("params");
        } else {
            params = JSON.arrayNode();
            action.set("params", params);
        }
        boolean changed = false;
        changed |= ensureParam(params, "maxPrice", "Maximum product price from a shopper-stated numeric upper-bound price constraint. Set only when directly implied; keep the topic in query.", "NUMBER", false);
        changed |= ensureParam(params, "availableOnly", "True only when the shopper asks for products that are available or in stock.", "BOOLEAN", false);
        return changed;
    }

    private static boolean ensureParam(ArrayNode params, String name, String description, String type, boolean required) {
        if (params == null || name == null) {
            return false;
        }
        for (JsonNode param : params) {
            if (name.equals(param.path("name").asText(null))) {
                return false;
            }
        }
        ObjectNode param = JSON.objectNode();
        param.put("name", name);
        param.put("description", description);
        param.put("type", type);
        param.put("required", required);
        params.add(param);
        return true;
    }

    private static ObjectNode defaultLlmFacts(String actionId) {
        return switch (normalize(actionId)) {
            case "list_products", "search_products" -> listProductFacts("items", "products");
            case "get_product_details" -> productDetailsFacts();
            case "check_availability" -> availabilityFacts();
            case "get_policy" -> policyFacts();
            default -> null;
        };
    }

    private static ObjectNode listProductFacts(String sourcePath, String target) {
        ObjectNode facts = baseFacts("query", "count");
        ArrayNode lists = JSON.arrayNode();
        lists.add(productList(sourcePath, target, 8));
        facts.set("lists", lists);
        return facts;
    }

    private static ObjectNode productDetailsFacts() {
        ObjectNode facts = baseFacts("lookup", "lookupMethod");
        ArrayNode objects = JSON.arrayNode();
        ObjectNode product = JSON.objectNode();
        product.put("sourcePath", "product");
        product.put("target", "product");
        product.set("includeFields", array(PRODUCT_FIELDS));
        product.put("fallbackContentField", "description");
        product.put("fallbackContentMaxChars", 700);
        objects.add(product);
        facts.set("objects", objects);
        return facts;
    }

    private static ObjectNode availabilityFacts() {
        return baseFacts(
            "lookup",
            "lookupMethod",
            "sku",
            "productTitle",
            "productHandle",
            "variantTitle",
            "available",
            "inventoryQuantity",
            "storefrontUrl"
        );
    }

    private static ObjectNode policyFacts() {
        ObjectNode facts = baseFacts("query", "count");
        ArrayNode lists = JSON.arrayNode();
        ObjectNode policies = JSON.objectNode();
        policies.put("sourcePath", "items");
        policies.put("target", "policies");
        policies.put("maxItems", 5);
        policies.set("includeFields", array(POLICY_FIELDS));
        policies.put("fallbackContentField", "body");
        policies.put("fallbackContentMaxChars", 900);
        policies.set("rankRules", rankBy("title"));
        lists.add(policies);
        facts.set("lists", lists);
        return facts;
    }

    private static ObjectNode baseFacts(String... copyFields) {
        ObjectNode facts = JSON.objectNode();
        facts.put("rootPath", "data");
        facts.set("copyFields", array(copyFields));
        return facts;
    }

    private static ObjectNode productList(String sourcePath, String target, int maxItems) {
        ObjectNode list = JSON.objectNode();
        list.put("sourcePath", sourcePath);
        list.put("target", target);
        list.put("maxItems", maxItems);
        list.set("includeFields", array(PRODUCT_FIELDS));
        list.put("fallbackContentField", "description");
        list.put("fallbackContentMaxChars", 700);
        list.set("rankRules", productRankRules("price", "available"));
        list.set("constraints", productConstraints(
            "price",
            "available",
            "title",
            "handle",
            "available",
            "price",
            "inventoryQuantity",
            "storefrontUrl",
            "reviewSignalsPresent",
            "reviewAverage",
            "reviewCount"
        ));
        list.set("summaries", productSummaries(target, "price", "title", "available", "inventoryQuantity", "storefrontUrl"));
        return list;
    }

    private static ArrayNode productRankRules(String priceField, String availableField) {
        ArrayNode rules = JSON.arrayNode();
        rules.add(priceUpperBoundRule(priceField));
        rules.add(availableRule(availableField));
        return rules;
    }

    private static ObjectNode productConstraints(String priceField, String availableField, String... includeFields) {
        ObjectNode constraints = JSON.objectNode();
        constraints.put("target", "productConstraintMatches");
        constraints.put("countTarget", "productConstraintMatchCount");
        constraints.set("includeFields", array(includeFields));
        ArrayNode rules = JSON.arrayNode();
        rules.add(priceUpperBoundRule(priceField));
        rules.add(availableRule(availableField));
        constraints.set("rules", rules);
        return constraints;
    }

    private static ArrayNode productSummaries(String target,
                                             String priceField,
                                             String labelField,
                                             String availableField,
                                             String inventoryField,
                                             String storefrontUrlField) {
        ArrayNode summaries = JSON.arrayNode();
        summaries.add(priceSummary(target + "PriceSummary", "ALL", priceField, labelField, availableField, inventoryField, storefrontUrlField));
        summaries.add(priceSummary(target + "MatchedPriceSummary", "CONSTRAINT_MATCHES", priceField, labelField, availableField, inventoryField, storefrontUrlField));
        return summaries;
    }

    private static ObjectNode priceSummary(String target,
                                           String source,
                                           String priceField,
                                           String labelField,
                                           String availableField,
                                           String inventoryField,
                                           String storefrontUrlField) {
        ObjectNode summary = JSON.objectNode();
        summary.put("target", target);
        summary.put("source", source);
        summary.put("field", priceField);
        summary.put("recordCountKey", "pricedProducts");
        summary.put("lowestValueKey", "lowestPrice");
        summary.put("highestValueKey", "highestPrice");
        summary.put("labelField", labelField);
        summary.put("lowestLabelKey", "lowestPriceTitle");
        summary.put("highestLabelKey", "highestPriceTitle");
        ArrayNode extraFields = JSON.arrayNode();
        extraFields.add(extraField(availableField, "lowestAvailable", "highestAvailable"));
        extraFields.add(extraField(inventoryField, "lowestInventoryQuantity", "highestInventoryQuantity"));
        extraFields.add(extraField(storefrontUrlField, "lowestStorefrontUrl", "highestStorefrontUrl"));
        summary.set("extraFields", extraFields);
        return summary;
    }

    private static ObjectNode extraField(String field, String lowestKey, String highestKey) {
        ObjectNode node = JSON.objectNode();
        node.put("field", field);
        node.put("lowestKey", lowestKey);
        node.put("highestKey", highestKey);
        return node;
    }

    private static ArrayNode rankBy(String field) {
        return JSON.arrayNode();
    }

    private static ObjectNode priceUpperBoundRule(String field) {
        ObjectNode rule = JSON.objectNode();
        rule.put("type", "PARAM_NUMERIC_UPPER_BOUND");
        rule.put("field", field);
        rule.put("paramPath", "maxPrice");
        rule.put("operator", "<=");
        rule.put("scoreMatch", 40);
        rule.put("scoreMissing", -15);
        rule.put("scoreMismatch", -20);
        rule.put("sortAscendingOnMatch", true);
        return rule;
    }

    private static ObjectNode availableRule(String field) {
        ObjectNode rule = JSON.objectNode();
        rule.put("type", "PARAM_BOOLEAN_TRUE");
        rule.put("field", field);
        rule.put("paramPath", "availableOnly");
        rule.put("scoreMatch", 25);
        rule.put("scoreMismatch", -25);
        return rule;
    }

    private static ArrayNode array(List<String> values) {
        ArrayNode array = JSON.arrayNode();
        if (values != null) {
            values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .forEach(array::add);
        }
        return array;
    }

    private static ArrayNode array(String... values) {
        ArrayNode array = JSON.arrayNode();
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    array.add(value.trim());
                }
            }
        }
        return array;
    }

    private static boolean isShopifyCompanionAction(JsonNode action, String actionId) {
        if (isDisabledAction(actionId)) {
            return false;
        }
        if (DEFAULT_ACTION_IDS.contains(actionId)) {
            return true;
        }
        String pluginId = ShopifyCompanionPluginSelection.canonicalizePluginId(
            blankToNull(action.path("marketplacePluginId").asText(null))
        );
        if (ShopifyCompanionPluginSelection.managedActionPluginIds().contains(pluginId)) {
            return true;
        }
        String category = blankToNull(action.path("category").asText(null));
        return "shopify-companion".equals(normalize(category));
    }

    private static boolean isDisabledAction(String actionId) {
        return DISABLED_ACTION_IDS.contains(normalize(actionId));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = blankToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
