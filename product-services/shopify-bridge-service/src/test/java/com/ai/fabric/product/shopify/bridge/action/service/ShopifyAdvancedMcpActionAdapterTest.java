package com.ai.fabric.product.shopify.bridge.action.service;

import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionExecuteRequest;
import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionResult;
import com.ai.fabric.product.shopify.bridge.client.mcp.ShopifyMcpClient;
import com.ai.fabric.product.shopify.bridge.client.mcp.ShopifyMcpClient.ShopifyMcpRequestOptions;
import com.ai.fabric.product.shopify.bridge.config.ShopifyCheckoutMcpProperties;
import com.ai.fabric.product.shopify.bridge.config.ShopifyCustomerAccountMcpProperties;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShopifyAdvancedMcpActionAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void customerAccountActionCallsDiscoveredMcpEndpointWithCustomerAuthorization() throws Exception {
        ShopifyMcpClient mcpClient = mock(ShopifyMcpClient.class);
        ShopifyCustomerAccountMcpDiscoveryService discoveryService = mock(ShopifyCustomerAccountMcpDiscoveryService.class);
        ShopifyCustomerAccountMcpActionAdapter adapter = new ShopifyCustomerAccountMcpActionAdapter(
            mcpClient,
            new ShopifyCustomerAccountMcpProperties(
                true,
                true,
                "app-id-1",
                "https://bridge.example/customer/callback",
                "customer-account-mcp-api:full"
            ),
            discoveryService,
            objectMapper
        );
        URI endpoint = URI.create("https://alpha.myshopify.com/customer/api/mcp");
        when(discoveryService.discover("alpha.myshopify.com")).thenReturn(
            new ShopifyCustomerAccountMcpDiscoveryService.CustomerAccountMcpDiscovery(
                URI.create("https://alpha.myshopify.com/.well-known/customer-account-api"),
                endpoint,
                null,
                null,
                objectMapper.createObjectNode()
            )
        );
        ArgumentCaptor<JsonNode> arguments = ArgumentCaptor.forClass(JsonNode.class);
        ArgumentCaptor<ShopifyMcpRequestOptions> options = ArgumentCaptor.forClass(ShopifyMcpRequestOptions.class);
        when(mcpClient.toolsCall(
            eq(endpoint),
            eq("lookup_order"),
            arguments.capture(),
            options.capture()
        )).thenReturn(objectMapper.readTree("""
            {"content":[{"type":"text","text":"Order"}],"structuredContent":{"id":"gid://shopify/Order/1"}}
            """));

        ShopifyBridgeActionResult result = adapter.lookupOrder(
            acquisition("alpha.myshopify.com"),
            new ShopifyBridgeActionExecuteRequest(
                "shopify_lookup_order",
                Map.of(
                    "shopperSessionId", "session-1",
                    "customerAccountAccessToken", "customer-token-1",
                    "order_id", "gid://shopify/Order/1"
                ),
                null,
                Map.of()
            )
        );

        assertThat(result.success()).isTrue();
        assertThat(result.data()).containsEntry("mcpServerRef", "shopify-customer-account");
        assertThat(result.data()).containsEntry("mcpToolName", "lookup_order");
        assertThat(options.getValue().headers()).containsEntry("Authorization", "customer-token-1");
        assertThat(arguments.getValue().path("order_id").asText()).isEqualTo("gid://shopify/Order/1");
        assertThat(arguments.getValue().has("customerAccountAccessToken")).isFalse();
    }

    @Test
    void customerAccountActionRequiresConfiguredProtectedDataPosture() {
        ShopifyCustomerAccountMcpActionAdapter adapter = new ShopifyCustomerAccountMcpActionAdapter(
            mock(ShopifyMcpClient.class),
            new ShopifyCustomerAccountMcpProperties(false, false, "", "", null),
            mock(ShopifyCustomerAccountMcpDiscoveryService.class),
            objectMapper
        );

        ShopifyBridgeActionResult result = adapter.getCustomerOrders(
            acquisition("alpha.myshopify.com"),
            new ShopifyBridgeActionExecuteRequest(
                "shopify_get_customer_orders",
                Map.of("shopperSessionId", "session-1", "customerAccountAccessToken", "customer-token-1"),
                null,
                Map.of()
            )
        );

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("CUSTOMER_ACCOUNT_MCP_NOT_CONFIGURED");
    }

    @Test
    void checkoutCreateUsesBearerTokenAndUcpProfileMetadata() throws Exception {
        ShopifyMcpClient mcpClient = mock(ShopifyMcpClient.class);
        ShopifyCheckoutMcpTokenService tokenService = mock(ShopifyCheckoutMcpTokenService.class);
        ShopifyCheckoutMcpActionAdapter adapter = new ShopifyCheckoutMcpActionAdapter(
            mcpClient,
            tokenService,
            new ShopifyCheckoutMcpProperties(
                true,
                "client-1",
                "secret-1",
                URI.create("https://api.shopify.com/auth/access_token"),
                null,
                false
            ),
            new ShopifyStorefrontMcpProperties("2025-11-25", "https://profiles.example/ucp.json", null, null),
            objectMapper
        );
        URI endpoint = URI.create("https://alpha.myshopify.com/api/ucp/mcp");
        when(tokenService.accessToken()).thenReturn("checkout-token-1");
        ArgumentCaptor<JsonNode> arguments = ArgumentCaptor.forClass(JsonNode.class);
        ArgumentCaptor<ShopifyMcpRequestOptions> options = ArgumentCaptor.forClass(ShopifyMcpRequestOptions.class);
        when(mcpClient.toolsCall(
            eq(endpoint),
            eq("create_checkout"),
            arguments.capture(),
            options.capture()
        )).thenReturn(objectMapper.readTree("""
            {"content":[{"type":"text","text":"Checkout"}],"structuredContent":{"continue_url":"https://checkout.example"}}
            """));

        ShopifyBridgeActionResult result = adapter.createCheckout(
            acquisition("alpha.myshopify.com"),
            new ShopifyBridgeActionExecuteRequest(
                "shopify_create_checkout",
                Map.of("shopperSessionId", "session-1", "cart_id", "gid://shopify/Cart/1"),
                null,
                Map.of()
            )
        );

        assertThat(result.success()).isTrue();
        assertThat(result.data()).containsEntry("mcpServerRef", "shopify-checkout");
        assertThat(result.data()).containsEntry("mcpToolName", "create_checkout");
        assertThat(options.getValue().headers()).containsEntry("Authorization", "Bearer checkout-token-1");
        assertThat(arguments.getValue().path("meta").path("ucp-agent").path("profile").asText())
            .isEqualTo("https://profiles.example/ucp.json");
        assertThat(arguments.getValue().path("cart_id").asText()).isEqualTo("gid://shopify/Cart/1");
    }

    @Test
    void checkoutTerminalOperationIsDisabledUnlessExplicitlyEnabled() {
        ShopifyCheckoutMcpActionAdapter adapter = new ShopifyCheckoutMcpActionAdapter(
            mock(ShopifyMcpClient.class),
            mock(ShopifyCheckoutMcpTokenService.class),
            new ShopifyCheckoutMcpProperties(
                true,
                "client-1",
                "secret-1",
                URI.create("https://api.shopify.com/auth/access_token"),
                null,
                false
            ),
            new ShopifyStorefrontMcpProperties("2025-11-25", "https://profiles.example/ucp.json", null, null),
            objectMapper
        );

        ShopifyBridgeActionResult result = adapter.completeCheckout(
            acquisition("alpha.myshopify.com"),
            new ShopifyBridgeActionExecuteRequest(
                "shopify_complete_checkout",
                Map.of("shopperSessionId", "session-1", "id", "gid://shopify/Checkout/1"),
                "idem-1",
                Map.of()
            )
        );

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("CHECKOUT_TERMINAL_OPERATION_DISABLED");
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
            new ShopifyTokenExchangeMaterial("token-1", null, null, null, "read_products", false)
        );
    }
}
