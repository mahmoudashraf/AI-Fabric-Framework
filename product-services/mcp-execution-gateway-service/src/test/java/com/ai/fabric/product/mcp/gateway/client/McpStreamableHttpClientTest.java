package com.ai.fabric.product.mcp.gateway.client;

import com.ai.fabric.product.mcp.gateway.config.McpGatewayProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;

class McpStreamableHttpClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void initializeAppliesConfiguredReadTimeout() throws Exception {
        HttpServer server = slowMcpServer(Duration.ofMillis(1_500));
        try {
            McpStreamableHttpClient client = new McpStreamableHttpClient(
                RestClient.builder(),
                objectMapper,
                properties(Duration.ofMillis(100), Duration.ofMillis(100))
            );

            long started = System.nanoTime();
            assertThatThrownBy(() -> client.initialize(endpoint(server), null))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(BAD_GATEWAY);
                    assertThat(exception).hasMessageContaining("MCP server request failed");
                });
            Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
            assertThat(elapsed).isLessThan(Duration.ofMillis(1_200));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void initializeFailsExplicitlyOnRedirect() throws Exception {
        HttpServer server = redirectingMcpServer();
        try {
            McpStreamableHttpClient client = new McpStreamableHttpClient(
                RestClient.builder(),
                objectMapper,
                properties(Duration.ofMillis(500), Duration.ofMillis(500))
            );

            assertThatThrownBy(() -> client.initialize(endpoint(server), null))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(BAD_GATEWAY);
                    assertThat(exception).hasMessageContaining("MCP server returned HTTP 302 redirect to /password");
                });
        } finally {
            server.stop(0);
        }
    }

    private HttpServer slowMcpServer(Duration delay) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mcp", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            try {
                Thread.sleep(delay.toMillis());
                byte[] response = """
                    {
                      "jsonrpc": "2.0",
                      "id": 1,
                      "result": {
                        "protocolVersion": "2025-11-25",
                        "capabilities": {},
                        "serverInfo": {
                          "name": "slow-test-mcp",
                          "version": "1.0.0"
                        }
                      }
                    }
                    """.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        return server;
    }

    private HttpServer redirectingMcpServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mcp", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            exchange.getResponseHeaders().add("Location", "/password");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
        return server;
    }

    private URI endpoint(HttpServer server) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/mcp");
    }

    private McpGatewayProperties properties(Duration connectTimeout, Duration readTimeout) {
        return new McpGatewayProperties(
            "mcp-execution-gateway-test",
            "test",
            "gateway-key",
            "X-MCP-GATEWAY-API-KEY",
            "2025-11-25",
            List.of(),
            List.of(),
            false,
            "MCP_SECRET_",
            connectTimeout,
            readTimeout
        );
    }
}
