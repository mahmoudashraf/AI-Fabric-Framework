package com.ai.infrastructure.intent.action.connector;

import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.ai.infrastructure.intent.action.ActionAccessMode;
import com.ai.infrastructure.intent.action.ActionPayload;
import com.ai.infrastructure.intent.action.ActionResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectorAIActionHandlerTest {

    @Test
    void shouldBuildCompactLlmFactsFromNestedConnectorPayload() {
        AIActionMetaData metadata = AIActionMetaData.builder()
            .name("relationship_query")
            .category("shopify-companion")
            .accessMode(ActionAccessMode.READ)
            .build();
        Map<String, Object> document = Map.of(
            "id", "gid://shopify/Product/1",
            "entityType", "product",
            "title", "The Collection Snowboard: Liquid",
            "metadata", Map.of(
                "handle", "the-collection-snowboard-liquid",
                "vendor", "Hydrogen Vendor",
                "productType", "snowboard",
                "available", true,
                "price", "749.95",
                "inventoryQuantity", 50,
                "storefrontUrl", "https://shopping-companion-test.myshopify.com/products/the-collection-snowboard-liquid",
                "raw", "large internal payload"
            ),
            "sourceUrl", "https://shopping-companion-test.myshopify.com/products/the-collection-snowboard-liquid"
        );
        ActionResult actionResult = ActionResult.builder()
            .success(true)
            .message("Action executed.")
            .data(ActionPayload.object(Map.of(
                "success", true,
                "message", "Relationship query results",
                "data", Map.of(
                    "query", "Find similar available snowboards under 800 dollars",
                    "documents", List.of(document),
                    "items", List.of(document),
                    "totalResults", 1,
                    "returnedResults", 1
                )
            )))
            .build();
        ConnectorAIActionHandler handler = new ConnectorAIActionHandler(
            metadata,
            false,
            null,
            Set.of(),
            null
        );

        Optional<Map<String, Object>> facts = handler.buildPostActionLlmFacts(actionResult, null);

        assertThat(facts).isPresent();
        assertThat(facts.get())
            .containsEntry("action", "relationship_query")
            .containsEntry("query", "Find similar available snowboards under 800 dollars")
            .containsEntry("totalResults", 1)
            .containsEntry("returnedResults", 1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> documents = (List<Map<String, Object>>) facts.get().get("documents");
        assertThat(facts.get()).doesNotContainKey("items");
        assertThat(documents).hasSize(1);
        assertThat(documents.getFirst())
            .containsEntry("title", "The Collection Snowboard: Liquid")
            .containsEntry("entityType", "product")
            .containsEntry("price", "749.95")
            .containsEntry("available", true)
            .containsEntry("inventoryQuantity", 50)
            .doesNotContainKey("handle")
            .doesNotContainKey("sourceUrl")
            .doesNotContainKey("id")
            .doesNotContainKey("raw");
    }

    @Test
    void shouldPrioritizeRecordsNamedInTheQueryBeforeApplyingEvidenceLimit() {
        AIActionMetaData metadata = AIActionMetaData.builder()
            .name("relationship_query")
            .category("shopify-companion")
            .accessMode(ActionAccessMode.READ)
            .build();
        ActionResult actionResult = ActionResult.builder()
            .success(true)
            .message("Action executed.")
            .data(ActionPayload.object(Map.of(
                "data", Map.of(
                    "query", "Compare The Collection Snowboard: Liquid and The Collection Snowboard: Oxygen",
                    "documents", List.of(
                        productRecord("The Collection Snowboard: Liquid", "749.95"),
                        productRecord("The Out of Stock Snowboard", "885.95"),
                        productRecord("The Inventory Not Tracked Snowboard", "949.95"),
                        productRecord("The Complete Snowboard", "699.95"),
                        productRecord("The Multi-managed Snowboard", "629.95"),
                        productRecord("The Collection Snowboard: Oxygen", "1025.00")
                    ),
                    "totalResults", 6,
                    "returnedResults", 6
                )
            )))
            .build();
        ConnectorAIActionHandler handler = new ConnectorAIActionHandler(
            metadata,
            false,
            null,
            Set.of(),
            null
        );

        Optional<Map<String, Object>> facts = handler.buildPostActionLlmFacts(actionResult, null);

        assertThat(facts).isPresent();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> documents = (List<Map<String, Object>>) facts.get().get("documents");
        assertThat(documents).hasSize(5);
        assertThat(documents)
            .extracting(document -> document.get("title"))
            .contains("The Collection Snowboard: Liquid", "The Collection Snowboard: Oxygen");
    }

    @Test
    void shouldPrioritizeAvailableBudgetMatchesBeforeApplyingEvidenceLimit() {
        AIActionMetaData metadata = AIActionMetaData.builder()
            .name("relationship_query")
            .category("shopify-companion")
            .accessMode(ActionAccessMode.READ)
            .build();
        ActionResult actionResult = ActionResult.builder()
            .success(true)
            .message("Action executed.")
            .data(ActionPayload.object(Map.of(
                "data", Map.of(
                    "query", "Find snowboards with price_usd < 800 AND stock_status = 'in_stock'",
                    "documents", List.of(
                        productRecord("The Collection Snowboard: Liquid", "749.95", true),
                        productRecord("The Out of Stock Snowboard", "885.95", false),
                        productRecord("The Inventory Not Tracked Snowboard", "949.95", true),
                        productRecord("The Collection Snowboard: Oxygen", "1025.00", true),
                        productRecord("The Multi-managed Snowboard", "629.95", true),
                        productRecord("The Complete Snowboard", "699.95", true)
                    ),
                    "totalResults", 6,
                    "returnedResults", 6
                )
            )))
            .build();
        ConnectorAIActionHandler handler = new ConnectorAIActionHandler(
            metadata,
            false,
            null,
            Set.of(),
            null
        );

        Optional<Map<String, Object>> facts = handler.buildPostActionLlmFacts(actionResult, null);

        assertThat(facts).isPresent();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> documents = (List<Map<String, Object>>) facts.get().get("documents");
        assertThat(documents).hasSize(5);
        assertThat(documents.subList(0, 3))
            .extracting(document -> document.get("title"))
            .containsExactly(
                "The Multi-managed Snowboard",
                "The Complete Snowboard",
                "The Collection Snowboard: Liquid"
            );
        @SuppressWarnings("unchecked")
        Map<String, Object> priceSummary = (Map<String, Object>) facts.get().get("documentsPriceSummary");
        assertThat(priceSummary)
            .containsEntry("lowestPriceTitle", "The Multi-managed Snowboard")
            .containsEntry("lowestPrice", 629.95)
            .containsEntry("highestPriceTitle", "The Collection Snowboard: Oxygen")
            .containsEntry("highestPrice", 1025.00);
        @SuppressWarnings("unchecked")
        Map<String, Object> matchedPriceSummary = (Map<String, Object>) facts.get().get("documentsMatchedPriceSummary");
        assertThat(matchedPriceSummary)
            .containsEntry("lowestPriceTitle", "The Multi-managed Snowboard")
            .containsEntry("lowestPrice", 629.95)
            .containsEntry("lowestAvailable", true)
            .containsEntry("lowestInventoryQuantity", 50)
            .containsEntry("highestPriceTitle", "The Collection Snowboard: Liquid")
            .containsEntry("highestPrice", 749.95)
            .containsEntry("highestAvailable", true)
            .containsEntry("highestInventoryQuantity", 50);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> constraintMatches = (List<Map<String, Object>>) facts.get().get("documentsConstraintMatches");
        assertThat(constraintMatches)
            .extracting(document -> document.get("title"))
            .containsExactly(
                "The Multi-managed Snowboard",
                "The Complete Snowboard",
                "The Collection Snowboard: Liquid"
            );
        assertThat(constraintMatches.getFirst())
            .containsEntry("price", "629.95")
            .containsEntry("available", true)
            .doesNotContainKey("vendor")
            .doesNotContainKey("productType")
            .doesNotContainKey("primarySku");
        assertThat(facts.get()).containsEntry("documentsConstraintMatchCount", 3);
    }

    private Map<String, Object> productRecord(String title, String price) {
        return productRecord(title, price, true);
    }

    private Map<String, Object> productRecord(String title, String price, boolean available) {
        return Map.of(
            "title", title,
            "entityType", "product",
            "metadata", Map.of(
                "vendor", "Hydrogen Vendor",
                "productType", "snowboard",
                "available", available,
                "price", price,
                "inventoryQuantity", 50
            )
        );
    }
}
