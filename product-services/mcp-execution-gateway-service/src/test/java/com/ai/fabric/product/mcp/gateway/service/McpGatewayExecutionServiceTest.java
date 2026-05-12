package com.ai.fabric.product.mcp.gateway.service;

import com.ai.fabric.product.mcp.gateway.client.McpStreamableHttpClient;
import com.ai.fabric.product.mcp.gateway.config.McpGatewayProperties;
import com.ai.fabric.product.mcp.gateway.model.McpGatewayContracts.ActionExecuteRequest;
import com.ai.fabric.product.mcp.gateway.model.McpGatewayContracts.ActionExecuteResponse;
import com.ai.fabric.product.mcp.gateway.model.McpGatewayContracts.DiscoveryRequest;
import com.ai.fabric.product.mcp.gateway.model.McpGatewayContracts.DiscoveryResponse;
import com.ai.fabric.product.mcp.gateway.model.McpGatewayContracts.ExpectedTool;
import com.ai.fabric.product.mcp.gateway.model.McpGatewayContracts.ServerVerificationRequest;
import com.ai.fabric.product.mcp.gateway.model.McpGatewayContracts.ServerVerificationResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class McpGatewayExecutionServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final McpGatewayProperties properties = new McpGatewayProperties(
        "mcp-gateway-test",
        "test",
        "secret",
        "X-MCP-GATEWAY-API-KEY",
        "2025-11-25",
        java.util.List.of("X-API-KEY", "X-MCP-API-KEY", "X-LOOM-MCP-KEY"),
        java.util.List.of("MCP_PROFILE_SHOPIFY_UCP_AGENT", "SHOPIFY_BRIDGE_MCP_UCP_AGENT_PROFILE"),
        false,
        "MCP_SECRET_",
        Duration.ofSeconds(1),
        Duration.ofSeconds(5)
    );

    @Test
    void executesMarketplaceMcpToolConfigAndNormalizesEvidence() throws Exception {
        McpStreamableHttpClient client = mock(McpStreamableHttpClient.class);
        McpGatewayExecutionService service = new McpGatewayExecutionService(client, objectMapper, properties);
        McpStreamableHttpClient.McpSession session = new McpStreamableHttpClient.McpSession(
            URI.create("https://example.com/mcp"),
            "2025-11-25",
            "session-1",
            objectMapper.createObjectNode()
        );
        ArgumentCaptor<JsonNode> arguments = ArgumentCaptor.forClass(JsonNode.class);
        when(client.initialize(eq(URI.create("https://example.com/mcp")), any())).thenReturn(session);
        when(client.toolsCall(
            eq(session),
            eq("inventory.search"),
            arguments.capture(),
            any()
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

        ActionExecuteResponse response = service.executeAction(new ActionExecuteRequest(
            "inventory_search",
            Map.of("query", "bag", "limit", 2),
            "idem-1",
            Map.of("actionConfig", actionConfig()),
            null
        ));

        assertThat(response.success()).isTrue();
        assertThat(response.data()).containsEntry("adapterType", "mcp-tool");
        assertThat(response.data()).containsEntry("mcpServerRef", "inventory-mcp");
        assertThat(response.data()).containsEntry("mcpToolName", "inventory.search");
        assertThat(arguments.getValue().path("query").asText()).isEqualTo("bag");
        assertThat(arguments.getValue().path("limit").asInt()).isEqualTo(2);
        @SuppressWarnings("unchecked")
        Map<String, Object> evidence = (Map<String, Object>) response.data().get("normalizedEvidence");
        assertThat(evidence).containsKey("mappedResult");
        assertThat(evidence).containsEntry("content", "2 products");
    }

    @Test
    void discoveryCanonicalizesSchemaHashIgnoringDescriptionsAndOrder() throws Exception {
        McpStreamableHttpClient client = mock(McpStreamableHttpClient.class);
        McpGatewayExecutionService service = new McpGatewayExecutionService(client, objectMapper, properties);
        McpStreamableHttpClient.McpSession session = new McpStreamableHttpClient.McpSession(
            URI.create("https://example.com/mcp"),
            "2025-11-25",
            null,
            objectMapper.createObjectNode()
        );
        when(client.initialize(eq(URI.create("https://example.com/mcp")), any())).thenReturn(session);
        when(client.toolsList(eq(session), any())).thenReturn(objectMapper.readTree("""
            {
              "tools": [
                {
                  "name": "inventory.search",
                  "description": "Search products.",
                  "inputSchema": {
                    "type": "object",
                    "required": ["query", "limit"],
                    "properties": {
                      "query": {"description": "Text", "type": "string"},
                      "limit": {"type": "integer"}
                    }
                  }
                }
              ]
            }
            """));
        DiscoveryResponse first = service.discover(new DiscoveryRequest(
            "inventory-mcp",
            Map.of("endpointUrl", "https://example.com/mcp", "auth", Map.of("mode", "NONE")),
            Map.of(),
            java.util.List.of()
        ));
        when(client.toolsList(eq(session), any())).thenReturn(objectMapper.readTree("""
            {
              "tools": [
                {
                  "name": "inventory.search",
                  "description": "Different text.",
                  "inputSchema": {
                    "required": ["limit", "query"],
                    "properties": {
                      "limit": {"type": "integer", "description": "Max"},
                      "query": {"type": "string"}
                    },
                    "type": "object"
                  }
                }
              ]
            }
            """));

        DiscoveryResponse second = service.discover(new DiscoveryRequest(
            "inventory-mcp",
            Map.of("endpointUrl", "https://example.com/mcp", "auth", Map.of("mode", "NONE")),
            Map.of(),
            java.util.List.of()
        ));

        assertThat(first.ready()).isTrue();
        assertThat(second.ready()).isTrue();
        assertThat(first.tools()).hasSize(1);
        assertThat(second.tools()).hasSize(1);
        assertThat(first.tools().getFirst().schemaHash()).isEqualTo(second.tools().getFirst().schemaHash());
    }

    @Test
    void discoveryRejectsNonPublicMcpEndpointBeforeOutboundRequest() {
        McpStreamableHttpClient client = mock(McpStreamableHttpClient.class);
        McpGatewayExecutionService service = new McpGatewayExecutionService(client, objectMapper, properties);

        DiscoveryResponse response = service.discover(new DiscoveryRequest(
            "metadata-mcp",
            Map.of("endpointUrl", "https://169.254.169.254/latest", "auth", Map.of("mode", "NONE")),
            Map.of(),
            List.of()
        ));

        assertThat(response.ready()).isFalse();
        assertThat(response.message()).contains("MCP server endpoint host is not allowed");
        verify(client, never()).initialize(any(), any());
    }

    @Test
    void discoveryRejectsUnresolvableMcpEndpointBeforeOutboundRequest() {
        McpStreamableHttpClient client = mock(McpStreamableHttpClient.class);
        McpGatewayExecutionService service = new McpGatewayExecutionService(client, objectMapper, properties);

        DiscoveryResponse response = service.discover(new DiscoveryRequest(
            "unresolved-mcp",
            Map.of("endpointUrl", "https://does-not-resolve.invalid/mcp", "auth", Map.of("mode", "NONE")),
            Map.of(),
            List.of()
        ));

        assertThat(response.ready()).isFalse();
        assertThat(response.message()).contains("MCP outbound host could not be resolved");
        verify(client, never()).initialize(any(), any());
    }

    @Test
    void executionRejectsPrivateOauthTokenEndpointBeforeOutboundRequest() {
        McpStreamableHttpClient client = mock(McpStreamableHttpClient.class);
        McpGatewayExecutionService service = new McpGatewayExecutionService(client, objectMapper, properties);

        ActionExecuteResponse response = service.executeAction(new ActionExecuteRequest(
            "inventory_search",
            Map.of("query", "bag"),
            null,
            Map.of(
                "mcpSecretValues", Map.of("MCP_SECRET_CLIENT_SECRET", "client-secret"),
                "actionConfig", Map.of(
                    "execution", Map.of(
                        "adapterType", "mcp-tool",
                        "mcp", Map.of(
                            "serverRef", "inventory-mcp",
                            "toolName", "inventory.search"
                        )
                    ),
                    "mcpServers", Map.of(
                        "inventory-mcp", Map.of(
                            "endpointUrl", "https://example.com/mcp",
                            "auth", Map.of(
                                "mode", "OAUTH2_CLIENT_CREDENTIALS",
                                "tokenUrl", "https://169.254.169.254/oauth/token",
                                "clientId", "client-1",
                                "clientSecretRef", "MCP_SECRET_CLIENT_SECRET"
                            )
                        )
                    )
                )
            ),
            null
        ));

        assertThat(response.success()).isFalse();
        assertThat(response.errorCode()).isEqualTo("INVALID_MCP_ACTION_CONFIG");
        assertThat(response.message()).contains("MCP OAuth2 tokenUrl host is not allowed");
        verify(client, never()).initialize(any(), any());
    }

    @Test
    void executesWithEnvironmentSecretResolutionOnlyWhenExplicitlyEnabledAndPrefixed() throws Exception {
        McpStreamableHttpClient client = mock(McpStreamableHttpClient.class);
        McpGatewayProperties envProperties = new McpGatewayProperties(
            "mcp-gateway-test",
            "test",
            "secret",
            "X-MCP-GATEWAY-API-KEY",
            "2025-11-25",
            java.util.List.of("X-API-KEY", "X-MCP-API-KEY", "X-LOOM-MCP-KEY"),
            java.util.List.of("MCP_PROFILE_SHOPIFY_UCP_AGENT", "SHOPIFY_BRIDGE_MCP_UCP_AGENT_PROFILE"),
            true,
            "MCP_SECRET_",
            Duration.ofSeconds(1),
            Duration.ofSeconds(5)
        );
        MockEnvironment environment = new MockEnvironment()
            .withProperty("MCP_SECRET_VENDOR_TOKEN", "env-vendor-token");
        McpGatewayExecutionService service = new McpGatewayExecutionService(client, objectMapper, envProperties, environment);
        McpStreamableHttpClient.McpSession session = new McpStreamableHttpClient.McpSession(
            URI.create("https://example.com/mcp"),
            "2025-11-25",
            "session-1",
            objectMapper.createObjectNode()
        );
        ArgumentCaptor<McpStreamableHttpClient.McpRequestOptions> options =
            ArgumentCaptor.forClass(McpStreamableHttpClient.McpRequestOptions.class);
        when(client.initialize(eq(URI.create("https://example.com/mcp")), options.capture())).thenReturn(session);
        when(client.toolsCall(eq(session), eq("inventory.search"), any(), any()))
            .thenReturn(objectMapper.readTree("""
                {"structuredContent":{"ok":true}}
                """));

        ActionExecuteResponse response = service.executeAction(new ActionExecuteRequest(
            "inventory_search",
            Map.of("query", "bag"),
            null,
            Map.of("actionConfig", Map.of(
                "execution", Map.of(
                    "adapterType", "mcp-tool",
                    "mcp", Map.of(
                        "serverRef", "inventory-mcp",
                        "toolName", "inventory.search"
                    )
                ),
                "mcpServers", Map.of(
                    "inventory-mcp", Map.of(
                        "endpointUrl", "https://example.com/mcp",
                        "auth", Map.of(
                            "mode", "API_KEY_HEADER_SECRET",
                            "headerName", "X-MCP-API-KEY",
                            "secretRef", "MCP_SECRET_VENDOR_TOKEN"
                        )
                    )
                )
            )),
            null
        ));

        assertThat(response.success()).isTrue();
        assertThat(options.getValue().headers()).containsEntry("X-MCP-API-KEY", "env-vendor-token");
    }

    @Test
    void resolvesShopifyEndpointKindAndAllowlistedProfileRefs() throws Exception {
        McpStreamableHttpClient client = mock(McpStreamableHttpClient.class);
        MockEnvironment environment = new MockEnvironment()
            .withProperty("MCP_PROFILE_SHOPIFY_UCP_AGENT", "https://example.com/ucp-agent.json");
        McpGatewayExecutionService service = new McpGatewayExecutionService(client, objectMapper, properties, environment);
        McpStreamableHttpClient.McpSession session = new McpStreamableHttpClient.McpSession(
            URI.create("https://example.com/api/ucp/mcp"),
            "2025-11-25",
            "session-1",
            objectMapper.createObjectNode()
        );
        ArgumentCaptor<JsonNode> arguments = ArgumentCaptor.forClass(JsonNode.class);
        when(client.initialize(eq(URI.create("https://example.com/api/ucp/mcp")), any())).thenReturn(session);
        when(client.toolsCall(eq(session), eq("search_catalog"), arguments.capture(), any()))
            .thenReturn(objectMapper.readTree("{\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}"));

        ActionExecuteResponse response = service.executeAction(new ActionExecuteRequest(
            "shopify_search_catalog",
            Map.of("query", "snowboard"),
            null,
            Map.of("shopDomain", "example.com", "actionConfig", Map.of(
                "execution", Map.of(
                    "adapterType", "mcp-tool",
                    "mcp", Map.of(
                        "serverRef", "shopify-storefront-ucp",
                        "endpointKind", "UCP_CATALOG",
                        "toolName", "search_catalog",
                        "argumentTemplate", Map.of(
                            "meta", Map.of("ucp-agent", Map.of("profileRef", "MCP_PROFILE_SHOPIFY_UCP_AGENT")),
                            "catalog", Map.of("query", "{{params.query}}")
                        )
                    )
                )
            )),
            null
        ));

        assertThat(response.success()).isTrue();
        assertThat(arguments.getValue().path("meta").path("ucp-agent").has("profileRef")).isFalse();
        assertThat(arguments.getValue().path("meta").path("ucp-agent").path("profile").asText())
            .isEqualTo("https://example.com/ucp-agent.json");
        assertThat(arguments.getValue().path("catalog").path("query").asText()).isEqualTo("snowboard");
    }

    @Test
    void omitsBlankOptionalTemplateArgumentsBeforeToolsCall() throws Exception {
        McpStreamableHttpClient client = mock(McpStreamableHttpClient.class);
        McpGatewayExecutionService service = new McpGatewayExecutionService(client, objectMapper, properties);
        McpStreamableHttpClient.McpSession session = new McpStreamableHttpClient.McpSession(
            URI.create("https://example.com/api/mcp"),
            "2025-11-25",
            "session-1",
            objectMapper.createObjectNode()
        );
        ArgumentCaptor<JsonNode> arguments = ArgumentCaptor.forClass(JsonNode.class);
        when(client.initialize(eq(URI.create("https://example.com/api/mcp")), any())).thenReturn(session);
        when(client.toolsCall(eq(session), eq("search_catalog"), arguments.capture(), any()))
            .thenReturn(objectMapper.readTree("{\"structuredContent\":{\"ok\":true}}"));

        ActionExecuteResponse response = service.executeAction(new ActionExecuteRequest(
            "shopify_search_catalog",
            Map.of("query", "ski wax"),
            null,
            Map.of("shopDomain", "example.com", "actionConfig", Map.of(
                "execution", Map.of(
                    "adapterType", "mcp-tool",
                    "mcp", Map.of(
                        "serverRef", "shopify-storefront",
                        "endpointKind", "STOREFRONT_STANDARD",
                        "toolName", "search_catalog",
                        "argumentTemplate", Map.of(
                            "catalog", Map.of(
                                "query", "{{params.query}}",
                                "context", Map.of(
                                    "address_country", "{{params.country}}",
                                    "intent", "{{params.intent}}"
                                ),
                                "pagination", Map.of("limit", "{{params.limit}}")
                            )
                        )
                    )
                )
            )),
            null
        ));

        assertThat(response.success()).isTrue();
        assertThat(arguments.getValue().path("catalog").path("query").asText()).isEqualTo("ski wax");
        assertThat(arguments.getValue().path("catalog").has("context")).isFalse();
        assertThat(arguments.getValue().path("catalog").has("pagination")).isFalse();
    }

    @Test
    void failsClosedWhenRequiredAnyArgumentsAreEmpty() {
        McpStreamableHttpClient client = mock(McpStreamableHttpClient.class);
        McpGatewayExecutionService service = new McpGatewayExecutionService(client, objectMapper, properties);

        ActionExecuteResponse response = service.executeAction(new ActionExecuteRequest(
            "shopify_update_cart",
            Map.of("shopperSessionId", "session-1", "confirmationAccepted", true),
            null,
            Map.of("shopDomain", "example.com", "actionConfig", Map.of(
                "execution", Map.of(
                    "adapterType", "mcp-tool",
                    "mcp", Map.of(
                        "serverRef", "shopify-storefront",
                        "endpointKind", "STOREFRONT_STANDARD",
                        "toolName", "update_cart",
                        "requiredAnyArguments", List.of("add_items", "update_items", "remove_line_ids"),
                        "argumentTemplate", Map.of(
                            "add_items", "{{params.add_items}}",
                            "update_items", "{{params.update_items}}",
                            "remove_line_ids", "{{params.remove_line_ids}}"
                        )
                    )
                )
            )),
            null
        ));

        assertThat(response.success()).isFalse();
        assertThat(response.errorCode()).isEqualTo("INVALID_MCP_ACTION_ARGUMENTS");
        assertThat(response.message()).contains("add_items");
        verify(client, never()).initialize(any(), any());
        verify(client, never()).toolsCall(any(McpStreamableHttpClient.McpSession.class), any(), any(), any());
        verify(client, never()).toolsCall(any(URI.class), any(), any(), any());
    }

    @Test
    void failsClosedWhenArrayArgumentItemMissesRequiredProperties() {
        McpStreamableHttpClient client = mock(McpStreamableHttpClient.class);
        McpGatewayExecutionService service = new McpGatewayExecutionService(client, objectMapper, properties);

        ActionExecuteResponse response = service.executeAction(new ActionExecuteRequest(
            "shopify_update_cart",
            Map.of(
                "add_items", List.of(Map.of("product_id", "gid://shopify/Product/1", "quantity", 1)),
                "shopperSessionId", "session-1",
                "confirmationAccepted", true
            ),
            null,
            Map.of("shopDomain", "example.com", "actionConfig", Map.of(
                "params", List.of(Map.of(
                    "name", "add_items",
                    "type", "ARRAY",
                    "items", Map.of(
                        "type", "OBJECT",
                        "requiredProperties", List.of("product_variant_id", "quantity"),
                        "properties", Map.of(
                            "product_variant_id", Map.of("type", "STRING", "required", true),
                            "quantity", Map.of("type", "INTEGER", "required", true)
                        )
                    )
                )),
                "execution", Map.of(
                    "adapterType", "mcp-tool",
                    "mcp", Map.of(
                        "serverRef", "shopify-storefront",
                        "endpointKind", "STOREFRONT_STANDARD",
                        "toolName", "update_cart",
                        "requiredAnyArguments", List.of("add_items", "update_items", "remove_line_ids"),
                        "argumentTemplate", Map.of(
                            "add_items", "{{params.add_items}}",
                            "update_items", "{{params.update_items}}",
                            "remove_line_ids", "{{params.remove_line_ids}}"
                        )
                    )
                )
            )),
            null
        ));

        assertThat(response.success()).isFalse();
        assertThat(response.errorCode()).isEqualTo("INVALID_MCP_ACTION_ARGUMENTS");
        assertThat(response.message()).contains("add_items[0]");
        assertThat(response.message()).contains("product_variant_id");
        verify(client, never()).initialize(any(), any());
        verify(client, never()).toolsCall(any(McpStreamableHttpClient.McpSession.class), any(), any(), any());
        verify(client, never()).toolsCall(any(URI.class), any(), any(), any());
    }

    @Test
    void failsClosedForMcpAuthModeWithoutConcreteCredentialBinding() {
        McpStreamableHttpClient client = mock(McpStreamableHttpClient.class);
        McpGatewayExecutionService service = new McpGatewayExecutionService(client, objectMapper, properties);

        ActionExecuteResponse response = service.executeAction(new ActionExecuteRequest(
            "shopify_get_order_status",
            Map.of("order_number", "1001", "shopDomain", "example.com"),
            null,
            Map.of("actionConfig", Map.of(
                "execution", Map.of(
                    "adapterType", "mcp-tool",
                    "mcp", Map.of(
                        "serverRef", "shopify-customer-account",
                        "endpointUrl", "https://example.com/customer/api/mcp",
                        "authMode", "CUSTOMER_OAUTH_PKCE",
                        "toolName", "get_order_status"
                    )
                )
            )),
            null
        ));

        assertThat(response.success()).isFalse();
        assertThat(response.errorCode()).isEqualTo("CUSTOMER_ACCOUNT_AUTH_REQUIRED");
        assertThat(response.message()).contains("customer OAuth/PKCE access token");
        verify(client, never()).initialize(any(), any());
    }

    @Test
    void executesCustomerAccountMcpWhenCustomerOauthTokenIsBound() throws Exception {
        McpStreamableHttpClient client = mock(McpStreamableHttpClient.class);
        McpGatewayExecutionService service = new McpGatewayExecutionService(client, objectMapper, properties);
        McpStreamableHttpClient.McpSession session = new McpStreamableHttpClient.McpSession(
            URI.create("https://example.com/customer/mcp"),
            "2025-11-25",
            "session-1",
            objectMapper.createObjectNode()
        );
        ArgumentCaptor<McpStreamableHttpClient.McpRequestOptions> options =
            ArgumentCaptor.forClass(McpStreamableHttpClient.McpRequestOptions.class);
        when(client.initialize(eq(URI.create("https://example.com/customer/mcp")), options.capture())).thenReturn(session);
        when(client.toolsCall(eq(session), eq("get_order_status"), any(), any()))
            .thenReturn(objectMapper.readTree("{\"structuredContent\":{\"status\":\"fulfilled\"}}"));

        ActionExecuteResponse response = service.executeAction(new ActionExecuteRequest(
            "shopify_get_order_status",
            Map.of("order_number", "1001"),
            null,
            Map.of(
                "mcpCustomerAccessToken", "customer-oauth-token",
                "actionConfig", Map.of(
                    "execution", Map.of(
                        "adapterType", "mcp-tool",
                        "mcp", Map.of(
                            "serverRef", "shopify-customer-account",
                            "endpointUrl", "https://example.com/customer/mcp",
                            "authMode", "CUSTOMER_OAUTH_PKCE",
                            "toolName", "get_order_status"
                        )
                    )
                )
            ),
            null
        ));

        assertThat(response.success()).isTrue();
        assertThat(options.getValue().headers()).containsEntry("Authorization", "Bearer customer-oauth-token");
    }

    @Test
    void blocksExecutionWhenConfiguredSchemaHashDrifts() throws Exception {
        McpStreamableHttpClient client = mock(McpStreamableHttpClient.class);
        McpGatewayExecutionService service = new McpGatewayExecutionService(client, objectMapper, properties);
        McpStreamableHttpClient.McpSession session = new McpStreamableHttpClient.McpSession(
            URI.create("https://example.com/mcp"),
            "2025-11-25",
            "session-1",
            objectMapper.createObjectNode()
        );
        when(client.initialize(eq(URI.create("https://example.com/mcp")), any())).thenReturn(session);
        when(client.toolsList(eq(session), any())).thenReturn(objectMapper.readTree("""
            {
              "tools": [
                {
                  "name": "inventory.search",
                  "inputSchema": {
                    "type": "object",
                    "required": ["q"],
                    "properties": {"q": {"type": "string"}}
                  }
                }
              ]
            }
            """));

        Map<String, Object> actionConfig = actionConfigWithSchemaHash("sha256:not-the-observed-hash", "BLOCK_RELEASE");
        ActionExecuteResponse response = service.executeAction(new ActionExecuteRequest(
            "inventory_search",
            Map.of("query", "bag"),
            "idem-1",
            Map.of("actionConfig", actionConfig),
            null
        ));

        assertThat(response.success()).isFalse();
        assertThat(response.errorCode()).isEqualTo("MCP_SCHEMA_DRIFT");
        verify(client, never()).toolsCall(eq(session), eq("inventory.search"), any(), any());
    }

    @Test
    void verifiesExpectedToolsAgainstObservedHashes() throws Exception {
        McpStreamableHttpClient client = mock(McpStreamableHttpClient.class);
        McpGatewayExecutionService service = new McpGatewayExecutionService(client, objectMapper, properties);
        McpStreamableHttpClient.McpSession session = new McpStreamableHttpClient.McpSession(
            URI.create("https://example.com/mcp"),
            "2025-11-25",
            null,
            objectMapper.createObjectNode()
        );
        when(client.initialize(eq(URI.create("https://example.com/mcp")), any())).thenReturn(session);
        when(client.toolsList(eq(session), any())).thenReturn(objectMapper.readTree("""
            {
              "tools": [
                {
                  "name": "inventory.search",
                  "inputSchema": {
                    "type": "object",
                    "required": ["query"],
                    "properties": {"query": {"type": "string"}}
                  }
                }
              ]
            }
            """));

        DiscoveryResponse discovery = service.discover(new DiscoveryRequest(
            "inventory-mcp",
            Map.of("endpointUrl", "https://example.com/mcp", "auth", Map.of("mode", "NONE")),
            Map.of(),
            List.of()
        ));
        ServerVerificationResponse response = service.verify(new ServerVerificationRequest(
            "inventory-mcp",
            Map.of("endpointUrl", "https://example.com/mcp", "auth", Map.of("mode", "NONE")),
            Map.of(),
            List.of(new ExpectedTool(
                "inventory.search",
                discovery.tools().getFirst().schemaHash(),
                "BLOCK_RELEASE"
            ))
        ));

        assertThat(response.ready()).isTrue();
        assertThat(response.tools()).hasSize(1);
        assertThat(response.tools().getFirst().status()).isEqualTo("OK");
    }

    @Test
    void resolvesOauth2ClientCredentialsTokenWithoutLeakingSecretsToMcpServer() throws Exception {
        McpStreamableHttpClient client = mock(McpStreamableHttpClient.class);
        RestClient.Builder tokenBuilder = RestClient.builder();
        MockRestServiceServer tokenServer = MockRestServiceServer.bindTo(tokenBuilder).build();
        tokenServer.expect(requestTo("https://example.com/oauth/token"))
            .andExpect(method(org.springframework.http.HttpMethod.POST))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=client_credentials")))
            .andRespond(withSuccess("{\"access_token\":\"oauth-access-token\",\"token_type\":\"Bearer\"}", org.springframework.http.MediaType.APPLICATION_JSON));
        McpGatewayExecutionService service = new McpGatewayExecutionService(
            client,
            objectMapper,
            properties,
            null,
            tokenBuilder
        );
        McpStreamableHttpClient.McpSession session = new McpStreamableHttpClient.McpSession(
            URI.create("https://example.com/mcp"),
            "2025-11-25",
            "session-1",
            objectMapper.createObjectNode()
        );
        ArgumentCaptor<McpStreamableHttpClient.McpRequestOptions> options =
            ArgumentCaptor.forClass(McpStreamableHttpClient.McpRequestOptions.class);
        when(client.initialize(eq(URI.create("https://example.com/mcp")), options.capture())).thenReturn(session);
        when(client.toolsCall(eq(session), eq("inventory.search"), any(), any()))
            .thenReturn(objectMapper.readTree("{\"structuredContent\":{\"ok\":true}}"));

        ActionExecuteResponse response = service.executeAction(new ActionExecuteRequest(
            "inventory_search",
            Map.of("query", "bag"),
            null,
            Map.of(
                "actionConfig", Map.of(
                    "execution", Map.of(
                        "adapterType", "mcp-tool",
                        "mcp", Map.of(
                            "serverRef", "inventory-mcp",
                            "toolName", "inventory.search"
                        )
                    ),
                    "mcpServers", Map.of(
                        "inventory-mcp", Map.of(
                            "endpointUrl", "https://example.com/mcp",
                            "auth", Map.of(
                                "mode", "OAUTH2_CLIENT_CREDENTIALS",
                                "tokenUrl", "https://example.com/oauth/token",
                                "clientId", "client-1",
                                "clientSecretRef", "MCP_SECRET_CLIENT_SECRET",
                                "scope", "catalog:read"
                            )
                        )
                    )
                ),
                "mcpSecretValues", Map.of("MCP_SECRET_CLIENT_SECRET", "client-secret")
            ),
            null
        ));

        assertThat(response.success()).isTrue();
        assertThat(options.getValue().headers()).containsEntry("Authorization", "Bearer oauth-access-token");
        tokenServer.verify();
    }

    @Test
    void resolvesShopifyCheckoutClientCredentialsWithJsonTokenRequest() throws Exception {
        McpStreamableHttpClient client = mock(McpStreamableHttpClient.class);
        McpGatewayProperties envProperties = new McpGatewayProperties(
            "mcp-gateway-test",
            "test",
            "secret",
            "X-MCP-GATEWAY-API-KEY",
            "2025-11-25",
            java.util.List.of("X-API-KEY", "X-MCP-API-KEY", "X-LOOM-MCP-KEY"),
            java.util.List.of("MCP_PROFILE_SHOPIFY_UCP_AGENT", "SHOPIFY_BRIDGE_MCP_UCP_AGENT_PROFILE"),
            true,
            "MCP_SECRET_",
            Duration.ofSeconds(1),
            Duration.ofSeconds(5)
        );
        MockEnvironment environment = new MockEnvironment()
            .withProperty("MCP_SECRET_SHOPIFY_CHECKOUT_MCP_CLIENT_ID", "checkout-client-id")
            .withProperty("MCP_SECRET_SHOPIFY_CHECKOUT_MCP_CLIENT_SECRET", "checkout-client-secret");
        RestClient.Builder tokenBuilder = RestClient.builder();
        MockRestServiceServer tokenServer = MockRestServiceServer.bindTo(tokenBuilder).build();
        tokenServer.expect(requestTo("https://api.shopify.com/auth/access_token"))
            .andExpect(method(org.springframework.http.HttpMethod.POST))
            .andExpect(content().json("""
                {
                  "grant_type": "client_credentials",
                  "client_id": "checkout-client-id",
                  "client_secret": "checkout-client-secret"
                }
                """))
            .andRespond(withSuccess("{\"access_token\":\"shopify-checkout-token\",\"scope\":\"checkout\",\"expires_in\":3600}", org.springframework.http.MediaType.APPLICATION_JSON));
        McpGatewayExecutionService service = new McpGatewayExecutionService(
            client,
            objectMapper,
            envProperties,
            environment,
            tokenBuilder
        );
        ArgumentCaptor<McpStreamableHttpClient.McpRequestOptions> options =
            ArgumentCaptor.forClass(McpStreamableHttpClient.McpRequestOptions.class);
        when(client.toolsCall(
            eq(URI.create("https://example.com/api/ucp/mcp")),
            eq("get_checkout"),
            any(),
            options.capture()
        ))
            .thenReturn(objectMapper.readTree("{\"structuredContent\":{\"id\":\"checkout-1\"}}"));

        ActionExecuteResponse response = service.executeAction(new ActionExecuteRequest(
            "shopify_get_checkout",
            Map.of("id", "checkout-1"),
            null,
            Map.of(
                "shopDomain", "example.com",
                "buyerIp", "203.0.113.10",
                "actionConfig", Map.of(
                    "execution", Map.of(
                        "adapterType", "mcp-tool",
                        "mcp", Map.of(
                            "serverRef", "shopify-checkout",
                            "endpointKind", "CHECKOUT_UCP",
                            "authMode", "SHOPIFY_AGENTIC_CLIENT_CREDENTIALS",
                            "toolName", "get_checkout",
                            "argumentTemplate", Map.of("id", "{{params.id}}")
                        )
                    )
                )
            ),
            null
        ));

        assertThat(response.success()).isTrue();
        assertThat(options.getValue().headers()).containsEntry("Authorization", "Bearer shopify-checkout-token");
        assertThat(options.getValue().headers()).containsEntry("Shopify-Buyer-IP", "203.0.113.10");
        verify(client, never()).initialize(eq(URI.create("https://example.com/api/ucp/mcp")), any());
        tokenServer.verify();
    }

    private Map<String, Object> actionConfig() {
        return Map.of(
            "execution", Map.of(
                "adapterType", "mcp-tool",
                "mcp", Map.of(
                    "serverRef", "inventory-mcp",
                    "toolName", "inventory.search",
                    "argumentTemplate", Map.of(
                        "query", "{{params.query}}",
                        "limit", "{{params.limit}}"
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
                    "endpointUrl", "https://example.com/mcp",
                    "auth", Map.of("mode", "NONE")
                )
            )
        );
    }

    private Map<String, Object> actionConfigWithSchemaHash(String schemaHash, String driftPolicy) {
        return Map.of(
            "execution", Map.of(
                "adapterType", "mcp-tool",
                "mcp", Map.of(
                    "serverRef", "inventory-mcp",
                    "toolName", "inventory.search",
                    "toolSchemaHash", schemaHash,
                    "schemaDriftPolicy", driftPolicy,
                    "argumentTemplate", Map.of("query", "{{params.query}}")
                )
            ),
            "mcpServers", Map.of(
                "inventory-mcp", Map.of(
                    "transport", "STREAMABLE_HTTP",
                    "endpointUrl", "https://example.com/mcp",
                    "auth", Map.of("mode", "NONE")
                )
            )
        );
    }
}
