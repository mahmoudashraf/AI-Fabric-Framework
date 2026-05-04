package com.ai.fabric.product.shopify.bridge.mcp.execution;

import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionExecuteRequest;
import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionResult;
import com.ai.fabric.product.shopify.bridge.config.McpExecutionGatewayProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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
}
