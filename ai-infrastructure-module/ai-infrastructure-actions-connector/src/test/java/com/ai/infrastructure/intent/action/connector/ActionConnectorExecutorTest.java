package com.ai.infrastructure.intent.action.connector;

import com.ai.infrastructure.http.OutboundHttpExecutionRequest;
import com.ai.infrastructure.http.OutboundHttpExecutionResponse;
import com.ai.infrastructure.http.OutboundHttpExecutor;
import com.ai.infrastructure.intent.action.ActionAccessMode;
import com.ai.infrastructure.intent.action.ActionContext;
import com.ai.infrastructure.intent.action.ActionListPayload;
import com.ai.infrastructure.intent.action.ActionObjectPayload;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.OrchestrationContextMetadataKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ActionConnectorExecutorTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void execute_shouldParseObjectPayloadSuccess() {
        StubHttpClient stub = new StubHttpClient(List.of(
            new OutboundHttpExecutionResponse(200, "{\"success\":true,\"message\":\"ok\",\"data\":{\"orderRef\":\"PO-1\"}}", Map.of())
        ));

        ActionConnectorExecutor executor = new ActionConnectorExecutor(
            connectorProps("https://example", 1, Duration.ZERO),
            stub,
            null,
            fixedClock()
        );

        ActionResult result = executor.execute(
            "create_purchase_order",
            ActionAccessMode.WRITE_ONLY,
            Map.of("sku", "SKU-1", "quantity", 1),
            testContext()
        );

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("ok");
        assertThat(result.getData()).isInstanceOf(ActionObjectPayload.class);
        assertThat(result.getData().toMap()).containsEntry("orderRef", "PO-1");
        assertThat(stub.lastRequestBody()).isNotBlank();
        Map<String, Object> request = readRequest(stub.lastRequestBody());
        @SuppressWarnings("unchecked")
        Map<String, Object> trace = (Map<String, Object>) request.get(ActionConnectorProtocol.KEY_TRACE);
        assertThat(trace)
            .containsEntry(ActionConnectorProtocol.TRACE_REQUEST_ID, "r1")
            .containsEntry(ActionConnectorProtocol.TRACE_CONVERSATION_ID, "c1")
            .containsEntry(ActionConnectorProtocol.TRACE_USER_ID, "user@example.com")
            .containsEntry(ActionConnectorProtocol.TRACE_SESSION_ID, "s1");
        @SuppressWarnings("unchecked")
        Map<String, Object> authContext = (Map<String, Object>) trace.get(ActionConnectorProtocol.TRACE_AUTH_CONTEXT);
        assertThat(authContext)
            .containsEntry("subjectId", "user@example.com")
            .containsEntry("subjectType", "INTERNAL_PLATFORM_USER")
            .containsEntry("authMode", "PLATFORM_PROXY_SESSION")
            .containsEntry("callerType", "PLATFORM_PROXY")
            .containsEntry("sessionId", "s1")
            .containsEntry("deploymentId", "dep-1")
            .containsEntry("customerId", "cust-1")
            .containsEntry("tenantId", "ten-1")
            .containsEntry("issuer", "platform-poc:SESSION");
    }

    @Test
    void execute_shouldParseListPayloadWithCursorAndTotalCount() {
        StubHttpClient stub = new StubHttpClient(List.of(
            new OutboundHttpExecutionResponse(
                200,
                """
                    {"success":true,"message":"Products","data":{"_count":2,"_items":[{"id":"p1"},{"id":"p2"}],"_totalCount":10,"_cursor":"abc","note":"x"}}
                    """.trim(),
                Map.of()
            )
        ));

        ActionConnectorExecutor executor = new ActionConnectorExecutor(
            connectorProps("https://example", 1, Duration.ZERO),
            stub,
            null,
            fixedClock()
        );

        ActionResult result = executor.execute(
            "list_products",
            ActionAccessMode.READ,
            Map.of("q", "sony"),
            testContext()
        );

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isInstanceOf(ActionListPayload.class);
        Map<String, Object> map = result.getData().toMap();
        assertThat(map).containsEntry("_count", 2);
        assertThat(map).containsEntry("_totalCount", 10L);
        assertThat(map).containsEntry("_cursor", "abc");
        assertThat(map).containsEntry("note", "x");
    }

    @Test
    void execute_shouldIncludeRuntimeActionConfigInTrace() {
        StubHttpClient stub = new StubHttpClient(List.of(
            new OutboundHttpExecutionResponse(200, "{\"success\":true,\"message\":\"ok\",\"data\":{}}", Map.of())
        ));
        AIActionConnectorProperties props = connectorProps("https://example", 1, Duration.ZERO);
        props.getMcpGateway().setBaseUrl("https://mcp-gateway.internal");
        props.getMcpGateway().setApiKey("gateway-secret");
        ActionConnectorExecutor executor = new ActionConnectorExecutor(
            props,
            stub,
            null,
            fixedClock()
        );

        executor.execute(
            "inventory_search",
            ActionAccessMode.READ,
            Map.of("query", "bag"),
            testContext(),
            Map.of(
                "adapterType", "mcp-tool",
                "execution", Map.of("mcp", Map.of("serverRef", "inventory-mcp", "toolName", "inventory.search")),
                "mcpServers", Map.of("inventory-mcp", Map.of("endpointUrl", "https://inventory.example/mcp"))
            )
        );

        Map<String, Object> request = readRequest(stub.lastRequestBody());
        assertThat(stub.lastRequest().url()).isEqualTo("https://mcp-gateway.internal/api/internal/mcp/actions/execute");
        assertThat(stub.lastRequest().headers()).containsEntry("X-MCP-GATEWAY-API-KEY", "gateway-secret");
        @SuppressWarnings("unchecked")
        Map<String, Object> trace = (Map<String, Object>) request.get(ActionConnectorProtocol.KEY_TRACE);
        @SuppressWarnings("unchecked")
        Map<String, Object> actionConfig = (Map<String, Object>) trace.get("actionConfig");
        assertThat(actionConfig).containsEntry("adapterType", "mcp-tool");
        assertThat(actionConfig).containsKeys("execution", "mcpServers");
    }

    @Test
    void execute_shouldFailClosedForMcpToolWhenGatewayApiKeyMissing() {
        StubHttpClient stub = new StubHttpClient(List.of());
        AIActionConnectorProperties props = connectorProps("https://example", 1, Duration.ZERO);
        props.getMcpGateway().setBaseUrl("https://mcp-gateway.internal");
        ActionConnectorExecutor executor = new ActionConnectorExecutor(
            props,
            stub,
            null,
            fixedClock()
        );

        ActionResult result = executor.execute(
            "inventory_search",
            ActionAccessMode.READ,
            Map.of("query", "bag"),
            testContext(),
            Map.of(
                "adapterType", "mcp-tool",
                "execution", Map.of("mcp", Map.of("serverRef", "inventory-mcp", "toolName", "inventory.search")),
                "mcpServers", Map.of("inventory-mcp", Map.of("endpointUrl", "https://inventory.example/mcp"))
            )
        );

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("INVALID_CONFIGURATION");
        assertThat(result.getMessage()).contains("mcp-gateway.api-key");
        assertThat(stub.callCount()).isZero();
    }

    @Test
    void execute_shouldRetryOnRetryableErrorCodeWhenIdempotent() {
        StubHttpClient stub = new StubHttpClient(List.of(
            new OutboundHttpExecutionResponse(200, "{\"success\":false,\"errorCode\":\"SERVICE_UNAVAILABLE\",\"message\":\"temp\"}", Map.of()),
            new OutboundHttpExecutionResponse(200, "{\"success\":true,\"message\":\"ok\",\"data\":{\"orderRef\":\"PO-2\"}}", Map.of())
        ));

        ActionConnectorExecutor executor = new ActionConnectorExecutor(
            connectorProps("https://example", 2, Duration.ZERO),
            stub,
            null,
            fixedClock()
        );

        ActionResult result = executor.execute(
            "create_purchase_order",
            ActionAccessMode.WRITE_ONLY,
            Map.of("sku", "SKU-2", "quantity", 1),
            testContext()
        );

        assertThat(result.isSuccess()).isTrue();
        assertThat(stub.callCount()).isEqualTo(2);
    }

    private static AIActionConnectorProperties connectorProps(String baseUrl, int maxAttempts, Duration initialBackoff) {
        AIActionConnectorProperties props = new AIActionConnectorProperties();
        props.setBaseUrl(baseUrl);
        props.setMaxAttempts(maxAttempts);
        props.setInitialBackoff(initialBackoff);
        props.setConnectTimeout(Duration.ofMillis(1));
        props.setReadTimeout(Duration.ofMillis(1));
        return props;
    }

    private static Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-02-11T00:00:00Z"), ZoneOffset.UTC);
    }

    private static ActionContext testContext() {
        OrchestrationContext orch = OrchestrationContext.builder()
            .userId("user@example.com")
            .sessionId("s1")
            .conversationId("c1")
            .requestId("r1")
            .metadata(Map.of(
                OrchestrationContextMetadataKeys.SUBJECT_ID, "user@example.com",
                OrchestrationContextMetadataKeys.SUBJECT_TYPE, "INTERNAL_PLATFORM_USER",
                OrchestrationContextMetadataKeys.AUTH_MODE, "PLATFORM_PROXY_SESSION",
                OrchestrationContextMetadataKeys.CALLER_TYPE, "PLATFORM_PROXY",
                OrchestrationContextMetadataKeys.DEPLOYMENT_ID, "dep-1",
                OrchestrationContextMetadataKeys.CUSTOMER_ID, "cust-1",
                OrchestrationContextMetadataKeys.TENANT_ID, "ten-1",
                OrchestrationContextMetadataKeys.AUTH_ISSUER, "platform-poc:SESSION"
            ))
            .build();
        return new ActionContext(orch, null);
    }

    private static Map<String, Object> readRequest(String body) {
        try {
            return OBJECT_MAPPER.readValue(body, Map.class);
        } catch (Exception ex) {
            throw new AssertionError("Failed to parse connector request JSON", ex);
        }
    }

    private static final class StubHttpClient implements OutboundHttpExecutor {
        private final List<OutboundHttpExecutionResponse> responses;
        private final AtomicInteger calls = new AtomicInteger(0);
        private volatile OutboundHttpExecutionRequest lastRequest;
        private volatile String lastRequestBody;

        private StubHttpClient(List<OutboundHttpExecutionResponse> responses) {
            this.responses = responses != null ? new ArrayList<>(responses) : List.of();
        }

        @Override
        public OutboundHttpExecutionResponse execute(OutboundHttpExecutionRequest request) {
            int idx = calls.getAndIncrement();
            lastRequest = request;
            lastRequestBody = request != null ? request.body() : null;
            return responses.get(Math.min(idx, responses.size() - 1));
        }

        int callCount() {
            return calls.get();
        }

        String lastRequestBody() {
            return lastRequestBody;
        }

        OutboundHttpExecutionRequest lastRequest() {
            return lastRequest;
        }
    }
}
