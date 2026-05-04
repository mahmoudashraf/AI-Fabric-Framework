package com.ai.fabric.product.shopify.bridge.mcp.execution;

import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionExecuteRequest;
import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionResult;
import com.ai.fabric.product.shopify.bridge.mcp.client.McpStreamableHttpClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpActionExecutionGatewayTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void executesMarketplaceProvidedMcpToolConfigAndNormalizesMappedEvidence() throws Exception {
        McpStreamableHttpClient client = mock(McpStreamableHttpClient.class);
        McpActionExecutionGateway gateway = new McpActionExecutionGateway(client, objectMapper);
        ArgumentCaptor<JsonNode> arguments = ArgumentCaptor.forClass(JsonNode.class);
        when(client.toolsCall(
            eq(URI.create("https://inventory.example/mcp")),
            eq("inventory.search"),
            arguments.capture(),
            any(McpStreamableHttpClient.McpRequestOptions.class)
        )).thenReturn(objectMapper.readTree("""
            {
              "content": [{"type": "text", "text": "2 products"}],
              "structuredContent": {
                "products": [
                  {"title": "Travel Bag"},
                  {"title": "Desk Lamp"}
                ]
              }
            }
            """));

        ShopifyBridgeActionResult result = gateway.execute(
            "alpha.myshopify.com",
            new ShopifyBridgeActionExecuteRequest(
                "inventory_search",
                Map.of("query", "bag", "limit", 2),
                "idem-1",
                Map.of(
                    "actionConfig", Map.of(
                        "execution", Map.of(
                            "adapterType", "mcp-tool",
                            "mcp", Map.of(
                                "serverRef", "inventory-mcp",
                                "toolName", "inventory.search",
                                "toolSchemaHash", "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                                "argumentTemplate", Map.of(
                                    "query", "{{params.query}}",
                                    "limit", "{{params.limit}}",
                                    "shop", "{{shopDomain}}"
                                ),
                                "responseMapping", Map.of(
                                    "resultPath", "$.structuredContent.products",
                                    "contentPath", "$.content[0].text"
                                )
                            )
                        ),
                        "mcpServers", Map.of(
                            "inventory-mcp", Map.of(
                                "transport", "STREAMABLE_HTTP",
                                "endpointUrl", "https://inventory.example/mcp",
                                "auth", Map.of("mode", "NONE")
                            )
                        )
                    )
                )
            )
        );

        assertThat(result.success()).isTrue();
        assertThat(result.data()).containsEntry("adapterType", "mcp-tool");
        assertThat(result.data()).containsEntry("mcpServerRef", "inventory-mcp");
        assertThat(result.data()).containsEntry("mcpToolName", "inventory.search");
        assertThat(result.data()).containsEntry("evidenceType", "MCP_TOOL_RESULT");
        assertThat(arguments.getValue().path("query").asText()).isEqualTo("bag");
        assertThat(arguments.getValue().path("limit").asInt()).isEqualTo(2);
        assertThat(arguments.getValue().path("shop").asText()).isEqualTo("alpha.myshopify.com");
        @SuppressWarnings("unchecked")
        Map<String, Object> evidence = (Map<String, Object>) result.data().get("normalizedEvidence");
        assertThat(evidence).containsKey("mappedResult");
        assertThat(evidence).containsEntry("content", "2 products");
    }

    @Test
    void apiKeyHeaderAuthUsesAllowlistedHeaderAndResolvedSecretValue() throws Exception {
        McpStreamableHttpClient client = mock(McpStreamableHttpClient.class);
        McpActionExecutionGateway gateway = new McpActionExecutionGateway(client, objectMapper);
        ArgumentCaptor<McpStreamableHttpClient.McpRequestOptions> options =
            ArgumentCaptor.forClass(McpStreamableHttpClient.McpRequestOptions.class);
        when(client.toolsCall(
            eq(URI.create("https://inventory.example/mcp")),
            eq("inventory.search"),
            any(JsonNode.class),
            options.capture()
        )).thenReturn(objectMapper.readTree("""
            {"content": [{"type": "text", "text": "ok"}]}
            """));

        ShopifyBridgeActionResult result = gateway.execute(
            "alpha.myshopify.com",
            new ShopifyBridgeActionExecuteRequest(
                "inventory_search",
                Map.of("query", "bag"),
                null,
                Map.of(
                    "execution", Map.of(
                        "mcp", Map.of(
                            "serverRef", "inventory-mcp",
                            "toolName", "inventory.search",
                            "argumentTemplate", Map.of("query", "{{params.query}}")
                        )
                    ),
                    "mcpServers", Map.of(
                        "inventory-mcp", Map.of(
                            "endpointUrl", "https://inventory.example/mcp",
                            "auth", Map.of(
                                "mode", "API_KEY_HEADER_SECRET",
                                "headerName", "X-MCP-API-Key",
                                "secretRef", "INVENTORY_MCP_API_KEY"
                            )
                        )
                    ),
                    "secretValues", Map.of("INVENTORY_MCP_API_KEY", "secret-value")
                )
            )
        );

        assertThat(result.success()).isTrue();
        assertThat(options.getValue().headers()).containsEntry("X-MCP-API-Key", "secret-value");
    }
}
