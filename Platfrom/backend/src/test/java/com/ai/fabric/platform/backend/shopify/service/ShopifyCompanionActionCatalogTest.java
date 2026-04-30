package com.ai.fabric.platform.backend.shopify.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

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
            .put("name", "add_product_to_cart")
            .put("category", "shopify-companion")
            .put("accessMode", "WRITE_ONLY");
        actions.addObject()
            .put("name", "custom_record_lookup")
            .put("category", "general")
            .put("accessMode", "READ");

        boolean changed = ShopifyCompanionActionCatalog.ensureLlmFactsDefaults(config);

        assertThat(changed).isTrue();
        ObjectNode relationship = (ObjectNode) config.path("actions").get(0);
        assertThat(relationship.path("llmFacts").path("rootPath").asText()).isEqualTo("data");
        assertThat(relationship.path("llmFacts").path("lists").size()).isEqualTo(3);
        assertThat(relationship.path("llmFacts").path("lists").get(0).path("target").asText()).isEqualTo("documents");
        assertThat(relationship.path("llmFacts").path("lists").get(0).path("constraints").path("target").asText())
            .isEqualTo("productConstraintMatches");
        assertThat(relationship.path("params").findValuesAsText("name")).contains("maxPrice", "availableOnly");
        assertThat(relationship.toString()).contains("PARAM_NUMERIC_UPPER_BOUND").contains("PARAM_BOOLEAN_TRUE");
        assertThat(relationship.toString()).doesNotContain("QUERY_").doesNotContain("queryPatterns").doesNotContain("queryTerms");
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
}
