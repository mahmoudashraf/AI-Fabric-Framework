package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformProductProvisioningProperties;
import com.ai.fabric.platform.backend.marketplace.model.MarketplaceMcpDiscoveryRequest;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginRepository;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginVersionRepository;
import com.ai.fabric.platform.backend.productservice.entity.PlatformManagedProductServiceEntity;
import com.ai.fabric.platform.backend.productservice.service.PlatformManagedProductServiceService;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketplaceMcpDiscoveryServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void discoverResolvesPlatformSecretRefsAndStripsCallerSuppliedSecretValues() throws Exception {
        AtomicReference<JsonNode> gatewayRequest = new AtomicReference<>();
        HttpServer gateway = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        gateway.createContext("/api/internal/mcp/import/discover", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("X-MCP-GATEWAY-API-KEY")).isEqualTo("gateway-secret");
            gatewayRequest.set(objectMapper.readTree(exchange.getRequestBody().readAllBytes()));
            byte[] response = """
                {
                  "ready": true,
                  "message": "ok",
                  "serverRef": "inventory-mcp",
                  "endpointUrl": "https://inventory.example/mcp",
                  "protocolVersion": "2025-11-25",
                  "tools": [
                    {
                      "name": "inventory.search",
                      "title": "Inventory search",
                      "description": "Search inventory.",
                      "inputSchema": {"type": "object"},
                      "outputSchema": {},
                      "schemaHash": "sha256:abc"
                    }
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        gateway.start();
        try {
            PlatformManagedProductServiceEntity gatewayService = new PlatformManagedProductServiceEntity();
            gatewayService.setServiceRef("mcp-execution-gateway");
            gatewayService.setBaseUrl("http://127.0.0.1:" + gateway.getAddress().getPort());
            gatewayService.setSecretName("MCP_GATEWAY_INTERNAL_API_KEY");

            PlatformManagedProductServiceService productServiceService = mock(PlatformManagedProductServiceService.class);
            when(productServiceService.requireService("mcp-execution-gateway")).thenReturn(gatewayService);
            PlatformSecretService secretService = mock(PlatformSecretService.class);
            when(secretService.resolveSecret("MCP_GATEWAY_INTERNAL_API_KEY")).thenReturn("gateway-secret");
            when(secretService.resolveSecret("MCP_SECRET_VENDOR_TOKEN")).thenReturn("resolved-vendor-token");

            MarketplaceMcpDiscoveryService service = new MarketplaceMcpDiscoveryService(
                properties(),
                productServiceService,
                secretService,
                mock(MarketplacePluginRepository.class),
                mock(MarketplacePluginVersionRepository.class),
                mock(MarketplaceManifestService.class),
                mock(PlatformAuditService.class),
                objectMapper
            );

            var summary = service.discover(new MarketplaceMcpDiscoveryRequest(
                "inventory-mcp",
                Map.of(
                    "endpointUrl", "https://inventory.example/mcp",
                    "auth", Map.of(
                        "mode", "API_KEY_HEADER_SECRET",
                        "headerName", "X-MCP-API-KEY",
                        "secretRef", "MCP_SECRET_VENDOR_TOKEN",
                        "resolvedSecretValue", "caller-supplied-secret",
                        "authorization", "Bearer caller-supplied-secret"
                    )
                ),
                Map.of(
                    "mcpSecretValues", Map.of("MCP_SECRET_VENDOR_TOKEN", "caller-supplied-secret"),
                    "secretValues", Map.of("OTHER_SECRET", "caller-supplied-secret"),
                    "traceId", "trace-1"
                ),
                List.of("inventory.search"),
                null
            ));

            assertThat(summary.ready()).isTrue();
            JsonNode request = gatewayRequest.get();
            assertThat(request.path("trace").path("traceId").asText()).isEqualTo("trace-1");
            assertThat(request.path("trace").path("mcpSecretValues").path("MCP_SECRET_VENDOR_TOKEN").asText())
                .isEqualTo("resolved-vendor-token");
            assertThat(request.path("trace").has("secretValues")).isFalse();
            assertThat(request.path("trace").path("mcpSecretValues").path("OTHER_SECRET").isMissingNode()).isTrue();
            assertThat(request.path("server").path("auth").has("resolvedSecretValue")).isFalse();
            assertThat(request.path("server").path("auth").has("authorization")).isFalse();
        } finally {
            gateway.stop(0);
        }
    }

    private PlatformProductProvisioningProperties properties() {
        return new PlatformProductProvisioningProperties(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            Duration.ofSeconds(1),
            Duration.ofSeconds(5)
        );
    }
}
