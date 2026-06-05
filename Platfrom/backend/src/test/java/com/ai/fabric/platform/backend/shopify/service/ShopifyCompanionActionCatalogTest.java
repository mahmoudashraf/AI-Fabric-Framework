package com.ai.fabric.platform.backend.shopify.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ShopifyCompanionActionCatalogTest {

    @Test
    void ensureLlmFactsDefaultsAddsShopifyProjectionOnlyForReadCompanionActions() {
        ObjectNode config = JsonNodeFactory.instance.objectNode();
        var actions = config.putArray("actions");
        actions.addObject()
            .put("name", "relationship_query")
            .put("category", "shopify-companion")
            .put("accessMode", "READ");
        actions.addObject()
            .put("name", "search_products")
            .put("category", "shopify-companion")
            .put("accessMode", "READ");
        actions.addObject()
            .put("name", "add_product_to_cart")
            .put("category", "shopify-companion")
            .put("accessMode", "WRITE_ONLY");
        actions.addObject()
            .put("name", "custom_record_lookup")
            .put("category", "general")
            .put("accessMode", "READ");

        boolean changed = ShopifyCompanionActionCatalog.ensureLlmFactsDefaults(config);

        assertThat(changed).isTrue();
        assertThat(config.path("actions").findValuesAsText("name")).doesNotContain("relationship_query");
        ObjectNode searchProducts = (ObjectNode) config.path("actions").get(0);
        assertThat(searchProducts.path("llmFacts").path("rootPath").asText()).isEqualTo("data");
        assertThat(searchProducts.path("llmFacts").path("lists").size()).isEqualTo(1);
        assertThat(searchProducts.path("llmFacts").path("lists").get(0).path("target").asText()).isEqualTo("products");
        assertThat(searchProducts.path("llmFacts").path("lists").get(0).path("constraints").path("target").asText())
            .isEqualTo("productConstraintMatches");
        assertThat(searchProducts.path("llmFacts").path("lists").get(0).toString())
            .contains("\"field\":\"price\"", "\"field\":\"available\"");
        assertThat(searchProducts.path("params").findValuesAsText("name")).contains("maxPrice", "availableOnly");
        assertThat(searchProducts.toString()).contains("PARAM_NUMERIC_UPPER_BOUND").contains("PARAM_BOOLEAN_TRUE");
        assertThat(searchProducts.toString()).doesNotContain("QUERY_").doesNotContain("queryPatterns").doesNotContain("queryTerms");
        assertThat(config.path("actions").get(1).has("llmFacts")).isFalse();
        assertThat(config.path("actions").get(2).has("llmFacts")).isFalse();
    }

    @Test
    void ensureLlmFactsDefaultsPreservesOperatorConfiguredProjection() {
        ObjectNode config = JsonNodeFactory.instance.objectNode();
        ObjectNode action = config.putArray("actions").addObject()
            .put("name", "search_products")
            .put("category", "shopify-companion")
            .put("accessMode", "READ");
        ObjectNode existing = action.putObject("llmFacts");
        existing.putArray("copyFields").add("customField");

        boolean changed = ShopifyCompanionActionCatalog.ensureLlmFactsDefaults(config);

        assertThat(changed).isTrue();
        assertThat(action.path("llmFacts").path("copyFields").get(0).asText()).isEqualTo("customField");
        assertThat(action.path("params").findValuesAsText("name")).contains("maxPrice", "availableOnly");
    }

    @Test
    void routeActionIdsIncludesManagedCustomerAccountAndCheckoutPlugins() {
        ObjectNode actionsConfig = JsonNodeFactory.instance.objectNode();
        var actions = actionsConfig.putArray("actions");
        actions.addObject()
            .put("name", "shopify_get_customer_context_summary")
            .put("marketplacePluginId", ShopifyCompanionPluginSelection.ACTION_CUSTOMER_ACCOUNT_MCP_PLUGIN_ID);
        actions.addObject()
            .put("name", "shopify_create_checkout")
            .put("marketplacePluginId", ShopifyCompanionPluginSelection.ACTION_CHECKOUT_MCP_PLUGIN_ID);

        Set<String> routeActionIds = ShopifyCompanionActionCatalog.routeActionIds(actionsConfig);

        assertThat(routeActionIds)
            .contains("shopify_get_customer_context_summary", "shopify_create_checkout");
    }

    @Test
    void routeActionIdsDoesNotInventMissingDefaultActions() {
        ObjectNode actionsConfig = JsonNodeFactory.instance.objectNode();
        var actions = actionsConfig.putArray("actions");
        actions.addObject()
            .put("name", "shopify_search_catalog")
            .put("marketplacePluginId", ShopifyCompanionPluginSelection.ACTION_STOREFRONT_READ_MCP_PLUGIN_ID);

        Set<String> routeActionIds = ShopifyCompanionActionCatalog.routeActionIds(actionsConfig);

        assertThat(routeActionIds)
            .containsExactly("shopify_search_catalog")
            .doesNotContain("shopify_get_cart", "shopify_update_cart");
    }
}
