package com.ai.fabric.product.shopify.bridge.action.service;

import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionExecuteRequest;
import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionResult;
import com.ai.fabric.product.shopify.bridge.client.mcp.ShopifyMcpClient;
import com.ai.fabric.product.shopify.bridge.config.ShopifyStorefrontMcpProperties;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyBridgeCredentialAcquisition;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyTokenExchangeMaterial;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShopifyStorefrontMcpActionAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void searchCatalogCallsShopifyUcpMcpAndNormalizesEvidence() throws Exception {
        ShopifyMcpClient mcpClient = mock(ShopifyMcpClient.class);
        ShopifyStorefrontMcpActionAdapter adapter = new ShopifyStorefrontMcpActionAdapter(
            mcpClient,
            new ShopifyStorefrontMcpProperties("2025-11-25", "https://profiles.example/ucp.json", null, null),
            objectMapper
        );
        ArgumentCaptor<JsonNode> argumentsCaptor = ArgumentCaptor.forClass(JsonNode.class);
        when(mcpClient.toolsCall(
            eq(URI.create("https://alpha.myshopify.com/api/ucp/mcp")),
            eq("search_catalog"),
            argumentsCaptor.capture()
        )).thenReturn(objectMapper.readTree("""
            {
              "content": [
                {
                  "type": "text",
                  "text": "Catalog result"
                }
              ],
              "structuredContent": {
                "products": [
                  {
                    "title": "Coffee Beans"
                  }
                ]
              }
            }
            """));

        ShopifyBridgeActionResult result = adapter.searchCatalog(
            acquisition("alpha.myshopify.com"),
            new ShopifyBridgeActionExecuteRequest(
                "shopify_search_catalog",
                Map.of("query", "coffee", "country", "US", "intent", "fair trade", "limit", 3),
                null,
                Map.of()
            )
        );

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("Catalog search");
        assertThat(result.data()).containsEntry("adapterType", "mcp-tool");
        assertThat(result.data()).containsEntry("mcpServerRef", "shopify-storefront-ucp");
        assertThat(result.data()).containsEntry("mcpEndpointKind", "UCP_CATALOG");
        assertThat(result.data()).containsEntry("mcpToolName", "search_catalog");
        assertThat(result.data()).containsEntry("evidenceType", "SHOPIFY_MCP_TOOL_RESULT");
        Map<?, ?> evidence = (Map<?, ?>) result.data().get("evidence");
        assertThat(evidence.get("type")).isEqualTo("SHOPIFY_MCP_TOOL_RESULT");
        assertThat(evidence.get("toolName")).isEqualTo("search_catalog");
        Map<?, ?> toolResult = (Map<?, ?>) result.data().get("toolResult");
        assertThat(toolResult.containsKey("structuredContent")).isTrue();

        JsonNode arguments = argumentsCaptor.getValue();
        assertThat(arguments.path("meta").path("ucp-agent").path("profile").asText())
            .isEqualTo("https://profiles.example/ucp.json");
        assertThat(arguments.path("catalog").path("query").asText()).isEqualTo("coffee");
        assertThat(arguments.path("catalog").path("context").path("address_country").asText()).isEqualTo("US");
        assertThat(arguments.path("catalog").path("context").path("intent").asText()).isEqualTo("fair trade");
        assertThat(arguments.path("catalog").path("pagination").path("limit").asInt()).isEqualTo(3);
    }

    @Test
    void searchPoliciesCallsShopifyStorefrontMcpAndNormalizesEvidence() throws Exception {
        ShopifyMcpClient mcpClient = mock(ShopifyMcpClient.class);
        ShopifyStorefrontMcpActionAdapter adapter = new ShopifyStorefrontMcpActionAdapter(
            mcpClient,
            new ShopifyStorefrontMcpProperties("2025-11-25", "https://profiles.example/ucp.json", null, null),
            objectMapper
        );
        ArgumentCaptor<JsonNode> argumentsCaptor = ArgumentCaptor.forClass(JsonNode.class);
        when(mcpClient.toolsCall(
            eq(URI.create("https://alpha.myshopify.com/api/mcp")),
            eq("search_shop_policies_and_faqs"),
            argumentsCaptor.capture()
        )).thenReturn(objectMapper.readTree("""
            {
              "content": [
                {
                  "type": "text",
                  "text": "Returns are accepted within 30 days."
                }
              ],
              "isError": false
            }
            """));

        ShopifyBridgeActionResult result = adapter.searchPolicies(
            acquisition("alpha.myshopify.com"),
            new ShopifyBridgeActionExecuteRequest(
                "shopify_search_policies",
                Map.of("query", "return policy", "context", "winter jacket"),
                null,
                Map.of()
            )
        );

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("Policy search");
        assertThat(result.data()).containsEntry("adapterType", "mcp-tool");
        assertThat(result.data()).containsEntry("mcpServerRef", "shopify-storefront");
        assertThat(result.data()).containsEntry("mcpEndpointKind", "STOREFRONT_STANDARD");
        assertThat(result.data()).containsEntry("mcpToolName", "search_shop_policies_and_faqs");
        Map<?, ?> evidence = (Map<?, ?>) result.data().get("evidence");
        assertThat(evidence.get("toolName")).isEqualTo("search_shop_policies_and_faqs");

        JsonNode arguments = argumentsCaptor.getValue();
        assertThat(arguments.path("query").asText()).isEqualTo("return policy");
        assertThat(arguments.path("context").asText()).isEqualTo("winter jacket");
    }

    @Test
    void getProductCallsShopifyUcpGetProductTool() throws Exception {
        ShopifyMcpClient mcpClient = mock(ShopifyMcpClient.class);
        ShopifyStorefrontMcpActionAdapter adapter = new ShopifyStorefrontMcpActionAdapter(
            mcpClient,
            new ShopifyStorefrontMcpProperties("2025-11-25", "https://profiles.example/ucp.json", null, null),
            objectMapper
        );
        ArgumentCaptor<JsonNode> argumentsCaptor = ArgumentCaptor.forClass(JsonNode.class);
        when(mcpClient.toolsCall(
            eq(URI.create("https://alpha.myshopify.com/api/ucp/mcp")),
            eq("get_product"),
            argumentsCaptor.capture()
        )).thenReturn(objectMapper.readTree("""
            {
              "content": [
                {
                  "type": "text",
                  "text": "Product"
                }
              ],
              "structuredContent": {
                "id": "gid://shopify/Product/123"
              }
            }
            """));

        ShopifyBridgeActionResult result = adapter.getProduct(
            acquisition("alpha.myshopify.com"),
            new ShopifyBridgeActionExecuteRequest(
                "shopify_get_product",
                Map.of("id", "gid://shopify/Product/123", "country", "US"),
                null,
                Map.of()
            )
        );

        assertThat(result.success()).isTrue();
        assertThat(result.data()).containsEntry("mcpServerRef", "shopify-storefront-ucp");
        assertThat(result.data()).containsEntry("mcpEndpointKind", "UCP_CATALOG");
        assertThat(result.data()).containsEntry("mcpToolName", "get_product");
        JsonNode arguments = argumentsCaptor.getValue();
        assertThat(arguments.path("meta").path("ucp-agent").path("profile").asText())
            .isEqualTo("https://profiles.example/ucp.json");
        assertThat(arguments.path("catalog").path("id").asText()).isEqualTo("gid://shopify/Product/123");
        assertThat(arguments.path("catalog").path("context").path("address_country").asText()).isEqualTo("US");
    }

    @Test
    void updateCartCallsShopifyStorefrontMcpUpdateCartTool() throws Exception {
        ShopifyMcpClient mcpClient = mock(ShopifyMcpClient.class);
        ShopifyStorefrontMcpActionAdapter adapter = new ShopifyStorefrontMcpActionAdapter(
            mcpClient,
            new ShopifyStorefrontMcpProperties("2025-11-25", "https://profiles.example/ucp.json", null, null),
            objectMapper
        );
        ArgumentCaptor<JsonNode> argumentsCaptor = ArgumentCaptor.forClass(JsonNode.class);
        when(mcpClient.toolsCall(
            eq(URI.create("https://alpha.myshopify.com/api/mcp")),
            eq("update_cart"),
            argumentsCaptor.capture()
        )).thenReturn(objectMapper.readTree("""
            {
              "content": [
                {
                  "type": "text",
                  "text": "Cart updated"
                }
              ],
              "structuredContent": {
                "cart_id": "cart-1"
              }
            }
            """));

        ShopifyBridgeActionResult result = adapter.updateCart(
            acquisition("alpha.myshopify.com"),
            new ShopifyBridgeActionExecuteRequest(
                "shopify_update_cart",
                Map.of(
                    "cart_id", "cart-1",
                    "add_items", List.of(Map.of("variant_id", "gid://shopify/ProductVariant/1", "quantity", 1))
                ),
                null,
                Map.of()
            )
        );

        assertThat(result.success()).isTrue();
        assertThat(result.data()).containsEntry("mcpServerRef", "shopify-storefront");
        assertThat(result.data()).containsEntry("mcpEndpointKind", "STOREFRONT_STANDARD");
        assertThat(result.data()).containsEntry("mcpToolName", "update_cart");
        JsonNode arguments = argumentsCaptor.getValue();
        assertThat(arguments.path("cart_id").asText()).isEqualTo("cart-1");
        assertThat(arguments.path("add_items")).hasSize(1);
    }

    @Test
    void readinessListsExpectedMcpToolsAcrossUcpAndStorefrontEndpoints() throws Exception {
        ShopifyMcpClient mcpClient = mock(ShopifyMcpClient.class);
        ShopifyStorefrontMcpActionAdapter adapter = new ShopifyStorefrontMcpActionAdapter(
            mcpClient,
            new ShopifyStorefrontMcpProperties("2025-11-25", "https://profiles.example/ucp.json", null, null),
            objectMapper
        );
        URI ucpEndpoint = URI.create("https://alpha.myshopify.com/api/ucp/mcp");
        URI storefrontEndpoint = URI.create("https://alpha.myshopify.com/api/mcp");
        ShopifyMcpClient.ShopifyMcpSession storefrontSession =
            new ShopifyMcpClient.ShopifyMcpSession(storefrontEndpoint, "2025-11-25", "storefront-session", objectMapper.createObjectNode());
        when(mcpClient.initialize(storefrontEndpoint)).thenReturn(storefrontSession);
        when(mcpClient.toolsCall(
            eq(ucpEndpoint),
            eq("search_catalog"),
            org.mockito.ArgumentMatchers.any(JsonNode.class)
        )).thenReturn(objectMapper.readTree("""
            {
              "content": [
                {
                  "type": "text",
                  "text": "Catalog readiness result"
                }
              ],
              "isError": false
            }
            """));
        when(mcpClient.toolsList(storefrontSession)).thenReturn(objectMapper.readTree("""
            {
              "tools": [
                {"name": "search_shop_policies_and_faqs"},
                {"name": "get_cart"},
                {"name": "update_cart"}
              ]
            }
            """));

        Map<String, Object> result = adapter.readiness("alpha.myshopify.com");

        assertThat(result).containsEntry("ready", true);
        List<?> servers = (List<?>) result.get("servers");
        assertThat(servers).hasSize(2);
        assertThat(((Map<?, ?>) servers.get(0)).get("verificationMethod")).isEqualTo("tools/call:search_catalog");
        assertThat(((Map<?, ?>) servers.get(1)).get("verificationMethod")).isEqualTo("initialize+tools/list");
    }

    @Test
    void getProductDetailsCallsShopifyStorefrontMcpAndNormalizesEvidence() throws Exception {
        ShopifyMcpClient mcpClient = mock(ShopifyMcpClient.class);
        ShopifyStorefrontMcpActionAdapter adapter = new ShopifyStorefrontMcpActionAdapter(
            mcpClient,
            new ShopifyStorefrontMcpProperties("2025-11-25", "https://profiles.example/ucp.json", null, null),
            objectMapper
        );
        ArgumentCaptor<JsonNode> argumentsCaptor = ArgumentCaptor.forClass(JsonNode.class);
        when(mcpClient.toolsCall(
            eq(URI.create("https://alpha.myshopify.com/api/mcp")),
            eq("get_product_details"),
            argumentsCaptor.capture()
        )).thenReturn(objectMapper.readTree("""
            {
              "content": [
                {
                  "type": "text",
                  "text": "Product details"
                }
              ],
              "isError": false
            }
            """));

        ShopifyBridgeActionResult result = adapter.getProductDetails(
            acquisition("alpha.myshopify.com"),
            new ShopifyBridgeActionExecuteRequest(
                "shopify_get_product_details",
                Map.of(
                    "product_id", "gid://shopify/Product/123",
                    "country", "US",
                    "language", "EN",
                    "options", Map.of("Size", "10")
                ),
                null,
                Map.of()
            )
        );

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("Product details");
        assertThat(result.data()).containsEntry("adapterType", "mcp-tool");
        assertThat(result.data()).containsEntry("mcpServerRef", "shopify-storefront");
        assertThat(result.data()).containsEntry("mcpEndpointKind", "STOREFRONT_STANDARD");
        assertThat(result.data()).containsEntry("mcpToolName", "get_product_details");
        Map<?, ?> evidence = (Map<?, ?>) result.data().get("evidence");
        assertThat(evidence.get("toolName")).isEqualTo("get_product_details");

        JsonNode arguments = argumentsCaptor.getValue();
        assertThat(arguments.path("product_id").asText()).isEqualTo("gid://shopify/Product/123");
        assertThat(arguments.path("country").asText()).isEqualTo("US");
        assertThat(arguments.path("language").asText()).isEqualTo("EN");
        assertThat(arguments.path("options").path("Size").asText()).isEqualTo("10");
    }

    private ShopifyBridgeCredentialAcquisition acquisition(String shopDomain) {
        return new ShopifyBridgeCredentialAcquisition(
            new ShopifyBridgeStoreSummary(
                "shp-1",
                shopDomain,
                "Alpha",
                "shopify-bridge-prod",
                "Shopify Bridge Prod",
                "cust-1",
                "Alpha Customer",
                "dep-1",
                "Alpha Deployment",
                "ACTIVE",
                "consumer-1",
                "Alpha Storefront",
                "INSTALLED",
                "SYNCED",
                "READY",
                "ENABLED",
                "LIVE",
                true,
                true,
                true,
                true,
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.parse("2026-04-19T00:00:00Z"),
                Instant.parse("2026-04-19T00:00:00Z"),
                Instant.parse("2026-04-19T00:00:00Z"),
                Instant.parse("2026-04-19T00:00:00Z"),
                Instant.parse("2026-04-19T00:00:00Z")
            ),
            new ShopifyTokenExchangeMaterial(
                "token-1",
                null,
                null,
                null,
                "read_products,read_content,read_legal_policies",
                false
            )
        );
    }
}
