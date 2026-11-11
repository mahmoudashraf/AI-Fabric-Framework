package com.ai.fabric.product.mcp.gateway.service;

import com.ai.fabric.product.mcp.gateway.client.McpStreamableHttpClient;
import com.ai.fabric.product.mcp.gateway.client.McpTestClients;
import com.ai.fabric.product.mcp.gateway.config.McpGatewayProperties;
import com.ai.fabric.product.mcp.gateway.model.McpGatewayContracts.ActionExecuteRequest;
import com.ai.fabric.product.mcp.gateway.model.McpGatewayContracts.ActionExecuteResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class McpWriteOutcomeTest {
    private static final URI DECLARED_ENDPOINT = URI.create("https://1.1.1.1/mcp");
    private final ObjectMapper mapper = new ObjectMapper();
    private final McpGatewayProperties properties = new McpGatewayProperties("test", "test", "fixture", "X-MCP-GATEWAY-API-KEY",
        "2025-11-25", List.of(), List.of(), false, "MCP_SECRET_", Duration.ofSeconds(1), Duration.ofSeconds(1));
    private final List<McpStreamableHttpClient> clients = new ArrayList<>();
    private final List<HttpServer> servers = new ArrayList<>();

    @AfterEach
    void cleanup() {
        clients.forEach(McpStreamableHttpClient::close);
        servers.forEach(server -> server.stop(0));
    }

    @ParameterizedTest
    @CsvSource({"WRITE_ONLY,disconnect", "READ_WRITE,disconnect", "WRITE_ONLY,malformed", "READ_WRITE,malformed",
        "WRITE_ONLY,500", "READ_WRITE,500"})
    void postCommitResponseLossIsUnknownAndNeverReplayed(String accessMode, String scenario) throws Exception {
        Fixture fixture = fixture(scenario);
        ActionExecuteResponse response = execute(fixture, configuration(accessMode, false));
        assertThat(response.success()).isFalse();
        assertThat(response.errorCode()).isEqualTo("MCP_EXECUTION_OUTCOME_UNKNOWN");
        assertThat(response.message()).contains("may have completed", "Check the target system before retrying", "did not retry");
        assertThat(fixture.calls.get()).isEqualTo(1);
        assertThat(fixture.completedOperations.get()).isEqualTo(1);
    }

    @Test
    void directCheckoutWriteAlsoReportsUnknownAfterPostCommitDisconnect() throws Exception {
        Fixture fixture = fixture("disconnect");
        ActionExecuteResponse response = execute(fixture, configuration("WRITE_ONLY", true));
        assertThat(response.errorCode()).isEqualTo("MCP_EXECUTION_OUTCOME_UNKNOWN");
        assertThat(fixture.calls.get()).isEqualTo(1);
        assertThat(fixture.completedOperations.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403})
    void explicitUpstreamDenialRemainsKnownAndDoesNotReplay(int status) throws Exception {
        Fixture fixture = fixture(Integer.toString(status));
        ActionExecuteResponse response = execute(fixture, configuration("READ_WRITE", false));
        assertThat(response.errorCode()).isEqualTo(status == 401 ? "MCP_UPSTREAM_AUTH_REQUIRED" : "MCP_UPSTREAM_ACCESS_DENIED");
        assertThat(fixture.calls.get()).isEqualTo(1);
        assertThat(fixture.completedOperations.get()).isZero();
    }

    @Test
    void inputSchemaFailureIsKnownBeforeDispatch() throws Exception {
        Fixture fixture = fixture("disconnect");
        Map<String, Object> config = configuration("WRITE_ONLY", false);
        config.put("inputSchema", Map.of("type", "object", "required", List.of("target")));
        assertThat(execute(fixture, config).errorCode()).isEqualTo("INVALID_MCP_ACTION_ARGUMENTS");
        assertThat(fixture.calls.get()).isZero();
        assertThat(fixture.completedOperations.get()).isZero();
    }

    @Test
    void missingCredentialIsKnownBeforeDispatch() throws Exception {
        Fixture fixture = fixture("disconnect");
        Map<String, Object> config = configuration("WRITE_ONLY", false);
        config.put("mcpServers", Map.of("fixture", Map.of("endpointUrl", DECLARED_ENDPOINT.toString(), "auth", Map.of(
            "mode", "BEARER_TOKEN_SECRET_REF", "secretRef", "MISSING_FIXTURE_SECRET"))));
        assertThat(execute(fixture, config).errorCode()).isEqualTo("INVALID_MCP_ACTION_CONFIG");
        assertThat(fixture.calls.get()).isZero();
        assertThat(fixture.completedOperations.get()).isZero();
    }

    @Test
    void sdkLocalConfigurationErrorInDirectPathIsKnownBeforeWireDispatch() throws Exception {
        Fixture fixture = fixture("disconnect");
        Map<String, Object> config = configuration("WRITE_ONLY", true);
        config.put("mcpServers", Map.of("fixture", Map.of("endpointUrl", DECLARED_ENDPOINT.toString(), "transport", "UNSUPPORTED")));
        assertThat(execute(fixture, config).errorCode()).isEqualTo("INVALID_MCP_ACTION_CONFIG");
        assertThat(fixture.calls.get()).isZero();
        assertThat(fixture.completedOperations.get()).isZero();
    }

    @Test
    void outputMismatchKeepsItsCodeAndWarnsThatTheOperationMayHaveCompleted() throws Exception {
        Fixture fixture = fixture("invalid-output");
        Map<String, Object> config = configuration("READ_WRITE", false);
        config.put("outputSchema", Map.of("type", "object", "required", List.of("receipt"),
            "properties", Map.of("receipt", Map.of("type", "string"))));
        ActionExecuteResponse response = execute(fixture, config);
        assertThat(response.errorCode()).isEqualTo("MCP_TOOL_OUTPUT_SCHEMA_MISMATCH");
        assertThat(response.message()).contains("operation may have completed", "Check the target system before retrying");
        assertThat(fixture.calls.get()).isEqualTo(1);
        assertThat(fixture.completedOperations.get()).isEqualTo(1);
    }

    @Test
    void readTransportFailureDoesNotClaimAnUncertainWrite() throws Exception {
        Fixture fixture = fixture("disconnect");
        assertThat(execute(fixture, configuration("READ", false)).errorCode()).isEqualTo("MCP_EXECUTION_FAILED");
        assertThat(fixture.calls.get()).isEqualTo(1);
    }

    private Fixture fixture(String scenario) throws IOException {
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servers.add(server);
        server.createContext("/mcp", exchange -> {
            try {
                if (!"POST".equals(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }
                JsonNode request = mapper.readTree(exchange.getRequestBody());
                if (!request.has("id")) {
                    exchange.sendResponseHeaders(202, -1);
                    return;
                }
                if ("initialize".equals(request.path("method").asText())) {
                    ObjectNode result = mapper.createObjectNode().put("protocolVersion", "2025-11-25");
                    result.putObject("capabilities").putObject("tools");
                    respond(exchange, request, result);
                    return;
                }
                calls.incrementAndGet();
                if ("401".equals(scenario) || "403".equals(scenario)) {
                    exchange.sendResponseHeaders(Integer.parseInt(scenario), -1);
                    return;
                }
                completed.incrementAndGet(); // Record the synthetic receiver commit before losing or corrupting its response.
                switch (scenario) {
                    case "disconnect" -> { }
                    case "malformed" -> write(exchange, 200, "{\"jsonrpc\":");
                    case "500" -> write(exchange, 500, "{\"error\":\"response lost after commit\"}");
                    default -> respond(exchange, request, mapper.readTree("{\"structuredContent\":{\"receipt\":123}}"));
                }
            } finally {
                exchange.close();
            }
        });
        server.start();
        return new Fixture(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/mcp"), calls, completed);
    }

    private ActionExecuteResponse execute(Fixture fixture, Map<String, Object> config) {
        // Relocate only the declared test endpoint to the isolated receiver; no request is made to the public literal.
        McpStreamableHttpClient client = new McpTestClients(mapper, properties) {
            @Override public McpSession initialize(URI endpoint, McpRequestOptions options) {
                assertThat(endpoint).isEqualTo(DECLARED_ENDPOINT);
                return super.initialize(fixture.endpoint, options);
            }
            @Override public JsonNode toolsCall(URI endpoint, String name, JsonNode args, McpRequestOptions options) {
                assertThat(endpoint).isEqualTo(DECLARED_ENDPOINT);
                return super.toolsCall(fixture.endpoint, name, args, options);
            }
        };
        clients.add(client);
        return new McpGatewayExecutionService(client, mapper, properties).executeAction(new ActionExecuteRequest(
            "fixture_operation", Map.of(), "fixture-key-does-not-prove-deduplication", Map.of(), config));
    }

    private Map<String, Object> configuration(String accessMode, boolean direct) {
        Map<String, Object> mcp = new LinkedHashMap<>(Map.of("serverRef", "fixture", "toolName", "fixture.operation"));
        if (direct) mcp.put("endpointKind", "CHECKOUT_UCP");
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("accessMode", accessMode);
        config.put("execution", Map.of("adapterType", "mcp-tool", "mcp", mcp));
        config.put("mcpServers", Map.of("fixture", Map.of("endpointUrl", DECLARED_ENDPOINT.toString(), "auth", Map.of("mode", "NONE"))));
        return config;
    }

    private void respond(HttpExchange exchange, JsonNode request, JsonNode result) throws IOException {
        ObjectNode response = mapper.createObjectNode().put("jsonrpc", "2.0");
        response.set("id", request.path("id"));
        response.set("result", result);
        write(exchange, 200, response.toString());
    }

    private void write(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private record Fixture(URI endpoint, AtomicInteger calls, AtomicInteger completedOperations) { }
}
