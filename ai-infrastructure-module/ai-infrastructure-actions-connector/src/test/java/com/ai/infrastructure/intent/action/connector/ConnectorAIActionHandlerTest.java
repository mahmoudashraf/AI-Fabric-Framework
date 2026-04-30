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
        assertThat(documents).hasSize(1);
        assertThat(documents.getFirst())
            .containsEntry("title", "The Collection Snowboard: Liquid")
            .containsEntry("entityType", "product")
            .containsEntry("handle", "the-collection-snowboard-liquid")
            .containsEntry("price", "749.95")
            .containsEntry("available", true)
            .containsEntry("inventoryQuantity", 50)
            .containsEntry("sourceUrl", "https://shopping-companion-test.myshopify.com/products/the-collection-snowboard-liquid")
            .doesNotContainKey("raw");
    }
}
