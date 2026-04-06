package com.ai.fabric.runtime;

import com.ai.fabric.runtime.admin.RuntimeConnectorAdminProxyService;
import com.ai.infrastructure.intent.action.connector.AIActionConnectorProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeConnectorAdminProxyServiceTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void forwardsConnectorAdminGetRequestsWithConfiguredApiKey() throws Exception {
        AtomicReference<String> observedPath = new AtomicReference<>();
        AtomicReference<String> observedApiKey = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/admin/overview", exchange -> {
            observedPath.set(exchange.getRequestURI().getPath());
            observedApiKey.set(exchange.getRequestHeaders().getFirst("X-AIFABRIC-API-KEY"));
            writeJson(exchange, 200, "{\"success\":true,\"surface\":\"connector-overview\"}");
        });
        server.start();

        AIActionConnectorProperties properties = new AIActionConnectorProperties();
        properties.setBaseUrl("http://localhost:" + server.getAddress().getPort());
        properties.setConnectTimeout(Duration.ofSeconds(2));
        properties.setReadTimeout(Duration.ofSeconds(2));
        properties.getApiKey().setHeader("X-AIFABRIC-API-KEY");
        properties.getApiKey().setValue("connector-secret");

        RuntimeConnectorAdminProxyService service = new RuntimeConnectorAdminProxyService(properties);
        RuntimeConnectorAdminProxyService.ProxyResponse response = service.forwardGet("/api/admin/overview");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.contentType()).contains("application/json");
        assertThat(response.body()).contains("\"surface\":\"connector-overview\"");
        assertThat(observedPath.get()).isEqualTo("/api/admin/overview");
        assertThat(observedApiKey.get()).isEqualTo("connector-secret");
    }

    @Test
    void returnsServiceUnavailableWhenConnectorBaseUrlIsMissing() {
        RuntimeConnectorAdminProxyService service = new RuntimeConnectorAdminProxyService(new AIActionConnectorProperties());

        RuntimeConnectorAdminProxyService.ProxyResponse response = service.forwardGet("/api/admin/overview");

        assertThat(response.status()).isEqualTo(503);
        assertThat(response.body()).contains("baseUrl is not configured");
    }

    private void writeJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }
}
