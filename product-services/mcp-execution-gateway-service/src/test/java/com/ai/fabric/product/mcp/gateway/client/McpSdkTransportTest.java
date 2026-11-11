package com.ai.fabric.product.mcp.gateway.client;

import com.ai.fabric.product.mcp.gateway.config.McpGatewayProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpSdkTransportTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<HttpServer> servers = new ArrayList<>();
    private final List<McpStreamableHttpClient> clients = new ArrayList<>();

    @AfterEach
    void closeFixtures() {
        clients.forEach(McpStreamableHttpClient::close);
        servers.forEach(server -> server.stop(0));
    }

    @Test
    void sseSkipsProgressAndUnrelatedResponsesUntilTheCorrelatedResult() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = fixture((exchange, request) -> {
            calls.incrementAndGet();
            ObjectNode result = response(request, mapper.readTree("{\"structuredContent\":{\"receipt\":\"done\"}}"));
            String events = ": keepalive\n\n"
                + "event: message\ndata: {\"jsonrpc\":\"2.0\",\"method\":\"notifications/progress\",\"params\":{\"progress\":1}}\n\n"
                + "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":\"another-request\",\"result\":{\"wrong\":true}}\n\n"
                + "event: message\ndata: " + mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result).replace("\n", "\ndata: ") + "\n\n";
            write(exchange, 200, "text/event-stream", events);
        });
        McpStreamableHttpClient client = client(Duration.ofSeconds(2), 4096);
        try (var session = client.initialize(endpoint(server), null)) {
            JsonNode result = client.toolsCall(session, "cancel_subscription", mapper.createObjectNode(), null);
            assertThat(result.path("structuredContent").path("receipt").asText()).isEqualTo("done");
        }
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void toolsListUsesParamsCursorAndRetainsShopifyExtensionAndSessionHeaders() throws Exception {
        AtomicReference<JsonNode> listed = new AtomicReference<>();
        AtomicReference<String> sessionHeader = new AtomicReference<>();
        AtomicReference<String> protocolHeader = new AtomicReference<>();
        HttpServer server = fixture((exchange, request) -> {
            listed.set(request);
            sessionHeader.set(exchange.getRequestHeaders().getFirst("MCP-Session-Id"));
            protocolHeader.set(exchange.getRequestHeaders().getFirst("MCP-Protocol-Version"));
            write(exchange, 200, "application/json", response(request, mapper.readTree("{\"tools\":[]}")).toString());
        });
        McpStreamableHttpClient client = client(Duration.ofSeconds(2), 4096);
        try (var session = client.initialize(endpoint(server), null)) {
            client.toolsList(session, "opaque:+/=", mapper.readTree("{\"meta\":{\"ucp-agent\":{\"profile\":\"https://example.com/profile\"}}}"), null);
        }
        assertThat(listed.get().path("params").path("cursor").asText()).isEqualTo("opaque:+/=");
        assertThat(listed.get().path("params").path("arguments").has("cursor")).isFalse();
        assertThat(listed.get().path("params").path("arguments").path("meta").path("ucp-agent").path("profile").asText()).isEqualTo("https://example.com/profile");
        assertThat(sessionHeader.get()).isEqualTo("fixture-session");
        assertThat(protocolHeader.get()).isEqualTo("2025-11-25");
    }

    @Test
    void negotiatesSupportedJuneProtocolOnceAndUsesItForLaterRequests() throws Exception {
        AtomicReference<String> protocolHeader = new AtomicReference<>();
        AtomicInteger listCalls = new AtomicInteger();
        HttpServer server = fixture((exchange, request) -> {
            listCalls.incrementAndGet();
            protocolHeader.set(exchange.getRequestHeaders().getFirst("MCP-Protocol-Version"));
            write(exchange, 200, "application/json", response(request, mapper.readTree("{\"tools\":[]}")).toString());
        }, "2025-06-18");
        McpStreamableHttpClient client = client(Duration.ofSeconds(2), 4096);
        try (var session = client.initialize(endpoint(server), null)) {
            assertThat(session.protocolVersion()).isEqualTo("2025-06-18");
            assertThat(client.toolsList(session, null).path("tools").isArray()).isTrue();
        }
        assertThat(protocolHeader.get()).isEqualTo("2025-06-18");
        assertThat(listCalls.get()).isEqualTo(1);
    }

    @Test
    void preservesProdus2024JsonHttpControllerWithoutSwitchingToHttpSseOrReplaying() throws Exception {
        AtomicInteger initializations = new AtomicInteger();
        AtomicInteger notifications = new AtomicInteger();
        AtomicInteger lists = new AtomicInteger();
        AtomicInteger calls = new AtomicInteger();
        List<String> paths = java.util.Collections.synchronizedList(new ArrayList<>());
        List<String> laterVersions = java.util.Collections.synchronizedList(new ArrayList<>());
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servers.add(server);
        server.createContext("/", exchange -> {
            paths.add(exchange.getRequestURI().getPath());
            try {
                if (!"/mcp".equals(exchange.getRequestURI().getPath()) || !"POST".equals(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }
                if (!"produs-fixture-key".equals(exchange.getRequestHeaders().getFirst("X-MCP-API-KEY"))) {
                    write(exchange, 401, "application/json", "{\"ready\":false,\"errorCode\":\"PRODUS_MCP_AUTH_REQUIRED\"}");
                    return;
                }
                JsonNode request = mapper.readTree(exchange.getRequestBody());
                JsonNode result;
                switch (request.path("method").asText()) {
                    case "initialize" -> {
                        initializations.incrementAndGet();
                        // Exact initialize envelope from ProdUS LoomAIMcpDiscoveryController, with no session header.
                        result = mapper.readTree("""
                            {"protocolVersion":"2024-11-05","capabilities":{"tools":{"listChanged":false}},
                             "serverInfo":{"name":"produs-loomai-productization","version":"1.0.0"}}
                            """);
                    }
                    case "tools/list" -> {
                        lists.incrementAndGet();
                        laterVersions.add(exchange.getRequestHeaders().getFirst("MCP-Protocol-Version"));
                        result = mapper.readTree("""
                            {"tools":[{"name":"produs.catalog.search","description":"Search approved lifecycle service categories, modules, dependencies, and package templates.",
                             "inputSchema":{"type":"object","additionalProperties":true},"annotations":{"mode":"read","confirmationRequired":false}}]}
                            """);
                    }
                    case "tools/call" -> {
                        calls.incrementAndGet();
                        laterVersions.add(exchange.getRequestHeaders().getFirst("MCP-Protocol-Version"));
                        result = mapper.readTree("{\"content\":[{\"type\":\"text\",\"text\":\"synthetic fixture only\"}]} ");
                    }
                    default -> {
                        notifications.incrementAndGet();
                        laterVersions.add(exchange.getRequestHeaders().getFirst("MCP-Protocol-Version"));
                        // The existing controller returns this JSON error for initialized notifications too.
                        ObjectNode error = mapper.createObjectNode().put("jsonrpc", "2.0").putNull("id");
                        error.putObject("error").put("code", -32601).put("message", "Method is not available during discovery");
                        write(exchange, 200, "application/json", error.toString());
                        return;
                    }
                }
                write(exchange, 200, "application/json", response(request, result).toString());
            } finally {
                exchange.close();
            }
        });
        server.start();
        McpStreamableHttpClient client = client(Duration.ofSeconds(2), 4096);
        var options = McpStreamableHttpClient.McpRequestOptions.withHeaders("2025-11-25", Map.of("X-MCP-API-KEY", "produs-fixture-key"));
        try (var session = client.initialize(endpoint(server), options)) {
            assertThat(session.protocolVersion()).isEqualTo("2024-11-05");
            assertThat(client.toolsList(session, options).path("tools").get(0).path("name").asText()).isEqualTo("produs.catalog.search");
            assertThat(client.toolsCall(session, "produs.catalog.search", mapper.createObjectNode(), options).path("content").size()).isEqualTo(1);
        }
        assertThat(initializations.get()).isEqualTo(1);
        assertThat(notifications.get()).isEqualTo(1);
        assertThat(lists.get()).isEqualTo(1);
        assertThat(calls.get()).isEqualTo(1);
        assertThat(laterVersions).containsOnly("2024-11-05");
        assertThat(paths).containsOnly("/mcp");
    }

    @Test
    void rejectsAnUnqualifiedServerVersionWithoutTryingOtherToolsOrTransports() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = fixture((exchange, request) -> calls.incrementAndGet(), "2099-01-01");
        McpStreamableHttpClient client = client(Duration.ofSeconds(2), 4096);
        assertThatThrownBy(() -> client.initialize(endpoint(server), null))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("unsupported protocol version");
        assertThat(calls.get()).isZero();
    }

    @Test
    void noAuthAndBusinessHeadersAreExplicitAndNeverImplicitlyMixed() throws Exception {
        List<Map<String, List<String>>> observed = new ArrayList<>();
        HttpServer server = fixture((exchange, request) -> {
            observed.add(Map.copyOf(exchange.getRequestHeaders()));
            write(exchange, 200, "application/json", response(request, mapper.createObjectNode()).toString());
        });
        McpStreamableHttpClient client = client(Duration.ofSeconds(2), 4096);
        client.toolsCall(endpoint(server), "read", mapper.createObjectNode(), null);
        client.toolsCall(endpoint(server), "read", mapper.createObjectNode(), McpStreamableHttpClient.McpRequestOptions.withHeaders(
            "2025-11-25", Map.of("Authorization", "Bearer business-token", "X-API-KEY", "company-key")));
        assertThat(observed.get(0).keySet()).noneMatch("Authorization"::equalsIgnoreCase);
        assertThat(observed.get(1).entrySet()).anySatisfy(entry -> {
            assertThat(entry.getKey()).isEqualToIgnoringCase("Authorization");
            assertThat(entry.getValue()).containsExactly("Bearer business-token");
        });
        assertThat(observed.get(1).keySet()).noneMatch("X-MCP-GATEWAY-API-KEY"::equalsIgnoreCase);
    }

    @Test
    void customBusinessHeaderRequiresGovernedConfigurationBeforeAnyRequest() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> auth = new AtomicReference<>();
        HttpServer server = fixture((exchange, request) -> {
            calls.incrementAndGet();
            auth.set(exchange.getRequestHeaders().getFirst("X-COMPANY-AUTH"));
            write(exchange, 200, "application/json", response(request, mapper.createObjectNode()).toString());
        });
        var options = McpStreamableHttpClient.McpRequestOptions.withHeaders("2025-11-25", Map.of("X-COMPANY-AUTH", "company-fixture-key"));
        McpStreamableHttpClient defaultClient = client(Duration.ofSeconds(2), 4096);
        assertThatThrownBy(() -> defaultClient.toolsCall(endpoint(server), "read", mapper.createObjectNode(), options))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("not allowlisted");
        assertThat(calls.get()).isZero();

        McpStreamableHttpClient configuredClient = client(Duration.ofSeconds(2), 4096, List.of("X-COMPANY-AUTH"));
        assertThat(configuredClient.toolsCall(endpoint(server), "read", mapper.createObjectNode(), options).isObject()).isTrue();
        assertThat(auth.get()).isEqualTo("company-fixture-key");
        assertThat(calls.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Host", "MCP-Protocol-Version", "Proxy-Authorization", "X-Forwarded-Port", "X-MCP-GATEWAY-API-KEY"})
    void configuredAllowlistCannotEnableRoutingProtocolOrInternalHeaders(String header) throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = fixture((exchange, request) -> calls.incrementAndGet());
        McpStreamableHttpClient client = client(Duration.ofSeconds(2), 4096, List.of(header));
        assertThatThrownBy(() -> client.toolsCall(endpoint(server), "read", mapper.createObjectNode(),
            McpStreamableHttpClient.McpRequestOptions.withHeaders("2025-11-25", Map.of(header, "fixture-value"))))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("not allowed");
        assertThat(calls.get()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"401", "403", "500", "wrong-id", "disconnect", "tool-error", "oversize"})
    void writeIsDispatchedOnceEvenWhenOutcomeFailsOrIsUncertain(String scenario) throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = fixture((exchange, request) -> {
            calls.incrementAndGet();
            switch (scenario) {
                case "401", "403", "500" -> write(exchange, Integer.parseInt(scenario), "application/json", "{\"private\":\"not echoed\"}");
                case "disconnect" -> exchange.close();
                case "tool-error" -> write(exchange, 200, "application/json", response(request, mapper.readTree("{\"isError\":true,\"content\":[]}")).toString());
                case "oversize" -> write(exchange, 200, "application/json", response(request, mapper.getNodeFactory().textNode("x".repeat(8192))).toString());
                default -> {
                    ObjectNode result = response(request, mapper.createObjectNode());
                    result.put("id", "unrelated-id");
                    write(exchange, 200, "application/json", result.toString());
                }
            }
        });
        McpStreamableHttpClient client = client(Duration.ofMillis(300), 4096);
        if ("tool-error".equals(scenario)) {
            assertThat(client.toolsCall(endpoint(server), "cancel_subscription", mapper.createObjectNode(), null).path("isError").asBoolean()).isTrue();
        } else {
            assertThatThrownBy(() -> client.toolsCall(endpoint(server), "cancel_subscription", mapper.createObjectNode(), null))
                .isInstanceOf(ResponseStatusException.class).hasMessageNotContaining("not echoed");
        }
        assertThat(calls.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403})
    void upstreamAuthorizationFailuresAreTypedWithoutOpeningAnInteractiveLogin(int status) throws Exception {
        HttpServer server = fixture((exchange, request) -> write(exchange, status, "application/json", "{\"token\":\"private\"}"));
        McpStreamableHttpClient client = client(Duration.ofMillis(500), 4096);
        assertThatThrownBy(() -> client.toolsCall(endpoint(server), "cancel_subscription", mapper.createObjectNode(), null))
            .isInstanceOfSatisfying(McpStreamableHttpClient.UpstreamAuthException.class, failure -> {
                assertThat(failure.errorCode()).isEqualTo(status == 401 ? "MCP_UPSTREAM_AUTH_REQUIRED" : "MCP_UPSTREAM_ACCESS_DENIED");
                assertThat(failure.getReason()).doesNotContain("private");
            });
    }

    @Test
    void timeoutDoesNotReconnectAndReplayAWrite() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = fixture((exchange, request) -> {
            calls.incrementAndGet();
            try {
                Thread.sleep(600);
                write(exchange, 200, "application/json", response(request, mapper.createObjectNode()).toString());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
        McpStreamableHttpClient client = client(Duration.ofMillis(100), 4096);
        assertThatThrownBy(() -> client.toolsCall(endpoint(server), "cancel_subscription", mapper.createObjectNode(), null))
            .isInstanceOf(ResponseStatusException.class);
        Thread.sleep(650);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void refusesCredentialedRedirectWithoutContactingTheRedirectTarget() throws Exception {
        AtomicInteger redirected = new AtomicInteger();
        HttpServer server = fixture((exchange, request) -> {
            exchange.getResponseHeaders().set("Location", "/capture");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/capture", exchange -> {
            redirected.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        McpStreamableHttpClient client = client(Duration.ofMillis(500), 4096);
        assertThatThrownBy(() -> client.toolsCall(endpoint(server), "read", mapper.createObjectNode(),
            McpStreamableHttpClient.McpRequestOptions.withHeaders("2025-11-25", Map.of("Authorization", "Bearer company-secret"))))
            .isInstanceOf(ResponseStatusException.class);
        assertThat(redirected.get()).isZero();
    }

    @Test
    void legacyHttpSseUsesMaintainedAdapterWithSameOriginMessageEndpoint() throws Exception {
        AtomicReference<OutputStream> stream = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servers.add(server);
        server.createContext("/sse", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            stream.set(exchange.getResponseBody());
            stream.get().write("event: endpoint\ndata: /messages?session=fixture\n\n".getBytes(StandardCharsets.UTF_8));
            stream.get().flush();
        });
        server.createContext("/messages", exchange -> {
            JsonNode request = mapper.readTree(exchange.getRequestBody());
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
            if (request.has("id")) {
                JsonNode result = "initialize".equals(request.path("method").asText())
                    ? mapper.readTree("{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{\"tools\":{}}}") : mapper.readTree("{\"tools\":[]}");
                synchronized (stream) {
                    stream.get().write(("event: message\ndata: " + response(request, result) + "\n\n").getBytes(StandardCharsets.UTF_8));
                    stream.get().flush();
                }
            }
        });
        server.start();
        McpStreamableHttpClient client = client(Duration.ofSeconds(2), 4096);
        URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/sse");
        var options = new McpStreamableHttpClient.McpRequestOptions("2025-11-25", Map.of(), "HTTP_SSE");
        try (var session = client.initialize(endpoint, options)) {
            assertThat(session.protocolVersion()).isEqualTo("2024-11-05");
            assertThat(client.toolsList(session, options).path("tools").isArray()).isTrue();
        }
    }

    private HttpServer fixture(RequestHandler handler) throws IOException {
        return fixture(handler, "2025-11-25");
    }

    private HttpServer fixture(RequestHandler handler, String protocolVersion) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servers.add(server);
        server.createContext("/mcp", exchange -> {
            try {
                if (!"POST".equals(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders("DELETE".equals(exchange.getRequestMethod()) ? 204 : 405, -1);
                    return;
                }
                JsonNode request = mapper.readTree(exchange.getRequestBody());
                if (!request.has("id")) {
                    exchange.sendResponseHeaders(202, -1);
                } else if ("initialize".equals(request.path("method").asText())) {
                    exchange.getResponseHeaders().set("MCP-Session-Id", "fixture-session");
                    write(exchange, 200, "application/json", response(request,
                        mapper.valueToTree(Map.of("protocolVersion", protocolVersion, "capabilities", Map.of("tools", Map.of())))).toString());
                } else {
                    handler.handle(exchange, request);
                }
            } finally {
                exchange.close();
            }
        });
        server.start();
        return server;
    }

    private McpStreamableHttpClient client(Duration timeout, int maxBytes) {
        return client(timeout, maxBytes, List.of());
    }

    private McpStreamableHttpClient client(Duration timeout, int maxBytes, List<String> allowedHeaders) {
        var properties = new McpGatewayProperties("test", "test", "internal", "X-MCP-GATEWAY-API-KEY", "2025-11-25",
            allowedHeaders, List.of(), false, "MCP_SECRET_", Duration.ofSeconds(1), timeout,
            maxBytes, 500, 100, 8 * 1024 * 1024, Duration.ofSeconds(60), 8);
        var client = new McpTestClients(mapper, properties);
        clients.add(client);
        return client;
    }

    private ObjectNode response(JsonNode request, JsonNode result) {
        ObjectNode response = mapper.createObjectNode().put("jsonrpc", "2.0");
        response.set("id", request.path("id"));
        response.set("result", result);
        return response;
    }

    private URI endpoint(HttpServer server) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/mcp");
    }

    private void write(HttpExchange exchange, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @FunctionalInterface
    interface RequestHandler {
        void handle(HttpExchange exchange, JsonNode request) throws IOException;
    }
}
