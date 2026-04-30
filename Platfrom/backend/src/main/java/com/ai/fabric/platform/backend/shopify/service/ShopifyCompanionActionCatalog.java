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

    static final String ACTION_PLUGIN_ID = "mkp-action-shopify-companion-read";
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private static final List<String> DEFAULT_ACTION_IDS = List.of(
        "list_products",
        "search_products",
        "get_product_details",
        "check_availability",
        "get_policy",
        "relationship_query",
        "add_product_to_cart",
        "add_to_cart",
        "update_cart_quantity"
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

    private static final List<String> PRODUCT_DOCUMENT_FIELDS = List.of(
        "title",
        "entityType",
        "content",
        "sourceUrl",
        "metadata.handle",
        "metadata.vendor",
        "metadata.productType",
        "metadata.available",
        "metadata.price",
        "metadata.primarySku",
        "metadata.inventoryQuantity",
        "metadata.storefrontUrl",
        "metadata.reviewSignalsPresent",
        "metadata.reviewAverage",
        "metadata.reviewCount"
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
        boolean changed = false;
        for (JsonNode action : actionsConfig.path("actions")) {
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
                if (actionId != null && isShopifyCompanionAction(action, actionId)) {
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

    private static ObjectNode defaultLlmFacts(String actionId) {
        return switch (normalize(actionId)) {
            case "list_products", "search_products" -> listProductFacts("items", "products");
            case "get_product_details" -> productDetailsFacts();
            case "check_availability" -> availabilityFacts();
            case "get_policy" -> policyFacts();
            case "relationship_query" -> relationshipFacts();
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

    private static ObjectNode relationshipFacts() {
        ObjectNode facts = baseFacts("query", "totalResults", "returnedResults");
        ArrayNode lists = JSON.arrayNode();
        lists.add(productDocumentList("documents", "documents", 8));
        lists.add(productList("items", "products", 8));
        ObjectNode policies = JSON.objectNode();
        policies.put("sourcePath", "policies");
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
        list.set("rankRules", productRankRules());
        list.set("constraints", productConstraints());
        list.set("summaries", productSummaries(target));
        return list;
    }

    private static ObjectNode productDocumentList(String sourcePath, String target, int maxItems) {
        ObjectNode list = JSON.objectNode();
        list.put("sourcePath", sourcePath);
        list.put("target", target);
        list.put("maxItems", maxItems);
        list.set("includeFields", array(PRODUCT_DOCUMENT_FIELDS));
        list.put("fallbackContentField", "content");
        list.put("fallbackContentMaxChars", 700);
        list.set("rankRules", productRankRules());
        list.set("constraints", productConstraints());
        list.set("summaries", productSummaries(target));
        return list;
    }

    private static ArrayNode productRankRules() {
        ArrayNode rules = rankBy("title");
        ObjectNode handle = JSON.objectNode();
        handle.put("type", "QUERY_CONTAINS_FIELD_VALUE");
        handle.put("field", "handle");
        handle.put("score", 80);
        rules.add(handle);
        rules.add(priceUpperBoundRule());
        rules.add(availableRule());
        return rules;
    }

    private static ObjectNode productConstraints() {
        ObjectNode constraints = JSON.objectNode();
        constraints.put("target", "productConstraintMatches");
        constraints.put("countTarget", "productConstraintMatchCount");
        constraints.set("includeFields", array(
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
        ArrayNode rules = JSON.arrayNode();
        rules.add(priceUpperBoundRule());
        rules.add(availableRule());
        constraints.set("rules", rules);
        return constraints;
    }

    private static ArrayNode productSummaries(String target) {
        ArrayNode summaries = JSON.arrayNode();
        summaries.add(priceSummary(target + "PriceSummary", "ALL"));
        summaries.add(priceSummary(target + "MatchedPriceSummary", "CONSTRAINT_MATCHES"));
        return summaries;
    }

    private static ObjectNode priceSummary(String target, String source) {
        ObjectNode summary = JSON.objectNode();
        summary.put("target", target);
        summary.put("source", source);
        summary.put("field", "price");
        summary.put("recordCountKey", "pricedProducts");
        summary.put("lowestValueKey", "lowestPrice");
        summary.put("highestValueKey", "highestPrice");
        summary.put("labelField", "title");
        summary.put("lowestLabelKey", "lowestPriceTitle");
        summary.put("highestLabelKey", "highestPriceTitle");
        ArrayNode extraFields = JSON.arrayNode();
        extraFields.add(extraField("available", "lowestAvailable", "highestAvailable"));
        extraFields.add(extraField("inventoryQuantity", "lowestInventoryQuantity", "highestInventoryQuantity"));
        extraFields.add(extraField("storefrontUrl", "lowestStorefrontUrl", "highestStorefrontUrl"));
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
        ArrayNode rules = JSON.arrayNode();
        ObjectNode rule = JSON.objectNode();
        rule.put("type", "QUERY_CONTAINS_FIELD_VALUE");
        rule.put("field", field);
        rule.put("score", 100);
        rules.add(rule);
        return rules;
    }

    private static ObjectNode priceUpperBoundRule() {
        ObjectNode rule = JSON.objectNode();
        rule.put("type", "QUERY_NUMERIC_UPPER_BOUND");
        rule.put("field", "price");
        ArrayNode patterns = JSON.arrayNode();
        patterns.add("\\b(?:under|below|less\\s+than|no\\s+more\\s+than|at\\s+most|max(?:imum)?|up\\s+to)\\s*\\$?\\s*(?<amount>\\d+(?:\\.\\d+)?)\\b");
        patterns.add("\\$\\s*(?<amount>\\d+(?:\\.\\d+)?)\\s*(?:or\\s+less|or\\s+under|and\\s+under)\\b");
        patterns.add("\\b(?:price|amount|cost)\\b\\s*(?<operator><=|<)\\s*\\$?\\s*(?<amount>\\d+(?:\\.\\d+)?)\\b");
        rule.set("queryPatterns", patterns);
        rule.put("scoreMatch", 40);
        rule.put("scoreMissing", -15);
        rule.put("scoreMismatch", -20);
        rule.put("sortAscendingOnMatch", true);
        return rule;
    }

    private static ObjectNode availableRule() {
        ObjectNode rule = JSON.objectNode();
        rule.put("type", "QUERY_TERMS_BOOLEAN_TRUE");
        rule.put("field", "available");
        rule.set("queryTerms", array("available", "in stock", "in-stock", "instock", "stock status"));
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
