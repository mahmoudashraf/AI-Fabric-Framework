package com.ai.fabric.product.shopify.bridge.mcp.execution;

import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionExecuteRequest;
import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionResult;
import com.ai.fabric.product.shopify.bridge.config.McpExecutionGatewayProperties;
import com.ai.fabric.product.shopify.bridge.config.ShopifyMcpExternalAuthProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class McpActionExecutionGatewayTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void supportsMarketplaceProvidedMcpTraceConfig() {
        McpActionExecutionGateway gateway = new McpActionExecutionGateway(
            properties("https://mcp-gateway.internal", "secret"),
            objectMapper,
            RestClient.builder()
        );

        boolean supported = gateway.supports(new ShopifyBridgeActionExecuteRequest(
            "inventory_search",
            Map.of("query", "bag"),
            null,
            Map.of("actionConfig", Map.of(
                "adapterType", "mcp-tool",
                "execution", Map.of("mcp", Map.of("serverRef", "inventory-mcp", "toolName", "inventory.search"))
            ))
        ));

        assertThat(supported).isTrue();
    }

    @Test
    void failsClosedWhenGatewayConfigIsMissing() {
        McpActionExecutionGateway gateway = new McpActionExecutionGateway(
            properties("", ""),
            objectMapper,
            RestClient.builder()
        );

        ShopifyBridgeActionResult result = gateway.execute(
            "alpha.myshopify.com",
            new ShopifyBridgeActionExecuteRequest(
                "inventory_search",
                Map.of("query", "bag"),
                null,
                Map.of("actionConfig", Map.of(
                    "adapterType", "mcp-tool",
                    "execution", Map.of("mcp", Map.of("serverRef", "inventory-mcp", "toolName", "inventory.search"))
                ))
            )
        );

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("MCP_GATEWAY_NOT_CONFIGURED");
    }

    @Test
    void storefrontReadinessVerifiesLiveStorefrontMcpEndpoint() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        McpActionExecutionGateway gateway = new McpActionExecutionGateway(
            properties("https://mcp-gateway.internal", "secret"),
            objectMapper,
            builder
        );

        server.expect(requestTo("https://mcp-gateway.internal/api/internal/mcp/servers/tools/list"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-MCP-GATEWAY-API-KEY", "secret"))
            .andRespond(withSuccess("""
                {
                  "success": true,
                  "result": [
                    {"name": "search_catalog"},
                    {"name": "search_shop_policies_and_faqs"},
                    {"name": "get_cart"},
                    {"name": "update_cart"},
                    {"name": "get_product_details"}
                  ]
                }
                """, MediaType.APPLICATION_JSON));

        Map<String, Object> readiness = gateway.storefrontReadiness("Alpha.MyShopify.Com");

        assertThat(readiness).containsEntry("ready", true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> servers = (List<Map<String, Object>>) readiness.get("servers");
        assertThat(servers).hasSize(1);
        assertThat(servers).extracting(serverSummary -> serverSummary.get("serverRef"))
            .containsExactly("shopify-storefront");
        assertThat(servers).extracting(serverSummary -> serverSummary.get("endpointUrl"))
            .containsExactly("https://alpha.myshopify.com/api/mcp");
        assertThat(servers).allMatch(serverSummary -> Boolean.TRUE.equals(serverSummary.get("ready")));
        server.verify();
    }

    @Test
    void customerAccountMcpFailsClosedUntilExternalOauthPostureIsConfigured() {
        McpActionExecutionGateway gateway = new McpActionExecutionGateway(
            properties("https://mcp-gateway.internal", "secret"),
            externalAuth(false, false, false),
            objectMapper,
            RestClient.builder()
        );

        ShopifyBridgeActionResult result = gateway.execute(
            "alpha.myshopify.com",
            new ShopifyBridgeActionExecuteRequest(
                "shopify_lookup_order",
                Map.of("order_id", "gid://shopify/Order/1"),
                null,
                Map.of("actionConfig", Map.of(
                    "execution", Map.of("mcp", Map.of(
                        "serverRef", "shopify-customer-account",
                        "endpointKind", "CUSTOMER_ACCOUNT",
                        "authMode", "CUSTOMER_OAUTH_PKCE",
                        "toolName", "lookup_order"
                    ))
                ))
            )
        );

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("CUSTOMER_ACCOUNT_MCP_NOT_CONFIGURED");
    }

    @Test
    void customerAccountMcpForwardsSessionTokenFromTraceOnly() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        McpActionExecutionGateway gateway = new McpActionExecutionGateway(
            properties("https://mcp-gateway.internal", "secret"),
            externalAuth(true, false, false),
            objectMapper,
            builder
        );

        server.expect(requestTo("https://mcp-gateway.internal/api/internal/mcp/actions/execute"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-MCP-GATEWAY-API-KEY", "secret"))
            .andExpect(content().json("""
                {
                  "actionId": "shopify_lookup_order",
                  "params": {"order_id": "gid://shopify/Order/1"},
                  "trace": {
                    "shopDomain": "alpha.myshopify.com",
                    "mcpCustomerAccessToken": "customer-token"
                  }
                }
                """))
            .andRespond(withSuccess("""
                {
                  "success": true,
                  "message": "ok",
                  "data": {"orderName": "#1001"}
                }
                """, MediaType.APPLICATION_JSON));

        ShopifyBridgeActionResult result = gateway.execute(
            "alpha.myshopify.com",
            new ShopifyBridgeActionExecuteRequest(
                "shopify_lookup_order",
                Map.of("order_id", "gid://shopify/Order/1"),
                null,
                Map.of(
                    "mcpCustomerAccessToken", "customer-token",
                    "actionConfig", Map.of(
                        "execution", Map.of("mcp", Map.of(
                            "serverRef", "shopify-customer-account",
                            "endpointKind", "CUSTOMER_ACCOUNT",
                            "authMode", "CUSTOMER_OAUTH_PKCE",
                            "toolName", "lookup_order"
                        ))
                    )
                )
            )
        );

        assertThat(result.success()).isTrue();
        assertThat(result.data()).containsEntry("orderName", "#1001");
        server.verify();
    }

    @Test
    void checkoutMcpFailsClosedUntilClientCredentialsAreConfigured() {
        McpActionExecutionGateway gateway = new McpActionExecutionGateway(
            properties("https://mcp-gateway.internal", "secret"),
            externalAuth(false, false, false),
            objectMapper,
            RestClient.builder()
        );

        ShopifyBridgeActionResult result = gateway.execute(
            "alpha.myshopify.com",
            checkoutRequest(Map.of("id", "checkout-1"), false)
        );

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("CHECKOUT_MCP_NOT_CONFIGURED");
    }

    @Test
    void terminalCheckoutMcpFailsClosedWithoutExplicitEnablement() {
        McpActionExecutionGateway gateway = new McpActionExecutionGateway(
            properties("https://mcp-gateway.internal", "secret"),
            externalAuth(false, true, false),
            objectMapper,
            RestClient.builder()
        );

        ShopifyBridgeActionResult result = gateway.execute(
            "alpha.myshopify.com",
            checkoutRequest(Map.of("id", "checkout-1"), true)
        );

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("CHECKOUT_TERMINAL_OPERATION_DISABLED");
    }

    private McpExecutionGatewayProperties properties(String baseUrl, String apiKey) {
        return new McpExecutionGatewayProperties(
            baseUrl,
            apiKey,
            "X-MCP-GATEWAY-API-KEY",
            "/api/internal/mcp/actions/execute",
            Duration.ofSeconds(1),
            Duration.ofSeconds(5)
        );
    }

    private ShopifyMcpExternalAuthProperties externalAuth(boolean customerConfigured,
                                                         boolean checkoutEnabled,
                                                         boolean terminalCheckoutEnabled) {
        return new ShopifyMcpExternalAuthProperties(
            customerConfigured,
            customerConfigured,
            customerConfigured ? "customer-client-id" : "",
            customerConfigured ? "https://bridge.example/customer/callback" : "",
            customerConfigured ? List.of("customer-account-mcp-api:full") : List.of(),
            checkoutEnabled,
            terminalCheckoutEnabled
        );
    }

    private ShopifyBridgeActionExecuteRequest checkoutRequest(Map<String, Object> params, boolean terminal) {
        return new ShopifyBridgeActionExecuteRequest(
            terminal ? "shopify_complete_checkout" : "shopify_get_checkout",
            params,
            terminal ? "idem-1" : null,
            Map.of("actionConfig", Map.of(
                "execution", Map.of("mcp", Map.of(
                    "serverRef", "shopify-checkout",
                    "endpointKind", "CHECKOUT_UCP",
                    "authMode", "SHOPIFY_AGENTIC_CLIENT_CREDENTIALS",
                    "requiresTerminalCheckoutEnablement", terminal,
                    "requiresIdempotencyKey", terminal,
                    "toolName", terminal ? "complete_checkout" : "get_checkout"
                ))
            ))
        );
    }
}
