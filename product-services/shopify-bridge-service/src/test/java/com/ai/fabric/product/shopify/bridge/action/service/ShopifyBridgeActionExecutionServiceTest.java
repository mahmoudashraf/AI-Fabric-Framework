package com.ai.fabric.product.shopify.bridge.action.service;

import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionExecuteRequest;
import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionResult;
import com.ai.fabric.product.shopify.bridge.client.shopify.ShopifyAdminGraphqlClient;
import com.ai.fabric.product.shopify.bridge.governedaction.service.ShopifyStorefrontGovernedActionService;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyBridgeCredentialAcquisition;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyTokenExchangeMaterial;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyBridgeInstallCredentialService;
import com.ai.fabric.product.shopify.bridge.mcp.execution.McpActionExecutionGateway;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ShopifyBridgeActionExecutionServiceTest {

    @Test
    void marketplaceMcpActionRoutesThroughGenericGateway() {
        ShopifyBridgeInstallCredentialService credentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyAdminGraphqlClient graphqlClient = mock(ShopifyAdminGraphqlClient.class);
        ShopifyStorefrontGovernedActionService governedActionService = mock(ShopifyStorefrontGovernedActionService.class);
        McpActionExecutionGateway gateway = mock(McpActionExecutionGateway.class);
        ShopifyBridgeActionExecuteRequest request = new ShopifyBridgeActionExecuteRequest(
            "shopify_search_catalog",
            Map.of("query", "coffee"),
            null,
            Map.of("actionConfig", Map.of(
                "adapterType", "mcp-tool",
                "execution", Map.of("mcp", Map.of("serverRef", "shopify-storefront", "toolName", "search_catalog"))
            ))
        );
        ShopifyBridgeActionResult gatewayResult = ShopifyBridgeActionResult.ok(
            "MCP tool result",
            Map.of("adapterType", "mcp-tool", "mcpServerRef", "shopify-storefront", "mcpToolName", "search_catalog")
        );
        ShopifyBridgeActionExecutionService service = new ShopifyBridgeActionExecutionService(
            credentialService,
            graphqlClient,
            governedActionService,
            gateway
        );

        when(credentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.of(acquisition("alpha.myshopify.com")));
        when(gateway.supports(request)).thenReturn(true);
        when(gateway.execute("alpha.myshopify.com", request)).thenReturn(gatewayResult);

        ShopifyBridgeActionResult result = service.execute("alpha.myshopify.com", request);

        assertThat(result.success()).isTrue();
        assertThat(result.data()).containsEntry("adapterType", "mcp-tool");
        assertThat(result.data()).containsEntry("mcpToolName", "search_catalog");
        verify(gateway).execute("alpha.myshopify.com", request);
        verifyNoInteractions(graphqlClient, governedActionService);
    }

    @Test
    void legacyBridgeActionAliasesAreNotSupportedWithoutMarketplaceMcpConfig() {
        ShopifyBridgeInstallCredentialService credentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyAdminGraphqlClient graphqlClient = mock(ShopifyAdminGraphqlClient.class);
        ShopifyStorefrontGovernedActionService governedActionService = mock(ShopifyStorefrontGovernedActionService.class);
        McpActionExecutionGateway gateway = mock(McpActionExecutionGateway.class);
        ShopifyBridgeActionExecuteRequest request = new ShopifyBridgeActionExecuteRequest(
            "list_products",
            Map.of(),
            null,
            Map.of()
        );
        ShopifyBridgeActionExecutionService service = new ShopifyBridgeActionExecutionService(
            credentialService,
            graphqlClient,
            governedActionService,
            gateway
        );

        when(credentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.of(acquisition("alpha.myshopify.com")));
        when(gateway.supports(request)).thenReturn(false);

        ShopifyBridgeActionResult result = service.execute("alpha.myshopify.com", request);

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("ACTION_NOT_SUPPORTED");
        assertThat(result.message()).contains("mcp-tool");
        verifyNoInteractions(graphqlClient, governedActionService);
    }

    @Test
    void missingStoreCredentialsStillBlocksExecutionBeforeGateway() {
        ShopifyBridgeInstallCredentialService credentialService = mock(ShopifyBridgeInstallCredentialService.class);
        McpActionExecutionGateway gateway = mock(McpActionExecutionGateway.class);
        ShopifyBridgeActionExecutionService service = new ShopifyBridgeActionExecutionService(
            credentialService,
            mock(ShopifyAdminGraphqlClient.class),
            mock(ShopifyStorefrontGovernedActionService.class),
            gateway
        );

        when(credentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.empty());

        ShopifyBridgeActionResult result = service.execute(
            "alpha.myshopify.com",
            new ShopifyBridgeActionExecuteRequest(
                "shopify_search_catalog",
                Map.of("query", "coffee"),
                null,
                Map.of("actionConfig", Map.of(
                    "adapterType", "mcp-tool",
                    "execution", Map.of("mcp", Map.of("serverRef", "shopify-storefront", "toolName", "search_catalog"))
                ))
            )
        );

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("NOT_CONNECTED");
        verifyNoInteractions(gateway);
    }

    private static ShopifyBridgeCredentialAcquisition acquisition(String shopDomain) {
        return new ShopifyBridgeCredentialAcquisition(
            new ShopifyBridgeStoreSummary(
                "store-1",
                shopDomain,
                "Alpha",
                "shopify-bridge",
                "Shopify Bridge",
                "cust-1",
                "Customer",
                "dep-1",
                "Deployment",
                "ACTIVE",
                "consumer-1",
                "Consumer",
                "INSTALLED",
                "ONLINE",
                "READY",
                "ACTIVE",
                "READY",
                true,
                true,
                true,
                true,
                true,
                true,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.now(),
                Instant.now()
            ),
            new ShopifyTokenExchangeMaterial(
                "token-1",
                "offline",
                Instant.now(),
                null,
                "write_products",
                false
            )
        );
    }
}
