package com.ai.fabric.platform.backend.shopify.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ShopifyCompanionActionCatalogTest {

    @Test
    void ensureLlmFactsDefaultsPrunesUnsupportedLegacyConnectorAliases() {
        ObjectNode config = JsonNodeFactory.instance.objectNode();
        var actions = config.putArray("actions");
        actions.addObject()
            .put("name", "relationship_query")
            .put("category", "shopify-companion")
            .put("accessMode", "READ");
        actions.addObject()
            .put("name", "list_products")
            .put("category", "shopify-companion")
            .put("adapterType", "connector-http")
            .put("accessMode", "READ");
        actions.addObject()
            .put("name", "add_product_to_cart")
            .put("category", "shopify-companion")
            .put("adapterType", "connector-http")
            .put("accessMode", "WRITE_ONLY");
        actions.addObject()
            .put("name", "custom_record_lookup")
            .put("category", "general")
            .put("accessMode", "READ");
        actions.addObject()
            .put("name", "shopify_search_catalog")
            .put("category", "shopify-companion")
            .put("adapterType", "mcp-tool")
            .put("accessMode", "READ");

        boolean changed = ShopifyCompanionActionCatalog.ensureLlmFactsDefaults(config);

        assertThat(changed).isTrue();
        assertThat(config.path("actions").findValuesAsText("name"))
            .containsExactly("custom_record_lookup", "shopify_search_catalog")
            .doesNotContain("relationship_query", "list_products", "add_product_to_cart");
    }

    @Test
    void ensureLlmFactsDefaultsPreservesOperatorConfiguredProjectionForRealMcpAction() {
        ObjectNode config = JsonNodeFactory.instance.objectNode();
        ObjectNode action = config.putArray("actions").addObject()
            .put("name", "list_products")
            .put("category", "shopify-companion")
            .put("adapterType", "mcp-tool")
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
        actions.addObject()
            .put("name", "list_products")
            .put("category", "shopify-companion")
            .put("adapterType", "connector-http");
        actions.addObject()
            .put("name", "search_products")
            .put("category", "shopify-companion")
            .put("adapterType", "connector-http");

        Set<String> routeActionIds = ShopifyCompanionActionCatalog.routeActionIds(actionsConfig);

        assertThat(routeActionIds)
            .containsExactly("shopify_search_catalog")
            .doesNotContain("shopify_get_cart", "shopify_update_cart", "list_products", "search_products");
    }
}
